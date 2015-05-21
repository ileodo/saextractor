// $Id: DistributionFactory.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.pd;

import java.util.HashMap;

public class DistributionFactory {
    
    private static HashMap<String,Character> s2t;
    static {
        s2t=new HashMap<String,Character>();
        s2t.put("minmax", new Character(Distribution.TYPE_MINMAX));
        s2t.put("table", new Character(Distribution.TYPE_TABLE));
        s2t.put("normal", new Character(Distribution.TYPE_NORMAL));
        s2t.put("mixture", new Character(Distribution.TYPE_MIXTURE));
    }
    
    public static char string2Type(String name) {
        char type=Distribution.TYPE_UNKNOWN;
        Character c=s2t.get(name.toLowerCase());
        if(c!=null)
            type=c.charValue();
        return type;
    }
    
    public static Distribution createDistribution(char type, char range, int min, int max, Object param) {
        Distribution d=null;
        switch(type) {
        case Distribution.TYPE_MINMAX:
            d=new IntDistribution(min, max);
            break;
        case Distribution.TYPE_TABLE:
            d=new TableDistribution(min, max, (double[]) param);
            break;
        }
        return d;
    }
    
    public static Distribution createDistribution(char type, char range, double min, double max, Object param) {
        Distribution d=null;
        switch(type) {
        case Distribution.TYPE_MINMAX:
            if(range==Distribution.RANGE_FLOAT)
                d=new FloatDistribution(min, max);
            else
                d=new IntDistribution((int)min, (int)max);
            break;
        case Distribution.TYPE_TABLE:
            d=new TableDistribution((int)min, (int)max, (double[]) param);
            break;
        case Distribution.TYPE_NORMAL:
            double[] mean_stdev=(param!=null)? (double[]) param: new double[] {0.0, 1.0};
            d=new NormalFloatDistribution(min, max, mean_stdev[0], mean_stdev[1]);
            break;
        case Distribution.TYPE_MIXTURE:
            String func=(param!=null)? ((String) param): "1.0 * gauss(0.0, 1.0)"; 
            d=new MixtureDistribution(min, max, func);
            break;
        }
        return d;
    }
}
