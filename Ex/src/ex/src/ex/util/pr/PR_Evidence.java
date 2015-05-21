// $Id: PR_Evidence.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util.pr;

/** 
 *  Represents an evidence that indicates whether
 *  some object should be classified as class A or not.
 *  Example evidence indicating that some phrase should be classified 
 *  as a monitor name could be a content pattern 
 *  "LCD <MANUFACTURER> <ALPHANUM>"
 *  with precision 0.95 and recall 0.30 (PR)
 *  @author Martin Labsky labsky@vse.cz
 */

public class PR_Evidence {
    public String name;
    public double prec;   // P(A|Evidence)
    public double recall; // P(Evidence|A)
    public byte defaultValue;
    /* idx where this is stored in the array of evidence for the PR_Class in question;
       same idx used in PR_Example's array of evidence values */
    public int idx;

    /* possibly make a common ancestor and derive another subclass like this with 2 ints, 
       or give PR_Evidence a flag and use the doubles:
       int cntEvA;
       int cntEvNonA;
       // int cntNonEvA=A.cnt-cntEvA;
       // int cntNonEvNonA=|train phrases|-(cntEvA+cntEvNonA+cntNonEvA);
     */

    public static final String strVal[]={"OFF","ON","NA"};

    public PR_Evidence(String n, double p, double r, byte defaultValue, int idx) {
        name=n;
        prec=p;
        recall=r;
        this.defaultValue=defaultValue;
        this.idx=idx;
    }

    public PR_Evidence(PR_Evidence other) {
        this.name=other.name;
        this.prec=other.prec;
        this.recall=other.recall;
        this.defaultValue=other.defaultValue;
        this.idx=other.idx;
    }
    
    // P(Evidence|non A) = P(Evidence|A) P(A) P(non A|Evidence) / P(non A) P(A|Evidence)
    public double negativeRecall(double classPrior) {
        return (classPrior * (1.0-prec) * recall) / ((1-classPrior) * prec);
    }
    
    public static String stringValue(byte val) {
        if(val<0||val>=strVal.length)
            val=(byte) (strVal.length-1);
        return strVal[val];
    }
    
    public String toString() {
        return name;
    }
}
