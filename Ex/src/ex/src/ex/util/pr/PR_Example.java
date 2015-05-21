// $Id: PR_Example.java 1865 2009-03-24 00:07:26Z labsky $
package ex.util.pr;

/** 
 * Example is in the form [A=1|0, E_1=1|0,...,E_n=1|0]
 * where n=|Phi_A|, the set of all evidence known for attribute A.
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

import uep.util.Logger;

public class PR_Example implements Comparable<PR_Example> {
    public Object obj;   // classified object
    public PR_Class cls; // class in question
    public boolean cVal; // obj is of class cls or not
    public byte[] eVals;  // values of evidences indicating cls
    public List<PR_Evidence> custEvs; // evidence extending that of cls.evs
    
    // used to store count of identical examples generated so far (only when generating)
    public int count;
    public double classProb; // used to cache result of condProb()
    private static Random rnd=new Random();

    public PR_Example(Object o, PR_Class c, boolean cV, byte[] eVs) {
        obj=o;
        cls=c;
        cVal=cV;
        eVals=eVs;
        count=1;
        classProb=-1.0;
        if(cls!=null) {
            if(eVals==null) {
                eVals=new byte[cls.evs.length]; // 0s initially
                for(int i=0;i<eVals.length;i++)
                    eVals[i]=cls.evs[i].defaultValue;
            }
//            if(cls.prior!=-1.0) {
//                condProb(); // TODO: typically called later after updating evidence; could be removed
//            }
        }
    }
    
    /** Frees all member references. */
    public void clear() {
        obj=null;
        cls=null;
        eVals=null;
        if(custEvs!=null) {
            custEvs.clear();
            custEvs=null;
        }
    }

    /** Computes P(Class|Observed and unobserved evidence values) */
    public double condProb() {
        int evCnt=0;
        double evidenceComponent=1.0;
        classProb=-1;
        if(cls.prior<=0||cls.prior>=1) {
            Logger.LOGERR("Class "+cls.toString()+" has illegal prior prob!");
        }
        boolean revert=false;
        boolean kuk=false;
        if(cls.evidenceGroups!=null) {
            for(PR_EvidenceGroup grp: cls.evidenceGroups) {
                if(grp.type==PR_EvidenceGroup.GRP_OR) {
                    // evidence must be sorted by precision within group, highest precision first
                    int bestOnIdx=-1;
                    for(PR_Evidence ev: grp.evList) {
                        if(eVals[ev.idx]==1) {
                            bestOnIdx=ev.idx;
                            break;
                        }
                    }
                    if(bestOnIdx!=-1) {
                        for(PR_Evidence ev: grp.evList) {
                            eVals[ev.idx]=-1;
                        }
                        eVals[bestOnIdx]=1;
                    }
                }else if(grp.type==PR_EvidenceGroup.GRP_AND) {
                    kuk=true;
                    int onCnt=0;
                    for(PR_Evidence ev: grp.evList) {
                        if(eVals[ev.idx]==1) {
                            onCnt++;
                            eVals[ev.idx]=-100; // will revert to 1 below - huge hack
                            revert=true;
                        }
                    }
                    if(onCnt==grp.evList.size()) {
                        eVals[grp.idx]=1;
                    }else {
                        eVals[grp.idx]=0;
                    }
                }else {
                    throw new IllegalArgumentException("Unknown evidence group type: "+grp.type);
                }
            }
        }
        // int len=getEvidenceSize();
evLoop: for(int i=0;i<eVals.length;i++) {
            PR_Evidence ev = getEvidence(i); // cls.evs[i];
            switch(eVals[i]) {
            case 1:
                if(ev.prec==0) {
                    classProb=0;
                    break evLoop;
                }else if(ev.prec<0.0||ev.prec>1.0) {
                    if(Logger.IFLOG(Logger.TRC))Logger.LOG(Logger.TRC,"Example "+obj+": evidence "+ev.toString()+" (on) has illegal precision "+ev.prec+"; ignoring evidence");
                }else {
                    evCnt++;
                    evidenceComponent*= (1.0-ev.prec)/ev.prec;
                }
                break;
            case 0: {
                if(ev.recall<0.0||ev.recall>1.0) {
                    if(Logger.IFLOG(Logger.TRC))Logger.LOG(Logger.TRC,"Example "+obj+": evidence "+ev.toString()+" (off) has illegal recall "+ev.recall+"; ignoring evidence");                    
                //}else if(ev.prec<0.0||ev.prec>1.0) {
                //    Logger.LOGERR("Example "+obj+": evidence "+ev.toString()+" (off) has illegal precision "+ev.prec);
                }else {
                    double prec=ev.prec;
                    if(prec<=0.0||prec>1.0) {
                        prec=1.0;
                        if(Logger.IFLOG(Logger.TRC))Logger.LOG(Logger.TRC,"Example "+obj+": evidence "+ev.toString()+" (off) setting precision = "+prec);
                    }
                    // check that the precision&recall of this evidence are consistent with the model, i.e.
                    // P(E) + P(C) - P(E,C) <= 1
                    double minPrec=(ev.recall*cls.prior)/(1-cls.prior*(1-ev.recall));
                    if(prec<minPrec) {
                        if(Logger.IFLOG(Logger.INF))Logger.LOG(Logger.INF,"Example "+obj+": evidence "+ev.toString()+" (off): fixing invalid precision="+prec+" to "+minPrec);
                        prec=minPrec;
                    }
                    evCnt++;
                    double non_e= 1.0 - ((ev.recall*cls.prior)/prec);
                    double a_given_non_e= ((1-ev.recall)*cls.prior)/non_e;
                    evidenceComponent*= (1-a_given_non_e)/a_given_non_e;
                }
                break;
            }
            case -1:
            case -100:
                // ignore unspecified values
                break;
            default:
                Logger.LOGERR("Evidence "+ev.toString()+" value=N/A");
            }
        }
        // compute classProb from accumulated components
        if(classProb==-1) {
            double priorLikelihoodComponent=Math.pow((cls.prior/(1-cls.prior)), evCnt-1);
            //double priorLikelihoodComponent=Math.pow(cls.prior, eVals.length-1) * (1.0-cls.prior);
            classProb = 1.0 / (1.0 + priorLikelihoodComponent * evidenceComponent);
        }
        // revert back abused values
        if(revert) {
            for(PR_EvidenceGroup grp: cls.evidenceGroups) {
                if(grp.type==PR_EvidenceGroup.GRP_AND) {
                    for(PR_Evidence ev: grp.evList) {
                        if(eVals[ev.idx]==-100) {
                            eVals[ev.idx]=1; // reverting back to 1
                        }
                    }
                }
            }
            //Logger.LOGERR("GRP was on for CP of "+this);
        }else if(kuk) {
            //Logger.LOGERR("GRP was OFF for CP of "+this);
        }
        
        if(Double.isNaN(classProb)) {
            classProb = 0;
        }
        
        return classProb;
    }

    /** Gets evidence by index. */
    public PR_Evidence getEvidence(int idx) {
        PR_Evidence ev=null;
        if(idx>=0 && idx<cls.evs.length) {
            ev=cls.evs[idx];
        }else {
            idx-=cls.evs.length;
            if(idx>=0 && custEvs!=null && idx<custEvs.size())
                ev=custEvs.get(idx);
        }
        return ev;
    }

    /** Returns the number of the class's and this example's evidence. */
    public int getEvidenceSize() {
        return cls.evs.length+((custEvs!=null)? custEvs.size(): 0);
    }
    
    /** Adds new evidence that is specific to this example. */
    public void addCustomEvidence(PR_Evidence ev, byte val) {
        if(custEvs==null) {
            custEvs=new ArrayList<PR_Evidence>(4);
        }
        custEvs.add(ev);
        byte[] newVals=new byte[cls.evs.length+custEvs.size()];
        if(eVals!=null) {
            System.arraycopy(eVals, 0, newVals, 0, eVals.length);
        }
        eVals=newVals;
        eVals[eVals.length-1]=val;
        ev.idx=eVals.length-1;
    }

    /** To be called when the range of evidence of the class changes. */
    public void updateEvidenceList() {
        if(eVals.length==getEvidenceSize()) {
            return;
        }
        byte[] eVals2=new byte[getEvidenceSize()];
        int toCopy=Math.min(eVals2.length, eVals.length);
        System.arraycopy(eVals, 0, eVals2, 0, toCopy);
        if(eVals2.length>toCopy) {
            Arrays.fill(eVals2, toCopy, eVals2.length, (byte)-1);
        }
        eVals=eVals2;
        condProb();
    }
    
    /** Sets the evidence's value to val; 
     *  to be used when the evidence's index is not known or does not exist. */
    public boolean setEvidenceValue(PR_Evidence ev, byte val) {
        int idx=cls.findEvidence(ev);
        boolean rc=false;
        if(idx!=-1) {
            eVals[idx]=val;
            rc=true;
        }else {
            if(custEvs!=null) {
                int len=custEvs.size();
                for(int i=0;i<len;i++) {
                    if(ev==custEvs.get(i)) {
                        eVals[cls.evs.length+i]=val;
                        rc=true;
                        break;
                    }
                }
            }
        }
        if(!rc){
            Logger.LOGERR("Cannot find evidence "+ev+" in class "+cls);
        }
        return rc;
    }
    
    /** In ancestor PR_Example, sets the evidence's value to val */
    public boolean setEvidenceValue(int evidenceIdx, byte val) {
        if(evidenceIdx<0||evidenceIdx>=eVals.length) {
            Logger.LOGERR("Evidence index "+evidenceIdx+" out of range="+eVals.length);
            return false;
        }
        eVals[evidenceIdx]=val;
        return true;
    }

    public void copyEvidenceValuesFrom(PR_Example other) {
        for(int i=0;i<other.eVals.length;i++) {
            eVals[i]=other.eVals[i];
        }
        if(other.custEvs!=null) {
            custEvs=new ArrayList<PR_Evidence>(other.custEvs);
        }
    }

    public String toValues() {
        StringBuffer buff=new StringBuffer(20);
        buff.append(cVal?1:0);
        buff.append(":");
        int len=getEvidenceSize();
        for(int i=0;i<len;i++) {
            if(i>1)
                buff.append(":");
            PR_Evidence ev=getEvidence(i);
            buff.append(ev);
        }
        return buff.toString();
    }

    /*
    public String toString2() {
	String objStr=obj.toString();
	StringBuffer buff=new StringBuffer(objStr.length()+64*eVals.length);
	buff.append(objStr+" "+(cVal?1:0)+" ["+count+"]\n");
	// buff.append("P("+cls.name+")="+cls.prior+"\n");
	for(int i=0;i<eVals.length;i++) {
	    PR_Evidence ev=cls.evs[i];
	    if(i>0)
		buff.append(" ");
	    buff.append(// "P(A|e"+(i+1)+")="+ev.prec+" "+
			// "P(e"+(i+1)+"|A)="+ev.recall+" "+
			ev.name+"="+PR_Evidence.strVal[eVals[i]]);
	}
	return buff.toString();
    }
     */

    public String toString() {
        String name=cls.cls.toString();
        StringBuffer buff=new StringBuffer(name.length()+64*cls.evs.length);
        buff.append("P("+name+")="+PR_Class.fmtNum(cls.prior)+"\n");
        int len=getEvidenceSize();
        for(int i=0;i<len;i++) {
            PR_Evidence ev=getEvidence(i);
            buff.append(PR_Evidence.stringValue(eVals[i])+
                    " P(A|e"+(i+1)+")="+PR_Class.fmtNum(ev.prec)+
                    " P(e"+(i+1)+"|A)="+PR_Class.fmtNum(ev.recall)+
                    " "+ev.name+"\n");
        }
        return buff.toString();
    }

    public static PR_Example generate(PR_Class cls, Object obj) {
        // throw dice if this will be cls
        boolean isClass=(rnd.nextDouble()<cls.prior)? true: false;
        byte[] eVals=new byte[cls.evs.length];
        for(int j=0;j<cls.evs.length;j++) {
            double genProb;
            PR_Evidence ev=cls.evs[j];
            if(isClass)
                genProb=ev.recall;
            else {
                genProb=cls.negRecalls[j];
            }
            eVals[j]=(byte) ((rnd.nextDouble()<genProb)? 1: 0);
        }
        return new PR_Example(obj,cls,isClass,eVals);
    }

    public int compareTo(PR_Example ex) {
        if(!cVal && ex.cVal)
            return 1;
        if(cVal && !ex.cVal)
            return -1;
        if(classProb<ex.classProb)
            return 1;
        if(classProb>ex.classProb)
            return -1;
        return 0;
    }

    public static void main(String[] args) {
        double prior=0;
        PR_Evidence[] evs=null;
        byte[] eVals=null;

        if(args.length==0) {
            // default P(A) with 2 evidences, 1 observed and 1 missing
            prior=0.1;
            PR_Evidence e1=new PR_Evidence("monitor name: <X>", 0.5, 0.5, (byte)0, 0);
            PR_Evidence e2=new PR_Evidence("lcd <manufacturer> <alphanum>", 0.5, 0.5, (byte)0, 1);
            evs=new PR_Evidence[2]; evs[0]=e1; evs[1]=e2;
            eVals=new byte[2]; eVals[0]=1; eVals[1]=0;
        }else if((args.length-1)%3==0) {
            prior=Double.parseDouble(args[0]);
            int evCnt=(args.length-1)/3;
            evs=new PR_Evidence[evCnt];
            eVals=new byte[evCnt];
            int cnt=0;
            for(int i=1;i<args.length;i+=3) {
                try {
                    double prec=Double.parseDouble(args[i]);
                    double recall=Double.parseDouble(args[i+1]);
                    byte on=(byte)Short.parseShort(args[i+2]);
                    evs[cnt]=new PR_Evidence("e"+cnt,prec,recall,(byte)0,cnt);
                    eVals[cnt]=on;
                    cnt++;
                }catch(NumberFormatException ex) {
                    System.out.println(ex.toString());
                }
            }
        }else {
            System.out.println("Expecting 1+N*3 args, e.g.: prior,  e1_prec, e1_rec, e1_on,  e2_prec, e2_rec, e2_on");
            System.out.println();
            System.out.println("The prior parameter sets the prior probability of a predicted class. \n"+
                    "Precisions of each observed evidence are combined according to the prior \n"+
                    "probability: precisions higher than prior boost the resulting probability, \n"+
                    "precisions lower than prior decrease it. \n"+
                    "For unobserved evidences, high recalls decrease the predicted class probability \n"+
            "while low recalls may boost it.");
            return;
        }

        PR_Class cls=new PR_Class("monitor_name",prior,evs);
        PR_Example ex=new PR_Example("ex",cls,false,eVals);
        System.out.println(ex.toString());

        if(evs.length==2) {
            System.out.println("CIE  ="+PR_Example.combineIndepEvidence(prior, evs[0], evs[1]));
            System.out.println("cie  ="+PR_Example.cie(prior, evs[0], evs[1]));
        }
        System.out.println("condProb ="+ex.condProb());
    }

    /** 
     *	Computes P(A|e1,e2) under the assumptions that:
     *  e1 is independent with e2 | A, and
     *  e1 is independent with e2 | non A.
     *  The combined conditional probability is based on priorClassProb
     *  and on the precision and recall estimates of the supplied evidences.
     */ 
    public static double combineIndepEvidence(double priorClassProb, PR_Evidence e1, PR_Evidence e2) {
        double e1_and_A= priorClassProb * e1.recall;
        double e2_and_A= priorClassProb * e2.recall;
        double e1_and_nonA= ((1.0-e1.prec)/e1.prec) * e1_and_A; // 2x bayes
        double e2_and_nonA= ((1.0-e2.prec)/e2.prec) * e2_and_A;
        double e1_e2_A= (e1_and_A * e2_and_A) / priorClassProb; // assumption: P(e2|A_and_e1) = P(e2|A)
        double e1_e2_nonA= (e1_and_nonA * e2_and_nonA) / (1-priorClassProb); // assumption: P(e2|nonA_and_e1) = P(e2|nonA)
        return e1_e2_A/(e1_e2_A + e1_e2_nonA);
    }
    /*
	return (priorClassProb * e1.recall * e2.recall) /
	    ( (priorClassProb * e1.recall * e2.recall) + 
	      ((1.0-e1.prec) * priorClassProb * e1.recall * (1/e1.prec) * 
	       (1.0-e2.prec) * priorClassProb * e2.recall * (1/e2.prec) ) / (1-priorClassProb)
	      );
     */

    public static double cie(double priorClassProb, PR_Evidence e1, PR_Evidence e2) {
        // return (1-priorClassProb) * (e1.prec/(1-e1.prec)) * (e2.prec/(1-e2.prec)); // chybka

        //correct:
        //return ((1-priorClassProb) * e1.prec * e2.prec) /  
        //      (((1-priorClassProb) * e1.prec * e2.prec) + (priorClassProb * (1-e1.prec) * (1-e2.prec)));
        // the above is same as:

        return 1 / (1 + ((priorClassProb)/(1-priorClassProb)) * ((1-e1.prec)/e1.prec) * ((1-e2.prec)/e2.prec));
        //return 1 / (1 + ((1-priorClassProb)/(priorClassProb)) * ((1-e1.prec)/e1.prec) * ((1-e2.prec)/e2.prec));
    }

    public static double cie2(double priorClassProb, PR_Evidence e1, PR_Evidence e2) {
        double e1_and_A= priorClassProb * e1.recall;
        double e2_and_A= priorClassProb * e2.recall;
        double e1_alone= e1_and_A / e1.prec;
        double e2_alone= e2_and_A / e2.prec;
        double e1_e2_A= (e1_and_A * e2_and_A) / priorClassProb;
        double e1_e2= (e1_alone * e2_alone);
        return e1_e2_A/e1_e2;
    }
}
