#!perl 
use strict;
use warnings;

#my @langs = ('en','sp','cz','de','fi','gr');
my @langs = ('fi');
my $suffix = '_elg';

for my $lang (@langs) {
  my $dir = ($lang eq 'en')? 'contact_src_docs': "con${lang}_src_docs";
  print `runtask /Collections/$dir/elex_20split.task`;
  print `cp iet.log _elex/iet_${lang}${suffix}.log`;
}
