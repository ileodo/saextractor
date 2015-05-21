// $Id: PR_Class.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util.pr;

/** 
 * Represents a binary class of some object,
 * e.g. a phrase contains a value for attribute 'monitor_name' or not.
 * Class is associated with its prior probability and 
 * with a fixed number of evidences that indicate its presence or abssence.
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.Logger;

public class PR_Class {
    public Object cls;
    public double prior; // prior probability of class in data
    public PR_Evidence[] evs; // evidences indicating presence/absence of this class
    public List<PR_EvidenceGroup> evidenceGroups;
    public double[] negRecalls; // P(Evidence|non A) precomputed for performance from P(Ev|A), P(A|Ev), P(A)

    public PR_Class(Object cls, double prior, PR_Evidence[] evs) {
        this.cls=cls;
        this.prior=prior;
        this.evs=evs;
        this.evidenceGroups=null;
        if(evs!=null)
            precomputeNegRecalls();
    }

    public void precomputeNegRecalls() {
        negRecalls=new double[evs.length];
        for(int j=0;j<evs.length;j++) {
            PR_Evidence ev=evs[j];
            negRecalls[j]=ev.negativeRecall(prior);
        }
    }
    
    public void setEvidence(PR_Evidence[] evs) {
        this.evs=evs;
        precomputeNegRecalls();
    }
    
    public int findEvidence(PR_Evidence ev) {
        for(int i=0;i<evs.length;i++)
            if(evs[i]==ev)
                return i;
        return -1;
    }
    
    public void addEvidence(PR_Evidence ev) {
        PR_Evidence[] evs2=new PR_Evidence[evs.length+1];
        System.arraycopy(evs, 0, evs2, 0, evs.length);
        evs=evs2;
        evs[evs.length-1]=ev;
        //ev.idx=evs.length-1;
        if(negRecalls!=null)
            precomputeNegRecalls();
    }

    public void removeLastEvidence(int cnt) {
        int idx=evs.length-cnt;
        if(idx<0)
            idx=0;
        PR_Evidence[] evs2=new PR_Evidence[idx];
        System.arraycopy(evs, 0, evs2, 0, idx);
        evs=evs2;
        if(negRecalls!=null)
            precomputeNegRecalls();
    }
    
    /** Defines a new OR group of evidence. */
    public void addEvidenceGroup(PR_EvidenceGroup group) {
        if(evidenceGroups==null) {
            evidenceGroups=new LinkedList<PR_EvidenceGroup>();
        }
        evidenceGroups.add(group);
    }
    
    /** Sorts evidence in each OR group by precision. */
    public void prepare() {
        if(evidenceGroups!=null) {
            for(PR_EvidenceGroup grp: evidenceGroups) {
                Collections.sort(grp.evList, new Comparator<PR_Evidence>() {
                    public int compare(PR_Evidence e1, PR_Evidence e2) {
                        if(e1.prec>e2.prec)
                            return -1;
                        else if(e1.prec<e2.prec)
                            return 1;
                        return 0;
                    }
                } );
            }
        }
    }
    
    public void dumpGenerativeModel() {
        Logger log=Logger.getLogger();
        log.LG(Logger.INF,"P(A)="+fmtNum(prior));
        for(int j=0;j<evs.length;j++) {
            log.LG(Logger.INF,"P(e"+(j+1)+"|A)="+fmtNum(evs[j].recall));
        }	
        log.LG(Logger.INF,"P(non A)="+fmtNum(1-prior));
        for(int j=0;j<evs.length;j++) {
            log.LG(Logger.INF,"P(e"+(j+1)+"|non A)="+fmtNum(negRecalls[j]));
        }	
    }

    public void setPrior(double prior) {
        this.prior=prior;
    }

    public String toString() {
        String name=cls.toString();
        StringBuffer buff=new StringBuffer(name.length()+64*evs.length);
        buff.append("P("+name+")="+fmtNum(prior)+"\n");
        for(int i=0;i<evs.length;i++) {
            PR_Evidence ev=evs[i];
            buff.append("P(A|e"+(i+1)+")="+fmtNum(ev.prec)+
                    " P(e"+(i+1)+"|A)="+fmtNum(ev.recall)+
                    " "+ev.name+"\n");
        }
        return buff.toString();
    }

    public static java.text.NumberFormat nf=java.text.NumberFormat.getInstance(Locale.ENGLISH);
    static {
        nf.setMaximumFractionDigits(9);
        nf.setGroupingUsed(false);
    }
    public static String fmtNum(double x) {
        return nf.format(x).trim();
    }
}
