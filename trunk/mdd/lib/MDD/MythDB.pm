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

package MDD::MythDB;
use strict;
use warnings;

use MythTV;

my $mythtv = MythTV->new(\{ 'connect' => 0 });
my ($dbh, $log);

# SQL statements
my $albumArtSQL = 
    'SELECT DISTINCT albumart_id from music_albumart LEFT JOIN music_songs ' .
    'ON music_albumart.directory_id = music_songs.directory_id LEFT JOIN ' .
    'music_albums on music_songs.album_id = music_albums.album_id WHERE ' .
    'music_albums.album_name = ? and music_albumart.imagetype = 1';

my $videoSQL = 
    'SELECT title, subtitle, director, plot, homepage, year, userrating, ' .
    'length,filename FROM videometadata where filename REGEXP ?';
    
my $getRecGroupsSQL = 
    'SELECT DISTINCT recgroup FROM recorded WHERE recgroup != "LiveTV" AND ' .
    'recgroup != "Deleted" ORDER by recgroup';

my $progSQL =
    'SELECT program.chanid,UNIX_TIMESTAMP(starttime),UNIX_TIMESTAMP(endtime),' .
    'title,subtitle,description,category,seriesid,programid,channel.callsign ' . 
    'FROM program LEFT JOIN channel ON program.chanid = channel.chanid WHERE ' . 
    'program.chanid = ? AND starttime = FROM_UNIXTIME(?)';

my $newRecSQL = 
    'INSERT INTO record (chanid,starttime,startdate,endtime,enddate,title,' .
    'subtitle,description,category,seriesid,programid,station,next_record,' .
    'last_record,last_delete,autoexpire,autocommflag) VALUES (?,' .
    'FROM_UNIXTIME(?),FROM_UNIXTIME(?),FROM_UNIXTIME(?),FROM_UNIXTIME(?),' .
    '?,?,?,?,?,?,?,\'00:00:00\',\'00:00:00\',\'00:00:00\',1,1)';

my $updateRecSQL = 
    'UPDATE record SET %UPDATES% where recordid = %RECID%';

my $delRecSQL = 
    'DELETE FROM record where recordid = ?';

my $settingSQL = 
    'SELECT data FROM settings WHERE value = ? AND hostname = ?';

my $settingNoHostSQL = 
    'SELECT data FROM settings WHERE value = ? AND hostname IS NULL';

my $getStorGroupsSQL = 'SELECT groupname,dirname FROM storagegroup';
my $recTypeSQL       = 'SELECT type FROM record WHERE recordid = ?';
my $storGroupSQL     = 'SELECT storagegroup FROM record WHERE recordid = ?';
my $upnpVideoSQL     =
    'SELECT intid,filepath FROM upnpmedia WHERE filepath LIKE \'%\' ?';

my @videoFields = (
    qw(
        title subtitle director plot homepage year userrating length filename
    )
);
    
my (
    $albumArtSth, $videoSth, $upnpVideoSth, $getStorGroupsSth,
    $getRecGroupsSth, $newRecSth, $progSth, $storGroupSth, $recTypeSth,
    $delRecSth, $settingSth, $settingNoHostSth
);

sub new {

    my $class = shift;
    $log = shift;

    my $self = {};

    $dbh = clone() unless $dbh;

    bless $self, $class;

    $self->{VidDBVer} = $self->settingNoHost('mythvideo.DBSchemaVer');
    $log->dbg("Video DB schema version is $self->{VidDBVer}") if $log;

    return $self;
}

sub clone {
    my $dbh = $mythtv->{dbh}->clone;
    $dbh->{mysql_auto_reconnect} = 1;
    return $dbh;
}

# Prepare a MySQL statement handle, pass a ref to the SQL string
sub execute {

    my $sth    = shift;
    my $sqlref = shift;
    my @args   = @_;

    unless ($sth && $sth->execute(@args)) {
        unless ($sth = $dbh->prepare($$sqlref)) {
            $dbh = clone();
            $sth = $dbh->prepare($$sqlref);
        }
        unless ($sth->execute(@args)) {
            $log->err("SQL stmt failed: " . $sth->errstr);
        }
    }

    return $sth;
}

sub dosql {

    my $sql = shift;
    my $ret;

    unless (($ret = $dbh->do($sql))) {
        $dbh = clone();
        $ret = $dbh->do($sql);
    }
    
    $log->err("SQL stmt failed: " . $dbh->errstr) unless $ret;

    return $ret;

}

sub getVideos($) {

    my $self  = shift;
    my $regex = shift;

    my %videos;
    my @vids;

    my $pathIdx = 8;

    # MythTV 0.21 doesn't have the subtitle column
    if ($self->{VidDBVer} < 1024) {
        $videoSQL =~ s/subtitle, //;
        $pathIdx = 7;
    }

    $videoSth = execute($videoSth, \$videoSQL, $regex);

    while (my $aref = $videoSth->fetchrow_arrayref) {
        my $path = $aref->[$pathIdx];
        $upnpVideoSth = execute($upnpVideoSth, \$upnpVideoSQL, $path);
        my $upnparef = $upnpVideoSth->fetchrow_arrayref;
        next unless $upnparef;
        my $id = $upnparef->[0];
        my %h;
        splice @$aref, 1, 0, '' if ($self->{VidDBVer} < 1024);
        @h{@videoFields} = @$aref;
        $h{filename} = $upnparef->[1];
        $videos{$id} = \%h;
    }
    
    foreach my $id (keys %videos) {
        my $msg = "VIDEO $id";
        foreach my $f (
            map { '||' . $videos{$id}{$_} } @videoFields
        ) { $msg .= $f }
        push @vids, $msg;
    }

    @vids = sort @vids;

    return \@vids;

}

# Get recording type from recid
sub getRecType($) {

    my $self  = shift;
    my $recid = shift;

    my $ret = 0;

    $recTypeSth = execute($recTypeSth, \$recTypeSQL, $recid);

    if (my $aref = $recTypeSth->fetchrow_arrayref) {
        $ret = $aref->[0];
    }
    
    $log->dbg("getRecType($recid) = $ret");
    
    return $ret;
}

# Get the storage group from a recid
sub getStorGroup($) {

    my $self  = shift;
    my $recid = shift;

    my $ret = "Default";
        
    $storGroupSth = execute($storGroupSth, \$storGroupSQL, $recid);

    if (my $aref = $storGroupSth->fetchrow_arrayref) {
        $ret = $aref->[0];
    }
    
    $log->dbg("getStorGroup($recid) = $ret");
    
    return $ret;

}

# Populate the global storageGroups hash
sub getStorGroups() {

    my %storGroups;

    $getStorGroupsSth = execute($getStorGroupsSth, \$getStorGroupsSQL);

    while (my $aref = $getStorGroupsSth->fetchrow_arrayref) {
        push @{ $storGroups{$aref->[0]} }, $aref->[1];
    }
    
    $log->dbg("getStorGroups() found " . scalar(keys %storGroups) . " groups");

    return \%storGroups;
}

# get a list of recording groups
sub getRecGroups() {

    my @recGroups;
    
    $getRecGroupsSth = execute($getRecGroupsSth, \$getRecGroupsSQL);

    while (my $aref = $getRecGroupsSth->fetchrow_arrayref) {
        push @recGroups, $aref->[0];
    }
    
    $log->dbg("getRecGroups() found " . scalar(@recGroups) . " groups");

    return \@recGroups;

}

# Update an existing recording rule
sub updateRec($$) {

    my $self    = shift;
    my $recid   = shift;
    my $updates = shift;

    my $sql = $updateRecSQL;
    $sql =~ s/%UPDATES%/$updates/;
    $sql =~ s/%RECID%/$recid/;
    
    $log->dbg("updateRec updating $recid with $updates");

    $recid = -1 if (dosql($sql) < 1);

    return $recid;

}

# Create a new recording rule
sub newRec($$$) {
    
    my $self      = shift;
    my $chanid    = shift;
    my $starttime = shift;
    my $updates   = shift;

    my @prog;
    
    $log->dbg("newRec $chanid $starttime $updates");
    
    $progSth = execute($progSth, \$progSQL, $chanid, $starttime);

    my $aref = $progSth->fetchrow_arrayref;

    return -1 unless defined $aref;

    $prog[0] = $aref->[0];
    $prog[1] = $prog[2] = $aref->[1];
    $prog[3] = $prog[4] = $aref->[2];
    push @prog, @{$aref}[3..$#{$aref}];
    
    $log->dbg("newRec prog chan $prog[0] start $prog[1] title $prog[5], prog len " . scalar @prog);

    $newRecSth = execute($newRecSth, \$newRecSQL, @prog);
    
    my $recid = $dbh->last_insert_id(undef,undef,undef,undef);
    
    $log->dbg("newRec recid = $recid");

    return $self->updateRec($recid, $updates);

}

# Delete a recording rule
sub delRec($) {

    my $self  = shift;
    my $recid = shift;
    
    $log->dbg("delRec($recid)");

    $delRecSth = execute($delRecSth, \$delRecSQL, $recid);

}

sub getAlbumArtId($) {

    my $self  = shift;
    my $album = shift;
    
    my $ret = undef;

    $albumArtSth = execute($albumArtSth, \$albumArtSQL, $album);

    if (my $aref = $albumArtSth->fetchrow_arrayref) {
        $ret = $aref->[0];
    }
    
    $log->dbg("getAlbumArtId($album) = $ret");

    return $ret;

}

sub setting($$) {

    my $self  = shift;
    my $value = shift;
    my $host  = shift;

    my $ret = undef;

    $settingSth = execute($settingSth, \$settingSQL, $value, $host);

    if (my $aref = $settingSth->fetchrow_arrayref) {
        $ret = $aref->[0];
    }
    
    $log->dbg("setting($value, $host) = $ret") if (defined $ret);

    return $ret;
        
}

sub settingNoHost($) {
    
    my $self  = shift;
    my $value = shift;

    my $ret = undef;

    $settingNoHostSth = execute($settingNoHostSth, \$settingNoHostSQL, $value);

    if (my $aref = $settingNoHostSth->fetchrow_arrayref) {
        $ret = $aref->[0];
    }
    
    $log->dbg("settingNoHost($value) = $ret") if (defined $ret && $log);

    return $ret;
    
}

return 1;
