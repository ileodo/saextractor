// $Id: MixtureDistribution.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.pd;

import java.util.*;
import java.util.regex.*;

public class MixtureDistribution extends FloatDistribution {
    
    class MixtureComponent {
        FloatDistribution dist;
        double coeff;
        public MixtureComponent(FloatDistribution dist, double coeff) {
            this.dist=dist;
            this.coeff=coeff;
        }
    }
    MixtureComponent[] components;
    
    /* reads */
    public MixtureDistribution(double min, double max, String function) throws IllegalArgumentException {
        super(min, max);
        parseFunction(function);
    }
    
    @Override
    public double getCumulative(double value) {
        double val=0.0;
        if(value<min)
            return val;
        if(value>max)
            return 1.0;
        for(int i=0;i<components.length;i++) {
            val+=components[i].coeff*components[i].dist.getCumulative(value);
        }
        return val;
    }
    
    public double getCumulative(double value1, double value2) {
        if(value1>value2)
            throw new IllegalArgumentException("getCumulative: "+value1+">"+value2);
        double cum1=getCumulative(value1);
        double cum2=getCumulative(value2);
        return cum2-cum1;
    }

    @Override
    public double getProb(double value) {
        double val=0.0;
        if(value<min || value>max)
            return val;
        for(int i=0;i<components.length;i++) {
            val+=components[i].coeff*components[i].dist.getProb(value);
        }
        return val;
    }

    @Override
    public char getType() {
        return TYPE_MIXTURE;
    }

    protected static final Pattern compPat=Pattern.compile(
            "([+-]?)\\s*([0-9.]+)\\s*\\*?\\s*([a-z0-9_]+)\\s*\\(\\s*([+-]?\\s*[0-9.]+)\\s*,([+-]?\\s*[0-9.]+)\\)", 
            Pattern.CASE_INSENSITIVE);

    protected void parseFunction(String expr) throws IllegalArgumentException {
        Matcher mat=compPat.matcher(expr);
        LinkedList<MixtureComponent> lst=new LinkedList<MixtureComponent>();
        double one=0.0;
        while(mat.find()) {
            String fun=mat.group(3).toLowerCase();
            if(!fun.equals("gauss")) {
                throw new IllegalArgumentException("Only gauss functions supported in mixtures: "+expr);
            }
            double coeff=0.0;
            double mean=0.0;
            double stdev=0.0;
            try {
                coeff=Double.parseDouble(mat.group(2));
                mean=Double.parseDouble(mat.group(4));
                stdev=Double.parseDouble(mat.group(5));
            }catch(NumberFormatException ex) {
                throw new IllegalArgumentException("Cannot parse Mixture function "+expr+": "+ex);
            }
            if(mat.group(1).equals("-"))
                coeff = -coeff;
            one+=coeff;
            NormalFloatDistribution nd=new NormalFloatDistribution(-Double.MAX_VALUE, Double.MAX_VALUE, mean, stdev);
            MixtureComponent mc=new MixtureComponent(nd, coeff);
            lst.add(mc);
        }
        if(java.lang.Math.abs(1.0-one)>ERROR_DELTA) {
            throw new IllegalArgumentException("MixtureDistribution: weights sum to "+one);
        }
        components=new MixtureComponent[lst.size()];
        lst.toArray(components);
    }
}
