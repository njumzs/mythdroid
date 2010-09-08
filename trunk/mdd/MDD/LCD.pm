package MDD::LCD;
use strict;
use warnings;

use IO::Socket::INET;
use Time::HiRes qw(usleep);
use MDD::MythDB;

my $mythdb = MDD::MythDB->new();

sub new {

    my $class = shift;
    
    my $self = {};
    
    $self->{SOCK} = undef;
    $self->{PORT} = 6545;

    $self->{LAST}{cmd} = $self->{LAST}{menu} = $self->{LAST}{music} =
    $self->{LAST}{pos} = $self->{LAST}{chan} = undef;

    $self->{FORNEW}{menu} = $self->{FORNEW}{music} =
    $self->{FORNEW}{chan} = undef;

    return bless ($self, $class);

}

sub start {

    my $self = shift;

    my $args = join ' ', @_;

    $args =~ s/&$//;

    # Check whether it's already running
    return $self->{PORT} unless system("ps ax | grep mythlcd[[:space:]] >/dev/null");

    if ($args =~ /-p\s*(\d+)/) {
        $self->{PORT} = $1;
        my $p = $self->{PORT} + 1000;
        $args =~ s/-p\s*(\d+)/-p $p/;
    }
    else {
        $args .= " -p " . ($self->{PORT} + 1000);
    }

    system("mythlcd $args &");

    return $self->{PORT};

}

sub connect {

    my $self = shift;

    foreach (0 .. 4) {

        $self->{SOCK} = IO::Socket::INET->new(
            Proto       => 'tcp',
            PeerAddr    => 'localhost',
            PeerPort    => $self->{PORT} + 1000
        ) and return $self->{SOCK};
        
        usleep(600000);

    }

    return undef;

}

sub forNewClient() {

    my $self = shift;

    my @msgs;

    foreach my $type (keys %{ $self->{FORNEW} }) {
        push @msgs, $self->{FORNEW}{$type} if $self->{FORNEW}{$type};
    }

    return @msgs;

}

sub command($) {

    my $self = shift;
    my $data = shift;

    return if (defined $self->{LAST}{cmd} && $data eq ${$self->{LAST}{cmd}});

    $self->{LAST}{cmd} = \$data;

    if    ($data =~ s/^SWITCH_TO_MENU\s+"MYTH-([^"]+)"\s+//) {
        return $self->menu($1, $data);
    }
    elsif ($data =~ s/^SWITCH_TO_MUSIC\s+//) {
        return $self->music($data);
    }
    elsif ($data =~ /^SET_MUSIC_PROGRESS.*?([\.\d]+)$/) {
        return $self->musicProgress($1);
    }
    elsif ($data =~ /^SET_MUSIC_PLAYER_PROP\s+(\w+)\s+(\w+)/) {
        return $self->musicPlayerProp($1, $2);
    }
    elsif ($data =~ s/^SWITCH_TO_CHANNEL\s+//) {
        return $self->channel($data);
    }
    elsif ($data =~ /^SET_CHANNEL_PROGRESS\s+([\.\d]+)/) {
        return $self->channelProgress($1);
    }
    elsif ($data =~ /^SWITCH_TO_TIME/) {
        return "DONE";
    }

}

sub menu($$) {
    
    my $self = shift;
    my $menu = shift;
    my $items = shift;

    return if (defined $self->{LAST}{menu} && $items eq ${$self->{LAST}{menu}});

    $self->{LAST}{menu}  = \$items;
    $self->{LAST}{music} = $self->{LAST}{chan} = undef;
    $self->{LAST}{pos}   = -1;

    my @menu;

    my (@items) = $items =~ 
        /"([^"]+)"\s+NOTCHECKABLE\s+(\w+)/g;

    my $idx = 0;
    my $curidx;
    
    while (my $item = shift @items) {
        my $cur = shift @items;
        push @menu, { 'item' => $item, 'cur' =>  $cur };
        $curidx = $idx if $cur eq 'TRUE';
        $idx++;
    }

    my $msg = "MENU $menu ITEM $menu[$curidx]->{item}";
    
    $self->{FORNEW}{menu} = $msg . "\n";

    return $msg;

}

sub music($) {

    my $self = shift;
    my $data = shift;
    my $albumartId;

    return if (defined $self->{LAST}{music} && $data eq ${$self->{LAST}{music}});

    $self->{LAST}{music} = \$data;

    my ($artist, $album, $track) = $data =~
        /^"([^"]+)"\s+"([^"]+)"\s+"([^"]+)"$/;

    my $msg = "MUSIC $artist ALBUM $album TRACK $track";

    $albumartId = $mythdb->getAlbumArtId($album);

    $msg .= " ARTID $albumartId" if (defined $albumartId);

    $self->{FORNEW}{music} = $msg . "\n";

    return $msg;

}

sub musicProgress($) {

    my $self = shift;
    my $pos  = shift;

    $pos = sprintf "%.2f", $pos;
    
    $pos *= 100;

    return unless $pos % 2;
    return if (defined $self->{LAST}{pos} && $pos == $self->{LAST}{pos});
    $self->{LAST}{pos} = $pos;

    return "MUSICPROGRESS $pos";

}

sub musicPlayerProp($$) {

    my $self = shift;
    my $prop = shift;
    my $val  = shift;

    return "MUSICPLAYERPROP $prop $val";
}

sub channel($) {

    my $self = shift;
    my $data = shift; 

    return if (defined $self->{LAST}{chan} && $data eq ${$self->{LAST}{chan}});

    $self->{LAST}{chan} = \$data;

    my ($chan, $title, $subtitle) = $data =~ /^"([^"]+)"\s+"([^"]+)"\s+"([^"]*)"$/;

    my $msg;

    if ($chan) {
        $msg = "CHANNEL $chan TITLE $title";
    }
    elsif ($title) {
        $msg = "CHANNEL Video TITLE $title";
    }
    else {
        return;
    }

    $msg .= " SUBTITLE $subtitle" if $subtitle;
    
    $self->{FORNEW}{chan} = $msg . "\n";

    return $msg;

}

sub channelProgress($) {

    my $self = shift;
    my $pos  = shift;

    $pos = sprintf "%.3f", $pos;
    
    $pos *= 1000;

    return unless $pos % 2;
    return if (defined $self->{LAST}{pos} && $pos == $self->{LAST}{pos});
    $self->{LAST}{pos} = $pos;

    return "CHANNELPROGRESS $pos";

}

return 1;
