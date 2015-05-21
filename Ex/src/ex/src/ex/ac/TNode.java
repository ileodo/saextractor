// $Id: TNode.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import uep.util.Logger;

/*
class TConstraints {
    public FAState[] state;
    public int[] loopCnt;
    public int size;
    public TConstraints(int cap) {
        size=0;
        state=new FAState[cap];
        loopCnt=new int[cap];
    }
    public TConstraints(TConstraints orig) {
        size=orig.size;
        state=(FAState[])orig.state.clone();
        loopCnt=(int[])orig.loopCnt.clone();
    }
    public void add(FAState s, int cnt) {
        if(size==state.length) {
            FAState[] oldState=state;
            int[] oldCnt=loopCnt;
            int cap=size*2;
            state=new FAState[cap];
            loopCnt=new int[cap];
            System.arraycopy(oldState,0,state,0,oldState.length);
            System.arraycopy(oldCnt,0,loopCnt,0,oldCnt.length);
        }
        state[size]=s;
        loopCnt[size]=cnt;
        size++;
    }
    public int remove(FAState s) {
        int rc=-1;
        int i=size-1;
        while(i>=0) {
            if(state[i]==s) {
                state[i]=null;
                loopCnt[i]=0;
                size--;
                if(i==size) {
                    rc=0; 
                }else {
                    rc=i; // the removed state should be the last one inserted but ...
                    while(i<size) {
                        state[i]=state[i+1];
                        loopCnt[i]=loopCnt[i+1];
                        i++;
                    }
                    i=rc;
                    rc=-2;
                }
                break;
            }
            i--;
        }
        if(rc!=0) {
            Logger.LOG(Logger.INF,"Failed to remove state "+s+" from constraints: rc="+rc+"; i="+i+" size="+size);
        }
        return rc;
    }
    public int inc(FAState s) {
        for(int i=0;i<size;i++)
            if(state[i]==s)
                return ++loopCnt[i];
        return -1;
    }
    public int get(FAState s) {
        for(int i=0;i<size;i++)
            if(state[i]==s)
                return loopCnt[i];
        return -1;
    }
    public boolean equals(Object o) {
        TConstraints c=(TConstraints) o;
        if(state.length!=c.state.length)
            return false;
        for(int i=0;i<size;i++) {
            if(state[i]!=c.state[i])
                return false;
            if(loopCnt[i]!=c.loopCnt[i])
                return false;
        }
        return true;
    }
}
*/

//class TAnnots {
//    public Annotable[] annotations;
//    public TAnnots(TAnnots oldAnnots, Annotable newAnnot) {
//        if(oldAnnots==null) {
//            annotations=new Annotable[1];
//            annotations[0]=newAnnot;
//        }else {
//            int len=oldAnnots.annotations.length;
//            annotations=new Annotable[len+1];
//            System.arraycopy(oldAnnots.annotations,0,annotations,0,len);
//            annotations[len]=newAnnot;
//        }
//    }
//}

class TAnnots {
    public Annotable annot;
    public TAnnots prev;
    public TAnnots(TAnnots oldAnnots, Annotable newAnnot) {
        prev=oldAnnots;
        annot=newAnnot;
    }
    public String toString() {
        StringBuffer b=new StringBuffer(32);
        b.append(annot);
        TAnnots ans=prev;
        while(ans!=null) {
            b.append(' ');
            b.append(ans.annot);
            ans=ans.prev;
        }
        return b.toString();
    }
}

/*
 * We could use this instead of int TNode.loopBackCnt; in this way we would not
 * need to generate FAPatternStates for submachines with {m,n}. But it may not be 
 * a good idea as there would be a new LoopCount() instance with every loop transition.
 * Currently we only increment a counter and rely on the assumption that there are no 
 * embedded counters in a single FA.
class LoopCount {
    public int cnt;
    public LoopCount prev;
    public LoopCount(int cnt, LoopCount prev) {
        this.cnt=0;
        this.prev=prev;
    }
}
*/

class TNode {
    public FAState state;   // corresponding FAState in the compiled pattern
    public int pathLen;     // length of the partial match in tokens
    // public TConstraints constraints;
    public int loopBackCnt; // how many times the loopBack arc of this.state was already taken
    // LoopCount loopBackCnt;
    public TAnnots annots;
    // public TNode prevNode;

    /*    
    public TNode(FAState st,int len,TConstraints con) {
    state=st;
    pathLen=len;
    constraints=null;
    annots=null;
    prevNode=null; // only used when this and the previous node are both null, to enable null cycle detection
    constraints=(con==null)? null: new TConstraints(con);
    }
     */
    
    public static long dbgCnt=0;
    
    public TNode(FAState st, TNode prev, int advance) {
        set(st, prev, advance);
//        state=st;
//        loopBackCnt=(prev!=null)? prev.loopBackCnt: 0;
//        if(prev==null) {
//            pathLen=advance;
//            // constraints=null;
//            annots=null;
//        }else {
//            pathLen=prev.pathLen+advance;
//            // constraints=(prev.constraints==null)? null: new TConstraints(prev.constraints);
//            // CANCELLED: do not 'inherit' previous nodes' annots so we can tell later the match positions for each Annotable
//            // annots=null;
//            // YES: 'inherit' previous node's annots and do not link to it so it
//            // can be garbage collected. Then we collect all annots from finalNode only,
//            // no backtracking over the tree.
//            annots=prev.annots;
//        }
        // prevNode=null; // only used when this and the previous node are both null, to enable null cycle detection
        if(PatMatcher.log.IFLG(Logger.MML)) {
            PatMatcher.log.LG(Logger.MML,"TNode "+this);
            if(++dbgCnt % 100 == 0) {
                Logger.LOGERR("TNode n="+dbgCnt);
                Logger.pause("TNode n="+dbgCnt);
            }
        }
    }
    
    public void set(FAState st, TNode prev, int advance) {
        state=st;
        if(prev==null) {
            pathLen=advance;
            loopBackCnt=0;
            annots=null;
        }else {
            pathLen=prev.pathLen+advance;
            loopBackCnt=prev.loopBackCnt;
            annots=prev.annots;
        }
    }

    public void clear() {
        state=null;
        pathLen=0;
        loopBackCnt=0;
        annots=null;
    }
    
    public String toString() {
        return state+"["+pathLen+"]L"+loopBackCnt+((annots!=null)? annots:"");
    }
    
//    public boolean setPrevNode(TNode prev) {
//        if(PatMatcher.log.IFLG(Logger.TRC)) {
//            TNode pn=prev;
//            while(pn!=null) {
//                if(this.equals(pn))
//                    return false; // null cycle detected; complain
//                pn=pn.prevNode;
//            }
//        }
//        prevNode=prev;
//        return true;
//    }
    
    public boolean equals(Object o) {
        TNode node=(TNode) o;
        return (state==node.state && loopBackCnt==node.loopBackCnt);
//        // verify - this check may be too strict
//        if(constraints==null)
//            return (node.constraints==null)? true: false;
//        if(node.constraints==null)
//            return false;
//        return constraints.equals(node.constraints);
    }
}
