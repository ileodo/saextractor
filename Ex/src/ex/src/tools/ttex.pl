#!perl -w
use strict;
use warnings;

sub fnum($) {
    my $n=shift @_;
    my $s=defined($n)? sprintf("%.1f", $n): '-';
}

sub fname($) {
    my $name = shift @_;
    my $line = sprintf('%s', $name);
    my $len=length($name);
    for(my $i=$len;$i<13;$i++) {
        $line.=' ';
    }
    return $line;
}

sub mycmp($$) {
    my ($a,$b) = @_;
    if(($a cmp 'avg')==0) {
        return 1;
    }
    if(($b cmp 'avg')==0) {
        return -1;
    }
    return ($a cmp $b);
}

sub saveNumber($$$) {
    my ($pMap,$key,$str)=@_;
    my @parts = split(/\s*\/\s*/, $str);
    my $n=scalar(@parts);
    if($n==1) {
        $pMap->{$key} = $str;
    }elsif($n==2) {
        $pMap->{$key} = $parts[0];
        $pMap->{$key.'_micro'} = $parts[1];
    }else {
        die "Illegal number: ".$str;
    }
}

my $tableId=-1;
my $inTable=0;
my @tables=();
my $lno=0;
my $foldCnt=0;
my $foldsToIgnore=0;
#               P       R       F       GOLD    AUTO    AMAT    GMAT
# name-strict	90.82	83.19	86.84	1511	1384	1257	1257
# name-loose 	95.23	85.35	90.02	1511	1384	61.04	32.64

# assemble counts
for(<>) {
    $lno++;
    chomp;
    $_=~s/(^\s+|\s+$)//g;
    if($_ =~ /Creating (\d+) folds/) {
        $foldCnt=$1*1;
        $foldsToIgnore = $foldCnt-1;
        print STDERR "Ignoring first $foldsToIgnore cumulative results of $foldCnt-fold cross-validation\n";
    }
    my @cells=split(/\s+/, $_);
    my $cnt=scalar(@cells);
    if($cnt==7 && $cells[0] eq 'P') {
        if($foldsToIgnore>0) {
            --$foldsToIgnore;
            if($foldsToIgnore==0) {
                $foldCnt=0;
            }
        }else {
            $inTable=1;
            $tableId++;
            my $pTable={'strict'=>{},'loose'=>{}};
            $tables[$tableId]=$pTable;
            #print STDERR "$tableId: $_\n";
        }
    }
    elsif($inTable && $tableId>=0 && $cnt==8) {
        my $key=$cells[0];
        if($key eq 'avg-loose') {
            $inTable=0;
        }
        my $att;
        my $mode;
        if($key=~/^(.*)-(strict|loose)$/) {
            $att=$1;
            $mode=$2;
        }else {
            print STDERR "Error reading line $lno: invalid key >$key<\n";
        }
        my $pRec = {};
        saveNumber($pRec, 'P', $cells[1]);
        saveNumber($pRec, 'R', $cells[2]);
        saveNumber($pRec, 'F', $cells[3]);
        $pRec->{'G'}=$cells[4];
        my $pTable = $tables[$tableId];
        $pTable->{$mode}->{$att} = $pRec;
        #print STDERR "$tableId";
    }
    elsif($tableId>=0 && $_=~/^Instances\(avg\).*villain prec=\d+\/\d+=([\d.]+), villain recall=\d+\/\d+=([\d.]+), F=([\d.]+)/i) {
        my $pRec = {};
        $pRec->{'P'}=$1*100;
        $pRec->{'R'}=$2*100;
        $pRec->{'F'}=$3*100;
        my $pTable = $tables[$tableId];
        $pTable->{'loose'}->{'villain'} = $pRec;
    }
}

# print them in tex tables
my $pMusterTable = $tables[0];
my @atts = sort mycmp keys (%{$pMusterTable->{'strict'}});
# print ">".(join ",", @atts)."\n";
# Speaker     &	69.85	&	66.45	&	68.10	&	75.37	&	72.92	&	74.13	&	+6.03	\\
# -loose      &	76.22	&	72.68	&	74.41	&	81.39	&	78.65	&	80.00	&	+5.59	\\

for my $key (@atts) {
    for my $mode ('strict','loose') {
        next if(($key cmp 'bg')==0 || ($key cmp 'villain')==0);
        my $key2 = uc(substr($key,0,1)).substr($key,1);
        my $h = ($mode eq 'strict')? $key2: '-loose';
        my $line = fname($h);
        # concatenate table rows
        for(my $t=0;$t<=$tableId;$t++) {
            my $pTable = $tables[$t];
            my $pRec=$pTable->{$mode}->{$key};
            $line.= '& '.fnum($pRec->{'P'}).' & '.fnum($pRec->{'R'}).' & '.fnum($pRec->{'F'});
            # diffs
            if($t>0) {
                my $pDiffs = {};
                my $prevRec = $tables[$t-1]->{$mode}->{$key};
                $pDiffs->{'P'} = $pRec->{'P'} - $prevRec->{'P'};
                $pDiffs->{'R'} = $pRec->{'R'} - $prevRec->{'R'};
                $pDiffs->{'F'} = $pRec->{'F'} - $prevRec->{'F'};
                my $fd = $pDiffs->{'F'};
                $line.= ' & '.(($fd>0)?'+':'').fnum($fd);
                if($pRec->{'G'} != $prevRec->{'G'}) {
                    print STDERR "Gold count mismatch for $key, table ".($t+1).": $pRec->{'G'}, prev table $prevRec->{'G'}\n";
                }
            }
        }
        # print single gold count for strict & loose row
        if($mode eq 'strict') {
            $line.= " & \\multirow{2}{*}{".$tables[0]->{$mode}->{$key}->{'G'}."}";
        }else {
            $line.= " & "
        }
        $line.= " \\\\\n";
        if($mode eq 'loose') {
            $line.= "\\hline\n";
        }
        print $line;
    }
}

# concatenate table rows with Villain score
my $line='';
$line.= fname('Villain');
for(my $t=0;$t<=$tableId;$t++) {
    my $pRec=$tables[$t]->{'loose'}->{'villain'};
    if(!$pRec) {
        last;
    }
    $line.= '& '.fnum($pRec->{'P'}).' & '.fnum($pRec->{'R'}).' & '.fnum($pRec->{'F'});
    # diffs
    if($t>0) {
        my $pDiffs = {};
        my $prevRec = $tables[$t-1]->{'loose'}->{'villain'};
        $pDiffs->{'P'} = $pRec->{'P'} - $prevRec->{'P'};
        $pDiffs->{'R'} = $pRec->{'R'} - $prevRec->{'R'};
        $pDiffs->{'F'} = $pRec->{'F'} - $prevRec->{'F'};
        my $fd = $pDiffs->{'F'};
        $line.= ' & '.(($fd>0)?'+':'').fnum($fd);
    }
}
if($line=~/\d/) {
    $line.= " \\\\\n";
    print $line;
}
