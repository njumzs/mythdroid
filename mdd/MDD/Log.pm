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
    print STDERR "MDD: $date - Warning - $msg\n";
}

sub err($) {
    my $self = shift;
    my $msg  = shift;
    my $date = date();
    print { $self->{FH} } "$date - Error -   $msg\n";
    print STDERR "MDD: $date - Error -   $msg\n";
}

sub fatal($) {
    my $self = shift;
    my $msg  = shift;
    my $date = date();
    print { $self->{FH} } "$date - Fatal Error -   $msg\n";
    croak "MDD: $date - Fatal Error - $msg\n";
}

1;
    
