#!perl
use warnings;
use strict;

my $sum=0;
my $sum_mi=0;
my $cnt=0;
while(<>) {
	if(/n=([\d.]+),i=([\d.]+)/) {
		$sum+=$1;
		$sum_mi+=$2;
		$cnt++;
	}
}
print "samples=$cnt, sum(weight)=$sum, sum(mi)=$sum_mi, avg(sum)=".($sum/$cnt).", avg(sum_mi)=".($sum_mi/$cnt)."\n";
