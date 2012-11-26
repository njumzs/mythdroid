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

# An implementation of CFB8 using AES 

package MDD::CFB;
use strict;
use warnings;

use Crypt::Rijndael;

sub new {
    my ($class, $key, $iv) = @_;
    my $self = {};
    $self->{algo} = Crypt::Rijndael->new($key, Crypt::Rijndael::MODE_ECB());
    $self->{register} = $iv;
    return bless ($self, $class);
}

sub process {
    my ($self, $string, $d) = @_;
    my ($output, $out, $i, $l, $byte, $xor, $key);
    $l = length ($string);
    for ($i = 0; $i < $l; $i++) {
        $byte = substr($string, $i, 1);
        $key  = $self->{algo}->encrypt($self->{register});
        $xor  = substr($key, 0, 1);
        $out  = $byte ^ $xor;
        substr($self->{register}, 0, 1, "");
        $self->{register} .= ($d ? $byte : $out);
        $output .= $out;
    }
    return $output;
}

sub encrypt {
    my $self = shift;
    return $self->process(shift, 0);
}

sub decrypt {
    my $self = shift;
    return $self->process(shift, 1);
}
		
1;
