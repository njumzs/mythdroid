#!/usr/bin/perl

use strict;
use warnings;
use MDD::ConfigData;

use Test::More tests => MDD::ConfigData->feature('backend') ? 6 : 11;

BEGIN {
    use_ok('MythTV');
    use_ok('MDD::Log');
    use_ok('MDD::MythDB'); 
    use_ok('Sys::Hostname');
}

my $mtv = new_ok(MythTV => [\{'connect' => 0}], 'MythTV');
my $mythdb = new_ok('MDD::MythDB' => [MDD::Log->new('/tmp/mdd.log', 0)], 'MythDB');

unless (MDD::ConfigData->feature('backend')) {

    is($mythdb->setting('LCDEnable', hostname), 1, 'LCD enabled')
        or diag("The LCD is not enabled in mythfrontend settings");
    is($mythdb->setting('LCDShowMenu', hostname), 1, 'LCD Menu enabled')
        or diag("LCD Show Menu is not enabled in mythfrontend settings");
    is($mythdb->setting('LCDShowMusic', hostname), 1, 'LCD Music enabled')
        or diag("LCD Show Music is not enabled in mythfrontend settings");
    is($mythdb->setting('LCDShowChannel', hostname), 1, 'LCD Channel enabled')
        or diag("LCD Show Channel is not enabled in mythfrontend settings");

    unlike((`which mythlcdserver`)[0], qr/^which:/, 'mythlcdserver installed');

}

