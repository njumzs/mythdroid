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

package MDD::XOSD;
use strict;
use warnings;
use X::Osd;

my $font = '-b&h-lucida-bold-*-normal-*-*-240-*-*-*-*-*-*';

sub new {

    my $class = shift;

    my $self = {};

    $self->{font}     = $font;
    $self->{pos}      = XOSD_bottom;
    $self->{align}    = XOSD_center;
    $self->{colour}   = 'green';
    $self->{outline}  = 5;
    $self->{timeout}  = 8;
    $self->{shadow}   = 0;

    $ENV{LANG} = 'foo';

    return bless $self, $class;

}

sub colour($) {
    my $self = shift;
    $self->{colour} = shift;
}

sub outline($) {
    my $self = shift;
    $self->{outline} = shift;
}

sub shadow($) {
    my $self = shift;
    $self->{shadow} = shift;
}

sub timeout($) {
    my $self = shift;
    $self->{timeout} = shift;
}

sub display($) {

    my $self = shift;
    my $msg = shift;

    my @lines;

    push @lines, $msg;

    if (length $msg > 50) {
        shift @lines;
        @lines = split /([\w\s]{30,50})\s+/, $msg;
    }

    my $osd = X::Osd->new(scalar @lines);

    return 0 unless defined $osd;

    $osd->set_font($self->{font});
    $osd->set_pos($self->{pos});
    $osd->set_align($self->{align});
    $osd->set_colour($self->{colour});
    $osd->set_outline_offset($self->{outline});
    $osd->set_shadow_offset($self->{shadow});
    $osd->set_timeout($self->{timeout});

    my $i = 0;

    foreach my $line (@lines) {
        next unless length $line;
        $osd->string($i++, $line);
    }

    $osd->wait_until_no_display();

    return 1;

}

1;

