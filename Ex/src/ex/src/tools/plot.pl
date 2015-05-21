#!perl -w
#$Id: plot.pl 1932 2009-04-10 22:17:12Z labsky $
use strict;
use File::stat;

my $dot="C:/Program Files/ATT/Graphviz/bin/dot.exe";
my $ps2pdf="ps2pdf";
my $fmt="ps";

my $filter=0;
if(scalar(@ARGV)>0) {
  if($ARGV[0] =~ /^\-/) {
  	$fmt=substr($ARGV[0], 1);
  	if(scalar(@ARGV)>1) {
  		$filter=$ARGV[1];
  	}
  }else {
  	$filter=$ARGV[0];
  }
}

my $finFmt=$fmt;
if($fmt eq 'pdf') {
  $fmt='ps';
}


my @files=`ls`;
my $i=0;
for my $file (@files) {
	chomp($file);
	if($filter && $file !~ /$filter/i) {
		next;
	}
	if($file !~ /\.dot$/i) {
		next;
	}
	my $target=$file;
	$target =~ s/\.dot$/.$fmt/i;
	my $finTarget=$file;
        $finTarget =~ s/\.dot$/.$finFmt/i;
        my $fi=stat($target);
        my $tgtTimeStamp=$fi? $fi->mtime: 0;

        if(stat($file)->mtime > $tgtTimeStamp) {
            $i++;
            print STDERR "$i. $file --> $target --> $finTarget\n";
            print STDERR `\"$dot\" -T$fmt $file -o $target`;
            if($finFmt eq 'pdf') {
              print STDERR `\"$ps2pdf\" $target`;
            }
        }
}
