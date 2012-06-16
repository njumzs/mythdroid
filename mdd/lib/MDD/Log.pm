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

package MDD::Log;
use strict;
use warnings;
use Carp;

my @months = qw(Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec);
my @wdays = qw(Sun Mon Tue Wed Thu Fri Sat);

sub new($$) {

    my $class = shift;
    my $file  = shift;
    my $debug = shift;

    my $self = {};

    $file = '/tmp/mdd.log' unless (defined $file);

    open $self->{FH}, ">$file" or croak "Error opening log file $file: $!";
    select((select($self->{FH}), $| = 1)[0]);
    $self->{DBG} = $debug ? 1 : 0;

    return bless ($self, $class);

}

sub date {
    my ($s,$m,$h,$d,$mo,$y,$wd) = localtime;
    return sprintf("$wdays[$wd] $d $months[$mo] %02d:%02d:%02d", $h,$m,$s);
}

sub dbg($) {
    my $self = shift;
    my $msg  = shift;
    my $date = date();
    print { $self->{FH} } "$date - Debug -   $msg\n" if $self->{DBG};
}

sub warn($) {
    my $self = shift;
    my $msg  = shift;
    my $date = date();
    print { $self->{FH} } "$date - Warning - $msg\n";
}

sub err($) {
    my $self = shift;
    my $msg  = shift;
    my $date = date();
    print { $self->{FH} } "$date - Error -   $msg\n";
}

sub fatal($) {
    my $self = shift;
    my $msg  = shift;
    my $date = date();
    print { $self->{FH} } "$date - Fatal Error -   $msg\n";
    croak "MDD: $date - Fatal Error - $msg\n";
}

1;
    
