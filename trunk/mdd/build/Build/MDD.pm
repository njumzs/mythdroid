package Build::MDD;
use Module::Build;
use Carp;
@ISA = qw(Module::Build);

sub create_account {

    my $self = shift;
    my $user = 'mdd';
    return if (getpwnam $user);
    print "Creating $user user account..\n";
    $self->do_system(
        "useradd -r -d /dev/null -c 'Added by MDD' -U $user -s /sbin/nologin"
    );

}

sub get_install_dir() {
    
    $ENV{PATH} .= ':/usr/local/bin' unless $ENV{PATH} =~ m#/usr/local/bin#;
    my $dir = (`which mythlcdserver`)[0];
    croak "Couldn't locate mythlcdserver. Aborted\n"
        unless ($dir && $dir !~ /^which:/);
    $dir =~ s/\/mythlcdserver$//;
    chomp $dir;
    return $dir;

}

sub install_frontend {

    use File::Copy qw(copy);

    my $self = shift;

    map { $self->do_system("killall -9 $_ 2>/dev/null") } 
        (qw(mythfrontend mythfrontend.real mythlcdserver mythlcd));

    my $dir = get_install_dir();

    $dst  = "$dir/mythlcd"; 
    $path = "$dir/mythlcdserver";

    unless ((`file $path`)[0] =~ /perl/i) {
        print "cp $path -> $dst\n";
        copy($path, $dst) or die "$!\n";
        chmod(0755, $dst) 
            or warn "chmod of $dst failed\n";
    }

    print "cp bin/mdd.pl -> $path\n";
    copy('bin/mdd.pl', $path) or die "$!\n";
    chmod(0755, $path) 
        or warn "chmod of $path failed\n";

}

sub install_conf {

    use File::Copy qw(copy);
    my $self    = shift;
    my $destdir = $self->destdir();
    $destdir    = "" unless $destdir;
    my $confdir = $destdir . $self->install_destination('conf');
    unless (-e "$confdir/mdd.conf") {
        print "cp conf/mdd.conf -> $confdir/mdd.conf\n";
        copy('conf/mdd.conf', "$confdir/mdd.conf") or die "$!\n";
        chmod(0644, "$confdir/mdd.conf")
            or warn "chmod of $confdir/mdd.conf failed\n";
    }

}

sub install_init {

    use File::Copy qw(copy);
    my $self    = shift;
    my $destdir = $self->destdir();
    $destdir    = "" unless $destdir;
    my $bindir  = $destdir . $self->install_destination('bin');
    my $confdir = $destdir . $self->install_destination('conf');
    my $init    = "$confdir/init.d/mdd";
    my $uinit   = "$confdir/init/mdd.conf"; 
    my $running = (`ps aux | grep /usr/bin/mdd | grep -v grep`)[0];
    my $new     = 0;
    

    if (-e '/usr/sbin/service' && -d "$confdir/init") {
        print "Installing upstart script\n";
        copy('init/upstart', $uinit);
        $self->do_system('service mdd ' . ($running ? 'restart' : 'start'));
        return;
    }
    
    if (
        -e '/sbin/runscript' && -e '/sbin/start-stop-daemon' &&
        -d "$confdir/init.d"
    ) {
        $new = -e $init; 
        print "Installing gentoo init script\n";
        copy('init/gentoo', $init);
        chmod(0755, $init);
        if ($new) {
            $self->do_system('rc-update add mdd default');
        }
        $self->do_system("$init restart");
        return;
    }

    if (-d '/etc/rc0.d' && -d "$confdir/init.d") {
        $new = -e $init; 
        print "Installing sysv init script\n";
        copy('init/sysv', $init);
        chmod(0755, $init);
        if ($new) {
            map { symlink $init, "$confdir/rc$_.d/K01mdd" } (qw(0 1 6));
            map { symlink $init, "$confdir/rc$_.d/S98mdd" } (qw(2 3 4 5));
        }
        $self->do_system($init . ' ' . ($running ? 'restart' : 'start'));
        return;
    }

    carp "\nWARNING: Unknown init system - you'll have to manually arrange " .
         "for '$bindir/mdd.pl' to be started at boot\n";

}

sub ACTION_install {
    my $self = shift;
    croak("$0 must be run as root to install") unless ($> == 0);
    $self->create_account();
    $self->SUPER::ACTION_install;
    $self->install_conf();
    if ($self->feature('backend')) {
        $self->install_init();
        return;
    }
    if ($self->destdir()) {
        print "Skipping frontend installation steps due to use of destdir\n";
        return;
    }
    if (!$self->y_n("Proceeding will stop mythfrontend. OK? [yn]")) {
        croak("Installation aborted");
    }
    $self->install_frontend();
    print "Done - restart mythfrontend now\n";
}

sub ACTION_uninstall {
    
    my $self    = shift;
    my $destdir = $self->destdir();
    $destdir    = "" unless $destdir;
    my $bindir  = $destdir . $self->install_destination('bin');
    my $confdir = $destdir . $self->install_destination('conf');
    my $poddir  = $destdir . $self->install_destination('libdoc');
    my $archdir = $destdir . $self->install_destination('arch');

    unlink "$poddir/MDD::ConfigData.3pm";
    unlink "$archdir/auto/MDD/.packlist";
    rmdir  "$archdir/auto/MDD";
    
    print "Uninstalling modules\n";
    my $moddir = $destdir . $self->install_destination('lib');
    if (-d "$moddir/MDD") {
        map { chomp; unlink "$moddir/MDD/$_" } `ls -1 $moddir/MDD`;
        rmdir "$moddir/MDD" or croak $!;
    }
    
    my $user = 'mdd';
    print "Removing $user user\n";
    if (getpwnam $user) {
        $self->do_system("userdel $user");
    }

    print "Removing mdd.pl\n";
    map { unlink $_ } ("$bindir/mdd", "$bindir/mdd.pl");

    if ($self->feature('backend')) {
        print "Uninstalling a backend-only installation\n";
        map { unlink $_ } ("$confdir/init.d/mdd", "$confdir/init/mdd.conf");
        map { unlink "$confdir/rc$_.d/K01mdd" } (qw(0 1 6));
        map { unlink "$confdir/rc$_.d/S98mdd" } (qw(2 3 4 5));
        print "Uninstall complete\n";
        return;
    }
    
    if (!$self->y_n("Proceeding will stop mythfrontend. OK? [yn]")) {
        croak("Uninstallation aborted");
    }

    print "Uninstalling a frontend/combined installation\n";
    
    if ($self->destdir()) {
        print "Skipping frontend uninstallation steps due to use of destdir\n";
    }
    else {

        map { $self->do_system("killall -9 $_ 2>/dev/null") } 
            (qw(mythfrontend mythfrontend.real mythlcdserver mythlcd));


        my $dir  = get_install_dir();
        my $dst  = "$dir/mythlcd"; 
        my $path = "$dir/mythlcdserver";

        if ((`file $path`)[0] =~ /perl/) {
            copy($dst, $path) or croak "$!\n";
        }
        unlink $dst;
    }

    print "Uninstall complete\n";

}

1;
