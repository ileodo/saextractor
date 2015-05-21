#!perl -w
# $Id: $
use strict;
use warnings;

sub readTex($$$$$$);
sub checkUsage($$$$$);

if(scalar(@ARGV)==0) {
    print "Usage: perl checkbib.pl main.tex\n";
    exit(-1);
}

my %cite=();
my %bib=();
my %ref=();
my %label=();
my %img=();

for my $file (@ARGV) {
    readTex($file, \%cite, \%bib, \%ref, \%label, \%img);
}
checkUsage(\%cite, \%bib, \%ref, \%label, \%img);

# subs

sub readTex($$$$$$) {
    my ($fname, $pCite, $pBib, $pRef, $pLab, $pImg) = @_;
    local (*F);
    if(!open(F, "$fname")) {
        print "readTex: cannot open $fname\n"; 
        return -1;
    }
    print "Reading $fname...\n";
    while(<F>) {
        chomp;
        next if($_!~/[a-zA-Z0-9_]/);
        my $line = $_; # we use recursion, $_ would be global...
        $line=~ s/[\r\n]//g; # get rid of \r
        $line=~ s/(^|[^\\])%.*//; # strip comments

        while($line =~ /\\cite\s*{\s*([^\s}]+)\s*}/gi) {
            my $val = $1;
            my @parts = split(/\s*,\s*/, $val);
            for my $p (@parts) { $pCite->{$p}++; }
        }
        while($line =~ /\\ref\s*{\s*([^\s}]+)\s*}/gi) {
            my $val = $1;
            my @parts = split(/\s*,\s*/, $val);
            for my $p (@parts) { $pRef->{$p}++; }
        }
        while($line =~ /\\bibitem\s*{\s*([^\s}]+)\s*}/gi) {
            $pBib->{$1}++;
        }
        while($line =~ /\\label\s*{\s*([^\s}]+)\s*}/gi) {
            $pLab->{$1}++;
        }
        while($line =~ /\\includegraphics\s*{\s*([^\s}]+)\s*}/gi) {
            $pImg->{$1}++;
        }
        
        while($line =~ /\\input\s*{\s*([^\s}]+)\s*}/gi) {
            my $fn = "$1.tex";
            readTex($fn, $pCite, $pBib, $pRef, $pLab, $pImg);
        }
    }
    close(F) || die "cannot close $fname";
    return 0;
}

sub checkUsage($$$$$) {
    my ($pCite, $pBib, $pRef, $pLab, $pImg) = @_;
    my ($citeUndef, $citeUnused, $refUndef, $refUnused) = (0,0,0,0);
    for my $key (keys(%$pCite)) { if(!$pBib->{$key}) { print "Cite undefined: $key\n"; $citeUndef++; } }
    for my $key (keys(%$pBib)) { if(!$pCite->{$key}) { print "Bibitem unused: $key\n"; $citeUnused++; } }
    for my $key (keys(%$pRef)) { if(!$pLab->{$key}) { print "Ref undefined: $key\n"; $refUndef++; } }
    for my $key (keys(%$pLab)) { if(!$pRef->{$key}) { print "Label unused: $key\n"; $refUnused++; } }
    print "Images:\n";
    for my $key (keys(%$pImg)) { print " $key\n"; }
}
