// $Id: TIC2.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

import java.util.*;
import java.lang.Math;
import uep.util.*;

import ex.model.*;
import ex.ac.*;
import ex.reader.Document;

/* Trellis Instance Candidate - several TICs can be placed in single TrellisRecord */
class TIC2 extends ICBase {
    // child ICs in the binary trees of trellis 
    public ICBase left;
    public ICBase right;
    public AC ac; // null or an aggregate AC composed of child ACs of this IC

    // left out ACs that do not overlap with member ACs;
    // when a group of overlapping ACs is left out, we choose
    // the path through the segment with maximum avg attribute conditional prob,
    // but then we use that path's lattice probability which includes engaged prob
    public ACSegment orphans;
    public double orphanPenaltySum;

    // TODO: include crossed tags and/or wrapper information

    // precomputed values
    public int acCnt;
    public int startIdx;
    public int endIdx;
    public int tokenLength;
    
    // crossed block tags accumulated when IC expands beyond block fmt elements
    protected int crossTagCount;

    public int contCode; // content key

    public boolean contained;

    // whether left or right can be considered a reference to another AC already present in this IC
    public byte reference;
    protected AC referencedBaseAC;
    
    /* if not contained, left and right do not overlap and left may at most border with right;
       if contained, left must fully contain right */
    public TIC2(ICBase left, ICBase right, ACSegment orphans, int crossBlockCnt, boolean contained, byte reference, AC referencedBaseAC) {
        super();
        this.left=left;
        this.right=right;
        // this.acCnt=left.attCount() + right.attCount();
        this.acCnt=(reference==REF_LEFT?  0: left.attCount()) + 
                   (reference==REF_RIGHT? 0: right.attCount());
        this.referencedBaseAC=referencedBaseAC;
//        this.tokenLength=left.getContentTokenLength()+right.getContentTokenLength();
        this.contCode=left.contentCode() + right.contentCode() + ((orphans!=null)? orphans.hashCode(): 0);
        this.clsDef=left.clsDef;
        this.contained=contained;
        this.crossTagCount=crossBlockCnt;
        this.reference=reference;
        if(contained) {
            startIdx=left.getStartIdx();
            endIdx=left.getEndIdx();
            if(right.getStartIdx()<startIdx || right.getEndIdx()>endIdx) {
                throw new IllegalArgumentException("TIC2 left component does not contain right: "+
                        "["+left.getStartIdx()+","+left.getEndIdx()+"]["+right.getStartIdx()+","+right.getEndIdx()+"]");
            }
        }else {
            startIdx=left.getStartIdx();
            endIdx=right.getEndIdx();
            if(left.getEndIdx()>=right.getStartIdx()) {
                throw new IllegalArgumentException("TIC2 components left right overlap or have invalid order: "+
                        "["+left.getStartIdx()+","+left.getEndIdx()+"]["+right.getStartIdx()+","+right.getEndIdx()+"]");
            }
        }
        this.orphans=orphans;
        orphanPenaltySum=0;
        if(orphans!=null) {
            orphanPenaltySum=orphans.getLatticeProb();
            if(log.IFLG(Logger.TRC)) Logger.LOG(Logger.TRC,"IC ["+icid+"] using "+orphans);
        }
        if(left instanceof TIC2)
            orphanPenaltySum+=((TIC2)left).orphanPenaltySum;
        if(right instanceof TIC2)
            orphanPenaltySum+=((TIC2)right).orphanPenaltySum;
        setScript();
        setScore();
    }
    
    public void clear() {
        if(ac!=null) {
            ac.clear();
            ac=null;
        }
        if(orphans!=null) {
            orphans.clear();
            orphans=null;
        }
        if(referencedBaseAC!=null) {
            referencedBaseAC.clear();
            referencedBaseAC=null;
        }
        if(left!=null) {
            left.clear();
            left=null;
        }
        if(right!=null) {
            right.clear();
            right=null;
        }
    }
    
    public int attCount() {
        return acCnt;
    }
    
    public int contentCode() {
        return contCode;
    }
    
    protected void setScript() {
        StringBuffer cs=new StringBuffer(left.contentScriptString.length()+right.contentScriptString.length());
        cs.append(left.contentScriptString);
        cs.append(right.contentScriptString);
        contentScriptString=cs.toString();
        clsDef.model.context.enter();
        contentScript=clsDef.model.context.compileString(contentScriptString,"IC"+hashCode(),0,null);
        clsDef.model.context.exit();
    }
    
    protected double setScore() {
        // orphanPenaltySum of children is included in their acSums 
        // acSum = left.acSum + right.acSum + ((orphans!=null)? orphans.getLatticeProb(): 0);
        acSum = (reference==REF_LEFT?  0: left.acSum) + 
                (reference==REF_RIGHT? 0: right.acSum) + 
                ((orphans!=null)? orphans.getLatticeProb(): 0);
        // fix acSum to contain the best AC from reference group:
        if(referencedBaseAC!=null) {
            // remove the worse AC contained somewhere in base
            acSum-=referencedBaseAC.getEngagedProb();
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,icid+" REM REF: "+referencedBaseAC.getEngagedProb());
            // and replace with the better referenced freshly added AC
            switch(reference) {
            case REF_LEFT:
                acSum+=((TIC1)left).ac.getEngagedProb();
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,icid+" ADD REF: "+((TIC1)left).ac.getEngagedProb());
                break;
            case REF_RIGHT:
                acSum+=((TIC1)right).ac.getEngagedProb();
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,icid+" ADD REF: "+((TIC1)right).ac.getEngagedProb());
                break;
            default:
                throw new IllegalArgumentException("No reference when rba="+referencedBaseAC);
            }
        }
        if(acSum>0) {
            log.LGERR("acSum="+acSum+" ic="+this);
        }
        // TODO: acSum -= crossed tags, cardinality setup, dom tree matching
        switch(Parser.SCORING_METHOD) {
        case Parser.SM_GEOMEAN_SUM:
            score = Math.exp(acSum/acCnt);
            break;
        case Parser.SM_LOGSUM_SUM:
            score = acSum;
            break;
        }
        applyClassEvidence(true, getDocument());
        return score;
    }
    
    public int getContentTokenLength() {
        return tokenLength;
    }
    
    protected void setCards(short[] cards) {
        if(ac!=null && !contained) { // if contained, do not double count the left (head) AC
            cards[ac.getAttribute().id]++;
            // TODO: check this handling of cardinalities is correct
            AttributeDef ad=ac.getAttribute().parent;
            while(ad!=null) {
                cards[ad.id]++;
                ad=ad.parent;
            }
        }
        if(reference!=REF_LEFT)
            left.setCards(cards);
        if(reference!=REF_RIGHT)
            right.setCards(cards);
    }
    protected int checkCards(short[] cards) {
        if(ac!=null && !contained) {
            AttributeDef ad=ac.getAttribute();
            if(++cards[ad.id] > ad.maxCard)
                return IC_MAXCARD;
            // TODO: determine how to treat cardinalities for inherited attributes;
            // currently the most general attribute determines the total count of the subtree,
            // the specialized attributes can be more restrictive
            ad=ad.parent;
            while(ad!=null) {
                if(++cards[ad.id] > ad.maxCard)
                    return IC_MAXCARD;
                ad=ad.parent;
            }
        }
        if(reference!=REF_LEFT && left.checkCards(cards)==IC_MAXCARD)
            return IC_MAXCARD;
        if(reference!=REF_RIGHT && right.checkCards(cards)==IC_MAXCARD)
            return IC_MAXCARD;
        return IC_OK;
    }
    /** Checks if this IC satisfies relevant axioms defined on the class.
	    If partial==true, only those axioms that apply to already populated attributes are evaluated,
	    otherwise all axioms are examined */
    public boolean isValid(boolean partial, Parser parser) {
        boolean rc=true;
        lastValid=ICVALID_FALSE;
        // check maxCard, minCard, containment
        short[] cards=parser.cards[clsDef.id]; // new int[clsDef.attArray.length];
        Arrays.fill(cards, (short)0);
        setCards(cards);
        // TODO: check AC-AC containment cardinality
        for(int i=0;i<cards.length;i++) {
            // check minCard
            if( (!partial && (cards[i] < clsDef.attArray[i].minCard)) || 
                (cards[i] > clsDef.attArray[i].maxCard) ) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"IC not valid due to cardinality of "+clsDef.attArray[i].name+
                  " ("+clsDef.attArray[i].minCard+","+clsDef.attArray[i].maxCard+"):\n"+toString());
                rc=false;
                break;
            }
        }
        // check all axioms of the model
        if(rc) {
            rc=clsDef.isValid(this, partial);
            if(rc)
                lastValid=partial? ICVALID_PARTIAL: ICVALID_TRUE;
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"IC2 valid "+(rc?"OK: ":"FAIL: ")+getId());
        return rc;
    }

    private void setStartIdx() {
        ICBase leftmost=this.left;
        while(leftmost instanceof TIC2)
            leftmost=((TIC2) leftmost).left;
        startIdx = leftmost.getStartIdx();
    }

    private void setEndIdx() {
        ICBase rightmost=this.right;
        while(rightmost instanceof TIC2)
            rightmost=((TIC2) rightmost).right;
        endIdx = rightmost.getEndIdx();
    }

    public int getStartIdx() {
        return startIdx;
    }

    public int getEndIdx() {
        return endIdx;
    }

    public boolean canBeAdded() {
        /* returns true in case all children may constitute a complex attribute (specified as ac), 
           such as free text product description consisting of detailed attributes and intermediate text */
        return (ac!=null)? true: false;
    }

    public AC getAC() {
        return ac;
    }

    public void setAC(AC ac) {
        this.ac=ac;
    }

    public void attrsToString(int depth, StringBuffer buff) {
        Util.rep(depth, ' ', buff);
        if(ac!=null) {
            buff.append("[");
            ac.toString(buff);
            buff.append("]");
        }else {
            buff.append('*');
        }
        buff.append('\n');

        if(left!=null) {
            if(reference==REF_LEFT)
                buff.append("ref:");
            left.attrsToString(depth+((ac!=null)?2:1), buff);
        }
        if(right!=null) {
            if(reference==REF_RIGHT)
                buff.append("ref:");
            right.attrsToString(depth+((ac!=null)?2:1), buff);
        }
        if(orphans!=null) {
            Util.rep(depth, ' ', buff);
            buff.append("orphans: ");
            buff.append(orphans.toString());
        }
    }

    public void attrsToTable(StringBuffer buff, boolean isReference) {
        if(ac!=null) {
            buff.append(ac.toTableRow(true, isReference));
        }
        left.attrsToTable(buff, reference==REF_LEFT);
        right.attrsToTable(buff, reference==REF_RIGHT);
        if(orphans!=null) {
            buff.append(orphans.toTableRow());
        }
    }

    public void getAttrNames(StringBuffer buff) {
        left.getAttrNames(buff);
        right.getAttrNames(buff);
    }

    /*
    public double getSubtractACScore() {
	return left.getSubtractACScore() + right.getSubtractACScore();
    }
     */

    public int getACCount() {
        return left.getACCount()+right.getACCount();
    } 

    public int getACs(Collection<AC> acs, int filter) {
        int cnt=0;
        if(((filter & ACS_ORPHANS)!=0 || (filter & ACS_FALSE)!=0)) {
            if(orphans!=null)
                cnt+=orphans.getACs(acs, filter);
            // for simplicity let's deal with orphans / false extracts before dealing with references
            int shalFilt=(filter & ACS_ORPHANS) | (filter & ACS_FALSE);
            cnt+=left.getACs(acs, shalFilt);
            cnt+=right.getACs(acs, shalFilt);
            filter &= ~shalFilt;
        }
        if(((reference==REF_LEFT || !(left instanceof TIC1)) && (filter & ACS_REF)!=0) ||
           (reference!=REF_LEFT && (filter & ACS_NONREF)!=0))
            cnt+=left.getACs(acs, filter);
        if(((reference==REF_RIGHT || !(right instanceof TIC1)) && (filter & ACS_REF)!=0) ||
           (reference!=REF_RIGHT && (filter & ACS_NONREF)!=0))
            cnt+=right.getACs(acs, filter);
        return cnt;
    }

    public int getOrphanSegments(Collection<ACSegment> segments) {
        int cnt=0;
        if(orphans!=null) {
            cnt++;
            segments.add(orphans);
        }
        if(left instanceof TIC2 && reference!=REF_LEFT)
            cnt+=((TIC2)left).getOrphanSegments(segments);
        if(right instanceof TIC2 && reference!=REF_RIGHT)
            cnt+=((TIC2)right).getOrphanSegments(segments);
        return cnt;
    }

    public AC getIntersection(Collection<AC> acSet, boolean getBest) {
        AC ac1=left.getIntersection(acSet, getBest);
        AC ac2=right.getIntersection(acSet, getBest);
        if(ac1==null)
            return ac2;
        else if(ac2==null)
            return ac1;
        else
            return ac1.getProb()>ac2.getProb()? ac1: ac2;
    }
    
    /** Recomputes IC score based on changes in the scores of
     * ACs given in acSet.
     * @param acSet The set of ACs whose score has changed. Set to null to force recomputation.
     * @return true if recomputation was needed.
     */
    public boolean recomputeScore(Set<AC> acSet) {
        boolean rc=false;
        if(left.recomputeScore(acSet) || right.recomputeScore(acSet) || acSet==null) {
            rc=true;
        }
        if(rc) {
            setScore();
        }
        return rc;
    }

    @Override
    protected int consumeACs(Annotable[] ans, int startIdx) {
        int curIdx=left.consumeACs(ans, startIdx);
        if(curIdx<ans.length) {
            curIdx=right.consumeACs(ans, curIdx);
        }
        return curIdx;
    }
    
    protected Document getDocument() {
        if(ac!=null)
            return ac.doc;
        ICBase ic=left;
        while(ic!=null && (ic instanceof TIC2)) {
            ic=((TIC2) ic).left;
        }
        return ((TIC1) ic).ac.doc;
    }
    
    /** sum of penalties accumulated when IC expands to another block fmt elements */
    public int getCrossTagCount() {
        return crossTagCount;
    }
}
