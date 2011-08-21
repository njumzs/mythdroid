#!/usr/bin/perl

=begin comment

MythDroid: Android MythTV Remote
Copyright (C) 2009-2010 foobum@gmail.com

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

=end comment
=cut

$VERSION = 0.5.0;

use strict;
use warnings;
use IO::Socket::INET;
use IO::Select;
use POSIX qw(setsid);
use Sys::Hostname;
use Config;
use MDD::ConfigData;
use MDD::LCD;
use MDD::MythDB;
use MDD::Log;
use MDD::CMux;
eval "use MDD::XOSD";

sub usage();
sub handleMdConn($);
sub handleDisconnect($);
sub readConfig();
sub clientMsg($);
sub sendMsg($);
sub sendMsgs($);
sub listCommands();
sub runCommand($);
sub osdMsg($);
sub videoList($);
sub streamFile($);
sub stopStreaming();
sub getStorGroups();
sub getRecGroups();
sub killKids();

my $lcdServerPort = 6545;
my $listenPort    = 16546;

my $mpid = $$;

my %config;
my ($backend, $debug);

# Check for and strip arguments intended for us
foreach my $idx (0 .. $#ARGV) {

    next unless exists $ARGV[$idx];

    if ($ARGV[$idx] eq '-h' || $ARGV[$idx] eq '--help') {
        usage();
    }
    if ($ARGV[$idx] eq '-d' || $ARGV[$idx] eq '--debug') {
        $debug = 1;
        $ARGV[$idx] = undef;
    }

}

@ARGV = grep { defined } @ARGV;

readConfig();

$debug |= exists $config{debug} && $config{debug} =~ /true/i;
print STDERR 'MDD: Debug mode is ' . ($debug ? 'on' : 'off') . "\n";

my $logfile = $config{logfile} || '/tmp/mdd.log';
my $log = MDD::Log->new($logfile, $debug);

$backend = ($0 =~ /mdd(?:\.pl)?$/);
$log->dbg(
    'Running in ' . ($backend ? 'backend only' : 'frontend/combined') . ' mode'
);

my (
    $data, $lcdClient, $lcdServer, $lcdListen, $lcd, $client, $videoDir,
    $cpus, $streampid, $mythdb
);

my @clients;

my (%commands, %videos, %storageGroups);

my $stream_cmd = $config{stream} ||
    '/usr/bin/vlc -vvv -I oldrc --rc-host 0.0.0.0:16547 --rc-fake-tty ' .
    '--file-caching=2000 %FILE% ' . 
    '--sout=\'#transcode{vcodec=h264,venc=x264{no-cabac,level=30,keyint=50,' .
    'ref=4,bframes=0,bpyramid=none,profile=baseline,no-weightb,weightp=0,' .
    'no-8x8dct,trellis=0},vb=%VB%,threads=%THR%,deinterlace,acodec=mp4a,' . 
    'samplerate=48000,ab=%AB%,channels=2,audio-sync}' .
    ':rtp{sdp=rtsp://0.0.0.0:5554/stream}\' 2>&1';

# List of regex to match messages we might get from MythDroid
# and refs to subroutines that will handle them
my @clientMsgs = (
    { regex => qr/^COMMANDS$/,       proc => \&listCommands              },
    { regex => qr/^STOPSTREAM$/,     proc => \&stopStreaming             },
    { regex => qr/^STORGROUPS$/,     proc => \&getStorGroups             },
    { regex => qr/^RECGROUPS$/,      proc => \&getRecGroups              },
    { regex => qr/^COMMAND (.*)$/,   proc => sub { runCommand($1) }      },
    { regex => qr/^VIDEOLIST (.*)$/, proc => sub { videoList($1) }       },
    { regex => qr/^STREAM (.*)$/,    proc => sub { streamFile($1) }      },
    { regex => qr/^OSD (.*)$/,       proc => sub { osdMsg($1) }          },
    { regex => qr/^DELREC (\d+)$/,   proc => sub { $mythdb->delRec($1) } },
    {
        regex => qr/^RECTYPE (\d+)$/,  
        proc  => sub { sendMsg($mythdb->getRecType($1))     }
    },
    { 
        regex => qr/^STORGROUP (\d+)$/,  
        proc  => sub { sendMsg($mythdb->getStorGroup($1))   }
    },
    {
        regex => qr/^UPDATEREC (\d+) (.*)$/, 
        proc  => sub { sendMsg($mythdb->updateRec($1, $2))  }
    },
    {
        regex => qr/^NEWREC (\d+) (\d+) (.*)$/,
        proc  => sub { sendMsg($mythdb->newRec($1, $2, $3)) }
    },
); 

# Get rid of old log files in case root owns them
if ( -e '/tmp/vlc.out') {
    unlink '/tmp/vlc.out' or $log->warn("Can't remove old /tmp/vlc.out: $!");
}

my $user = $config{user} || 'mdd';
# Change euid/uid
my @pwent = getpwnam $user;
if (@pwent) { $< = $> = $pwent[2] }
else { $log->warn("Failed to chuid to user $user") }

$log->warn('mdd is running as root - streaming will not work')
    if ($> == 0);

if ($backend && !$debug) {
    $log->dbg("Daemonise");
    chdir '/'                 or $log->err("Couldn't chdir() to /: $!");
    open STDIN, '/dev/null'   or $log->err("Couldn't open() /dev/null: $!");
    open STDOUT,'>>/dev/null' or $log->err("Couldn't open() /dev/null: $!");
    open STDERR,'>>/dev/null' or $log->err("Couldn't open() /dev/null: $!");
    defined(my $pid = fork)   or $log->err("Couldn't fork(): $!");
    exit if $pid;
    setsid                    or $log->err("Couldn't setsid(): $!");
    umask 0;
}

elsif (!$backend) {

    $lcd = MDD::LCD->new();
    $log->dbg("Start LCD server with arguments: @ARGV");

    $lcdServerPort = $lcd->start(@ARGV);
    $log->dbg("Listen on port $lcdServerPort/tcp");

    # Listen for connections from mythfrontend
    $lcdListen = IO::Socket::INET->new(
        Listen      => 1,
        Proto       => 'tcp',
        ReuseAddr   => 1,
        LocalPort   => $lcdServerPort
    
    ) or $log->fatal("Couldn't listen on $lcdServerPort/tcp: $!");

}

if (exists $config{cmux_extra_ports}) {
    MDD::CMux->addAllowedPorts(split ' ', $config{cmux_extra_ports});
}
if (exists $config{cmux}) {
    if ($config{cmux} =~ /true/i) { MDD::CMux->new($log) }
}
else { MDD::CMux->new($log) }

# Forking is done.. install our signal handlers

# Kill off the real mythlcdserver when we are killed
$SIG{INT} = $SIG{TERM} = $SIG{KILL} = \&killKids; 
# Re-read mdd.conf if we are HUP'd
$SIG{HUP} = \&readConfig;
$SIG{CHLD} = 'IGNORE';
sub END { killKids() }

$mythdb = MDD::MythDB->new($log);;

$log->dbg("Listen on port $listenPort/tcp");

# Listen for connections from MythDroid
my $listen = IO::Socket::INET->new(
    Listen      => 1,
    Proto       => 'tcp',
    ReuseAddr   => 1,
    LocalPort   => $listenPort
) or $log->fatal("Couldn't listen on $listenPort/tcp: $!");

my @handles = ( $listen );
push @handles, $lcdListen if $lcdListen;
my $s = IO::Select->new(@handles);
undef @handles;

# Connect to the real mythlcdserver
unless ($backend) {
    $log->dbg("Connect to real mythlcdserver");
    if ($lcdServer = $lcd->connect()) { $s->add($lcdServer) }
    else { $log->err("Couldn't connect to mythlcdserver") }
}
    
# Main Loop
while (my @ready = $s->can_read) {
    
    foreach my $fd (@ready) {
        
        if (!$backend && $fd == $lcdListen) {
            $log->dbg("New connection from mythfrontend");
            if ($lcdClient = $fd->accept) { $s->add($lcdClient) }
            next;
        }
        elsif ($fd == $listen) {
            $log->dbg("New connection from MythDroid");
            handleMdConn($fd);
            next;
        }

        unless (sysread($fd, $data, 1024)) {
            # Someone disconnected
            handleDisconnect($fd);
            next;
        }

        if (!$backend && defined $lcdServer && $fd == $lcdServer) {
            # Ferry data from lcdserver -> mythfrontend
            syswrite($lcdClient, $data);
        }
        elsif ($client = (grep { $fd == $_ } @clients)[0]) {
            # From MythDroid
            $data =~ s/\r//;
            clientMsg($data);
        }
        elsif (!$backend) {
            # Process and ferry data from mythfrontend -> lcdserver
            foreach (split /\n/, $data) { 
                $log->dbg("-> LMSG: $_");
                my $msg = $lcd->command($_) if length > 4;
                sendMsgs($msg) if $msg;
            }
            syswrite($lcdServer, $data) if $lcdServer;
        }

    }

}

sub usage() {

    print <<EOF;

MDD - MythDroid Daemon

Usage:

    --help    [-h]      - Show this message
    --backend [-b]      - Backend-only mode
    --debug   [-d]      - Debug mode

Backend-only mode should be used on systems that serve only as a backend. 
MDD will detach from the terminal and run as a daemon in backend mode.

Debug mode will result in debug information being output to the logfile 
(/tmp/mdd.log). It will also cause MDD to run in the foreground even
in backend-only mode.

MDD will install itself if run from a non-installed location - i.e. from a
location that is not /usr/bin/ for backend-only mode or 
\$PREFIX/bin/mythlcdserver otherwise.

EOF

    exit 0;

}

# Handle new connection from MythDroid
sub handleMdConn($) {

    my $fd = shift;

    return unless ($client = $fd->accept);

    $s->add($client);
    push @clients, $client;

    return if $backend;

    # Send last LCD statuses 
    map { syswrite($client, $_) } ($lcd->forNewClient());

}

sub handleDisconnect($) {

    my $fd = shift;

    # Someone disconnected
    $s->remove($fd);
    $fd->close;

    foreach (0 .. $#clients) {
        if ($clients[$_] == $fd) {
            splice @clients, $_, 1;
            last;
        }
    }

    if (!$backend && defined $lcdServer && $fd == $lcdServer) {
        $log->warn("Lost connection to LCD server, reconnecting");
        $lcdServer = $lcd->connect();
        $s->add($lcdServer);
    }

}

# Parse MDD commands from /etc/mdd.conf
sub readConfig() {

    %config = ();
    
    my $f = '/etc/mdd.conf';
    open F, "<$f" or return;

    my $line = 0;

    while (<F>) {

        $line++;
        s/^\s+//;
        s/\s+$//;
        next if /^#/;
        s/#.*$//;
        next unless length;
        my ($name, $value) = /(.*?)=(.*)/;
        unless ($name && $value) {
            print STDERR "MDD: Error parsing line $line of $f, ignoring\n";
            next;
        }
        $name =~ s/\s+$//;
        $value =~ s/^\s+//;

        while ($value =~ s/\\$//) { 
            chomp $value;
            $value .= readline F;
        }

        if ($name =~ /command/i) {
            my ($cm, $cv) = $value =~ /(.*)=>(.*)/;
            unless ($cm && $cv) {
                print STDERR "MDD: Error parsing command on line $line of " .
                             "$f, ignoring\n";
                next;
            }
            $commands{$cm} = $cv;
        }

        $config{$name} = $value;

    }

    close F;
}

# Send a message to the current client (MythDroid)
sub sendMsg($) {

    my $msg = shift;
    $msg .= "\n";
    syswrite($client, $msg) if (defined $client);
    $log->dbg("<- CMSG: $msg");

}

# Send a message to all clients (MythDroid)
sub sendMsgs($) {

    my $msg = shift;
    $msg .= "\n";
    map { syswrite($_, $msg) } @clients;
    $log->dbg("<- CMSG: $msg");

}

# Process messages from the client (MythDroid)
sub clientMsg($) {

    my $msg = shift;

    $log->dbg("-> CMSG: $msg");

    map { 
        if ($msg =~ $_->{regex}) { 
            sendMsg("OK"); 
            $_->{proc}();
            return;
        } 
    } @clientMsgs;

    $log->err("Unknown command $msg");
    sendMsg("UNKNOWN");

}

sub listCommands() {
    # Send a list of MDD commands
    map { sendMsg("COMMAND $_") } (keys %commands);
    sendMsg("COMMANDS DONE");
}

# Run an MDD command
sub runCommand($) {
    
    my $cmd = shift;

    if (! exists $commands{$cmd}) {
        $log->err("mdd command $cmd is not defined");
        return;
    }

    system($commands{$cmd});

}

sub osdMsg($) {

    unless (MDD::ConfigData->feature('xosd_support')) {
        $log->err("Can't display OSD message - X::Osd isn't installed");
        return;
    }

    my $osd = MDD::XOSD->new();

    return if ($osd->display(shift));

    $log->err("Failed to create OSD object");

}

# Get a list of videos in given subddirectory of video storage groups
sub videoListSG($) {

    my $subdir = shift;

    my (@dirs, @vids);

    $subdir =~ s/^-?\d+\s//;
    $subdir = '.' if ($subdir eq 'ROOT');

    my $videos = $mythdb->getVideos("^$subdir");

    foreach my $vid (@$videos) {
        my $file = ($vid =~ /\|\|([^\|]+)$/)[0];
        map { $file =~ s/^$_\/?// } @{ $storageGroups{Videos} };
        $file =~ s/^$subdir// unless $subdir eq '.';
        if ($file =~ /(.+?)\//) {
            push @dirs, $1 unless grep { $1 eq $_ } @dirs;
            next;
        }
        else {
            push @vids, $vid;
        }
    }

    @dirs = sort @dirs;

    map { sendMsg("-1 DIRECTORY $_") } @dirs;
    map { sendMsg($_) } (@vids);

    sendMsg("VIDEOLIST DONE");

}

# Get a list of videos in given subddirectory of VideoStartupDir
sub videoList($) {

    my $subdir = shift;
    my $regex;
    
    %storageGroups = %{ $mythdb->getStorGroups() } 
        unless (scalar %storageGroups);

    return videoListSG($subdir) if (exists $storageGroups{Videos});

    $videoDir = $mythdb->setting('VideoStartupDir', hostname)
        unless $videoDir;

    my @videoDirs = split /:/, $videoDir;
    map { s/\/+$// } @videoDirs;

    if (scalar @videoDirs) {
        $log->dbg("VideoDirs: @videoDirs");
    }
    else {
        sendMsg("VIDEOLIST DONE");
        return;
    }

    my ($vd, $sd) = $subdir =~ /^(-?\d+)\s(.+)/;

    # Top level and more than one videodir, send numbered list
    if (scalar @videoDirs > 1 && $vd == -1 && $sd eq 'ROOT') {
        my $i = 0;
        map { sendMsg($i++ . " DIRECTORY $_") } @videoDirs;
        sendMsg("VIDEOLIST DONE");
        return;
    }
    # Top level but only one videodir, set viddir = -1
    elsif (scalar @videoDirs < 2) {
        $regex = "$videoDirs[0]/";
        $vd = -1;
    }
    else {
        $regex = "$videoDirs[$vd]/";
    }

    $regex .= "$sd/" unless ($sd eq 'ROOT');
    
    $regex =~ s/'/\\'/;

    my @dirs = grep(
        /\S+/, 
        map { chomp; s/^$regex//; $_ } `find -L '$regex' -maxdepth 1 -type d`
    );

    $regex .= '[^/]+$';

    @dirs = sort @dirs;

    map { sendMsg("$vd DIRECTORY $_") } @dirs;
    map { sendMsg($_) } (@{ $mythdb->getVideos($regex) });

    sendMsg("VIDEOLIST DONE");

}

sub findFileSG($$) {

    my $sgd = shift;
    my $file = shift;

    foreach my $d (@$sgd) {
        return "$d$file" if (-e "$d$file");
    }

    return undef;
}

# Stream a recording or video
sub streamFile($) {
    
    my $file = shift;
    my $dir;
    my $pat = qr/(\d+)x(\d+)\s+VB\s+(\d+)\s+AB\s+(\d+)\s+SG\s+(.+)\s+FILE\s+/;

    $log->dbg("Streaming $file");

    my ($width, $height, $vb, $ab, $sg) = $file =~ /$pat/;
    $file =~ s/$pat//;

    unless (defined $cpus) {
        my $buf;
        open CPUS, "</sys/devices/system/cpu/present";
        read CPUS, $buf, 64;
        $cpus = () = $buf =~ /(\d+)/g;
        close CPUS;
    }

    $cpus = 1 unless $cpus;

    $log->dbg("Will use $cpus threads for transcode");

    if ($file =~ /^myth:\/\//) {

        %storageGroups = %{ $mythdb->getStorGroups() } 
            unless (scalar %storageGroups);
    
        $file =~ s/.*\//\//;
        my $filename = $file;

        if (exists $storageGroups{$sg}) {
            $file = findFileSG($storageGroups{$sg}, $file);
        }
        else {
            $log->dbg("Storage Group $sg not found, assume 'Default'");
            $file = findFileSG($storageGroups{'Default'}, $file);
        }
        
        unless (defined $file) {
            $log->err("Couldn't find $filename in SG $sg");
            return;
        }

    }
    else {
        $file =~ s/ /\\ /g;
        $file =~ s/'/\\'/g;
    }

    $log->dbg("Streaming - resolved path is $file");
    
    my $cmd = $stream_cmd;

    # The default demuxer doesn't support get_length et al in ts
    if (
        $file =~ /\.mpg$/ || $file =~ /\.mpeg$/ ||
        $file =~ /\.ts$/ || $file =~ /\.m2ts$/
    ) {
        $cmd =~ s/vlc /vlc --demux avformat /;
    }

    $cmd =~ s/%FILE%/$file/;
    $cmd =~ s/%VB%/$vb/;
    $cmd =~ s/%AB%/$ab/;
    $cmd =~ s/%THR%/$cpus/;
    $cmd =~ s/%WIDTH%/$width/g;
    $cmd =~ s/%HEIGHT%/$height/g;

    $log->dbg("Execute $cmd");

    $cmd .= ' |';

    if (($streampid = fork()) == 0) { 
        setpgrp;
        open OUT, ">/tmp/vlc.out" or $log->fatal("Can't open /tmp/vlc.out: $!");
        select OUT;
        $| = 1;
        open VLC, $cmd or $log->fatal($!); 
        while (<VLC>) {
            print;
            $log->warn("VLC: $_") if /error/i;
        }
        exit 0;
    }

}

# Stop streaming a recording or video
sub stopStreaming() {

    return unless $streampid;
    $log->dbg("Stop streaming");
    # Kill the whole process group
    kill -9, $streampid;
    waitpid $streampid, 0;
    undef $streampid;

}

# Get a list of storage groups
sub getStorGroups() {

    %storageGroups = %{ $mythdb->getStorGroups() } 
        unless (scalar %storageGroups);

    map { sendMsg($_) } (keys %storageGroups);

    sendMsg("STORGROUPS DONE");

}

# get a list of recording groups
sub getRecGroups() {

    map { sendMsg($_) } (@{$mythdb->getRecGroups()});
    sendMsg("RECGROUPS DONE");

}

# Kill the original mythlcdserver
sub killKids() {
    return unless ($$ == $mpid);
    my @ps = `ps ax | grep 'mythlc[d]'`;
    foreach my $ps (@ps) {
        if ($ps =~ /^\s*(\d+)/) {
            next if ($1 == $$);
            kill 'KILL', $1;
        }
    }
}
