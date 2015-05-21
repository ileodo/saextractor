#!perl -w
use strict;
use warnings;

my $dir=$ARGV[0];

die "Usage: keep_if_exists DIR < files\n\tPrints those files from stdin that exist in DIR." if(!-d $dir);
print STDERR "Processing $dir\n";

while(<STDIN>) {
    chomp;
    my $fn="$dir/$_";
    print "$_\n" if(-e $fn);
}
