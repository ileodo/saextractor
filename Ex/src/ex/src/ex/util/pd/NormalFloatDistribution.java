// $Id: NormalFloatDistribution.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.pd;

import uep.util.Logger;
import org.apache.commons.math.distribution.NormalDistribution;
import org.apache.commons.math.distribution.NormalDistributionImpl;
import org.apache.commons.math.MathException;

public class NormalFloatDistribution extends FloatDistribution {
    double mean;
    double stdev;
    NormalDistribution ndist;
    // gaussian constant terms
    private double k1;
    private double k2;
    
    public char getType() { return TYPE_NORMAL; }

    public NormalFloatDistribution(double min, double max, double mean, double stdev) {
        super(min, max);
        setParams(mean, stdev);
    }

    public NormalFloatDistribution(double min, double max, String params) {
        super(min, max);
        setParams(params);
    }
    
    public void setParams(double mean, double stdev) {
        this.mean=mean;
        this.stdev=stdev;
        init();
    }
    
    public void setParams(String params) {
        String[] ps=params.trim().split("[\\s,;]+");
        if(ps.length!=2)
            throw new IllegalArgumentException("Error parsing normal distribution parameters: "+params+"; expected: mean, stdev");
        try {
            mean=Double.parseDouble(ps[0]);
            stdev=Double.parseDouble(ps[1]);
        }catch(NumberFormatException ex) {
            throw new IllegalArgumentException("Error parsing normal distribution parameters: "+params+"; expected 2 numbers: mean, stdev");
        }
        init();
    }

    public void init() {
        k1 = (double) (1.0 / Math.sqrt(2 * Math.PI * stdev));
        k2 = - 1 / (2 * stdev * stdev);
        ndist = new NormalDistributionImpl(mean, stdev);
    }

    public double getCumulative(double value) {
        double cum=0.0;
        if(value<min || value>max)
            return cum;
        try {
            cum=ndist.cumulativeProbability(value);
        }catch(MathException ex) {
            Logger.LOG(Logger.ERR, "Error evaluating NormalDistribution.getCumulative("+value+")");
        }
        return cum;
    }

    public double getCumulative(double value1, double value2) {
        double cum=0.0;
        if(value1>value2)
            throw new IllegalArgumentException("getCumulative: "+value1+">"+value2);
        if(value1<min)
            value1=min;
        if(value2>max)
            value2=max;
        try {
            cum=ndist.cumulativeProbability(value1, value2);
        }catch(MathException ex) {
            Logger.LOG(Logger.ERR, "Error evaluating NormalDistribution.getCumulative("+value1+","+value2+")");
        }
        return cum;
    }

    public double getProb(double value) {
        if(value<min || value>max)
            return 0.0;
        return (double) (k1 * Math.exp((value - mean) * (value - mean) * k2));
    }
    
    /** Computes the probability of observing a value whose probability is 
     *  strictly greater than the given value. */
    public double getBetterValueProb(double value) {
        if(value<min || value>max) {
            return 1.0;
        }
        if(value==mean)
            return 0.0;
        double low=(value>mean)? (mean-(value-mean)): value;
        double high=(value<mean)? (mean+(mean-value)): value;
        double cum=0.0;
        try {
            cum=ndist.cumulativeProbability(low,high);
        }catch(MathException ex) {
            Logger.LOG(Logger.ERR, "Error evaluating NormalDistribution.getCumulative("+value+")");
        }
        return cum;
    }
}
