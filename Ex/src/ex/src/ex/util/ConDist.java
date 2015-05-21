// $Id: ConDist.java 1641 2008-09-12 21:53:08Z labsky $
package ex.util;

/** 
 *  Estimates a conditional probability distribution
 *  based on given examples of  
 *  [float generative_distribution_value, bool is_positive_example] 
 *  The result is a monotonous non-increasing function of
 *  generative probability value, which outputs the conditional.
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.*;

public class ConDist {
    private double[] steps; // steps in the condProb function (n), increasing
    private double[] condProb; // conditional probability values between the steps (n+1), monotonous
    private Logger log;
    private boolean increasing;

    public ConDist(boolean inc) {
        log=Logger.getLogger("ConDist");
        increasing=inc;
    }

    /**
       examples must be sorted according to their decreasing probability.
     */
    public int estimate(ScoredExample[] examples, boolean monotonous) {
        if(examples==null || examples.length==0)
            return -1;

        Arrays.sort(examples);
        if(!increasing)
            Util.reverse(examples);

        // go from highest cond probability
        double[] newSteps=new double[examples.length];
        double[] newProbs=new double[examples.length+1];

        int lastExIdx=-1;
        // 1st example is the 1st step:
        newSteps[0]=examples[0].genProb;
        int lastStepIdx=0;
        double lastStep=newSteps[0];
        while(lastExIdx+1<examples.length) {
            int blockSpan=0;
            int positives=0;
            int bestExIdx=lastExIdx+1;
            double bestProb=0.0;
            while(lastExIdx+1<examples.length) {
                ScoredExample ex=examples[lastExIdx+1];
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"prob="+ex.genProb+", class="+ex.clsId);
                if(!monotonous) {
                    /* for non-monotonous, stop when first negative example 
		       is found after at least 1 positive */
                    if(blockSpan>0) {
                        bestProb=(double)positives/blockSpan;
                        bestExIdx=lastExIdx+1;
                    }
                    if(positives>0 && ex.clsId==0) {
                        bestExIdx=lastExIdx;
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"BREAK pos="+positives);
                        break;
                    }
                }
                blockSpan++;
                lastExIdx++;
                if(ex.clsId!=0)
                    positives++;

                if(monotonous) {
                    /* for monotonous, find the subsequence that yields 
		       maximum conditional probability */
                    if(blockSpan>0) {
                        double curProb=(double)positives/blockSpan;
                        if(curProb>=bestProb) {
                            bestProb=curProb;
                            bestExIdx=lastExIdx;
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"BEST=("+bestExIdx+","+bestProb+")");
                        }
                    }
                }
            }
            lastStepIdx++;
            newSteps[lastStepIdx]=examples[bestExIdx].genProb;
            newProbs[lastStepIdx]=bestProb;
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"steps["+lastStepIdx+"]="+newSteps[lastStepIdx]+
                    ", probs["+lastStepIdx+"]="+bestProb);
            lastExIdx=bestExIdx;
        }
        newProbs[0]=newProbs[1];
        // copy arrays
        steps=new double[lastStepIdx+1];
        condProb=new double[lastStepIdx+2];
        System.arraycopy(newSteps,0,steps,0,lastStepIdx+1);
        System.arraycopy(newProbs,0,condProb,0,lastStepIdx+2);
        if(increasing) {
            Util.reverse(steps); // must be in ascending order for binarysearch in valueOf 
            Util.reverse(condProb); // keep probability indices in sync with steps
        }
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"steps="+arrayToString(steps));
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"probs="+arrayToString(condProb));

        return condProb.length;
    }

    public String arrayToString(double[] x) {
        StringBuffer s=new StringBuffer(x.length*10);
        s.append("[");
        for(int i=0;i<x.length;i++) {
            if(i!=0)
                s.append(",");
            s.append(x[i]);
        }
        s.append("]");
        return s.toString();
    }

    /* gets conditional probability for a generative proability value */
    public double valueOf(double gen, boolean interpolate) {
        int idx=Arrays.binarySearch(steps,gen);
        // gen is exactly equal to some step
        if(idx>=0)
            return condProb[idx+(increasing?1:0)];
        // extreme values
        if(idx==-1)
            return condProb[0];
        if(idx==-steps.length-1)
            return condProb[condProb.length-1];
        // interpolate two steps:
        idx*=-1; idx-=1;

        if(!interpolate)
            return condProb[idx];

        double s1=steps[idx-1];
        double s2=steps[idx];
        double span=s2-s1;
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"condProb["+(idx-1)+"]*("+(gen-s1)+"/"+span+") + condProb["+
                idx+"]*("+(s2-gen)+"/"+span+")");

        return condProb[idx+(increasing?0:-1)]*(1.0-((gen-s1)/span)) + 
        condProb[idx+(increasing?1: 0)]*(1.0-((s2-gen)/span));
    }

    void dump(int from, int to, int step, boolean interpolate) {
        while(from<=to) {
            double genProb=(double)from/100.0;
            System.out.println("cond("+genProb+")="+valueOf(genProb, interpolate));
            from+=step;
        }
    }

    public static void main(String args[]) {
        System.out.println("karel");
        ArrayList lst=new ArrayList(20);

        boolean increasing=false;
        int from, to, step;
        if(increasing) {
            lst.add(new ScoredExample(0.98, 1));
            lst.add(new ScoredExample(0.95, 1));
            lst.add(new ScoredExample(0.94, 1));
            lst.add(new ScoredExample(0.85, 0));
            lst.add(new ScoredExample(0.82, 0));
            lst.add(new ScoredExample(0.80, 1));
            lst.add(new ScoredExample(0.77, 0));
            lst.add(new ScoredExample(0.71, 1));
            lst.add(new ScoredExample(0.60, 0));
            lst.add(new ScoredExample(0.57, 0));
            lst.add(new ScoredExample(0.55, 0));
            lst.add(new ScoredExample(0.53, 0));
            from=50; to=100; step=1;
        }else {
            lst.add(new ScoredExample(0.00, 1));
            lst.add(new ScoredExample(0.01, 1));
            lst.add(new ScoredExample(0.06, 1));
            lst.add(new ScoredExample(0.10, 0));
            lst.add(new ScoredExample(0.12, 1));
            lst.add(new ScoredExample(0.30, 0));
            lst.add(new ScoredExample(0.31, 0));
            lst.add(new ScoredExample(0.50, 0));
            from=0; to=60; step=1;
        }
        ConDist cd=new ConDist(increasing);
        ScoredExample[] kopyto=new ScoredExample[0];
        System.out.println("\nNON-MONOTONOUS:");
        cd.estimate((ScoredExample[])lst.toArray(kopyto), false);
        System.out.println("No-Intp:");
        cd.dump(from,to,step,false);
        System.out.println("Intp:");
        cd.dump(from,to,step,true);

        System.out.println("\nMONOTONOUS:");
        cd.estimate((ScoredExample[])lst.toArray(kopyto), true);
        System.out.println("No-Intp:");
        cd.dump(from,to,step,false);
        System.out.println("Intp:");
        cd.dump(from,to,step,true);
    }
}
