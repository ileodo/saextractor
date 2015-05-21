// $Id: TableDistribution.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.pd;

public class TableDistribution extends IntDistribution {
    double[] probs;
    
    public char getType() { return TYPE_TABLE; }
    
    public TableDistribution(int min, int max, double[] probs) {
        super(min, max);
        setProbs(probs);
    }
    
    public TableDistribution(int min, int max, String tableExpr) {
        super(min, max);
        setProbs(tableExpr);
    }

    protected void setProbs(double[] newProbs) {
        if(newProbs==null) {
            probs=new double[max-min+1];
            double uni=1.0/probs.length;
            for(int i=0;i<probs.length;i++)
                probs[i]=uni;
        }else {
            if(newProbs.length!=max-min+1) {
                throw new IllegalArgumentException("TableDistribution: table only has "+newProbs.length+" items; expected max-min+1="+(max-min+1));
            }
            this.probs=newProbs;
            double one=getCumulative(max);
            if(java.lang.Math.abs(1.0-one)>ERROR_DELTA) {
                throw new IllegalArgumentException("TableDistribution: given table sums to "+one);
            }
        }
    }
    
    protected void setProbs(String tableExpr) {
        String[] ps=tableExpr.split("\\s*[,;]\\s*");
        if(ps.length!=max-min+1) {
            throw new IllegalArgumentException("TableDistribution: table only has "+ps.length+" items; expected max-min+1="+(max-min+1));            
        }
        probs=new double[max-min+1];
        for(int i=0;i<ps.length;i++) {
            try {
                probs[i]=Double.parseDouble(ps[i]);
            }catch(NumberFormatException ex) {
                throw new IllegalArgumentException("TableDistribution: cannot read "+i+"-th item of "+tableExpr+": "+ex);
            }
        }
    }
    
    public double getCumulative(int value) {
        double cum=0.0;
        for(int i=0;i<=value;i++)
            cum+=this.probs[i];
        return cum;
    }

    public double getProb(int value) {
        if(value<min || value>max)
            return 0.0;
        return probs[value];
    }
}
