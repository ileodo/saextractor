#!perl -w
#$Id: $
use strict;
use warnings;

sub fisher_yates_shuffle {
    my $array = shift;
    if(@$array>0) {
        my $i;
        for ($i = @$array; --$i; ) {
            my $j = int rand ($i+1);
            next if $i == $j;
            @$array[$i,$j] = @$array[$j,$i]; # what a beautiful perl construct
        }
    }
}

my @lines=<>;
fisher_yates_shuffle(\@lines);
print @lines;
