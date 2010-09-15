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

sub handleMdConn($);
sub handleDisconnect($);
sub readCommands();
sub clientMsg($);
sub sendMsg($);
sub runCommand($);
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

my ($backend, $debug, $lcd, $lcdListen);

# Check for and strip arguments intended for us
foreach my $idx (0 .. $#ARGV) {
    if ($ARGV[$idx] eq '--backend') {
        $backend = 1;
        delete $ARGV[$idx];
    }
    elsif ($ARGV[$idx] eq '--debug') {
        $debug = 1;
        delete $ARGV[$idx];
    }
}

# Install ourselves if necessary
install() unless ($0 =~ /mythlcdserver$/ || ($backend && $0 =~ /^\/usr\/bin/));

my (
    $data, $lcdClient, $lcdServer, $client, $videoDir, $cpus, $streampid
);

my $size = 1024;

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

warn("WARNING: mdd is running as root - streaming will not work\n")
    if ($> == 0);

if ($backend && !$debug) {
    # Daemonise
    chdir '/'                 or die "Couldn't chdir() to /: $!";
    open STDIN, '/dev/null'   or die "Couldn't open() /dev/null: $!";
    open STDOUT,'>>/dev/null' or die "Couldn't open() /dev/null: $!";
    open STDERR,'>>/dev/null' or die "Couldn't open() /dev/null: $!";
    defined(my $pid = fork)   or die "Couldn't fork(): $!";
    exit if $pid;
    setsid                    or die "Couldn't setsid(): $!";
    umask 0;
}

elsif (!$backend) {

    $lcd = MDD::LCD->new();
    $lcdServerPort = $lcd->start(@ARGV);

    # Listen for connections from mythfrontend
    $lcdListen = IO::Socket::INET->new(
        Listen      => 1,
        Proto       => 'tcp',
        ReuseAddr   => 1,
        LocalPort   => $lcdServerPort
    
    ) or die "Couldn't listen on $lcdServerPort/tcp: $!\n";

}

my $mythdb = MDD::MythDB->new();;

readCommands();

# Listen for connections from MythDroid
my $listen = IO::Socket::INET->new(
    Listen      => 1,
    Proto       => 'tcp',
    ReuseAddr   => 1,
    LocalPort   => $listenPort
) or die "Couldn't listen on $listenPort/tcp: $!\n";

my @handles = ( $listen );
push @handles, $lcdListen if $lcdListen;
my $s = IO::Select->new(@handles);
undef @handles;

# Connect to the real mythlcdserver
unless ($backend) {
    $lcdServer = $lcd->connect();
    die "Couldn't connect to mythlcdserver" unless $lcdServer;
    $s->add($lcdServer);
}
    
# Main Loop
while (my @ready = $s->can_read) {
    
    foreach my $fd (@ready) {
        
        if (!$backend && $fd == $lcdListen) {
            # New connection from mythfrontend
            if ($lcdClient = $fd->accept) {
                $s->add($lcdClient);
            }
            next;
        }

        elsif ($fd == $listen) {
            # New connection from MythDroid
            handleMdConn($fd);
            next;
        }

        unless (sysread($fd, $data, $size)) {
            # Someone disconnected
            handleDisconnect($fd);
            next;
        }

        if (!$backend && defined $lcdServer && $fd == $lcdServer) {
            # Ferry data from lcdserver -> mythfrontend
            syswrite($lcdClient, $data);
        }
        elsif ($client && $fd == $client) {
            # From MythDroid
            $data =~ s/\r//;
            clientMsg($data);
        }
        elsif (!$backend) {
            # Process and ferry data from mythfrontend -> lcdserver
            foreach (split /\n/, $data) { 
                print "-> LMSG: $_\n" if $debug;
                my $msg = $lcd->command($_) if length > 4;
                sendMsg($msg) if $msg;
            }
            syswrite($lcdServer, $data) if $lcdServer;
        }

    }

}

# Handle new connection from MythDroid
sub handleMdConn($) {

    my $fd = shift;

    if (defined $client) {
        $s->remove($client);
        $client->close;
    }

    if ($client = $fd->accept) {

        $s->add($client);

        # Send a list of MDD commands
        foreach my $key (keys %commands) {
            syswrite($client, "COMMAND $key\n");
        }
        syswrite($client, "COMMANDS DONE\n");

        return if $backend;

        # Send last LCD statuses 
        foreach my $msg ($lcd->forNewClient()) {
            syswrite($client, $msg);
        }

    }

}

sub handleDisconnect($) {

    my $fd = shift;

    # Someone disconnected
    $s->remove($fd);
    $fd->close;

    undef $client if (defined $client && $fd == $client);

    if (!$backend && defined $lcdServer && $fd == $lcdServer) {
        # Reconnect if we lost connection to the real mythlcdserver
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
            warn("Error parsing line $line of /etc/mdd.conf");
            next;
        }
        $name =~ s/\s+$//;
        $cmd =~ s/^\s+//;
        $commands{$name} = $cmd;
    }

    close F;
}

# Send a message to the client (MythDroid)
sub sendMsg($) {

    my $msg = shift;
    $msg .= "\n";
    syswrite($client, $msg) if (defined $client);
    print "<- SMSG: $msg\n" if $debug;

}

# Process messages from the cliet (MythDroid)
sub clientMsg($) {

    my $msg = shift;

    print "-> CMSG: $msg\n" if $debug;

    if    ($msg =~ /^COMMAND (.*)$/) {
        sendMsg("OK");
        runCommand($1);
    }
    elsif ($msg =~ /VIDEOLIST (.*)$/) {
        sendMsg("OK");
        videoList($1);
    }
    elsif ($msg =~ /STREAM (.*)$/) {
        sendMsg("OK");
        streamFile($1);
    }
    elsif ($msg =~ /STOPSTREAM/) {
        sendMsg("OK");
        stopStreaming();
    }
    elsif ($msg =~ /RECTYPE (\d+)$/) {
        sendMsg("OK");
        sendMsg($mythdb->getRecType($1));
    }
    elsif ($msg =~ /STORGROUP (\d+)$/) {
        sendMsg("OK");
        sendMsg($mythdb->getStorGroup($1));
    }
    elsif ($msg =~ /STORGROUPS/) {
        sendMsg("OK");
        getStorGroups();
    }
    elsif ($msg =~ /RECGROUPS/) {
        sendMsg("OK");
        getRecGroups();
    }
    elsif ($msg =~ /UPDATEREC (\d+) (.*)$/) {
        sendMsg("OK");
        sendMsg($mythdb->updateRec($1, $2));
    }
    elsif ($msg =~ /NEWREC (\d+) (\d+) (.*)$/) {
        sendMsg("OK");
        sendMsg($mythdb->newRec($1, $2, $3));
    }
    elsif ($msg =~ /DELREC (\d+)$/) {
        sendMsg("OK");
        $mythdb->delRec($1);
    }
    else {
        sendMsg("UNKNOWN");
    }

}

# Run an MDD command
sub runCommand($) {
    
    my $cmd = shift;

    if (! exists $commands{$cmd}) {
        warn("mdd command $cmd is not defined");
        return;
    }

    system($commands{$cmd});

}

# Get a list of videos in given subddirectory of VideoStartupDir
sub videoList($) {

    my $subdir = shift;

    my $regex;

    $videoDir = $mythdb->setting('VideoStartupDir', hostname)
        unless $videoDir;

    my @videoDirs = split /:/, $videoDir;

    my ($vd, $sd) = $subdir =~ /^(-?\d+)\s(.+)/;

    # Top level and more than one videodir, send numbered list
    if (scalar @videoDirs > 1 && $vd == -1 && $sd eq 'ROOT') {
        my $i = 0;
        foreach my $dir (@videoDirs) { sendMsg($i++ . " DIRECTORY $dir") }
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
    
    $regex =~ s/'/\'/;

    my @dirs = grep(
        /\S+/, 
        map { chomp; s/^$regex//; $_ } `find -L '$regex' -maxdepth 1 -type d`
    );

    $regex .= '[^/]+$';

    foreach my $dir (@dirs) { sendMsg("$vd DIRECTORY $dir") }

    foreach my $vid (@{ $mythdb->getVideos($regex) }) { sendMsg($vid) }

    sendMsg("VIDEOLIST DONE");

}

# Stream a recording or video
sub streamFile($) {
    
    my $file = shift;
    my $dir;
    my $pat = qr/(\d+)x(\d+)\s+VB\s+(\d+)\s+AB\s+(\d+)\s+SG\s+(.+)\s+FILE\s+/;

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

    if ($file =~ /^myth:\/\//) {

        %storageGroups = %{ $mythdb->getStorGroups() } 
            unless (scalar %storageGroups);
    
        $file =~ s/.*\//\//;

        if (exists $storageGroups{$sg}) {
            $file = $storageGroups{$sg} . $file;
        }
        else {
            $file = $storageGroups{'Default'} . $file;
        }

    }
    else {
        $file =~ s/ /\\ /g;
    }

    my $cmd = $stream_cmd;
    $cmd =~ s/%FILE%/$file/;
    $cmd =~ s/%VB%/$vb/;
    $cmd =~ s/%AB%/$ab/;
    $cmd =~ s/%THR%/$cpus/;
    $cmd =~ s/%WIDTH%/$width/g;
    $cmd =~ s/%HEIGHT%/$height/g;

    if (($streampid = fork()) == 0) { 
        system($cmd); 
        exit 0;
    }

}

# Stop streaming a recording or video
sub stopStreaming() {

    return unless $streampid;

    # I'd like to setpgrp in the child but that seems to cause vlc issues :(
    kill 'KILL', $streampid, $streampid+1, $streampid+2, $streampid+3;
    waitpid $streampid, 0;
    undef $streampid;

}


# Get a list of storage groups
sub getStorGroups() {

    %storageGroups = %{ $mythdb->getStorGroups() } 
        unless (scalar %storageGroups);

    foreach my $grp (keys %storageGroups) {
        sendMsg($grp);
    }

    sendMsg("STORGROUPS DONE");

}

# get a list of recording groups
sub getRecGroups() {

    foreach my $rg (@{$mythdb->getRecGroups()}) {
        sendMsg($rg);
    }

    sendMsg("RECGROUPS DONE");

}

# Kill the original mythlcdserver
sub killKids() {
    my @ps = `ps ax | grep 'mythlcd ' | grep -v grep`;
    foreach my $ps (@ps) {
       if ($ps =~ /^\s*(\d+)/) {
            kill 'TERM', $1;
        }
    }
}

sub install {

    print "Installing mdd..\n";
    
    # Install modules
    mkdir($Config{vendorlib} . "/MDD");
    copy("MDD/LCD.pm", $Config{vendorlib} . "/MDD");
    copy("MDD/MythDB.pm", $Config{vendorlib} . "/MDD");

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
        "killall mythlcdserver 2>/dev/null"
    );

    my $dir = (`which mythlcdserver`)[0];
    if ($dir =~ /^which:/) {
        print "Couldn't locate mythlcdserver. Aborted\n";
        exit;
    }
    $dir =~ s/\/mythlcdserver$//;
    chomp $dir;

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

    print "Check settings..\n";
    my $mythdb = MDD::MythDB->new();;
    unless (
        $mythdb->setting('LCDEnable', hostname)      &&
        $mythdb->setting('LCDShowMenu', hostname)    &&
        $mythdb->setting('LCDShowMusic', hostname)   &&
        $mythdb->setting('LCDShowChannel', hostname) 
    ) {
        print "\nNOTICE: Please enable all MythTV LCD options\n";
    }
    print "Done - you can restart mythfrontend now..\n";
    exit;

}