// $Id: IntDistribution.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.pd;

public class IntDistribution implements Distribution {
    public int min;
    public int max;

    public char getType() { return Distribution.TYPE_MINMAX; }
    
    public IntDistribution(int min, int max) {
        this.max=max;
        this.min=min;
        if(min>max)
            throw new IllegalArgumentException("IntDistribution: max<min");
    }

    public double getMinValue() {
        return (double) min;
    }

    public double getMaxValue() {
        return (double) max;
    }

    public int getMinIntValue() {
        return min;
    }

    public int getMaxIntValue() {
        return max;
    }
    
    public char getRangeType() {
        return RANGE_INT;
    }

    public int getDimension() {
        return 1;
    }

    public double getProb(int value) {
        throw new UnsupportedOperationException("MinMax getProb not implemented");
    }
    
    public double getCumulative(int value) {
        throw new UnsupportedOperationException("MinMax getCumulative not implemented");
    }
    
    /** Computes the probability of observing a value whose probability is 
     *  strictly greater than the given value. */
    public double getBetterValueProb(int value) {
        double baseP=getProb(value);
        if(baseP==0.0)
            return 1.0;
        double cum=0.0;
        for(int i=min;i<=max;i++) {
            double p=getProb(i);
            if(p>baseP)
                cum+=p;
        }
        return cum;
    }
}
