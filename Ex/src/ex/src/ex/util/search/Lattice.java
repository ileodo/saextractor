// $Id: Lattice.java 1933 2009-04-12 09:14:52Z labsky $
package ex.util.search;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.Logger;
import ex.parser.GState; // garbage, GState's startIdx and endIdx must move to util

/**
   Finds n-best optimal paths through a lattice of states using the viterbi algorithm.
 */
public class Lattice {
    protected TreeSet<State> agenda;
    protected Logger log;
    public boolean useNBest=true;

    public Lattice() {
        agenda=new TreeSet<State>();
        log=Logger.getLogger("Lattice");
    }
    
    public void clear() {
        agenda.clear();
    }

    /** Finds the highest scoring paths of states, 
	fills them into results as State[] arrays, 
	returns the number of paths found. */
    public int search(State iniState, State finState, int maxNBest, List<Path> results) {
        heuristicSearch(iniState, finState, maxNBest);
        return useNBest? 
                backtrack(finState, maxNBest, results): 
                    backtrack(finState, results);
    }

    /** Search the lattice for maxNBest highest-scoring paths and store them in results. 
     * @param lattice partially ordered states of the lattice to search
     * @param maxNBest maximum number of paths to return
     * @results list to which found paths are added, sorted by decreasing score */
    public int search(State[] lattice, int maxNBest, List<Path> results) {
        forwardSearch(lattice);
        return useNBest?
                backtrack(lattice[lattice.length-1], maxNBest, results):
                    backtrack(lattice[lattice.length-1], results);
    }

    // populate state.prev[]
    // this originally came together with forwardSearch(), prev was intented to be kept sorted 
    // and prev.length<=maxNBest (populate max. n-best prev entries)
    public static void populatePrev(State[] states) {
        for(int i=0;i<states.length;i++) {
            states[i].pc=0;
        }
        for(int i=0;i<states.length;i++) {
            State curr=states[i];
            if(curr.next==null)
                continue;
            for(int j=0;j<curr.next.length;j++) {
                curr.next[j].pc++;
            }
        }
        for(int i=0;i<states.length;i++) {
            State curr=states[i];
            if(curr.pc==0) {
                curr.prev=null;
                continue;
            }
            curr.prev=new State[curr.pc];
            curr.pc=0;
        }
        for(int i=0;i<states.length;i++) {
            State curr=states[i];
            if(curr.next==null)
                continue;
            for(int j=0;j<curr.next.length;j++) {
                State next=curr.next[j];
                next.prev[next.pc++]=curr;
            }
        }
    }

    /** clears forward prob sums for all states in states
     * 
     * @param states
     */
    public static void clear(State[] states, double val) {
        clear(states,0,states.length-1,val);
    }

    /** clears forward prob sums for each state from start inclusive to end inclusive.
     * 
     * @param states
     * @param start
     * @param end
     */
    public static void clear(State[] states, int start, int end, double val) {
        for(int i=start;i<=end;i++) {
            states[i].sum = val; //-Double.MAX_VALUE;
        }
    }
    
    /** accumulate forward probs through the whole lattice
     * 
     * @param states
     */
    public void forwardSearch(State[] states) {
        // populate sums
        states[0].sum=states[0].myScore;
        for(int i=1;i<states.length;i++) {
            State curr=states[i];
            // keep all prev pointers to already computed states sorted by their accumulated forward prob
            Arrays.sort(curr.prev);
            curr.sum=curr.prev[0].sum+curr.myScore;
        }
        if(Logger.IFLOG(Logger.INF)) log.LG(Logger.INF,"Forward search complete");
    }

    /** accumulate forward probs between start and end, not including the start and end states' scores
     * 
     * @param states
     * @param start
     * @param end
     */
    public void forwardSearch(State[] states, int start, int end) {
        // populate sums
        //states[start].sum=0;
        for(int i=start+1;i<=end;i++) {
            State curr=states[i];
            // all prev pointers before start have -MAX_VALUE probability from clear(), will end up last
            Arrays.sort(curr.prev);
            curr.sum = ((curr.prev[0].sum != -Double.MAX_VALUE)? curr.prev[0].sum: 0) + ((i<end)? curr.myScore: 0);
        }
        if(Logger.IFLOG(Logger.INF)) log.LG(Logger.INF,"Forward search complete");
        //states[start].sum=-Double.MAX_VALUE;
    }

    public void heuristicSearch(State iniState, State finState, int maxNBest) {
        int pathsFound=0;
        agenda.add(iniState);
        while(!agenda.isEmpty()) {
            State best=agenda.first();
            agenda.remove(best);
            log.LG(Logger.WRN,best.data+"->");

            for(int i=0; i<best.next.length; i++) {
                State next=best.next[i];
                log.LG(Logger.WRN,"\t"+next.data);
                boolean addedAsBestParent=next.addPrev(best, maxNBest);
                // end at final state
                if(next==finState) {
                    pathsFound++;
                    continue;
                }
                // shall we need to (re-)process children of next?
                if(addedAsBestParent && next.next!=null) {
                    log.LG(Logger.WRN,"\tadding "+next.data);
                    boolean rc=agenda.add(next);
                    if(!rc)
                        log.LG(Logger.ERR,"\tFailed to add "+next.data);
                    if(next.pc>1) {
                        log.LG(Logger.WRN,"put "+best+" as the first parent in "+next.data);
                    }
                }
            }
        }
        if(Logger.IFLOG(Logger.INF)) log.LG(Logger.INF,"Heuristic search complete");
    }

    public int backtrack(State finState, List<Path> results) {
        return backtrack(finState, false, results);
    }

    /** searches using precomputed forward probs until the first initial state is reached
     *  or, if terminateOnImpossible, until the first impossible state is reached */
    public int backtrack(State finState, boolean terminateOnImpossible, List<Path> results) {
        // 1-best
        if(finState.prev==null || finState.prev.length==0)
            return 0;
        LinkedList<State> path=new LinkedList<State>();
        State st=finState;
        path.addFirst(st);
        while(st.prev!=null) { // while st!=initial state
            st=st.prev[0];
            path.addFirst(st);
            if(terminateOnImpossible && st.sum==-Double.MAX_VALUE) // good for range search only
                break;
        }
        /*
        int len=path.size();
        ArrayList pa=new ArrayList<State>(len);
        for(int i=0;i<len;i++) {
            pa.set(len-i-1,path.get(i));
        }
        path.clear();
        */
        results.add(new Path(path, finState.sum, true));
        return 1;
    }

    public int backtrack(State finState, int maxNbest, List<Path> results) {
        // n-best
        TreeSet<Fork> forks=new TreeSet<Fork>();
        Path singleton=new Path(finState);
        for(int i=0;finState.prev!=null && i<finState.prev.length; i++) {
            forks.add(new Fork(singleton, i));
        }
        while(results.size()<maxNbest) {
            int sz=forks.size();
            if(sz==0)
                break;
            Fork f=forks.last(); // get the highest scoring fork
            forks.remove(f);
            LinkedList<State> path=new LinkedList<State>(f.tail.states); // path: copy fork's tail incl the fork node
            State forkState=f.tail.states.getFirst();
            State st=forkState.prev[f.idx]; // n-th best predecessor of fork

            // path: add best path to initial from n-th best predecessor of fork
            path.addFirst(st);
            Path lastTail=f.tail;
            while(st.prev!=null) { // while st!=initial state
                // write down alternatives we have (TODO: we can limit their number here)
                Path tail=new Path(path, lastTail.score + st.myScore, true);
                for(int i=1;i<st.prev.length;i++) {
                    forks.add(new Fork(tail, i));
                }
                // move left, taking the best path
                st=st.prev[0];
                path.addFirst(st);
                lastTail=tail;
            }
            Path p=new Path(path, lastTail.score + st.myScore, true);
            // log.LG(log.WRN,p.toString());
            results.add(p);
        }
        return results.size();
    }
    
    /** Finds 1 best path by doing a forward step from iniState to finState and backtracking from finState to iniState.
     * The path score excludes both iniState and finState scores.
     * Returns 1 if the path was found and added to results, 0 when no path */
    public int findBestPathBetween(GState[] states, int iniStateIdx, State finState, List<Path> results) {
        // 0. set all predecessors to -inf
        int i=0;
        for( ; i<iniStateIdx; i++) { states[i].sum=-Double.MAX_VALUE; }
        // 1. clear scores of all states in the segment
        i=iniStateIdx;
        for(i=iniStateIdx; states[i]!=finState; i++) { states[i].sum=0; }
        states[i].sum=0;
        int finStateIdx=i;
        // 2. forward
        for(i=iniStateIdx+1;i<=finStateIdx;i++) {
            State curr=states[i];
            Arrays.sort(curr.prev);
            curr.sum = curr.prev[0].sum + ((i<finStateIdx)? curr.myScore: 0);
        }
        // 3. backtrack
        int rc=0;
        if(finState.prev!=null || finState.prev.length>0) {
            GState iniState=states[iniStateIdx];
            LinkedList<State> path=new LinkedList<State>();
            State st=finState;
            path.addFirst(st);
            while(st.prev!=null) { // while st!=initial state
                st=st.prev[0];
                path.addFirst(st);
                if(st==iniState) {
                    results.add(new Path(path, finState.sum, false));
                    rc=1;
                    break;
                }else if(st.sum==-Double.MAX_VALUE) { // can always happen naturally
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Hit impossible state "+st+", iniState="+iniState);
                    rc=6;
                    break;
                }
            }
            if(rc==0) {
                log.LG(Logger.WRN,"Terminating on dead-end state "+st+", iniState="+iniState);
                rc=-2;
            }
        }
        return rc;
    }
    
    /** Finds n best paths by backtracking from finState to iniState. 
     *  To be used only when it is guaranteed that all paths going back from finState also pass through iniState. 
     *  Otherwise, the search is potentially exponential. */
    public int backtrackSubLattice(State finState, State iniState, int maxNbest, List<Path> results) {
        int iniStateEndIdx=(iniState!=null)? ((GState) iniState).endIdx: -1;
        // n-best
        if(log.IFLG(Logger.ERR)) log.LG(Logger.ERR,"3 between ["+iniState+","+finState+"]");
        TreeSet<Fork> forks=new TreeSet<Fork>();
        Path singleton=new Path(finState);
        for(int i=0; finState.prev!=null && i<finState.prev.length; i++) {
            GState pr=(GState) finState.prev[i];
            if(pr.startIdx > iniStateEndIdx) {
                forks.add(new Fork(singleton, i));
            } else if(pr==iniState) {
                LinkedList<State> states=new LinkedList<State>(singleton.states);
                states.addFirst(iniState);
                Path p=new Path(states, singleton.score + iniState.myScore, true);
                results.add(p);
            }
        }
        int dbgTryCnt=0;
        while(results.size()<maxNbest && forks.size()>0) {
            Fork f=forks.last(); // get the highest scoring fork
            forks.remove(f);
            
            LinkedList<State> forkStates=new LinkedList<State>(f.tail.states); // path: copy fork's tail incl the fork node
            GState st=(GState) f.getTarget(); // n-th best predecessor of fork

            // path: add best path to initial from n-th best predecessor of fork
            forkStates.addFirst(st);
            Path tail=new Path(forkStates, f.tail.score + st.myScore, true);
            for(int i=0;i<st.prev.length;i++) {
                GState pr=(GState) st.prev[i];
                if(pr.startIdx > iniStateEndIdx) {
                    forks.add(new Fork(tail, i));
                }else if(pr==iniState) {
                    LinkedList<State> states=new LinkedList<State>(tail.states);
                    states.addFirst(iniState);
                    Path resultPath=new Path(states, tail.score + iniState.myScore, true);
                    results.add(resultPath);
                }else if(pr.startIdx <= iniStateEndIdx) {
                    break;
                }
            }
            if(results.size()<maxNbest) {
                if(log.IFLG(Logger.ERR)) log.LG(Logger.ERR,"Retry cnt="+(++dbgTryCnt));
            }
        }
        if(log.IFLG(Logger.ERR)) log.LG(Logger.ERR,"~3 between ["+iniState+","+finState+"]");
        return results.size();
    }

    public static String toGraph(State iniState) {
        Set<State> visited=new HashSet<State>();
        StringBuffer s=new StringBuffer(512);
        StringBuffer t=new StringBuffer(512);
        s.append("digraph FA {\n");
        s.append(" orientation=\"landscape\";");
        if(false) {
            s.append(" size=\"10.693,8.068\";");
        }
        s.append(" margin=\"0.1\"; ratio=\"auto\";\n");
        s.append(" node [shape = box];\n");
        s.append(" rankdir=LR;\n");
        try {
            iniState.toGraph(s,t,visited);
        }catch(StackOverflowError ex) {
            Logger.LOG(Logger.ERR,"Error generating lattice graph: "+ex);
        }
        s.append(t);
        s.append("}");
        return s.toString();
    }

    public static void main(String[] args) {
        if(args.length!=1) {
            System.err.print("Usage: ex.util.search.Lattice <nbest>");
            return;
        }
        int nbest=Integer.parseInt(args[0]);
        
        State a=new GState("a",2,-1,-1);
        State b=new GState("b",2, 10, 10);
        State c=new GState("c",2, 10, 12);
        State d=new GState("d",2, 15, 16);
        State e=new GState("e",2, 20, 20);
        State f=new GState("f",2, 20, 20);
        State g=new GState("G",3, 20, 20);
        State h=new GState("h",2, 30, 30);
        State i=new GState("i",2,Integer.MAX_VALUE,Integer.MAX_VALUE);
        a.next=new State[] {b,c,d};
        b.next=new State[] {d};
        c.next=new State[] {e,f};
        d.next=new State[] {g,i};
        e.next=new State[] {h};
        f.next=new State[] {h};
        g.next=new State[] {i};
        h.next=new State[] {i};
        State[] lattice=new State[] {a,b,c,d,e,f,g,h,i};

        /*
	State ini=new State(null,0);
	State b=new State("b",2);
	State c=new State("c",3);
	State d=new State("d",3);
	State e=new State("e",2);
	State fin=new State(null,0);
	ini.next=new State[] {b,c,d,e,fin};
	b.next=new State[] {fin}; // TODO: same next[] and prev[] arrays could be shared
	c.next=new State[] {fin};
	d.next=new State[] {fin};
	e.next=new State[] {fin};
	State[] lattice=new State[] {ini,b,c,d,e,fin};
         */

        Lattice lat=new Lattice();
        ArrayList<Path> results=new ArrayList<Path>(nbest);
        int rc;
        
        boolean test=true;
        if(test) {
            Lattice.populatePrev(lattice);
            rc=lat.search(lattice,nbest,results);
            System.out.println("Forward found "+rc+" paths:");
        }else {
            Lattice.populatePrev(lattice);
            lat.forwardSearch(lattice);
            State finSt=lattice[lattice.length-1];
            State iniSt=b; // lattice[0];
            rc=lat.backtrackSubLattice(finSt, iniSt, nbest, results);
            System.out.println("backtrackSubLattice found "+rc+" paths:");
        }

        //rc=lat.search(a,i,nbest,results);
        //System.out.println("Heuristic found "+rc+" paths:");

        for(int ii=0;ii<rc;ii++) {
            Path path=(Path) results.get(ii);
            System.out.println(path.toString());
        }
        
        System.out.println("Testing constrained search: only states with the same casing allowed on path.");
        Lattice conLat=new ConstrainedLattice(new TestConstraintFactory());
        Lattice.populatePrev(lattice);
        results.clear();
        rc=conLat.search(lattice,1,results);
        System.out.println("Constrained search found "+rc+" paths:");
        for(int ii=0;ii<rc;ii++) {
            Path path=(Path) results.get(ii);
            System.out.println(path.toString());
        }
    }
}

class Fork implements Comparable<Fork> {
    public Fork(Path t, int i) {
        tail=t;
        idx=i;
        State tailStart=tail.states.getFirst();
        score=tail.score + tailStart.prev[i].sum;
    }
    public int compareTo(Fork f) {
        if(score<f.score)
            return -1;
        else if(score>f.score)
            return 1;
        else if(this==f)
            return 0;
        else
            return this.hashCode()-f.hashCode();
    }
    public boolean equals(Fork f) {
        return this==f;
    }
    public State getTarget() {
        return tail.states.getFirst().prev[idx];
    }
    public Path tail; // backtracked tail path from the final node to this fork node
    public int idx; // backtracking index to take at the fork node
    public double score; // total score of tail + the i-th best path from the fork node to the inititial state
}

/** Sorts by ascending State.sum */
class AscStateComparator implements Comparator<State> {
    public int compare(State a, State b) {
        if(a.sum>b.sum)
            return -1;
        else if(a.sum<b.sum)
            return 1;
        else if(a==b)
            return 0;
        else
            return a.hashCode()-b.hashCode();
    }
    public boolean equals(State a, State b) {
        return (a==b);
    }
}
