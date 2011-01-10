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

use strict;
use warnings;
use IO::Socket::INET;
use IO::Select;
use POSIX qw(setsid);
use File::Copy;
use Sys::Hostname;
use Config;
use MDD::LCD;
use MDD::MythDB;
use MDD::Log;

sub usage();
sub handleMdConn($);
sub handleDisconnect($);
sub readCommands();
sub clientMsg($);
sub sendMsg($);
sub sendMsgs($);
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

# Kill off the real mythlcdserver when we are killed
$SIG{'INT'} = $SIG{'TERM'} = $SIG{'KILL'} = \&killKids; 
# Re-read mdd.conf if we are HUP'd
$SIG{'HUP'} = \&readCommands;
sub END { killKids() }

my ($backend, $debug);

# Check for and strip arguments intended for us
foreach my $idx (0 .. $#ARGV) {

    next unless exists $ARGV[$idx];

    if ($ARGV[$idx] eq '-h' || $ARGV[$idx] eq '--help') {
        usage();
    }
    elsif ($ARGV[$idx] eq '-b' || $ARGV[$idx] eq '--backend') {
        $backend = 1;
        splice @ARGV, $idx, 1;
    }
    elsif ($ARGV[$idx] eq '-d' || $ARGV[$idx] eq '--debug') {
        $debug = 1;
        splice @ARGV, $idx, 1;
    }

}

my $log = MDD::Log->new('/tmp/mdd.log', $debug);

# Install ourselves if necessary
install() unless ($0 =~ /mythlcdserver$/ || ($backend && $0 =~ /^\/usr\/bin/));

my (
    $data, $lcdClient, $lcdServer, $lcdListen, $lcd, $client, $videoDir,
    $cpus, $streampid, $mythdb, $no_xosd
);

my @clients;

my (%commands, %videos, %storageGroups);

# Use ffmpeg to demux and decode; VLC's TS demuxer doesn't cope with 
# a significant proportion of dvb recordings :(
my $stream_cmd = 
    'ffmpeg -i %FILE% -vcodec rawvideo -acodec pcm_s16le -deinterlace ' .
    '-s %WIDTH%x%HEIGHT% -ac 2 -ar 48000 -copyts -async 100 -f asf -y - ' .
    '2>/tmp/ffmpeg.out | /usr/bin/vlc -vvv -I dummy - --sout=\'' .
    '#transcode{vcodec=h264,venc=x264{no-cabac,level=30,keyint=250,ref=4,' .
    'bframes=0},vb=%VB%,threads=%THR%,width=%WIDTH%,height=%HEIGHT%,' .
    'acodec=mp4a,samplerate=48000,ab=%AB%,channels=2}' .
    ':rtp{sdp=rtsp://0.0.0.0:5554/stream}\' >/tmp/vlc.out 2>&1';

# List of regex to match messages we might get from MythDroid
# and refs to subroutines that will handle them
my @clientMsgs = (
    { regex => qr/^COMMAND (.*)$/,   proc => sub { runCommand($1) }      },
    { regex => qr/^VIDEOLIST (.*)$/, proc => sub { videoList($1) }       },
    { regex => qr/^STREAM (.*)$/,    proc => sub { streamFile($1) }      },
    { regex => qr/^OSD (.*)$/,       proc => sub { osdMsg($1) }          },
    { regex => qr/^STOPSTREAM$/,     proc => \&stopStreaming             },
    { regex => qr/^STORGROUPS$/,     proc => \&getStorGroups             },
    { regex => qr/^RECGROUPS$/,      proc => \&getRecGroups              },
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

eval "use MDD::XOSD";
$no_xosd++ if $@;

# Change euid/uid
my @pwent = getpwnam 'mdd';
if (@pwent) { $< = $> = $pwent[2] }

$log->warn("WARNING: mdd is running as root - streaming will not work\n")
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

$mythdb = MDD::MythDB->new($log);;

readCommands();

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
    $lcdServer = $lcd->connect();
    $log->err("Couldn't connect to mythlcdserver") unless $lcdServer;
    $s->add($lcdServer);
}
    
# Main Loop
while (my @ready = $s->can_read) {
    
    foreach my $fd (@ready) {
        
        if (!$backend && $fd == $lcdListen) {
            $log->dbg("New connection from mythfrontend");
            if ($lcdClient = $fd->accept) {
                $s->add($lcdClient);
            }
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

    # Send a list of MDD commands
    map { syswrite($client, "COMMAND $_\n") } (keys %commands);
    syswrite($client, "COMMANDS DONE\n");

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
sub readCommands() {

    %commands = ();
    
    open F, '</etc/mdd.conf' or return;

    my $line = 0;

    while (<F>) {
        $line++;
        s/^\s+//;
        s/\s+$//;
        next if /^#/;
        s/#.*$//;
        my ($name, $cmd) = /(.*)=>(.*)/;
        if (!($name && $cmd)) {
            $log->err("Error parsing line $line of /etc/mdd.conf, ignoring");
            next;
        }
        $name =~ s/\s+$//;
        $cmd =~ s/^\s+//;
        $commands{$name} = $cmd;
        $log->dbg("Add MDD command $name: $cmd");
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

    if ($no_xosd) {
        $log->err("Can't display OSD - X::Osd isn't installed");
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

    map { sendMsg("$vd DIRECTORY $_") } @dirs;
    map { sendMsg($_) } (@{ $mythdb->getVideos($regex) });

    sendMsg("VIDEOLIST DONE");

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

        if (exists $storageGroups{$sg}) {
            $file = $storageGroups{$sg} . $file;
        }
        else {
            $log->dbg("Storage Group $sg not found, assume 'Default'");
            $file = $storageGroups{'Default'} . $file;
        }

    }
    else {
        $file =~ s/ /\\ /g;
        $file =~ s/'/\\'/g;
    }

    $log->dbg("Streaming - resolved path is $file");

    my $cmd = $stream_cmd;
    $cmd =~ s/%FILE%/$file/;
    $cmd =~ s/%VB%/$vb/;
    $cmd =~ s/%AB%/$ab/;
    $cmd =~ s/%THR%/$cpus/;
    $cmd =~ s/%WIDTH%/$width/g;
    $cmd =~ s/%HEIGHT%/$height/g;

    $log->dbg("Execute $cmd");

    if (($streampid = fork()) == 0) { 
        system($cmd); 
        exit 0;
    }

}

# Stop streaming a recording or video
sub stopStreaming() {

    return unless $streampid;

    $log->dbg("Stop streaming");

    # I'd like to setpgrp in the child but that seems to cause vlc issues :(
    kill 'KILL', $streampid, $streampid+1, $streampid+2, $streampid+3;
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
    my @ps = `ps ax | grep 'mythlc[d]'`;
    foreach my $ps (@ps) {
        if ($ps =~ /^\s*(\d+)/) {
            next if ($1 == $$);
            kill 'KILL', $1;
        }
    }
}

sub create_user_account() {

    my $user = 'mdd';

    if (getpwnam $user) { return }

    print "Creating mdd user account..\n";

    system("useradd -d /dev/null -c 'Added by MDD' -U $user -s /sbin/nologin");

}


sub install_modules() {

    my @mods = (qw(LCD MythDB Log XOSD));
    
    # Install modules
    mkdir($Config{vendorlib} . "/MDD");
    foreach my $mod (@mods) {
        print "cp MDD/$mod.pm -> $Config{vendorlib}/MDD\n";
        copy("MDD/$mod.pm", $Config{vendorlib} . "/MDD");
    }

}

sub get_install_dir() {
    
    my $dir = (`which mythlcdserver`)[0];

    if ($dir =~ /^which:/) {
        print "Couldn't locate mythlcdserver. Aborted\n";
        exit;
    }

    $dir =~ s/\/mythlcdserver$//;
    chomp $dir;
    return $dir;

}

sub check_lcd_settings() {

    print "Check LCD settings..\n";
    my $mythdb = MDD::MythDB->new($log);;
    unless (
        $mythdb->setting('LCDEnable', hostname)      &&
        $mythdb->setting('LCDShowMenu', hostname)    &&
        $mythdb->setting('LCDShowMusic', hostname)   &&
        $mythdb->setting('LCDShowChannel', hostname) 
    ) {
        print "\nNOTICE: Please enable all MythTV LCD options\n";
    }

}



sub install {

    print "Installing mdd..\n";

    create_user_account();

    install_modules();
    
    if ($backend) {
        print "cp $0 -> /usr/bin/mdd\n";
        copy($0, "/usr/bin/mdd");
        chmod(0755, "/usr/bin/mdd") 
            or warn "chmod of /usr/bin/mdd failed\n";
        exit;
    }

    print "Stopping mythfrontend and mythlcdserver\n";

    system(
        "killall mythfrontend 2>/dev/null;" .
        "killall mythfrontend.real 2>/dev/null;" . 
        "killall -9 mythlcdserver 2>/dev/null"
    );

    my $dir = get_install_dir();

    my $path = "$dir/mythlcdserver";
    my $dst  = "$dir/mythlcd"; 

    unless ((`file $path`)[0] =~ /perl/) {
        print "cp $path -> $dst\n";
        copy($path, $dst) or die "$!\n";
        chmod(0755, $dst) 
            or warn "chmod of $dst failed\n";
    }

    print "cp $0 -> $path\n";
    copy($0, $path) or die "$!\n";
    chmod(0755, $path) 
        or warn "chmod of $path failed\n";

    check_lcd_settings();

    print "Done - you can restart mythfrontend now..\n";
    exit;

}
