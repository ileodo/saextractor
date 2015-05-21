// $Id: FmtPattern.java 1641 2008-09-12 21:53:08Z labsky $
package ex.wrap;

import ex.util.pr.PR_Evidence;

public class FmtPattern implements Comparable<FmtPattern> {
    public FmtLayout layout;
    public int posCnt;
    public int matchCnt;
    public int icCnt;
    public double precision;
    public double recall;
    public PR_Evidence evidence;

    public FmtPattern(FmtLayout layout, int posCnt, int matchCnt, int icCnt, double precision, double recall) {
        this.layout=layout;
        this.posCnt=posCnt;
        this.matchCnt=matchCnt;
        this.icCnt=icCnt;
        this.precision=precision;
        this.recall=recall;
    }

    public String toString() {
        StringBuffer buff=new StringBuffer(128);
        buff.append(layout.toString());
        buff.append(" prec="+precision+", recall="+recall+", C(POS,MATCH)="+posCnt+", C(POS)="+icCnt+", C(MATCH)="+matchCnt);
        return buff.toString();
    }

    public int compareTo(FmtPattern other) {
        if(precision>other.precision)
            return -1;
        else if(precision<other.precision)
            return 1;
        if(recall>other.recall)
            return -1;
        else if(recall<other.recall)
            return 1;
        return 0;
    }
}
