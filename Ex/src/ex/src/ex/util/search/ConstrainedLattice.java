// $Id: ConstrainedLattice.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import uep.util.Logger;

public class ConstrainedLattice extends Lattice {
    
    protected PathConstraintFactory pcFact;
    protected List<ConstraintListItem> citList;
    
    public ConstrainedLattice(PathConstraintFactory pcFact) {
        this.pcFact=pcFact;
        this.citList=new ArrayList<ConstraintListItem>(256);
    }
    
    /** Accumulates forward probs through the whole lattice, taking into account constraints.
    * @param states */
    public void forwardSearchConstrained(State[] states, PathConstraint initialConstraint) {
        // populate sums
        // states[0].sum=states[0].myScore;
        states[0].data=new ConstraintList(
                new ConstraintListItem(initialConstraint, states[0].myScore, null, -1), 
                states[0].data);
        int cnt=0;
        for(int i=1;i<states.length;i++) {
            State curr=states[i];
            // keep all prev pointers to already computed states sorted by their accumulated forward prob
            // Arrays.sort(curr.prev);
            // curr.sum=curr.prev[0].sum+curr.myScore;
            citList.clear();
            
            /* For each preceding state, */
            for(int j=0;j<curr.prev.length;j++) {
                State prev=curr.prev[j];
                /* ... iterate over its constrained scores: */
                if(prev.data instanceof ConstraintList) {
                    for(int k=0;k<((ConstraintList)prev.data).lst.length;k++) {
                        ConstraintListItem prevCit=((ConstraintList)prev.data).lst[k];
                        // constraint created by reaching this state from prev state (from some of its constraints)
                        PathConstraint currCon=pcFact.createNextConstraint(curr.data, prevCit.c);
                        if(currCon==PathConstraint.FORBIDDEN)
                            continue;
                        // No n-best is implemented, we discard all dominated items.
                        // Make sure the list only contains one (the best scoring) 
                        // item for each constraint.
                        ConstraintListItem curCit=null;
                        double newScore=prevCit.bestPathScore+curr.myScore;
                        int len=citList.size();
                        int m=0;
                        for( ;m<len;m++) {
                            ConstraintListItem cit2=citList.get(m);
                            if(currCon==cit2.c || (currCon!=null && currCon.equals(cit2.c))) {
                                if(newScore > cit2.bestPathScore) {
                                    curCit=new ConstraintListItem(currCon, newScore, prev, k);
                                    citList.set(m, curCit);
                                }
                                break;
                            }
                        }
                        if(m==len) {
                            curCit=new ConstraintListItem(currCon, newScore, prev, k);
                            citList.add(curCit);
                        }
                    }
                }
            }
            if(citList.size()>0) {
                Collections.sort(citList);
                if(log.IFLG(Logger.TRC)) {
                    log.LG(Logger.TRC,"State "+i+"/"+states.length);
                    for(int k=0;k<citList.size(); k++) {
                        log.LG(Logger.TRC," C"+k+". "+citList.get(k));
                    }
                }
                // abuse State's userdata to store constraintList
                ConstraintList cl=new ConstraintList(citList, curr.data);
                curr.data=cl;
                // just to have sum populated. only used when the best path is created.
                curr.sum=cl.lst[0].bestPathScore;
            }else {
                // just to have sum populated. not used by the search.
                curr.sum=-Double.MAX_VALUE;
            }
        }
        if(Logger.IFLOG(Logger.INF)) log.LG(Logger.INF,"Constrained forward search complete, backptr items="+cnt);
    }
    
    /** Constrained 1-best version of Lattice.backtrack(). Searches using precomputed forward probs 
     *  until the first initial state is reached or, if terminateOnImpossible, until the first 
     *  impossible state is reached. */
    public int backtrack(State finState, boolean terminateOnImpossible, List<Path> results) {
        // 1-best
        if(finState.prev==null || finState.prev.length==0)
            return 0;
        LinkedList<State> path=new LinkedList<State>();
        State st=finState;
        
        // start with the final state's best valid final constraint
        if(!(st.data instanceof ConstraintList)) {
            return 0;
        }
        ConstraintList citList=(ConstraintList) st.data;
        int cit=0;
        for( ; cit<citList.lst.length; cit++) {
            if(pcFact.isValidFinal(citList.lst[cit].c))
               break;
        }
        if(cit==citList.lst.length) {
            return 0; // none of the paths was valid
        }
        // backtrack from the best valid final constraint towards the initial state 
        while(true) {
            path.addFirst(st);
            if(!(st.data instanceof ConstraintList)) {
                return 0;
            }
            citList=(ConstraintList) st.data;
            if(citList.lst[cit].backState==null)
                break;
            if(terminateOnImpossible && st.sum==-Double.MAX_VALUE) // good for range search only
                break;
            st =citList.lst[cit].backState;
            cit=citList.lst[cit].backCit;
        }
        results.add(new Path(path, finState.sum, true));
        return 1;
    }

    /** Undoes the abuse of State.data by forward search, frees back pointers. */
    public void restoreUserData(State[] lattice) {
        for(State s: lattice) {
            if(s.data instanceof ConstraintList) {
                s.data=((ConstraintList)s.data).userData;
            }
        }
    }

    /** Search the lattice for maxNBest highest-scoring paths and store them in results. 
     * @param lattice partially ordered states of the lattice to search
     * @param maxNBest maximum number of paths to return - for ConstrainedLattice this must be set to 1
     * @results list to which found paths are added, sorted by decreasing score */
    public int search(State[] lattice, int maxNBest, List<Path> results) {
        forwardSearchConstrained(lattice, null);
        if(maxNBest>1)
            log.LG(Logger.WRN,"Constrained lattice does not support nbest "+maxNBest+", using nbest=1");
        int rc=backtrack(lattice[lattice.length-1], false, results);
        restoreUserData(lattice);
        return rc;
    }
}

/** State's array of constrained scored back pointers. */
class ConstraintList {
    ConstraintListItem[] lst;
    Object userData;
    public ConstraintList(ConstraintListItem item, Object userData) {
        lst=new ConstraintListItem[1];
        lst[0]=item;
        this.userData=userData;
    }
    public ConstraintList(List<ConstraintListItem> items, Object userData) {
        int n=items.size();
        if(n>0) {
            lst=new ConstraintListItem[n];
            for(int i=0;i<n;i++) {
                lst[i]=items.get(i);
            }
        }
        this.userData=userData;
    }    
}

/** Item in a state's array of constrained scored back pointers. */
class ConstraintListItem implements Comparable<ConstraintListItem> {
    PathConstraint c;
    double bestPathScore;
    State backState;
    int backCit;
    public ConstraintListItem(PathConstraint c, double bestPathScore, State backState, int backCit) {
        this.c=c;
        this.bestPathScore=bestPathScore;
        this.backState=backState;
        this.backCit=backCit;
    }
    public int compareTo(ConstraintListItem o) {
        if(bestPathScore>o.bestPathScore)
            return -1;
        else if(bestPathScore<o.bestPathScore)
            return 1;
        else if(c==null) {
            if(o.c==null) {
                return hashCode()-o.hashCode();
            }else {
                return -1;
            }
        }
        else if(o.c==null)
            return 1;
        else
            return c.compareTo(o.c);
    }
    public String toString() {
        Object o=((backState.data instanceof ConstraintList)? ((ConstraintList)backState.data).userData :backState.data);
        String s="null";
        if(o!=null) {
            s=o.toString();
            if(s.length()>40) {
                s=s.substring(0,35)+"...";
            }
        }
        return bestPathScore+"/"+c+": from "+backState.id+"."+s+"/"+backCit;
    }
}
