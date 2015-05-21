// $Id: FloatDistribution.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.pd;

public class FloatDistribution implements Distribution {
    double min;
    double max;

    public char getType() { return Distribution.TYPE_MINMAX; }

    public FloatDistribution(double min, double max) {
        this.max=max;
        this.min=min;
        if(min>max)
            throw new IllegalArgumentException("FloatDistribution: max<min");
    }
    
    public double getMinValue() {
        return min;
    }

    public double getMaxValue() {
        return max;
    }
    
    public char getRangeType() {
        return RANGE_FLOAT;
    }

    public int getDimension() {
        return 1;
    }

    public double getProb(double value) {
        throw new UnsupportedOperationException("MinMax getProb not implemented");
    }
    
    public double getCumulative(double value) {
        throw new UnsupportedOperationException("MinMax getCumulative not implemented");
    }

    public double getCumulative(double value1, double value2) {
        throw new UnsupportedOperationException("MinMax getCumulative not implemented");
    }
    
    /** Computes the probability of observing a value whose probability is 
     *  strictly greater than the given value; to be overriden by more effective implementations */
    public static final int gbvpStepCnt=100;
    public double getBetterValueProb(double value) {
        double baseP=getProb(value);
        if(baseP==0.0)
            return 1.0;
        double cum=0.0;
        double step=(max-min)/(double)gbvpStepCnt;
        double preVa=min;
        double curVa=min;
        boolean prevIncluded=false;
        for(int i=0;i<=gbvpStepCnt;i++) {
            double p=getProb(curVa);
            if(p>baseP) {
                if(i>0) {
                    double area=getCumulative(preVa, curVa);
                    if(!prevIncluded)
                        area/=(double)2;
                }
                cum+=getCumulative(preVa, curVa);
                prevIncluded=true;
            }else {
                prevIncluded=false;
            }
            preVa=curVa;
            curVa+=step;
        }
        return cum;
    }
}
