// $Id: ScoredExample.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util;

public class ScoredExample implements Comparable {
    public double genProb;
    public int clsId;
    public ScoredExample(double gp, int c) {
        genProb=gp;
        clsId=c;
    }
    public int compareTo(Object o) { // high probability examples first
        ScoredExample ge=(ScoredExample)o;
        if(genProb<ge.genProb)
            return 1;
        else if(genProb>ge.genProb)
            return -1;
        return 0;
    }
}
