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
use threads;
use Socket qw(IPPROTO_TCP TCP_NODELAY);
eval "use Crypt::Rijndael";
eval "use MDD::CFB";

my %conns;
my $mux_port = 16550;
my ($log, $key);

my @allowed_ports = ( 6543, 6544, 16546, 16547, 16551 );

sub new {

    my $class = shift;
    $log = shift;

    my $self = {};

    bless($self, $class);

    $log->warn("CMux: No key - connections will be insecure")
        unless (defined $key);
    
    $self->setup();
    
    threads->create(sub { $self->mainloop })->detach;

    return $self;

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

# Add a list of ports to the allowed ports list
sub addAllowedPorts {

    foreach my $port (@_) {
        next unless ($port =~ /^\d+$/);
        push @allowed_ports, $port;
    }

}

# Set the encryption key
sub setKey {
    my $class = shift;
    $key = pack("H*", shift);
}

# Initialise a new connection pair
sub initConn {
    
    my $sel = shift;
    my $c   = shift;
    my ($port, $data);

    my $peer = $c->peerhost . ':' . $c->peerport;

    $log->dbg("CMux: New connection from $peer");

    # Disable nagle's algorithm
    $c->setsockopt(IPPROTO_TCP, TCP_NODELAY, 1);

    my $con = { sock => $c };
    
    # Authenticate the client if we have a key and they're not coming
    # from localhost
    if (defined $key && (substr($c->peerhost,0,3) ne '127')) {
        my $iv = authenticate($sel, $c);
        unless (defined $iv) {
            $log->dbg("CMux: $peer failed to authenticate");
            return undef;
        }
        # Initialise the send and recv ciphers (AES CFB8)
        $con->{wcipher} = MDD::CFB->new($key, $iv);
        $con->{rcipher} = MDD::CFB->new($key, $iv);
        $log->dbg("CMux: $peer authenticated successfully, encryption enabled");
    }
    
    # Find out what remote port they're after
    unless ($sel->can_read(4)) {
        $log->dbg("CMux: Timeout waiting for port/data from $peer");
        return undef;
    }

    unless (readSock($con->{sock}, $port, 512, $con->{rcipher})) {
        $log->dbg("CMux: Failed to read port/data from $peer");
        return undef;
    }

    # We can get rid of this hacky HTTP handling at some point
    # MythDroid versions 0.6.3 and above don't need it
    if ($port =~ /^GET/ || $port =~ /^POST/ || $port =~ /^HEAD/) {
        $data = $port;
        if ($data =~ s#/MDDHTTP##) {
            $port = 16551;
        }
        else {
            $port = 6544;
        }
    }
    else {
        chomp($port);
        $port =~ s/\s+$//;
    }
    
    # Check it's in the list of allowed ports
    unless (grep { $_ == $port } @allowed_ports) {
        my $msg = "CMux: connections to port $port are not permitted";
        $log->err($msg);
        syswrite $c, $msg;
        return undef;
    }

    # Make the new connection
    $con->{sock2} = IO::Socket::INET->new(
        PeerAddr => "localhost:$port"
    ) or do {
        my $msg = "CMux: Connection to localhost:$port failed: $!";
        $log->err($msg);
        syswrite $c, $msg;
        return undef;
    };
    $con->{sock2}->setsockopt(IPPROTO_TCP, TCP_NODELAY, 1);

    # More hacky HTTP handling that we can remove at some point
    if ($data) {
        writeSock($con->{sock2}, $data, undef);
    }
    else {
        writeSock($con->{sock}, "OK", $con->{wcipher});
    }

    $sel->add($con->{sock2});
    $log->dbg("CMux: Opened connection to localhost:$port");
    return $con;

}

sub authenticate {

    my $sel = shift;
    my $c   = shift;
    
    my $peer = $c->peerhost . ':' . $c->peerport;

    # Send a nonce / iv
    open R, "</dev/urandom" or do {
        $log->err("Cmux: Failed to read nonce: $!");
        return undef;
    };
    my $nonce;
    if (sysread(R, $nonce, 16) < 16) {
        $log->err("CMux: Failed to read nonce");
        return undef;
    }
    syswrite $c, $nonce;

    # Read the encrypted nonce response and check that it's valid
    # Allow up to 10 attempts
    foreach (0 .. 10) { 
        
        unless ($sel->can_read(4)) {
            $log->dbg("CMux: Timeout waiting for encrypted nonce from $peer");
            syswrite $c, "ER";
            return undef;
        }

        my $encrypted;

        unless (sysread($c, $encrypted, 16)) {
            $log->dbg("CMux: Failed to read encrypted nonce from $peer");
            syswrite $c, "ER";
            return undef;
        }

        if (length($encrypted) != 16) {
            $log->dbg("CMux: Received nonce with invalid length");
            syswrite $c, "ER";
            return undef;
        }

        # Decrypt the nonce and check that it's what we sent them
        my $cipher  = Crypt::Rijndael->new($key, Crypt::Rijndael::MODE_ECB());
        my $decrypt = $cipher->decrypt($encrypted);

        if (
            join("",unpack("H*", $cipher->decrypt($encrypted))) eq
            join("",unpack("H*", $nonce))
        ) {
            syswrite $c, "OK";
            return $nonce;
        }

        # They didn't use the correct key
        syswrite $c, "ER";
        next;
    }

    # We failed to authenticate them
    return undef;

}

# Read from a socket, decrypt the data if appropriate
sub readSock {

    my $ret = sysread $_[0], $_[1], $_[2];
    return $ret unless $_[3];

    $_[1] = $_[3]->decrypt($_[1]);
    return length $_[1];

}

# Write to a socket, encrypt the data if appropriate
sub writeSock {

    my ($s, $buf, $cipher) = @_;
    return syswrite $s, $buf unless $cipher;
    return syswrite $s, $cipher->encrypt($buf);

}

sub connectionThread {

    my $c = shift;

    return unless $c;

    my $sel = IO::Select->new($c);

    my $con = initConn($sel, $c);

    unless ($con) {
        $c->close;
        return;
    }

    my ($data, $len, $wsock, $rcipher, $wcipher);

    SELECT: while (my @ready = $sel->can_read) {
        
        foreach my $fd (@ready) {

            if ($fd == $con->{sock}) {
                $wsock   = $con->{sock2};
                $rcipher = $con->{rcipher};
                $wcipher = undef;
            }
            else {
                $wsock = $con->{sock};
                $rcipher = undef;
                $wcipher = $con->{wcipher};
            }

            last SELECT unless ($len = readSock($fd, $data, 4096, $rcipher));
            
            if (! defined writeSock($wsock, $data, $wcipher)) {
                $log->warn("Error writing to socket: $!");
            }

            $data = undef;

        }

    }

    $log->dbg(
        "CMux: Closing connection to " . $con->{sock}->peerhost .
            ":" . $con->{sock}->peerport
    );
    $log->dbg(
        "CMUx: Closing connection to " . $con->{sock2}->peerhost .
            ":" . $con->{sock2}->peerport
    );
    $con->{sock}->close;
    $con->{sock2}->close;


}

sub mainloop {

    my $self = shift;

    while (my @ready = $self->{select}->can_read) {
        foreach my $fd (@ready) {
            # New connection
            threads->create(sub { connectionThread($fd->accept) })->detach; 
        }
    }

    $log->err(
        "CMux: mainloop ended whilst selecting on " .
        $self->{select}->count . " handles"
    );

}

return 1;
