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

package MDD::CMux;
use strict;
use warnings;

my %conns;
my $mux_port = 16550;
my $log;

my @allowed_ports = ( 6543, 6544, 16546, 16547 );

sub new {

    my $class = shift;
    $log = shift;
    my $pid;

    my $self = {};

    unless (defined($pid = fork)) {
        $log->err("CMux: Couldn't fork: $!\n");
        return undef;
    }

    $log->dbg("CMux: running in pid $pid") if $pid;

    bless($self, $class);
    return $self if $pid;

    %SIG = ();
    $self->setup();
    $self->mainloop();
    exit 0;
}

sub setup {

    my $self = shift;

    $self->{listen} = IO::Socket::INET->new(
        Listen      => 1,
        Proto       => 'tcp',
        ReuseAddr   => 1,
        LocalPort   => $mux_port
    ) or $log->fatal("CMux: Couldn't listen on $mux_port/tcp: $!");

    $log->dbg("CMux: Listening on $mux_port");

    $self->{select} = IO::Select->new($self->{listen});
}

sub initConn {
    
    my $self = shift;
    my $c = shift;
    my ($port, $data);

    $log->dbg("CMux: New connection from " . $c->peerhost . ":" . $c->peerport);

    unless (sysread($c, $port, 512)) {
        handleDisconnect($c);
        return;
    }

    if ($port =~ /^GET/ || $port =~ /^POST/ || $port =~ /^HEAD/) {
        $data = $port;
        $port = 6544;
    }
    else {
        chomp($port);
        $port =~ s/\s+$//;
    }

    unless (grep { $_ == $port } @allowed_ports) {
        my $msg = "CMux: connections to port $port are not permitted";
        $log->err($msg);
        print $c $msg;
        close $c;
        return;
    }


    $conns{$c} = IO::Socket::INET->new(
        PeerAddr => "localhost:$port"
    ) or do {
        my $msg = "CMux: Connection to localhost:$port failed: $!";
        $log->err($msg);
        print $c $msg;
        close $c;
        return;
    };

    if ($data) {
        syswrite($conns{$c}, $data);
    }
    else {
        syswrite($c, "OK");
    }

    $conns{$conns{$c}} = $c;
    $self->{select}->add($c);
    $self->{select}->add($conns{$c});

    $log->dbg("CMux: Opened connection to localhost:$port");

}

sub handleDisconnect {

    my $self = shift;
    my $c = shift;
    my $c2 = $conns{$c};
    $log->dbg(
        "CMux: Closing connection to " . $c->peerhost . ":" . $c->peerport
    );
    $log->dbg(
        "CMUx: Closing connection to " . $c2->peerhost . ":" . $c2->peerport
    );
    $self->{select}->remove($c);
    $self->{select}->remove($c2);
    $c->close;
    $c2->close;

}

sub mainloop {

    my $self = shift;
    my ($data, $len, $c);

    while (my @ready = $self->{select}->can_read) {

        foreach my $fd (@ready) {
            
            if ($fd == $self->{listen}) {
                # New connection
                $self->initConn($c) if ($c = $fd->accept); 
                next;
            }
            
            unless ($len = sysread($fd, $data, 1024)) {
                # Someone disconnected
                $self->handleDisconnect($fd);
                next;
            }

            if (! defined(syswrite($conns{$fd}, $data, $len))) {
                $log->warn("Error writing to socket: $!");
            }

            $data = undef;
        }

    }

    $log->err(
        "CMux: mainloop ended whilst selecting on " .
        $self->{select}->count . " handles"
    );

}

return 1;
