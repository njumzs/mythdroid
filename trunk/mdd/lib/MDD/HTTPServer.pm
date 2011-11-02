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

package MDD::HTTPServer;
use strict;
use warnings;
use threads;
use threads::shared;

use HTTP::Daemon;
use URI::Escape;
use Image::Imlib2;

my $log;
my @files :shared;
my $tmpfile = '/tmp/mddimg.jpg';

sub new {

    my $class = shift;
    $log = shift;

    my $self = {};

    bless $self, $class;

    threads->create('mainloop')->detach;

    return $self;

}

sub addFile($) {
    
    my $self = shift;
    my $file = shift;
    return unless $file && $file =~ m#^/#;
    return if grep { $_ eq $file } @files;
    $log->dbg("HTTPServer: adding $file");
    push @files, $file;

}

sub mainloop {

    my $lastfile = "";

    my $daemon = HTTP::Daemon->new(LocalPort => 16551)
        or $log->fatal("HTTPServer: Failed to create server: $!");

    $log->dbg("HTTPServer: listening on port 16551");

    while (my $client = $daemon->accept) {
        
        my $req = $client->get_request;

        my $method = $req->method;
        my $file = uri_unescape($req->uri->path);
        my ($width, $height) = $req->uri->query =~ /width=(\d+)&height=(\d+)/;

        if ($width && $height) {
            $log->dbg("HTTPServer: $method $file $width x $height");
        }
        else {
            $log->dbg("HTTPServer: $method $file");
        }

        if ($method eq 'GET') {
            unless (grep { $_ eq $file } @files) {
                $log->dbg("$file is not registered - send 404");
                $client->send_status_line(404);
                $client->close;
                next;
            }
            if ($width && $height) {
                if ($file ne $lastfile) {
                    my $img = Image::Imlib2->load($file);
                    unless ($img) {
                        $client->send_status_line(404);
                        $client->close;
                        next;
                    }
                    my $simg = $img->create_scaled_image($width,$height);
                    $simg->image_set_format("jpeg");
                    $simg->save($tmpfile);
                    undef $simg;
                }
                $lastfile = $file;
                $file = $tmpfile;
            }
            $client->send_file_response($file);
            $client->close;
        }

        $client->send_status_line(404);
        $client->close;
    }

}

1;
