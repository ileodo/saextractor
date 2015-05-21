#!perl -w
#$Id: plot.pl 1178 2008-02-16 22:43:31Z labsky $
use strict;
use File::stat;

my $dot="C:/Program Files/ATT/Graphviz/bin/dot.exe";
my $ps2pdf="ps2pdf";
my $fmt="ps";

my $filter=0;
if(scalar(@ARGV)>0) {
  $filter=$ARGV[0];
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
	my $targetPdf=$file;
        $targetPdf =~ s/\.dot$/.pdf/i;
        my $fi=stat($target);
        my $tgtTimeStamp=$fi? $fi->mtime: 0;

        if(stat($file)->mtime > $tgtTimeStamp) {
            $i++;
            print STDERR "$i. $file --> $target --> $targetPdf\n";
            print STDERR `\"$dot\" -T$fmt $file -o $target`;
            print STDERR `\"$ps2pdf\" $target`;
        }
}
