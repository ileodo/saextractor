#!perl -w
#$Id: $
use strict;
use File::stat;

print "Confirm you want to remove automatic annotations from html files in this directory by typing yes and enter\n";
my $yes=readline(STDIN);
chomp $yes;
print "Ok, $yes\n";
exit if(lc($yes) ne "yes");

my @files=`ls`;
my $i=0;
for my $file (@files) {
	chomp($file);
	next if(!-f $file || -d $file);
	if($file !~ "htm") {
		print "Skipping $file (only processing html files)\n";
		next;
	}
	print "> $file ...";
	print `mv "$file" "$file.backup"`;
	die "cant open $file.backup" if(!open F, "$file.backup");
	my $cnt=0;
	while(<F>) {
		if($_ =~ / {confidence {GDM_STRING /) {
			$cnt++;
		}
	}
	close(F);
	die "cant open $file.backup" if(!open F, "$file.backup");
	die "cant open $file " if (!open F2, ">$file");
	while(<F>) {
		if($cnt>0 && $_ =~ /^Annotations::(\d+)$/) {
			my $dec = $1 - $cnt;
			$_ =~ s/\d+/$dec/;
			print "Removed $cnt automatic annots, $dec remaining\n";
			print F2 $_;
			$cnt = -1;
		}elsif($_ !~ / {confidence {GDM_STRING /) {
			print F2 $_;
		}
	}
	close F;
	close F2;
}
