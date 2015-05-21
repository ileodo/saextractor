// $Id: FAState.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * Base class for State of an FA (compiled pattern)
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import ex.reader.TokenAnnot;
import uep.util.Logger;

public class FAState implements Comparable<FAState> {
    public static final short ST_TOKEN=3;
    public static final short ST_TRIE=4;
    public static final short ST_PATTERN=5;
    public static final short ST_NULL=6;
    public static final short ST_AC=7;
    public static final short ST_IC=8;
    public static final short ST_TAG=9;
    public static final short ST_PHRASE=10;
    public static int nextId=0;

    public short type;
    // public FAState[] next;

    public FAState[] prev; // only needed for "minimization" can be deleted after compilation

    public short minCnt; // min and max number of visits to this state
    public short maxCnt;
    
    public boolean neg; // match is to be interpreted as not match and vice versa

    public int id; // debugging, sorting
    public Object data;  // general purpose for debugging
    
    //public SortedSet<FASimpleTokenState> nextTokens;
    NextArcs nextArcs;

//    public FAState() {
//        this(ST_TOKEN);
//    }

    public FAState(short tp) {
        this(tp, null);
    }

    public FAState(short tp, Object d) {
        id=getNextId();
        type=tp;
//        next=null;
        prev=null;
        minCnt=-2;
        maxCnt=-2;
        neg=false;
        data=d;
        nextArcs=null;
    }

    private synchronized int getNextId() {
        return ++nextId;
    }

    public int compareTo(FAState o) {
        if(id<o.id)
            return -1;
        else if(id>o.id)
            return 1;
        return 0;
    }

    /**
     * Accepts next TokenAnnots, starting at t.tokens[offset], 
     * inserting a new TNode into Trellis for every accepted TokenAnnot.
     * to be overriden by subclasses. Returns the number of distinct matches.
     */
    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        switch(type) {
        case ST_NULL:
            // TNode node=new TNode(this,prev,0);
            TNode node=PatMatcher.newNode(this, prev, 0);
//            if(prev.state.type==ST_NULL) {
//                if(!node.setPrevNode(prev)) { // null cycle detected
//                    if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"Null cycle detected");
//                    return 0;
//                }
//            }
//            if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"Null matched");
            newNodes.add(node);
            return 1;
        default:
            PatMatcher.log.LG(Logger.ERR,"FAState is not ST_NULL!");
        }
        return -1;
    }

    /** Adds s as the next state and also updates s to link to this previous node. */
    public void addArcTo(FAState s) {
        if(addArcToInternal(s)) {
            s.addArcFromInternal(this);
        }
//        boolean rc=addArcToInternal(s);
//        if(rc)
//            s.addArcFromInternal(this);
//        if(!rc)
//            PatMatcher.log.LG(Logger.ERR,"VERY WRONG ERROR!!");
//        return rc;
    }
    
    /* Behaves exactly like addArcTo(). In addition, registers the new arc as a loopback. */
    public void addArcToFirst(FAState s) {
        addArcTo(s);
        if(! (nextArcs instanceof NextArcsFull)) { 
            nextArcs=new NextArcsFull(nextArcs);
        }
        nextArcs.setLoopArc(s);
//        if(!addArcTo(s))
//            return false;
//        // make the new arc first:
//        FAState temp=next[0];
//        next[0]=next[next.length-1];
//        next[next.length-1]=temp;
//        return true;
    }

    public void addArcToFollowersOfFirst(FAState s) {
        if(s.nextArcs==null)
            return;

        int len=s.nextArcs.size();
        if(len==1) {
            addArcToFirst(s.nextArcs.first());
      
        }else if(len>1) {
            // insert extra null state so we can link only to that single state 
            // to maintain the 1st loopback arc:
            FAState sextra=new FAState(FAState.ST_NULL);
            addArcToFirst(sextra);
//          suboptimal (extra arcs):
//          for(FAState fol: s.next) {
//          sextra.addArcTo(fol);
//          }
//          better:
            // reconnect graph ahead of sextra:
            sextra.nextArcs=s.nextArcs;
            // repair backlinks of the original followers of s (s -> sextra)
            for(FAState s1: sextra.nextArcs) {
                for(int i=0;i<s1.prev.length;i++) {
                    if(s1.prev[i]==s) {
                        s1.prev[i]=sextra;
                        break;
                    }
                }
            }
            // reconnect graph before sextra:
            s.nextArcs=null;
            s.addArcTo(sextra);
        }
    }
    
    /** Adds s as the next state. Does not update previous nodes structure of s. */
    private boolean addArcToInternal(FAState s) {
        boolean isFAST=s instanceof FASimpleTokenState;
        if(isFAST && !(nextArcs instanceof NextArcsFull)) {
            nextArcs=new NextArcsFull(nextArcs);
        }else if(nextArcs==null) {
            nextArcs=new NextArcsArray();
        }
        return nextArcs.addArcTo(s);
//        if(next==null) {
//            next=new FAState[1];
//            next[0]=s;
//            return true;
//        }
//        for(int i=0;i<next.length;i++)
//            if(next[i]==s)
//                return false;
//        FAState[] old=next;
//        next=new FAState[old.length+1];
//        System.arraycopy(old,0,next,0,old.length);
//        next[next.length-1]=s;
//        return true;
    }

    /** Adds s as the previous state. Does not update next nodes structure of s. */
    private boolean addArcFromInternal(FAState s) {
        if(prev==null) {
            prev=new FAState[1];
            prev[0]=s;
            return true;
        }
        for(int i=0;i<prev.length;i++)
            if(prev[i]==s)
                return false;
        FAState[] old=prev;
        prev=new FAState[old.length+1];
        System.arraycopy(old,0,prev,0,old.length);
        prev[prev.length-1]=s;
        return true;
    }

    public void eatPrevState(FAState nullState, FAState startSt) {
        if(nullState.prev==null)
            return;
        // move nullState's incoming arcs here
        for(int i=0;i<nullState.prev.length;i++) {
            FAState pr=nullState.prev[i];
            this.addArcFromInternal(pr);
            // fix next[] in my new prev nodes
            boolean upgrade=(!(pr.nextArcs instanceof NextArcsFull) && (this instanceof FASimpleTokenState));
            FAState[] nextStates=pr.nextArcs.getNextStates();
            for(int j=0;j<nextStates.length;j++) {
                if(nextStates[j]==nullState) {
                    if(upgrade) {
                        // delete the old one from array
                        FAState[] old=nextStates;
                        nextStates=new FAState[old.length-1];
                        if(j>0)
                            System.arraycopy(old, 0, nextStates, 0, j);
                        if(j!=nextStates.length-1)
                            System.arraycopy(old, j+1, nextStates, j, old.length-j-1);
                        // add the new one to map
                        pr.nextArcs=new NextArcsFull(nextStates, pr.nextArcs.getLoopArc());
                        pr.nextArcs.addArcTo(this);
                    }else {
                        nextStates[j]=this;
                    }
                    break;
                }
            }
            // copy nullState's outgoing arcs (back loops) backward to this pr state
            if(pr!=startSt && nullState.nextArcs!=null) {
                for(FAState nx: nullState.nextArcs) {
                    pr.addArcTo(nx);
                }
            }
        }
    }

    public void eatNextState(FAState nullState, FAState finalSt) {
        if(nullState.nextArcs==null)
            return;
        // first, update prev in all states pointed to by nullState
        for(FAState nx: nullState.nextArcs) {
            // find the link and redirect
            for(int j=0;j<nx.prev.length;j++) {
                if(nx.prev[j]==nullState) {
                    nx.prev[j]=this;
                    break;
                }
            }
            // copy nullState's incoming arcs (back loops) forward to this nx state
            if(nx!=finalSt && nullState.prev!=null) {
                for(int j=0;j<nullState.prev.length;j++) {
                    FAState pr=nullState.prev[j];
                    pr.addArcTo(nx);
                }
            }
        }
        // move nullState's outgoing arcs here
        // int len2=nullState.next.length;
        // int oldLen=0;
        if(nextArcs==null) {
            //next=new FAState[len2];
            nextArcs=nullState.nextArcs; // steal nextArcs from the deleted state
        }else {
            //FAState[] old=next;
            //oldLen=old.length;
            //next=new FAState[oldLen+len2];
            //System.arraycopy(old,0,next,0,oldLen);
            boolean isArray=!(nextArcs instanceof NextArcsFull);
            for(FAState s: nullState.nextArcs) {
                if(isArray && (s instanceof FASimpleTokenState)) {
                    nextArcs=new NextArcsFull(nextArcs);
                    isArray=false;
                }
                nextArcs.addArcTo(s);
            }
        }
        // System.arraycopy(nullState.next,0,next,oldLen,len2);
    }

    public void disposePrev(Set<FAState> visited) {
        visited.add(this);
        prev=null;
        for(FAState s: nextArcs) {
            if(!visited.contains(s)) {
                s.disposePrev(visited);
            }
        }
    }

    public void toString(StringBuffer s, StringBuffer t, Set<FAState> visited) {
        visited.add(this);
        String cntInfo="";
        if(minCnt!=-2) {
            if(minCnt==maxCnt) {
                cntInfo=" ("+minCnt+")";
            }else if(maxCnt==-1) {
                cntInfo=" ("+minCnt+",*)";
            }else {
                cntInfo=" ("+minCnt+","+maxCnt+")";
            }
        }
        s.append(" s"+id+" [label=\""+id+"."+(neg?"neg:":"")+data+cntInfo+"\"];\n");
        if(nextArcs==null)
            return;
        t.append(" s"+id+" -> {");
        for(FAState st: nextArcs) {
            t.append(" s");
            t.append(st.id);
        }
        t.append(" };\n");
        // recurse
        for(FAState st: nextArcs) {
            if(!visited.contains(st)) {
                st.toString(s,t,visited);
            }
        }
    }
    
    public String toString() {
        return getClass().getName()+".s"+id+"("+(neg?"neg:":"")+data+")";
    }
}
