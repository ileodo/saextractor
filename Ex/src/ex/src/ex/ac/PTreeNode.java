// $Id: PTreeNode.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 *  Pattern parse tree node for the PatComp compiler of TokenPatterns
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

import uep.util.Logger;

import ex.train.KB;
import ex.reader.TokenAnnot;
import ex.train.TokenInfo;
import ex.reader.Tokenizer;
import ex.features.FM;

public class PTreeNode {
    public static final short LEAF=1;
    public static final short AND=2;
    public static final short OR=3;
    public static final short PATTERN=4; // has single token state, or single sub-pattern state
    public static final short VALUE=5; // placeholder $ for attribute's value, used for context patterns

    List<PTreeNode> children;
    public PTreeNode parent;
    public short type;
    public short minCnt;
    public short maxCnt;
    public boolean isORChild;
    public String content;
    public FA fa;
    
    public static Logger log=null;

    public PTreeNode(PTreeNode par, short t, short min, short max, String ct) {
        parent=par;
        type=t;
        minCnt=min;
        maxCnt=max;
        children=null;
        isORChild=false;
        content=ct;
        fa=new FA();
        if(log==null) {
            log=Logger.getLogger("PatComp");
        }
    }

    protected void copyFromCombineCard(PTreeNode node) {
        parent=node.parent;
        type=node.type;
        children=node.children; node.children=null;
        if(children!=null) {
            int len=children.size();
            for(int i=0;i<len;i++) {
                getChild(i).parent=this;
            }
        }
        content=node.content;
        isORChild=node.isORChild;
        // combine my card with redundant node's
        minCnt=(short) (minCnt*node.minCnt);
        if(maxCnt==-1 || node.maxCnt==-1)
            maxCnt=-1;
        else
            maxCnt=(short) (maxCnt*node.maxCnt);

        fa.startState=node.fa.startState;
        fa.finalState=node.fa.finalState;
    }

    public void addChild(PTreeNode child) {
        if(children==null)
            children=new ArrayList<PTreeNode>(8);
        children.add(child);
    }
    public PTreeNode getChild(int i) {
        if(children==null)
            return null;
        int len=children.size();
        if(len==0)
            return null;
        if(i==-1)
            i=len-1;
        return (PTreeNode) children.get(i);
    }
    public void setOR() {
        type=OR;
        if(children==null) { // happens only when the first option is empty (|A)
            children=new ArrayList<PTreeNode>(8);
            children.add(new PTreeNode(this,PTreeNode.LEAF,(short)1,(short)1,null)); // matches null string
        }else {
            int len=children.size();
            if(len>1) {
                PTreeNode extraNode=new PTreeNode(this,PTreeNode.AND,(short)1,(short)1,null);
                extraNode.children=this.children;
                this.children=null;
                this.addChild(extraNode);
            }
        }
    }
    public void collapseLastChild() {
        int myLen=this.children.size();
        PTreeNode lastChild=this.getChild(-1);
        if(lastChild.children==null) { // null orNode, e.g. (A|)
            lastChild.type=LEAF;
            return;
        }
        int len=lastChild.children.size();
        if(len==1) { // AND child has only 1 child - skip the AND child
            PTreeNode singleChild=lastChild.getChild(0);
            singleChild.isORChild=true;
            singleChild.parent=this;
            this.children.set(myLen-1,singleChild);
            lastChild=null;
        }
    }

    private void serializeCard(StringBuffer buff) {
        if(minCnt==1 && maxCnt==1)
            return;
        if(minCnt!=1 && minCnt==maxCnt) {
            buff.append("{");
            buff.append(minCnt);
            buff.append("}");
            return;
        }
        if(minCnt==0 && maxCnt==1) {
            buff.append("?");
            return;
        }
        if(minCnt==0 && maxCnt==-1) {
            buff.append("*");
            return;
        }
        if(minCnt==1 && maxCnt==-1) {
            buff.append("+");
            return;
        }
        buff.append("{");
        buff.append(minCnt);
        buff.append(",");
        buff.append(maxCnt);
        buff.append("}");
    }

    public void serialize(StringBuffer buff) {
        switch(type) {
        case LEAF:
            buff.append(content==null?"(null)":content);
            serializeCard(buff);
            return;
        case PATTERN:
            buff.append(content==null?"(sub)":content);
            serializeCard(buff);
            return;
        case VALUE:
            buff.append(content==null?"(val)":content);
            serializeCard(buff);
            return;
        }
        buff.append("(");
        if(children!=null) {
            int len=children.size();
            for(int i=0;i<len;i++) {
                if(i>0)
                    buff.append((type==OR)?"|":" ");
                PTreeNode child=getChild(i);
                child.serialize(buff);
            }
        }
        buff.append(")");
        serializeCard(buff);
    }
    
    public String toString() {
        StringBuffer b=new StringBuffer(64);
        serialize(b);
        return b.toString();
    }

    public void normalize() {
        switch(type) {
        case PTreeNode.AND:
        case PTreeNode.OR:
            // if I only have 1 child, put it instead of me - this can only happen for the current root node
            while(children.size()==1) {
                PTreeNode child=getChild(0);
                child.parent=this.parent;
                this.copyFromCombineCard(child); // moves all children and sets their parent to this
                child=null; // forget child
                switch(type) { // type may now be different; if not, try to continue this loop
                case PTreeNode.AND:
                case PTreeNode.OR:
                    break;
                default:
                    return;
                }
            }
            // replace each my child that only has 1 child, with that child
            // if not done, the above step may change children types of an already normalized parent
            int len=children.size();
            for(int i=0;i<len;i++) {
                PTreeNode child=getChild(i);
                while((child.type==PTreeNode.AND || child.type==PTreeNode.OR) && child.children.size()==1) {
                    PTreeNode grandChild=child.getChild(0);
                    grandChild.parent=this;
                    child.copyFromCombineCard(grandChild); // moves all children and sets their parent to this
                    grandChild=null; // forget grandChild
                }
            }
            // skip each child of the same type (OR, AND) as me and attach its children directly to us
            int i=0;
            while(i<children.size()) {
                PTreeNode child=getChild(i);
                // OR{1,1} is redundant under OR, AND{1,1} is redundant under AND
                if(child.type==type && child.minCnt==1 && child.maxCnt==1) { 
                    children.remove(i); // remove the AND child
                    int childLen=(child.children==null)? 0: child.children.size();
                    if(childLen>0) {
                        children.addAll(i,child.children);
                    }
                    continue; // i now points to the next child to be examined
                }
                i++;
            }
            break;
        default:
            return;
        }
        // recurse
        int len=children.size();
        for(int i=0;i<len;i++) {
            PTreeNode child=getChild(i);
            switch(child.type) {
            case PTreeNode.AND:
            case PTreeNode.OR:
                child.normalize();
                break;
            }
        }
    }
    
    protected void genStates(TokenPattern compiledPat, LinkedList<TokenPattern> patLst) throws TokenPatternSyntaxException {
        switch(type) {
        case AND: {
            int len=children.size();
            for(int i=0;i<len;i++) {
                PTreeNode child=getChild(i);
                child.genStates(compiledPat, patLst);
            }
            PTreeNode c1=getChild(0);
            PTreeNode c2=null;
            fa.startState=c1.fa.startState;
            for(int i=1;i<len;i++) {
                c2=getChild(i);
                /* check if we can merge null nodes between neighbors; we can do this 
                 * if the to-be-deleted state is null with no loop-back leading to it */
                // 1. final state eats the next start state
                if(c2.fa.startState.type==FAState.ST_NULL && c2.fa.startState.prev==null) {
                    c1.fa.finalState.eatNextState(c2.fa.startState,c2.fa.finalState);
                    c2.fa.startState=c1.fa.finalState;
                    c1=c2;
                    continue;
                }
                // 2. start state eats the previous final state
                if(c1.fa.finalState.type==FAState.ST_NULL && c1.fa.finalState.nextArcs==null) {
                    c2.fa.startState.eatPrevState(c1.fa.finalState,c1.fa.startState);
                    c1.fa.finalState=c2.fa.startState;
                    c1=c2;
                    continue;
                }
                c1.fa.finalState.addArcTo(c2.fa.startState);
                c1=c2;
            }
            fa.finalState=c1.fa.finalState;
            break;
        }
        case OR: {
            fa.startState=new FAState(FAState.ST_NULL);
            fa.finalState=new FAState(FAState.ST_NULL);
            int len=children.size();
            for(int i=0;i<len;i++) {
                PTreeNode child=getChild(i);
                child.genStates(compiledPat, patLst);
                fa.startState.addArcTo(child.fa.startState);
                child.fa.finalState.addArcTo(fa.finalState);
            }
            break;
        }
        case LEAF:
            if(fa.startState!=null) // already set in setTokenFeatures(all tokens) or in readModel(FATokenOrStates)
                break;
            // startState=finalState=new FAState(FAState.ST_TOKEN, content);
            // break;
            throw new TokenPatternSyntaxException("Internal error generating FA; token="+content);
            
        case PATTERN:
            break; // subpatterns already compiled, startState and finalState have been populated
        case VALUE:
            break; // already done as part of setTokenFeatures()
        }

        boolean generateSingleFAWithCardBug = false;
        
        if(!generateSingleFAWithCardBug) {
            // if we need to track transition pass number and the inner content may contain number limits itself,
            // we must embed the inner content in a standalone state
            if((type==AND || type==OR) && (minCnt>1 || maxCnt>1)) {
                String bordel="(fa)";
                // TODO: src only needed for debugging, remove
                String src=log.IFLG(Logger.MML)? this.toString(): bordel;
                TokenPattern subPat=new TokenPattern(bordel, src, TokenPattern.PAT_GEN, null, 
                        compiledPat.matchFlags, compiledPat.forceCaseValues, compiledPat.forceCaseArea);
                // TODO: testing whether this is needed by the patmatcher, if yes, fix patmatcher
                this.fa.makeStartFinalNull();
                subPat.fa=this.fa;
                FAPatternState subState=new FAPatternState(subPat, FAPatternState.PS_GEN, src);
                this.fa=new FA(subState,subState);
                if(log.IFLG(Logger.TRC)) {
                    String data=subPat.fa.toString();
                    String id=PatComp.faCnt+"_"+(++PatComp.faSubCnt);
                    log.LG(Logger.TRC,"Generated FA_"+id+":\n"+data);
                    if(true) {
                        log.LGX(Logger.TRC, data, id+".dot");
                    }
                }
            }
            
            // add skip-arc (need null start and final states, may need to add them)
            boolean addSkipArc=false;
            if(minCnt==0) {
                // need extra connecting state even before initial null state when any arcs lead back to it
                if(fa.startState.type!=FAState.ST_NULL || fa.startState.prev!=null) {
                    FAState tmp=fa.startState;
                    fa.startState=new FAState(FAState.ST_NULL);
                    fa.startState.addArcTo(tmp);
                }
                if(fa.finalState.type!=FAState.ST_NULL) {
                    FAState tmp=fa.finalState;
                    fa.finalState=new FAState(FAState.ST_NULL);
                    tmp.addArcTo(fa.finalState);
                }
                // remember to add the skip arc between appropriate states below
                addSkipArc=true;
            }
            
            // add self-loop
            if(maxCnt>1 || maxCnt==-1) {
                if(minCnt>0) { // simple self-loop, covers both the case where startState==finalState and where not
                    fa.finalState.addArcToFirst(fa.startState); // ensure loop arc is first
                    fa.finalState.minCnt=(short)((minCnt==0)?0: (minCnt-1));
                    fa.finalState.maxCnt=(short)((maxCnt==-1)?Short.MAX_VALUE: (maxCnt-1));
                }else { // minCnt==0
                    if(type==AND || type==OR) { // always true: && fa.startState.type==FAState.ST_NULL) {
                        // self loop via extra state to prevent null cycle:
                        FAState repState=fa.finalState;
                        repState.minCnt=(short)((minCnt==0)?0: (minCnt-1));
                        repState.maxCnt=(short)((maxCnt==-1)?Short.MAX_VALUE: (maxCnt-1));
                        // repState.addArcTo(fa.startState); // first is the loop arc
                        repState.addArcToFollowersOfFirst(fa.startState); // first is the loop arc
                        fa.finalState=new FAState(FAState.ST_NULL);
                        repState.addArcTo(fa.finalState);
                        // add the skip arc here:
                        addSkipArc=false;
                        fa.startState.addArcTo(fa.finalState);
                    }else { // LEAF, PATTERN, VALUE (single state) - simple self loop is ok:
                        // FAState repState=fa.startState.next[0];
                        FAState repState=fa.startState.nextArcs.first();
                        repState.addArcToFirst(repState); // ensure loop arc is first
                        repState.minCnt=(short)((minCnt==0)?0: (minCnt-1));
                        repState.maxCnt=(short)((maxCnt==-1)?Short.MAX_VALUE: (maxCnt-1));
                    }
                }
            }
            
            if(addSkipArc) {
                fa.startState.addArcTo(fa.finalState);
            }

        }else {
            // FIXME: pretend we are minCnt==0:
            int realMinCnt=minCnt;
            if(type==AND || type==OR)
                minCnt=0;

            // add skip-arc (need null start and final state, may need to add them)
            if(minCnt==0) {
                // need extra connecting state even before initial null state when any arcs lead back to it
                if(fa.startState.type!=FAState.ST_NULL || fa.startState.prev!=null) {
                    FAState tmp=fa.startState;
                    fa.startState=new FAState(FAState.ST_NULL);
                    fa.startState.addArcTo(tmp);
                }
                if(fa.finalState.type!=FAState.ST_NULL) {
                    FAState tmp=fa.finalState;
                    fa.finalState=new FAState(FAState.ST_NULL);
                    tmp.addArcTo(fa.finalState);
                }
                // otherwise final state will become extra loop state, wait to add skip arc to the real final node:
                if(realMinCnt==0) {
                    if((maxCnt<=1 && maxCnt!=-1) || type==LEAF) 
                        fa.startState.addArcTo(fa.finalState);
                }
            }

            // add self-loop
            if(maxCnt>1 || maxCnt==-1) {
                //Logger.getLoggerInstance().LGERR("yes");
                if(minCnt>0) { // simple self-loop, covers both the case where startState==finalState and where not
                    fa.finalState.addArcToFirst(fa.startState); // ensure loop arc is first
                    fa.finalState.minCnt=(short)((minCnt==0)?0: (minCnt-1));
                    fa.finalState.maxCnt=(short)((maxCnt==-1)?Short.MAX_VALUE: (maxCnt-1));
                }else { // minCnt==0
                    if(type==LEAF) { // simple self loop is ok
                        // FAState repState=fa.startState.next[0];
                        FAState repState=fa.startState.nextArcs.first();
                        repState.addArcToFirst(repState); // ensure loop arc is first
                        repState.minCnt=(short)((minCnt==0)?0: (minCnt-1));
                        repState.maxCnt=(short)((maxCnt==-1)?Short.MAX_VALUE: (maxCnt-1));
                    }else { // self loop via extra state
                        FAState repState=fa.finalState;
                        repState.minCnt=(short)((minCnt==0)?0: (minCnt-1));
                        repState.maxCnt=(short)((maxCnt==-1)?Short.MAX_VALUE: (maxCnt-1));
                        repState.addArcTo(fa.startState); // first is the loop arc
                        fa.finalState=new FAState(FAState.ST_NULL);
                        repState.addArcTo(fa.finalState);
                        // add the skip arc here:
                        if(realMinCnt==0)
                            fa.startState.addArcTo(fa.finalState);
                    }
                }
            }
        }
    }

    public void setTokenFeatures(KB kb, int matchFlags, Tokenizer tok) throws TokenPatternSyntaxException {
        if(type==LEAF && content==null)
            throw new TokenPatternSyntaxException("Leaf with no content");
        if(type==LEAF && !content.equals("\1")) {
            tok.setInput(content);
            TokenAnnot ta=tok.next();
            if(ta==null)
                throw new TokenPatternSyntaxException("Error tokenizing '"+content+"': no tokens");
            ta.setFeatures(kb.vocab);
            //int val=ta.ti.intVals.get(fid);
            //startState=finalState=new FATokenState(new int[]{fid}, new int[]{val}, ta.token);
            fa.startState=fa.finalState=createTokenState(matchFlags, ta.ti);
            // is there a next token (document tokenizer splits into more tokens than pattern tokenizer - solve this better)
            TokenAnnot ta2=tok.next();
            if(ta2==null)
                return;
            Logger log=Logger.getLogger("PTreeNode");
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"PTreeNode: tokenizing '"+content+"': found multiple tokens in node type="+type+", splitting");
            // new child
            PTreeNode leaf=new PTreeNode(this, LEAF, (short)1, (short)1, ta.token);
            leaf.fa.startState=leaf.fa.finalState=fa.startState;
            addChild(leaf);
            // remove leaf values from here
            type=AND;
            content=null; // don't keep glued tokens
            fa.startState=fa.finalState=null;
            while(ta2!=null) {
                leaf=new PTreeNode(this, LEAF, (short)1, (short)1, ta2.token);
                ta2.setFeatures(kb.vocab);
                //val=ta2.ti.intVals.get(fid);
                //leaf.startState=leaf.finalState=new FATokenState(new int[]{fid}, new int[]{val}, ta2.token);
                leaf.fa.startState=leaf.fa.finalState=createTokenState(matchFlags, ta2.ti);
                addChild(leaf);
                ta2=tok.next();
            }
            return;
        }
        if(children!=null) {
            int len=children.size();
            for(int i=0;i<len;i++) {
                PTreeNode child=getChild(i);
                child.setTokenFeatures(kb, matchFlags, tok);
            }
        }
    }

    public FAState createTokenState(int matchFlags, TokenInfo ti) {
        int tokenId=ti.getTokenIdToMatch(matchFlags);
        return new FASimpleTokenState(tokenId);
    }
    
//    public FAState createTokenStateOld(int[] fids, TokenInfo ti) {
//        if(fids==null || fids.length==0) {
//            Logger.LOG(Logger.ERR,"PTreeNode: Error creating TokenState, no features specified");
//            return null;
//        }
//        FAState st=null;
//        int setCnt=0;
//        for(int i=0;i<fids.length;i++) {
//            int val=ti.intVals.get(fids[i]);
//            // make lemma ID the same as my ID if missing, same for LC form
//            if(val==-1) {
//                switch(fids[i]) {
//                case FM.TOKEN_LC:
//                case FM.TOKEN_LEMMA:
//                    val=ti.intVals.get(FM.TOKEN_ID);
//                    break;
//                }
//            }
//            if(val!=-1)
//                setCnt++;
//        }
//        if(setCnt==0) {
//            Logger.LOG(Logger.ERR,"PTreeNode: Error creating TokenState, no features set of total "+fids.length+" for "+ti);
//            return null;
//        }
//        int[] vals=new int[setCnt];
//        int[] fids2=null;
//        if(setCnt<fids.length)
//            fids2=new int[setCnt];
//        setCnt=0;
//        for(int i=0;i<fids.length;i++) {
//            int val=ti.intVals.get(fids[i]);
//            // make lemma ID the same as my ID if missing, same for LC form
//            if(val==-1) {
//                switch(fids[i]) {
//                case FM.TOKEN_LC:
//                case FM.TOKEN_LEMMA:
//                    val=ti.intVals.get(FM.TOKEN_ID);
//                    break;
//                }
//            }
//            if(val!=-1) {
//                vals[setCnt]=val;
//                if(fids2!=null)
//                    fids2[setCnt]=fids[i];
//                setCnt++;
//            }
//        }
//        if(fids2==null) {
//            fids2=fids; // reuse fids in multiple states in this pattern
//        }
//        if(setCnt==1) {
//            st=new FATokenState(fids2, vals, ti.token);
//        }else {
//            // feature OR in addition to individual feature value OR
//            boolean featOr=true;
//            st=new FATokenOrState(fids2, vals, null, featOr, ti.token);
//        }
//        return st;
//    }
}
