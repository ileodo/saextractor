#!perl -w
use strict;
use warnings;

#my @weights=(0.1, 0.15, 0.5, 0.25);
my @weights=(0.25, 0.25, 0.25, 0.25);
my $p=0;

# this can be binary features, 
# or pattern conditional probabilities given by user P(attribute_a|pattern_i)
# my @features=(0.7, 0.8, 0.8, 1.0);
my @features=(0.1, 0.2, 0.2, 0.0);

# log-linear probabilistic model
# for combining weighted binary features (possibly dependent) into a conditional distribution,
# also accepts single-evidence conditional probs instead of binary features 
sub probF($$) {
    my($pWeights, $pFeatures)=@_;
    my $x=0;
    my $cnt=scalar(@$pWeights);
    for(my $i=0;$i<$cnt;$i++) {
	$x+=$pWeights->[$i]*$pFeatures->[$i];
    }
    return exp($x);
}

# this is not the normalisation factor used e.g. for maxent, since that
# sums over all possible classes (e.g. POS tags), here we only add
# maximal feature values (this is ok if they are cond probs)
sub normF($) {
    my($pWeights)=@_;
    my $x=0;
    my $cnt=scalar(@$pWeights);
    for(my $i=0;$i<$cnt;$i++) {
	$x+=$pWeights->[$i];
    }
    return exp($x);
}

# more similar to maxent normalisation factor
sub normF2($$) {
    my($pWeights, $pFeatures)=@_;
    my $x=0;
    my $cnt=scalar(@$pWeights);
    for(my $i=0;$i<$cnt;$i++) {
	# is predicted class
	$x+=$pWeights->[$i]*$pFeatures->[$i];
	# is not predicted class
	$x+=$pWeights->[$i]*(1-$pFeatures->[$i]);
    }
    return exp($x);
}

sub prob($$) {
    my($pWeights, $pFeatures)=@_;
    return probF($pWeights,$pFeatures)/normF($pWeights);
}

sub prob2($$) {
    my($pWeights, $pFeatures)=@_;
    return probF($pWeights,$pFeatures)/normF2($pWeights,$pFeatures);
}

# linear 'old lady says' interpolated probabilistic model 
# for combining conditional probs of multiple (possibly dependent) evidences
sub myprob($$) {
    my($pWeights, $pFeatures)=@_;
    my $x=0;
    my $cnt=scalar(@$pWeights);
    for(my $i=0;$i<$cnt;$i++) {
	$x+=$pWeights->[$i]*$pFeatures->[$i];
    }
    return $x;
}

# generative 'naive bayes' model
# for combining single-evidence generative probabilities, 
# works under assumption of indpendence among evidences, which is never true
# generative probs can be weighted as well exponentially
# equivalent to: print (((.7**.1)*(.8**.15)*(.8**.5)*(1.0**.25))**(1/4));
sub genprob($$) {
    my($pWeights, $pFeatures)=@_;
    my $x=0;
    my $cnt=scalar(@$pWeights);
    for(my $i=0;$i<$cnt;$i++) { # work in log domain
	return 0 if($pFeatures->[$i]<=0);
	$x+=$pWeights->[$i]*log($pFeatures->[$i]); # i.e. PI_i(pFeatures[i]^pWeights[i])
    }
    $x=$x/$cnt; # normalize i.e. cnt-th root
    return exp($x); # back to prob domain
}

print "prob=".prob(\@weights, \@features)."\n";
print "prob2=".prob(\@weights, \@features)."\n";
print "myprob=".myprob(\@weights, \@features)."\n";
print "genprob=".genprob(\@weights, \@features)."\n";
