// $Id: ICBase.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

import org.mozilla.javascript.Script;
import uep.util.Logger;
import uep.util.Util;
import ex.util.pr.*;
import ex.reader.*;
import ex.model.*;
import ex.ac.*;
import ex.wrap.*;

public abstract class ICBase implements Comparable<ICBase>, Annotable, Extractable, Iterable<AC> {
    public static final int IC_OK=0;
    public static final int IC_OVERLAP=-1;
    public static final int IC_MAXCARD=-2;
    public static final int IC_OTHERCLASS=-3;
    public static final int IC_DUPL=-4;
    public static final int IC_AXIOM_NOT_VALID=-5;
    public static final int IC_LATER=-6;

    public static final String[] RC2STR={
        "OK", "OVERLAP", "MAXCARD", "OTHERCLASS", "DUPL", "AXIOM_NOT_VALID"
    };

    public static final int ACS_MEMBERS=1;
    public static final int ACS_ORPHANS=2;
    public static final int ACS_FALSE=4;
    public static final int ACS_REF=8;
    public static final int ACS_NONREF=16;
    public static final int ACS_ALL = ACS_MEMBERS | ACS_ORPHANS | ACS_FALSE | ACS_REF | ACS_NONREF;
    
    public static final char ICVALID_UNK=0;
    public static final char ICVALID_FALSE=1;
    public static final char ICVALID_PARTIAL=2;
    public static final char ICVALID_TRUE=3;

    public static final byte REF_NONE=0;
    public static final byte REF_LEFT=1;
    public static final byte REF_RIGHT=2;
    
    public static ICDocOrderComparator icDocOrdCmp=new ICDocOrderComparator();

    public ClassDef clsDef;
    public Script contentScript;
    public String contentScriptString;
    public double score;

    // so far only used by TIC1 and TIC2
    Object container;

    // precomputed
    public double acSum; // sum of member AC log probs
    public char lastValid; // reflects last isValid() rc
    public double combinedProb; // weighted combination of inst patterns and AC scores
    
    // for parser
    protected boolean pruned;
    
    // debugging only
    protected static Logger log;
    public static int icidCnt=0;
    public int icid;

    public ICBase() {
        if(log==null)
            log=Logger.getLogger("IC"); // debugging only
        icid=++icidCnt;
        lastValid=ICVALID_UNK;
        combinedProb=0.0;
        pruned=false;
    }

    public int compareTo(ICBase ic) {
        if(this.score<ic.score)
            return 1;
        else if(this.score>ic.score)
            return -1;
        if(this.attCount()<ic.attCount())
            return 1;
        else if(this.attCount()>ic.attCount())
            return -1;
        return (this==ic)? 0: icid-ic.icid; // not 0 since Sets would replace 2 elements whose compareTo()==0
    }

    public abstract void clear();
    public abstract int attCount();
    protected abstract void setScript();
    protected abstract double setScore();
    protected abstract void setCards(short[] cards);
    protected abstract int checkCards(short[] cards);
    /** Checks if this IC satisfies relevant axioms defined on the class.
	If partial==true, only those axioms that apply to already populated attributes are evaluated,
	otherwise all axioms are examined */
    public abstract boolean isValid(boolean partial, Parser parser);
    public char isValidCached() { return lastValid; }

//    /** This is for testing - more a hack. In case of equal IC scores this is used
//        to benefit ICs with longer content values. This includes the length of 
//        all referenced ACs. */
//    public abstract int getContentTokenLength();
    
    // returns almost a hash code; used to compare whether IC with identical content is already present in some IC set 
    // currently this is sum of pointers to contained ACs - thus we rely on a sum of pointers to be always 
    // different with different addents - of course this 'assumption' can fuck up the parse eventually
    public abstract int contentCode();

    /* ex.ac.Extractable interface */
    public abstract int getStartIdx();
    
    public abstract int getEndIdx();
    
    public double getLatticeProb() {
        return score;
    }
    public void setLatticeProb(double newScore) {
        score=newScore;
    }
    
    /* ex.ac.Annnotable interface */
    public int getLength() { 
        return getEndIdx() - getStartIdx() + 1;
    }
    public int getType() { 
        return Annotable.TYPE_IC;
    }
    public double getProb() {
        return getLatticeProb();
    }
    public ModelElement getModelElement() {
        return clsDef;
    }
    
    /* other methods */
    public double getOrphanProb() throws UnsupportedOperationException {
        AC ac=getAC();
        if(ac==null)
            throw new UnsupportedOperationException("getOrphanProb() called on IC that has no head AC");
        return ac.getOrphanProb();
    }

    public double getACProb() throws UnsupportedOperationException {
        AC ac=getAC();
        if(ac==null)
            throw new UnsupportedOperationException("getACProb() called on IC that has no head AC");
        return ac.getLatticeProb();
    }

    public abstract boolean canBeAdded();
    public abstract AC getAC();
    public abstract void setAC(AC ac);

    public abstract void attrsToString(int depth, StringBuffer buff);
    public String toString() {
        StringBuffer buff=new StringBuffer(256);
        buff.append(getId()+" ("+String.format("%.4f",combinedProb)+"/"+String.format("%.4f",score)+"/"+attCount()+")\n");
        attrsToString(1, buff);
        return buff.toString();
    }
    public String getId() {
        return ((clsDef!=null)? clsDef.name: "<unknown class>")+"["+icid+"]";
    }
    public String getIdScore() {
        return getId()+" ("+String.format("%.4f",combinedProb)+"/"+String.format("%.4f",score)+"/"+attCount()+")";
    }
    public String getText() {
        ArrayList<AC> orphans=new ArrayList<AC>(4);
        getACs(orphans, ACS_ORPHANS);
        ArrayList<AC> all=new ArrayList<AC>(8);
        getACs(all, ACS_ALL);
        // make sure we always get the correct doc order - this should be the case but...
        Collections.sort(all, icDocOrdCmp); // by startIdx then endIdx

        StringBuffer s=new StringBuffer(64);
        s.append(clsDef.name);
        s.append(' ');
        s.append(icid);
        int cnt=all.size();
        if(cnt>0)
            s.append("\\n");
        for(int i=0;i<cnt;i++) {
            if(i>0)
                s.append('|');
            AC ac=all.get(i);
            if(orphans.contains(ac))
                s.append('~');
            ac.getText(s);
        }
        return s.toString();
    }

    public String toTable() {
        StringBuffer buff=new StringBuffer(256);
        buff.append("<tr><th colspan=\"2\">");
        buff.append((clsDef!=null)? clsDef.name: "Unknown");
        buff.append(" "+icid);
        buff.append(" ("+String.format("%.4f",combinedProb)+"/"+String.format("%.4f",score)+")");
        buff.append("</th></tr>\n");
        attrsToTable(buff,false);
        return buff.toString();
    }
    public abstract void attrsToTable(StringBuffer buff, boolean isReference);

    public FmtLayout getFmtLayout() { return null; }

    public void getAttrNames(StringBuffer buff) { }

    public int getACCount() {
        return 1;
    }

    /** filter = ACS_ALL, ACS_MEMBERS, ACS_ORPHANS */
    public int getACs(Collection<AC> acs, int filter) {
        return 0;
    }
    
    public AC getIntersection(Collection<AC> acSet, boolean getBest) {
        return null;
    }
    
    public boolean recomputeScore(Set<AC> acSet) {
        return false;
    }

    /** Uses all patterns known for this class to compute conditional prob of this instance. 
     * Positive evidence is applied based on applicable pattern matches. */
    public void applyClassEvidence(boolean partial, Document doc) {
        // we ignore local FmtPatterns for now since we can induce them only for attributes
        // prepare default values
        int clen=clsDef.ctxPatterns.size();
        int slen=clsDef.scriptPatterns.size();
        int dlen=clsDef.defPatternList.size();
        if(clen+slen+dlen==0) {
            combinedProb = Math.exp(acSum/(double)attCount()); // getCombinedProb() used by ICQueueComparator to prioritize parsing 
            score = acSum;
            if(log.IFLG(Logger.TRC)) 
                log.LG(Logger.TRC, getId()+" "+(partial? "partial": "final")+", combined(mean)="+combinedProb+" score(akalogsum)="+score+" (0 class evs)");
            return;
        }
        int ec=clen+slen+dlen; // +(Parser.COMBINE_METHOD==Parser.CM_PR? 1: 0);
        byte[] eVals=new byte[ec];
        // List<PR_Evidence> custEvs=new LinkedList<PR_Evidence>();
        int i;
        int on=0;
        int off=0;
        // context and value patterns
        if(clen>0) {
            for(i=0;i<clen;i++) {
                TokenPattern pat=clsDef.ctxPatterns.get(i);
                int evIdx=pat.evidence.idx; // i;
                eVals[i]=pat.evidence.defaultValue;
                if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"Searching for instpatmatches of "+pat+" in "+this);
                if((pat.contentType & TokenPattern.PATTERN_WITH_ACS)!=0 &&
                   (pat.condType==Axiom.AXIOM_COND_ALL || pat.condType==Axiom.AXIOM_COND_ANY)) {
                    short foundCnt=0;
                    for(ModelElement usedElem: pat.getUsedElements()) {
                        boolean found=false;
                        for(AC memberAc: this) {
                            if(usedElem==memberAc.getAttribute()) {
                                found=true;
                                foundCnt++;
                                break;
                            }
                        }
                        if(!found && pat.condType==Axiom.AXIOM_COND_ALL) {
                            foundCnt=0;
                            break;
                        }
                    }
                    if(foundCnt==0) {
                        eVals[evIdx]=-1;
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Skipped cond instpat "+pat+" for "+this.getId());
                        continue;
                    }
                }
                //if(log.IFLG(Logger.USR)) log.LG(Logger.USR,pat.id+"."+pat.type);
                PatMatch pm=doc.findMatch(pat, this);
                if(pm!=null) {
                    if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"Class "+getId()+" pattern match "+pm);
                    if(pm.pat.logLevel>0 && log.IFLG(Logger.USR)) log.LG(Logger.USR,pm.pat.id+": "+this.toString());
                }
                double rc=0.0;
                if(pm!=null) {
                    rc=pm.getMatchLevel();
                }
                if(rc==0.0) {
                    if(!partial) {
                        eVals[evIdx]=0;
                        off++;
                    }else {
                        switch(pat.type) {
                        default:
                            eVals[evIdx]=-1;
                        }
                    }
                }else if(rc==1.0) {
                    eVals[evIdx]=1;
                    on++;
                }else if(rc==-1.0) {
                    eVals[evIdx]=-1;
                }else {
                    if(rc>0.5) {
                        eVals[evIdx]=1;
                        on++;
                    }else {
                        if(partial) {
                            eVals[evIdx]=(byte) -1;
                        }else {
                            eVals[evIdx]=(byte) 0;
                            off++;
                        }
                    }
                    // the following is wrong: we must keep the original prec and recall 
                    // while reducing the weight of the evidence's contribution somehow
                    // PR_Evidence ev=pat.evidence;
                    // PR_Evidence cev=new PR_Evidence(ev.name+"_cus", rc*ev.prec, ev.recall, ev.defaultValue, -1);
                    // custEvs.add(cev);
                }
            }
        }
        // script patterns
        if(slen>0) {
            clsDef.model.clearScope();
            clsDef.model.eval(contentScript);
            for(i=0;i<slen;i++) {
                ScriptPattern pat=clsDef.scriptPatterns.get(i);
                int evIdx=pat.evidence.idx; // clen+i;
                eVals[evIdx]=pat.evidence.defaultValue;
                boolean rc=true;
                if(pat.axiom.condScript!=null && !clsDef.evalCond(pat.axiom.condScript)) {
                    eVals[evIdx]=-1;
                }else {
                    rc=clsDef.isValid(pat.axiom, partial);
                    if(rc) {
                        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"Class "+getId()+" script pattern match "+pat);
                        eVals[evIdx]=(byte)1;
                        on++;
                    }else {
                        if(partial) {
                            eVals[evIdx]=(byte) -1;
                        }else {
                            eVals[evIdx]=(byte) 0;
                            off++;
                        }
                    }
                }
            }
        }
        // default patterns
        if(dlen>0) {
            int[] crossTagCounts={0,0,0,0};
            doc.fillCrossTagCounts(getStartIdx(), getEndIdx(), crossTagCounts);
            for(i=0;i<dlen;i++) {
                DefaultPattern pat=clsDef.defPatternList.get(i);
                int evIdx=pat.evidence.idx; // clen+slen+i;
                switch(evIdx) {
                case DefaultPattern.EVIDENCE_NOCROSS_INLINE_TAG:
                    if(crossTagCounts[evIdx]>0) {
                        eVals[evIdx]=0;
                        off++;
                    }else {
                        eVals[evIdx]=pat.evidence.defaultValue;
                    }
                    break;
                case DefaultPattern.EVIDENCE_NOCROSS_BLOCK_TAG:
                    if(getCrossTagCount()>0) {
                        eVals[evIdx]=(byte)0;
                        off++;
                    }else {
                        eVals[evIdx]=pat.evidence.defaultValue;
                    }
                    break;
                case DefaultPattern.EVIDENCE_FIT_IN_TAG:
                case DefaultPattern.EVIDENCE_HAS_ONE_PARENT:
                    if(crossTagCounts[evIdx]==0) {
                        eVals[evIdx]=0;
                        off++;
                    }else {
                        eVals[evIdx]=pat.evidence.defaultValue;
                    }
                    break;
                //case DefaultPattern.EVIDENCE_MULTIBLOCK:
                //    eVals[evIdx]=(getCrossTagCount()>0)? 0: pat.evidence.defaultValue;
                //    break;
                default:
                    log.LG(Logger.ERR,"Unknown default pattern evIdx="+evIdx+" for IC="+getId());
                }
                if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"IC "+getId()+" default pattern match of "+pat+"="+eVals[evIdx]+" in "+this);
            }
        }
        // find acp
        double logacp=acSum/attCount();
        double acp=Math.exp(logacp);
        if(acp>1) {
            log.LG(Logger.ERR,"mean(acp)="+acp+" atts="+attCount()+" ic="+this);
            acp=1;
        }
        
        // compute icp
        int evCnt=on+off;
        int reallyOn=0;
        for(int k=0;k<clsDef.prClass.evs.length;k++)
            if(eVals[k]>0 && clsDef.prClass.evs[k].prec>=0)
                reallyOn++;
        double icp=0;
        if(reallyOn>0) {
            icp = PR_Evaluator.eval(clsDef.prClass, eVals);
        }else {
            if(Util.equalsApprox(acp,0))
                icp=0;
            else if(Util.equalsApprox(acp,1))
                icp=1;
            else {
                icp = Math.min(acp, PR_Evaluator.eval(acp, clsDef.prClass.evs, null, eVals));
            }
        }
        if(log.IFLG(Logger.TRC)) {
            StringBuffer b=new StringBuffer(128);
            b.append(getId()+" "+(partial? "partial": "final")+" comb:\n");
            for(int j=0;j<eVals.length;j++) {
                b.append(clsDef.prClass.evs[j].name+"="+eVals[j]+"\n");
            }
            log.LG(Logger.TRC,b.toString());
        }

        // Combine icp and acp; TODO: check alternative ways of combining these
        if(evCnt>5)
            evCnt=5;
        if(evCnt>0) {
            int stopDebuggerHere=1;
        }
        double[] wics={0.0, 0.20, 0.30, 0.40, 0.45, 0.5};
        double w=wics[evCnt];
        double cop=0;
        double logcop=0;
        switch(Parser.COMBINE_METHOD) {
        case Parser.CM_MEAN:
            logcop = (1-w)*logacp + w*Math.log(icp);
            cop = Math.exp(logcop);
            break;
        case Parser.CM_GEOMEAN: // same as above
            cop = Math.pow(acp, 1-w) * Math.pow(icp, w);
            logcop = Math.log(cop);
            break;
        case Parser.CM_PSEUDOBAYES:
            if(w==0.0)
                cop = acp;
            else if(reallyOn==0)
                cop = icp; // acp already included as prior
            else
                cop = (acp *  icp) / ((acp * icp) + (1 - acp) * (1 - icp));
            logcop = Math.log(cop);
            break;
        case Parser.CM_PR:
            eVals=new byte[] {1,1};
            PR_Evidence[] evs=new PR_Evidence[] {
                new PR_Evidence("acs", acp, icp, (byte)1, -1),
                new PR_Evidence("props", acp, icp, (byte)1, -1)
            };
            cop=PR_Evaluator.eval(clsDef.prClass.prior, evs, null, eVals);
            logcop = Math.log(cop);
            break;
        }
        combinedProb = cop; // getCombinedProb() used by ICQueueComparator to prioritize parsing 
        score = logcop * attCount();
        if(log.IFLG(Logger.TRC)) 
            log.LG(Logger.TRC, getId()+" "+(partial? "partial": "final")+" acp="+acp+", icp="+icp+", combined(mean)="+combinedProb+" score(akalogsum)="+score+" ("+reallyOn+"/"+on+"+"+off+" class evs, w="+w+")");
    }

    /** Returns true if this IC contains all ACs matched 
     *  by the given pattern match. */
    public boolean containsAllACs(PatMatch pm) {
        if(pm.children==null)
            return true;
        // roll to the 1st AC
        int curIdx;
        for(curIdx=0; curIdx<pm.children.length; curIdx++) {
            if(pm.children[curIdx] instanceof AC)
                break;
        }
        if(curIdx<pm.children.length) {
            // roll through the first and subsequent ACs in the PatMatch
//            log.LG(Logger.USR,this.toString());
//            if(this.toString().indexOf("stime")!=-1 && this.toString().indexOf("etime")!=-1) {
//                int stopHere=1;
//            }
            curIdx=consumeACs(pm.children, curIdx);
        }
        boolean rc=(curIdx==pm.children.length);
        if(rc && pm.pat.logLevel>0 && log.IFLG(Logger.USR)) log.LG(Logger.USR,pm.toString()+" containsAllACs="+rc+" idx="+curIdx);
        return rc;
    }
    
    protected abstract int consumeACs(Annotable[] ans, int startIdx);

    public double getCombinedProb() {
        return combinedProb;
    }
    
    /** sum of penalties accumulated when IC expands to another block fmt elements */
    public abstract int getCrossTagCount();
    
    /** Returns an iterator through all member ACs. */
    public Iterator<AC> iterator() {
        Iterator<AC> it=null;
        if(this instanceof TIC1) 
            it=new TIC1Iterator((TIC1)this);
        else if(this instanceof TIC2) 
            it=new TIC2Iterator((TIC2)this);
        return it;
    }

    public void prune() {
        pruned=true;        
    }
    
    public boolean isPruned() {
        return pruned;
    }
}

class ICDocOrderComparator implements Comparator<Extractable> {
    public int compare(Extractable a, Extractable b) {
        int d=a.getStartIdx() - b.getStartIdx();
        return (d!=0)? d: (a.getEndIdx() - b.getEndIdx());
    }
    public boolean equals(Extractable a, Extractable b) {
        return compare(a, b)==0;
    }
}

class TIC1Iterator implements Iterator<AC> {
    public ICBase ic;
    public TIC1Iterator(TIC1 ic) {
        this.ic=ic;
    }
    public boolean hasNext() {
        return (ic!=null);
    }
    public AC next() {
        AC ac=ic.getAC();
        ic=null;
        return ac;
    }
    public void remove() {
        throw new UnsupportedOperationException();
    }        
}

class TIC2Iterator implements Iterator<AC> {
    public LinkedList<ICBase> path;
    public TIC2Iterator(TIC2 ic) {
        path=new LinkedList<ICBase>();
        //path.add(ic);
        goDownAndLeft(ic);
    }
    private void prepareNext() {
        // roll back from last TIC1 until we hit the first right path that has not been followed yet
        ICBase last=path.removeLast();
        while(path.size()>0 && last==((TIC2)path.getLast()).right) {
            last=path.removeLast();
        }
        // terminate if we backed up all the way up, or follow the right path
        if(path.size()==0) {
            path=null;
        }else {
            goDownAndLeft(((TIC2)path.getLast()).right);
        }
    }
    private void goDownAndLeft(ICBase ic) {
        while(ic instanceof TIC2) { // first.attCount()>1
            path.add(ic);
            ic=((TIC2)ic).left;
        }
        if(!(ic instanceof TIC1))
            throw new IllegalArgumentException();
        path.add(ic);
    }
    public boolean hasNext() {
        return (path!=null);
    }
    public AC next() {
        if(path==null)
            throw new NoSuchElementException();
        AC ac=path.getLast().getAC();
        prepareNext();
        return ac;
    }
    public void remove() {
        throw new UnsupportedOperationException();
    }        
}
