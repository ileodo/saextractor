// $Id: TIC1.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

import java.util.*;
import java.lang.Math;
import uep.util.*;

import org.cyberneko.html.HTMLElements;
import ex.model.*;
import ex.reader.*;
import ex.features.*;
import ex.ac.*;
import ex.wrap.*;

class TIC1 extends ICBase {
    public AC ac; // single wrapped AC

    public TIC1(AC ac) {
        super();
        this.ac=ac;
        clsDef=ac.getAttribute().myClass;
        setScript();
        setScore();
    }
    public void clear() {
        if(ac!=null) {
            ac.clear();
            ac=null;
        }
    }
    public int attCount() {
        return 1;
    }
    public int contentCode() {
        return ac.hashCode();
    }
    protected void setScript() {
        StringBuffer cs=new StringBuffer(128);
        ac.toScript(cs);
        contentScriptString=cs.toString();
        // clsDef.model.context.enter();
        // contentScript=clsDef.model.context.compileString(contentScriptString,"IC"+hashCode(),0,null);
        // clsDef.model.context.exit();
        contentScript=ac.getScript();
    }
    protected double setScore() {
        // acSum = Math.log(ac.getProb()) + Math.log(ac.getAttribute().engagedProb);
        acSum = ac.getEngagedProb();
        switch(Parser.SCORING_METHOD) {
        case Parser.SM_GEOMEAN_SUM:
            score = Math.exp(acSum);
            break;
        case Parser.SM_LOGSUM_SUM:
            score = acSum;
            break;
        }
        applyClassEvidence(true, ac.doc);
        return score;
    }
//    public int getContentTokenLength() {
//        return ac.getLength();
//    }
    protected void setCards(short[] cards) {
        cards[ac.getAttribute().id]++;
        // TODO: check this handling of cardinalities is correct
        AttributeDef ad=ac.getAttribute().parent;
        while(ad!=null) {
            cards[ad.id]++;
            ad=ad.parent;
        }
    }
    protected int checkCards(short[] cards) {
        AttributeDef ad=ac.getAttribute();
        if(++cards[ad.id] > ad.maxCard)
            return IC_MAXCARD;
        // TODO: check this handling of cardinalities is correct
        ad=ad.parent;
        while(ad!=null) {
            if(++cards[ad.id] > ad.maxCard)
                return IC_MAXCARD;
            ad=ad.parent;
        }
        return IC_OK;
    }
    public boolean isValid(boolean partial, Parser parser) {
        boolean rc=true;
        lastValid=ICVALID_FALSE;
        // check maxCard (is 1 too much? :)
        AttributeDef myAd=ac.getAttribute();
        if(clsDef.attArray[myAd.id].maxCard < 1)
            return false;
        // check minCard
        if(!partial) {
            for(int i=0;i<clsDef.attArray.length;i++) {
                AttributeDef ad=clsDef.attArray[i];
                if((ad.minCard > 1) || (ad.minCard > 0 && (myAd!=ad && !myAd.isDescendantOf(ad)))) {
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"IC not valid due to cardinality of "+ad.name+
                      " ("+clsDef.attArray[i].minCard+","+clsDef.attArray[i].maxCard+"):\n"+getId());
                    rc=false;
                    break;
                }
            }
        }
        // check all axioms of the model
        if(rc) {
            rc=clsDef.isValid(this, partial);
            if(rc)
                lastValid=partial? ICVALID_PARTIAL: ICVALID_TRUE;
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"IC1 valid "+(rc?"OK: ":"FAIL: ")+getIdScore());
        return rc;
    }

    public int getStartIdx() {
        return ac.startToken.idx;
    }

    public int getEndIdx() {
        return ac.startToken.idx + ac.len - 1;
    }

    public boolean canBeAdded() {
        return true;
    }

    public AC getAC() {
        return ac;
    }

    public void setAC(AC ac) {
        this.ac=ac;
    }

    public void attrsToString(int depth, StringBuffer buff) {
        Util.rep(depth,' ',buff);
        //buff.append(ac.getNameText());
        //buff.append(ac.getAttribute().name+"="+ac);
        ac.toString(buff);
        //ac.toString(buff);
        buff.append('\n');
    }

    public void attrsToTable(StringBuffer buff, boolean isReference) {
        buff.append(ac.toTableRow(false, isReference));
    }

    /*
    public double getSubtractACScore() {
        // AC really represents Attribute but is standalone (not part of any instance),
        // or it does not represent an Attribute at all (AC should not exist)
        double pr = ac.getOrphanProb();
        return (pr==0)? (-Integer.MAX_VALUE): Math.log(pr);
    }
     */

    public FmtLayout getFmtLayout() {
        Document doc=ac.doc;

        // get containing block element (layout's root tag)
        TagAnnot container=ac.getParentBlock(-1);
        if(container==null) {
            Logger.LOG(Logger.WRN,"No container found for ac "+ac);
            return null;
        }
        if(log.IFLG(Logger.TRC)) Logger.LOG(Logger.TRC,"Container "+container+container.hashCode()+" found for "+ac);
        boolean wentUp=false;
        while(container.parent!=null) {
            boolean isBlock=false;
            switch(TagTypeF.getValue(container.type)) {
            case TagTypeF.BLOCK:
            case TagTypeF.CONTAINER:
                isBlock=true;
                break;
            }
            if(isBlock)
                break;
            wentUp=true;
            container=(TagAnnot) container.parent;
        }
        if(wentUp) {
            if(log.IFLG(Logger.TRC)) Logger.LOG(Logger.TRC,"Using block container "+container+container.hashCode());
        }
        // copy this IC's subtree of tags with ACs as leafs: save attribute # in instance, attval length, parentIdx
        TagAnnot containerCopy=null;
        TagAnnot firstTag=null;
        AttributeDef attDef=ac.getAttribute();
        SemAnnot sa=new SemAnnot(attDef.id, 0, ac.len, null, ac.startToken.parentIdx, attDef, null);
        // connect sa bottom-up to container
        LinkedList<TagAnnot> frontier=new LinkedList<TagAnnot>(); // to be connected to containerCopy
        HashMap<TagAnnot,TagAnnot> orig2copy=new HashMap<TagAnnot,TagAnnot>(); // all already copied
        TagAnnot last=null;
        for(int i=0;i<ac.len;i++) {
            TokenAnnot ta=doc.tokens[ac.startToken.idx+i];
            if(ta.parent!=last && ta.parent!=null) {
                TagAnnot par=(TagAnnot) ta.parent;
                TagAnnot copy=copyTagAsLayout(par);
                // sa's parent kept null
                copy.appendChild(sa);
                if(firstTag==null) {
                    firstTag=copy;
                }
                if(par==container) {
                    if(containerCopy==null) {
                        containerCopy=copy;
                        containerCopy.parent=null;
                    }
                }else {
                    frontier.add(copy); // copy is to be connected to containerCopy (its parent must be overwritten by a copy)
                }
                orig2copy.put(par, copy);
            }
            last=(TagAnnot) ta.parent;
        }

        // connect intermediate nodes to container
        int k=0;
        while(frontier.size()>0) {
            if(log.IFLG(Logger.TRC)) {
                StringBuffer sb=new StringBuffer(64);
                for(int i=0;i<frontier.size();i++) {
                    if(i>0)
                        sb.append(",");
                    TagAnnot ta=frontier.get(i);
                    sb.append(ta.toString()+ta.hashCode()+"["+ta.parent+((ta.parent!=null)?ta.parent.hashCode():"")+"]");
                }
                log.LG(Logger.TRC,"Frontier "+(++k)+": "+sb.toString());
            }
            // copy parent
            TagAnnot node=(TagAnnot) frontier.remove(0); // already copied            
            TagAnnot par=(TagAnnot) node.parent; // to be copied
            if(par==null) {
                if(node!=containerCopy) {
                    Logger.LOG(Logger.ERR,"Failed inducing fmt pattern (TIC1): element "+node+" has no parent but is contained in "+container);
                }
                //Logger.LOG(Logger.TRC,"Repeated reference to "+containerCopy+"; from "+node);
                //containerCopy.appendChild(node);
                //node.parent=containerCopy;
                continue;
            }
            TagAnnot copy=orig2copy.get(par);
            if(copy==null) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Copying parent element "+par+par.hashCode()+" of child "+node+" as layout");
                copy=copyTagAsLayout(par);
                orig2copy.put(par, copy);
            }
            copy.appendChild(node);
            node.parent=copy;
            if(par==container) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Parent element was container, containerCopy="+containerCopy);
                if(containerCopy==null) {
                    containerCopy=copy;
                    containerCopy.parent=null;
                }
            }else {
                if(!frontier.contains(copy))
                    frontier.add(copy); // copy is to be connected to containerCopy (its parent must be overwritten by a copy)
            }
        }

        FmtLayout layout=new FmtLayout(containerCopy, firstTag);        
        return layout;
    }

    protected TagAnnot copyTagAsLayout(TagAnnot src) {
        // generalize pos in parent based on tag type
        int minParIdx=src.parentIdx;
        int maxParIdx=src.parentIdx;
        switch(src.type) {
        case HTMLElements.TR:
            if(src.parentIdx>0) { // treat all table rows the same except the first
                minParIdx=1;
                maxParIdx=Integer.MAX_VALUE;
            }
            break;
        }
        // orig parent will be replaced by copied when available
        TagAnnot copy=new TagAnnot(src.type, minParIdx, maxParIdx, -1, -1, src.parent, src.parentIdx);
        return copy;
    }

    public void getAttrNames(StringBuffer buff) {
        if(buff.length()>0)
            buff.append(' ');
        buff.append(ac.getAttribute().name);
    }

    public int getACCount() {
        return 1;
    }

    public int getACs(Collection<AC> acs, int filter) {
        int cnt=0;
        if((filter & ACS_MEMBERS)!=0) {
            acs.add(ac);
            cnt=1;
        }
        return cnt;
    }
    
    public AC getIntersection(Collection<AC> acSet, boolean getBest) {
        if(acSet.contains(ac)) {
            return ac;
        }
        return null;
    }
    
    public boolean recomputeScore(Set<AC> acSet) {
        if(acSet==null || acSet.contains(ac)) {
            setScore();
            return true;
        }
        return false;
    }
    
    @Override
    protected int consumeACs(Annotable[] ans, int startIdx) {
        if(ans[startIdx]!=ac) {
            return startIdx;
        }
        startIdx++;
        while(startIdx<ans.length && (! (ans[startIdx] instanceof AC)))
            startIdx++;
        return startIdx;
    }
    
    public int getCrossTagCount() {
        return 0;
    }
}
