// $Id: IC.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.Logger;
import ex.model.*;
import ex.ac.*;

public class IC extends ICBase {

    // references to 1st-level ACs, each may contain child ACs
    protected ACRef[] acs;
    // unique ids used as keys into a cache (trie) of already parsed ICs
    protected int[] ids;

    // ACs needed to become valid
    public ArrayList wanted;

    // int startIdx; // leftIdx of the leftmost AC
    // int endIdx; // rightIdx of the rightmost AC

    // ACs orphaned within the extend of this IC 
    // protected TreeSet<AC> orphans;

    // debugging only
    public String origin;

    protected IC(AC ac) {
        super();
        origin=Integer.toString(icid);
        acs=new ACRef[1];
        acs[0]=new ACRef(ac,null);
        clsDef=ac.getAttribute().myClass;
        setIds(1);
        setScore();
        //	startIdx=-1;
        //	endIdx=-1;
    }
    
    public void clear() {
        acs=null;
        ids=null;
        wanted=null;
        origin=null;
    }

    public void setScript() {
        if(contentScriptString==null) {
            StringBuffer cs=new StringBuffer(128); // maybe keep this for derived ICs so as not to compute again
            for(int i=0;i<acs.length;i++)
                acs[i].toScript(cs);
            contentScriptString=cs.toString();
        }
        contentScript=clsDef.model.context.compileString(contentScriptString,"IC"+hashCode(),0,null);
    }

    /** 
	Create a new IC from {IC+newAC}. 
	If newAC contains some ACs already present in IC,
	list of these ACs is given in containsList.
	If newAC is contained by some AC already present in IC,
	the index of such AC is given by containingAC.
     */
    public IC(IC base, AC ac, ArrayList<ACRef> containsList, int containingAC) {
        icid=++icidCnt;
        origin=base.origin+"."+icid;
        int toBePushed=(containsList!=null)? containsList.size(): 0;
        acs=new ACRef[base.acs.length-toBePushed+((containingAC==-1)? 1: 0)];
        ACRef acr=new ACRef(ac, null);
        if(toBePushed>0)
            acr.children=new ACRef[toBePushed];
        clsDef=ac.getAttribute().myClass;
        log=Logger.getLogger("IC");

        // keep new ACs sorted
        int c=ac.hashCode();
        int pushed=0;
        int idx=0;
        ACRef nextPushed=(pushed<toBePushed)? containsList.get(pushed): null;
        for(int j=0; j<base.acs.length; j++) {
            if(j==containingAC) {
                base.acs[j].addChild(acr);
            }else if(base.acs[j]==nextPushed) {
                acr.children[pushed]=base.acs[j];
                nextPushed=(++pushed<toBePushed)? containsList.get(pushed): null;
            }else {
                if(c!=0 && c<base.acs[j].ac.hashCode()) {
                    c=0;
                    acs[idx++]=acr;
                }
                acs[idx++]=base.acs[j];
            }
        }
        if(idx<acs.length)
            acs[idx]=acr;
        setIds(base.ids.length+1);

        // prepare script
        StringBuffer buff=new StringBuffer(base.contentScriptString.length()+64);
        buff.append(base.contentScriptString);
        ac.toScript(buff);
        contentScriptString=buff.toString();

        setScore();
        /* keep ACs sorted
	int j;
	for(j=0;j<base.acs.length;j++) {
		break;
	}
	System.arraycopy(base.acs, 0, acs, 0, j);
	acs[j]=ac;
	if(j<base.acs.length)
	    System.arraycopy(base.acs, j, acs, j+1, base.acs.length-j);
	ids=new int[acs.length];
	for(int i=0;i<acs.length;i++)
	    ids[i]=acs[i].hashCode();
         */
    }

    public double setScore() {
        double sum=0;
        for(int i=0;i<acs.length;i++) {
            // sum+=acs[i].ac.getProb(); // mean seems to be a bad idea
            sum-=java.lang.Math.log(acs[i].ac.getProb()); // log geometric mean seems better
        }
        score=java.lang.Math.exp(sum/acs.length);
        return score;
    }

    public int attCount() {
        return acs.length; // only returns top level ACs, does not include embedded
    }

    public void setIds(int cnt) {
        ids=new int[cnt];
        int pos=0;
        for(int i=0;i<acs.length;i++) {
            pos+=acs[i].setIds(ids,pos);
        }
        if(pos!=cnt)
            log.LOG(log.ERR,"Set "+pos+" ids in IC, expected "+cnt);
    }

    /**
       Returns the first AC in this IC corresponding to the specified AttributeDef, null otherwise.
     */
    public AC getACs(AttributeDef ad, List<AC> lst) {
        if(acs==null)
            return null;
        int cnt=0;
        for(int i=0;i<acs.length;i++) {
            cnt+=acs[i].getACs(ad, lst);
        }
        return null;
    }

    /** returns count of added ICs if AC can be added, otherwise negative IC_OVERLAP, IC_MAXCARD, IC_OTHERCLASS, IC_DUPL */
    public int tryAdd(AC ac, List<ICBase> newList) {
        if(log.IFLG(Logger.TRC)) log.LG(log.TRC,"Trying to add "+ac.toString());
        AttributeDef attDef=ac.getAttribute();
        // class ok?
        if(attDef.myClass!=clsDef)
            return IC_OTHERCLASS;
        // check maxCard, duplication, overlap of spans
        int card=0;
        ArrayList<ACRef> containedACs=null;
        int containingAC=-1;
        int cnt=acs.length;
        for(int i=0;i<cnt;i++) {
            AC myAC=acs[i].ac;
            // maxCard
            if(myAC.obj==attDef)
                card++;
            // duplication
            if(myAC==ac)
                return IC_DUPL;
            // overlap
            int rc=myAC.overlapsWith(ac);
            switch(rc) {
            case AC.OVL_NONE:
                break;
            case AC.OVL_CONTAINED_CAN:
                if(containedACs==null)
                    containedACs=new ArrayList<ACRef>(8);
                containedACs.add(acs[i]);
                break;
            case AC.OVL_CONTAINS_CAN: // ac has the same span as myAC, and myAC can contain ac 
                containingAC=i;
                break;
            case AC.OVL_CROSS:
            case AC.OVL_CONTAINED_CANNOT:
            case AC.OVL_CONTAINS_CANNOT:
                return IC_OVERLAP;

            default:
                log.LOG(log.ERR,"Unknown overlap rc="+rc+": "+myAC.toString()+" cannot contain "+ac.toString());
            return IC_OVERLAP;
            }
        }
        if(card>=attDef.maxCard)
            return IC_MAXCARD;
        // check whether {this IC+AC} will be valid supposing this IC is valid - 
        // only check class's axioms that apply to the AC being added
        if (!clsDef.canBecomeValid(this, ac))
            return IC_AXIOM_NOT_VALID;

        IC derivedIC=new IC(this,ac,containedACs,containingAC);
        derivedIC.setScript();
        // if(log.IFLG(Logger.TRC)) log.LG(log.TRC,"Derived new "+derivedIC.toString());
        newList.add(derivedIC);
        return 1;
    }

    protected void setCards(short[] cards) {
        for(int i=0;i<acs.length;i++) {
            AC ac=acs[i].ac;
            cards[ac.getAttribute().id]++;
        }
    }

    protected int checkCards(short[] cards) {
        for(int i=0;i<acs.length;i++) {
            AC ac=acs[i].ac;
            if(++cards[ac.getAttribute().id] > ac.getAttribute().maxCard)
                return IC_MAXCARD;
        }
        return IC_OK;
    }

    /** Checks if this IC satisfies relevant axioms defined on the class.
	If partial==true, only those axioms that apply to already populated attributes are evaluated,
	otherwise all axioms are examined */
    public boolean isValid(boolean partial, Parser parser) {
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Checking validity of IC: "+toString());
        // check maxCard, minCard, containment
        short[] cards=parser.cards[clsDef.id]; // new int[clsDef.attArray.length];
        Arrays.fill(cards, (short)0);
        // ArrayList[] containedACs=new ArrayList[clsDef.attArray.length];
        // FIXME containment cardinality
        for(int i=0;i<cards.length;i++) {
            if((!partial && (cards[i] < clsDef.attArray[i].minCard)) || (cards[i] > clsDef.attArray[i].maxCard)) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"IC not valid due to cardinality of "+clsDef.attArray[i].name+":\n"+toString());
                return false;
            }
        }
        // check all axioms of the model
        return clsDef.isValid(this, partial);
    }

    public int getStartIdx() {
        //	if(startIdx!=-1)
        //	    return startIdx;
        int startIdx=Integer.MAX_VALUE;
        for(int i=0;i<acs.length;i++) {
            int si=acs[i].ac.startToken.idx; // startIdx
            if(si<startIdx)
                startIdx=si;
        }
        return startIdx;
    }

    public int getEndIdx() {
        //	if(endIdx!=-1)
        //	    return endIdx;
        int endIdx=-1;
        for(int i=0;i<acs.length;i++) {
            int ei=acs[i].ac.startToken.idx + acs[i].ac.len - 1;
            if(ei>endIdx)
                endIdx=ei;
        }
        return endIdx;
    }

    public String getId() {
        return super.getId()+"."+origin+"-"+idsToString();
    }

    // debugging only
    private String idsToString() {
        StringBuffer b=new StringBuffer(64);
        for(int i=0;i<ids.length;i++) {
            if(i>0)
                b.append(".");
            b.append(ids[i]);
        }
        return b.toString();
    }

    public void attrsToString(int depth, StringBuffer buff) {
        if(acs==null)
            return;
        for(int i=0;i<acs.length;i++)
            acs[i].toString(buff, depth);
    }

    public String toTable() {
        StringBuffer buff=new StringBuffer(1024);
        buff.append("<tr><th colspan=\"2\">");
        if(clsDef==null)
            buff.append("Unknown");
        else
            buff.append(clsDef.name);
        buff.append(" ("+String.format("%.4f",score)+")");
        buff.append("</th></tr>\n");
        if(acs==null)
            return buff.toString();
        for(int i=0;i<acs.length;i++) {
            buff.append(acs[i].ac.toTableRow());
        }
        return buff.toString();
    }

    public boolean canBeAdded() {
        return false;
    }

    public AC getAC() {
        return (acs.length==1)? acs[0].ac: null;
    }

    public void setAC(AC ac) {
        log.LG(log.ERR,"setAC not implemented for IC");
    }

    public void attrsToTable(StringBuffer buff, boolean isReference) {
        log.LG(log.ERR,"attrsToTable not implemented for IC");
    }

    public int contentCode() {
        log.LG(log.ERR,"contentCode not implemented for IC");
        return hashCode();
    }

    @Override
    protected int consumeACs(Annotable[] ans, int startIdx) {
        log.LG(log.ERR,"consumeACs not implemented for IC");
        return 0;
    }
    
    public int getCrossTagCount() {
        return -1;
    }
}
