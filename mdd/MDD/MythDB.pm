package MDD::MythDB;
use strict;
use warnings;

use MythTV;

my $mythtv = MythTV->new(\{ 'connect' => 0 });
my $dbh;

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

my $upnpVideoSQL     = 'SELECT intid FROM upnpmedia WHERE filepath = ?';
my $getStorGroupsSQL = 'SELECT groupname,dirname FROM storagegroup';
my $recTypeSQL       = 'SELECT type FROM record WHERE recordid = ?';
my $storGroupSQL     = 'SELECT storagegroup FROM record WHERE recordid = ?';

my @videoFields = (
    qw(
        title subtitle director plot homepage year userrating length filename
    )
);
    
my (
    $albumArtSth, $videoSth, $upnpVideoSth, $getStorGroupsSth,
    $getRecGroupsSth, $newRecSth, $progSth, $storGroupSth, $recTypeSth,
    $delRecSth, $settingSth
);

sub new {

    my $class = shift;

    my $self = {};

    $dbh = clone() unless $dbh;

    return bless ($self, $class);
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
        $sth->execute(@args);
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

    return $ret;

}

sub getVideos($) {

    my $self  = shift;
    my $regex = shift;

    my %videos;
    my @vids;

    $videoSth = execute($videoSth, \$videoSQL, $regex);

    while (my $aref = $videoSth->fetchrow_arrayref) {
        my $path = $aref->[8];
        $upnpVideoSth = execute($upnpVideoSth, \$upnpVideoSQL, $path);
        my $id = ($upnpVideoSth->fetchrow_arrayref)->[0];
        my %h;
        @h{@videoFields} = @$aref;
        $videos{$id} = \%h;
    }
    
    foreach my $id (keys %videos) {
        my $msg = "VIDEO $id";
        foreach my $f (
            map { '||' . $videos{$id}{$_} } @videoFields
        ) { $msg .= $f }
        push @vids, $msg;
    }

    return \@vids;

}

# Get recording type from recid
sub getRecType($) {

    my $self  = shift;
    my $recid = shift;

    $recTypeSth = execute($recTypeSth, \$recTypeSQL, $recid);

    if (my $aref = $recTypeSth->fetchrow_arrayref) {
        return $aref->[0];
    }
    
    return "0";
}

# Get the storage group from a recid
sub getStorGroup($) {

    my $self  = shift;
    my $recid = shift;
        
    $storGroupSth = execute($storGroupSth, \$storGroupSQL, $recid);

    if (my $aref = $storGroupSth->fetchrow_arrayref) {
        return $aref->[0];
    }
    
    return "Default";

}

# Populate the global storageGroups hash
sub getStorGroups() {

    my %storGroups;

    $getStorGroupsSth = execute($getStorGroupsSth, \$getStorGroupsSQL);

    while (my $aref = $getStorGroupsSth->fetchrow_arrayref) {
        $storGroups{$aref->[0]} = $aref->[1];
    }

    return \%storGroups;
}

# get a list of recording groups
sub getRecGroups() {

    my @recGroups;
    
    $getRecGroupsSth = execute($getRecGroupsSth, \$getRecGroupsSQL);

    while (my $aref = $getRecGroupsSth->fetchrow_arrayref) {
        push @recGroups, $aref->[0];
    }

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
    
    $progSth = execute($progSth, \$progSQL, $chanid, $starttime);

    my $aref = $progSth->fetchrow_arrayref;

    $prog[0] = $aref->[0];
    $prog[1] = $prog[2] = $aref->[1];
    $prog[3] = $prog[4] = $aref->[2];
    push @prog, @{$aref}[3..$#{$aref}];

    $newRecSth = execute($newRecSth, \$newRecSQL, @prog);
    
    my $recid = $dbh->last_insert_id(undef,undef,undef,undef);

    return $self->updateRec($recid, $updates);

}

# Delete a recording rule
sub delRec($) {

    my $self  = shift;
    my $recid = shift;

    $delRecSth = execute($delRecSth, \$delRecSQL, $recid);

}

sub getAlbumArtId($) {

    my $self  = shift;
    my $album = shift;

    $albumArtSth = execute($albumArtSth, \$albumArtSQL, $album);

    if (my $aref = $albumArtSth->fetchrow_arrayref) {
        return $aref->[0];
    }

}

sub setting($$) {

    my $self  = shift;
    my $value = shift;
    my $host  = shift;

    $settingSth = execute($settingSth, \$settingSQL, $value, $host);

    if (my $aref = $settingSth->fetchrow_arrayref) {
        return $aref->[0];
    }
        
}

return 1;
