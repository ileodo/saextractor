// $Id: NextArcs.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import uep.util.Logger;

public interface NextArcs extends Iterable<FAState> {
    /** Returns the next SimpleTokenState that exactly matches tokenId, or null. */
    public FASimpleTokenState findState(FASimpleTokenState query);
    /** Returns all next states of types other than SimpleTokenState, or null. */
    public FAState[] getNextStates();
    /** Behaves like getNextStates but also clears next states. */
    public FAState[] removeNextStates();
    /** Returns the loop back arc, if any. */
    public FAState getLoopArc();
    /** Adds the state to the group of next states. */
    public boolean addArcTo(FAState s);
    /** Adds the state to the array part of the group of next states. */
    public boolean addArcToSlow(FAState s);
    /** Returns an iterator over all next states. */
    public Iterator<FAState> iterator();
    /** Marks the arc between current state and s as loopback. */
    public void setLoopArc(FAState s);
    /** Returns the number of next states. */
    public int size();
    /** Returns the first token or throws NoSuchElementException. */ 
    public FAState first();
    /** Returns the count of simple token states among the next states */
    public int tokenStateCount();
    /** Finds the specified state among the next states and replaces it by st2. */
    public boolean changeNextState(FAState st1, FAState st2);
}

class NextArcsArray implements NextArcs {
    protected static final FAState[] emptyStates=new FAState[0];
    FAState[] next;

    public NextArcsArray() {
        this.next=emptyStates;
    }
    public NextArcsArray(FAState[] next) {
        this.next=(next!=null)? next: emptyStates;
    }
    public FAState[] getNextStates() { return next; }
    public FAState[] removeNextStates() { FAState[] tmp=next; next=emptyStates; return tmp; }
    public FASimpleTokenState findState(FASimpleTokenState query) { return null; }
    public FAState getLoopArc() { return null; }
    public boolean addArcTo(FAState s) {
        return addArcInternal(s, true);
    }
    public boolean addArcToSlow(FAState s) {
        return addArcInternal(s, false);
    }
    private boolean addArcInternal(FAState s, boolean strict) {
        if(strict && (s instanceof FASimpleTokenState)) {
            throw new IllegalArgumentException(s+" being added to "+this);
        }
        if(next==null) {
            next=new FAState[1];
            next[0]=s;
        }else {
            if(Logger.IFLOG(Logger.TRC)) {
                for(int i=0;i<next.length;i++) {
                    if(next[i]==s) {
                        Logger.LOG(Logger.ERR, "addArcTo("+s+") state exists!");
                    }
                }
            }
            FAState[] old=next;
            next=new FAState[old.length+1];
            if(old.length>0)
                System.arraycopy(old,0,next,0,old.length);
            next[next.length-1]=s;
        }
        return true;
    }
    public void setLoopArc(FAState s) {
        throw new IllegalArgumentException("NextArcsArray cannot handle loopback arcs.");
    }
    public int size() {
        return next.length;
    }
    public FAState first() {
        if(next.length==0)
            throw new NoSuchElementException();
        return next[0];
    }
    public int tokenStateCount() {
        return 0;
    }
    public void toString(StringBuffer s) {
        s.append("[");
        int i=0;
        for(FAState st: next) {
            if(i++>0)
                s.append(",");
            s.append(st.data);
        }
        s.append("]");
    }
    public String toString() {
        StringBuffer s=new StringBuffer(1024);
        toString(s);
        return s.toString();
    }
    public Iterator<FAState> iterator() {
        return new NSIt1();
    }
    private class NSIt1 implements Iterator<FAState> {
        int p=0;
        public boolean hasNext() {
            return p<next.length;
        }
        public FAState next() {
            return next[p++];
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    public boolean changeNextState(FAState st1, FAState st2) {
        boolean rc=false;
        for(int i=0;i<next.length;i++) {
            if(next[i]==st1) {
                next[i]=st2;
                rc=true;
                break;
            }
        }
        return rc;
    }
}

class NextArcsFull extends NextArcsArray {
    SortedMap<FASimpleTokenState,FASimpleTokenState> nextTokens;
    FAState loopBack;
    
    NextArcsFull(NextArcs arcs) {
        if(arcs!=null) {
            this.next=arcs.getNextStates();
            this.loopBack=arcs.getLoopArc();
            if(arcs instanceof NextArcsFull) {
                throw new IllegalArgumentException("NextArcsFull");
            }
        }else {
            this.next=emptyStates;
            this.loopBack=null;
        }
        nextTokens=null;
    }
    NextArcsFull(FAState[] next, FAState loopBack) {
        this.next=(next!=null)? next: emptyStates;
        this.loopBack=loopBack;
        nextTokens=null;
    }
    public FASimpleTokenState findState(FASimpleTokenState query) {
        if(Logger.IFLOG(Logger.MML)) {
            StringBuffer s=new StringBuffer(128);
            s.append("> ");
            s.append(query.tokenId);
            s.append(" ");
            toString(s);
            s.append("=");
            FASimpleTokenState st=nextTokens.get(query);
            s.append(st);
            Logger.LOG(Logger.MML, s.toString());
            return st;
        }
        return nextTokens.get(query); 
    }
    public FAState getLoopArc() { return loopBack; }
    public void setLoopArc(FAState s) {
        this.loopBack=s;
    }
    public boolean addArcTo(FAState s) {
        boolean rc=true;
        if(s instanceof FASimpleTokenState) {
            if(nextTokens==null)
                nextTokens=new TreeMap<FASimpleTokenState,FASimpleTokenState>(FASimpleTokenState.comparator);
            FASimpleTokenState st=(FASimpleTokenState) s;
            // find if a state with the same token already follows
            FASimpleTokenState ex=nextTokens.get(st);
            if(ex!=null) {
                if(ex!=st) { // if it is a different state, we have to merge them
                    // check if the states are really equivalent
                    boolean equiv=false;
                    if(ex.maxCnt==st.maxCnt && ex.minCnt==st.minCnt && ex.neg==st.neg) {
                        // FIXME: now we do not examine identity of previous nodes which is incorrect
                        // but in our graphs it should be ok - verify
                        if(true || ex.prev==st.prev || st.prev==null) { // st.prev==null when st is the next alternative of an OR
                            equiv=true;
                        }else if(ex.prev!=null && st.prev!=null && ex.prev.length==st.prev.length) {
                            for(FAState s1: ex.prev) {
                                int j=0;
                                for(; j<st.prev.length; j++) {
                                    if(s1==st.prev[j])
                                        break;
                                }
                                if(j==st.prev.length) {
                                    equiv=false;
                                    break;
                                }
                            }
                        }
                    }
                    if(equiv) {
                        rc=false;
                        // add new arcs to existing state
                        if(st.nextArcs!=null) {
                            for(FAState otherState: st.nextArcs) {
                                if((otherState instanceof FASimpleTokenState) && (!(ex.nextArcs instanceof NextArcsFull))) {
                                    ex.nextArcs=new NextArcsFull(ex.nextArcs);
                                }
                                if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"Merging arc to "+otherState+" to "+ex);
                                ex.nextArcs.addArcTo(otherState);
                            }
                        }
                        // redirect all arcs of st's predecessors to the merged state
                        if(st.prev!=null) {
                            for(FAState pre: st.prev) {
                                if(!(pre.nextArcs instanceof NextArcsFull)) {
                                    pre.nextArcs=new NextArcsFull(pre.nextArcs);
                                }
                                pre.nextArcs.changeNextState(st, ex);
                            }
                        }
                        // redirect all prev arcs of st's followers to the merged state
                        if(st.nextArcs!=null) {
                            for(FAState fol: st.nextArcs) {
                                for(int j=0;j<fol.prev.length;j++) {
                                    if(fol.prev[j]==st) {
                                        fol.prev[j]=ex;
                                        break;
                                    }
                                }
                            }
                        }
                    }else {
                        rc=super.addArcToSlow(s);
                    }
                }
            }else {
                nextTokens.put(st, st);
            }
        }else {
            rc=super.addArcTo(s);
        }
        return rc;
    }
    public int size() {
        return next.length+nextTokens.size();
    }
    public FAState first() {
        if(next.length>0)
            return next[0];
        else if(nextTokens!=null && nextTokens.size()>0)
            return nextTokens.firstKey();
        else            
            throw new NoSuchElementException();
    }
    public int tokenStateCount() {
        return (nextTokens!=null)? nextTokens.size(): 0;
    }
    public void toString(StringBuffer s) {
        super.toString(s);
        s.append("{");
        int i=0;
        for(FASimpleTokenState st: nextTokens.keySet()) {
            if(i++>0)
                s.append(",");
            s.append(st.data);
        }
        s.append("}");
        if(loopBack!=null) {
            s.append("L:");
            s.append(loopBack);
        }
    }
    public String toString() {
        StringBuffer s=new StringBuffer(1024);
        toString(s);
        return s.toString();
    }
    public boolean changeNextState(FAState st1, FAState st2) {
        boolean rc=super.changeNextState(st1, st2);
        if(!rc && nextTokens!=null && st1 instanceof FASimpleTokenState) {
            FAState s=nextTokens.get(st1);
            if(s==st1) {
                nextTokens.remove(st1);
                if(st2 instanceof FASimpleTokenState) {
                    FASimpleTokenState added=(FASimpleTokenState) st2;
                    nextTokens.put(added, added);
                }else {
                    super.addArcTo(st2);
                }
            }
        }
        return rc;
    }
    public Iterator<FAState> iterator() {
        return new NSIt2();
    }
    private class NSIt2 implements Iterator<FAState> {
        int p=0;
        Iterator<FASimpleTokenState> sit=null;
        public boolean hasNext() {
            if(next!=null && p<next.length)
                return true;
            if(sit!=null)
                return sit.hasNext();
            if(nextTokens!=null && nextTokens.size()>0) {
                sit=nextTokens.keySet().iterator();
                return true;
            }
            return false;
        }
        public FAState next() {
            if(next!=null && p<next.length)
                return next[p++];
            if(sit!=null)
                return sit.next();
            if(nextTokens!=null) {
                sit=nextTokens.keySet().iterator();
                return sit.next();
            }
            throw new NoSuchElementException();
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
