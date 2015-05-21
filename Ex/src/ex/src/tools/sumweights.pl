#!perl
use warnings;
use strict;

my $sum=0;
my $cnt=0;
my %counts=();
my %fc=();
my %fc_occ=();
while(<>) {
	my $i=index($_,'<value index="3">1</value><value index="9">1</value><value index="10">1</value><value index="26">1</value><value index="31">1</value><value index="35">1</value><value index="38">1</value><value index="49">1</value><value index="51">1</value>');
	if($i!=-1 && $_ =~ /speaker/) {
		print "$_\n";
	}
	
	if(/<instance weight=\"([\d.]+)\" type=\"sparse\"><value index=\"1\">([^<]+)/) {
		my $w=$1;
		my $cls=$2;
		$sum+=$w;
		$cnt++;
		$counts{$cls}+=$w;
		while(/<value index=\"(\d+)\">/g) {
			$fc{$1}++;
			$fc_occ{$1}+=$w;
			
			$fc{-1}++;
			$fc_occ{-1}+=$w;
		}
	}
}
print "samples=$cnt, sum(weight)=$sum, avg(sum)=".($sum/$cnt)."\n";
foreach my $att (keys %counts) {
	print "$att=$counts{$att}\n";
}
foreach my $feat (sort {$a <=> $b} (keys %fc)) {
	print "f$feat = $fc{$feat}, $fc_occ{$feat}\n";
}
