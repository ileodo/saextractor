// $Id: ICLattice.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.Logger;
import ex.util.search.*;
import ex.util.*;
import ex.reader.*;
import ex.ac.*;

/**
   Finds n-best optimal sequences of instance candidates
   within a document.
 */
public class ICLattice {
    protected static DocOrderComparator docOrdCmp=new DocOrderComparator();
    // public GState[] states;
    protected static Logger log;

    public ICLattice() {
        //log=Logger.getLogger("ICLattice");
        //states=null;
    }
    
    private static void setLogger() {
        if(log==null)
            log=Logger.getLogger("Parser");
    }
    
    public static <E extends Extractable> GState[] toGraph(Collection<E> entitySet, Document doc, boolean maySkip, ScoreGetter sg) {
        setLogger();
        int len=entitySet.size();
        // generate states (1:1 with ICs)
        GState[] states=new GState[len+2];
        states[0]=new GState(null, 0, -1, -1);
        states[states.length-1]=new GState(null, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        Iterator<E> it=entitySet.iterator();
        int i=0;
        while(it.hasNext()) {
            Extractable en=it.next();
            double score=(sg==null)? en.getLatticeProb(): sg.getScore(en);
            states[++i]=new GState(en, score, en.getStartIdx(), en.getEndIdx());
        }
        Arrays.sort(states, 1, states.length-1, docOrdCmp); // by startIdx

        // populate state.next[]
        for(i=0;i<states.length-1;i++) {
            GState curr=states[i];
            for(int ph=0;ph<2;ph++) { // in phase 0 compute outgoing arcCnt and create next[arcCnt], in phase 1 populate 
                int limit=Integer.MAX_VALUE; // endIdx of one of the next entities that ends closest
                int arcCnt=0;
                for(int j=i+1;j<states.length;j++) {
                    GState next=states[j];
                    if(next.startIdx<=curr.endIdx)
                        continue;
                    if(!maySkip) {
                        if(next.startIdx > limit) {
                            break; // there is already at least a single whole entity between curr and next
                        }
                        if(next.endIdx < limit) {
                            limit=next.endIdx;
                        }
                    }
                    if(ph==1)
                        curr.next[arcCnt]=next;
                    arcCnt++;
                    // next.pc++;
                }
                if(ph==0)
                    curr.next=new GState[arcCnt];
            }
        }

        // populate state.prev[]
        Lattice.populatePrev(states);

        return states;
    }

    public static GState[] segmentToACGraph(Document doc, int startIdx, int endIdx) {
        GState[] lattice=null;
        LinkedList<Extractable> acs=new LinkedList<Extractable>();
        int cnt=doc.getACsInRange(startIdx, endIdx, acs);
        if(cnt>0) {
            // in generated lattice, arcs may not skip whole ACs
            // use default latticeProb of ACs
            lattice=toGraph(acs, doc, false, null); 
        }
        return lattice;
    }

    public static GState[] insertOrphanStates(GState[] states, Document doc) {
        setLogger();
        // eval each arc for AC presence
        // if ACs present on arc, store the segment in map keyed by first AC's startIdx and last AC's endIdx
        int[] segmentKey=new int[2];
        IntTrie map=new IntTrie(null,-1);
        Lattice segLattice=new Lattice();
        ArrayList<Path> paths=new ArrayList<Path>(1);
        int newStateCnt=0, newStateReuseCnt=0;

        ArrayList<State> newArcs=new ArrayList<State>(16); // arcs to AC segment states to be added to curr state

        // collect best AC segments
        for(int i=0;i<states.length-1;i++) {
            GState curr=states[i];
            int segStartIdx=doc.getNextTokenWithACs(curr.endIdx);
            if(segStartIdx==-1)
                continue;
            // store the cheapest path through ACs between 2 ICs under segmentKey
            segmentKey[0]=segStartIdx;
            newArcs.clear();
            int created=0, reused=0;

            for(int j=0;j<curr.next.length;j++) {
                int segEndIdx=doc.getPrevTokenWithACEnds(((GState)curr.next[j]).startIdx);
                newArcs.add(curr.next[j]);
                if(segEndIdx<segStartIdx)
                    continue;
                // segmentKey part 2
                segmentKey[1]=segEndIdx;
                DynState existing=(DynState) map.get(segmentKey);
                if(existing!=null) {
                    int len=newArcs.size();
                    newArcs.set(len-1, existing);
                    if(len>1 && newArcs.get(len-1)==newArcs.get(len-2))
                        newArcs.remove(len-1);
                    existing.addNext(curr.next[j], true);
                    reused++;
                    continue;
                }

                GState[] segStates=segmentToACGraph(doc, segStartIdx, segEndIdx);
                if(segStates==null) {
                    continue;
                }

                // forward search
                int pc=segLattice.search(segStates, 1, paths);
                if(pc!=1) {
                    log.LG(Logger.ERR,"Error generating paths through AC segment; path count="+pc);
                    continue;
                }
                Path acPath=paths.remove(0);
                ACSegment acSegment=new ACSegment(acPath);
                DynState acsState=new DynState(acSegment, acSegment.getLatticeProb(), segStartIdx, segEndIdx);
                map.put(segmentKey, acsState);
                newArcs.set(newArcs.size()-1, acsState);
                acsState.addNext(curr.next[j], false);
                created++;
            }

            newStateReuseCnt+=reused;
            newStateCnt+=created;

            // update curr state
            if(reused>0||created>0) {
                if(curr.next==null || newArcs.size()!=curr.next.length) {
                    curr.next=new State[newArcs.size()];
                }
                int len=newArcs.size();
                for(int j=0;j<len;j++) {
                    curr.next[j]=newArcs.get(j);
                }
            }
        }

        // finalize next[] arrays of new DynStates
        Iterator it=map.iterator();
        while(it.hasNext()) {
            DynState s=(DynState) it.next();
            s.commit();
        }

        // incorporate AC segments into graph
        GState[] newLattice=new GState[states.length + newStateCnt];
        it=map.iterator();
        GState nextNew=it.hasNext()? (GState)it.next(): null;
        int j=0;
        for(int i=0; i<states.length; i++) {
            GState ic=states[i]; // orig state: IC state, start or end state
            if(nextNew==null || docOrdCmp.compare(ic, nextNew)<0) {
                newLattice[j++]=ic;
            }else {
                newLattice[j++]=nextNew;
                nextNew=it.hasNext()? (GState)it.next(): null;
                i--;
            }
        }

        // populate state.prev[]
        Lattice.populatePrev(newLattice);

        return newLattice;
    }

    public static String toString(State[] states) {
        StringBuffer buff=new StringBuffer(states.length*512);
        buff.append("size="+states.length+"\n");
        for(int i=0;i<states.length;i++) {
            buff.append(states[i]);
            buff.append("\n");
        }
        return buff.toString();
    }

    public static void fromLogDomain(State[] states) {
        for(int i=0;i<states.length;i++) {
            Extractable ex=((Extractable)states[i].data);
            if(ex==null) {
                //if(i!=0 && i!=states.length-1) {
                //    Logger.LOG(Logger.ERR,"Path["+i+"] contains null data; state="+states[i]);
                //}
                continue;
            }
            ex.setLatticeProb(Math.exp(ex.getLatticeProb()));
        }
    }

    /* reuses precomputed ACSegment cache */
    public static GState[] insertOrphanStatesII(GState[] states, ACSegmentCache segCache) {
        setLogger();
        // eval each arc for AC presence
        // if ACs present on arc, store the segment in map keyed by first AC's startIdx and last AC's endIdx
        int[] segKey=new int[2];
        int created=0, reused=0;
        int newStateCnt=0, newStateReuseCnt=0;
        IntTrie stateMap=new IntTrie(null,-1);
        HashSet<State> nextSet=new HashSet<State>(16);

        // collect the best AC segment that fits between each pair of neighboring ICs
        for(int i=0;i<states.length-1;i++) {
            GState curr=states[i];
            nextSet.clear();
            created=0; 
            reused=0;
            for(int j=0;j<curr.next.length;j++) {
                int finalIdx=((GState) curr.next[j]).startIdx;
                ACSegment acs=(ACSegment) segCache.get(curr.endIdx, finalIdx, false); // currently not used = is empty
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"QUERY (insertOrphanStatesII) ["+curr.endIdx+","+finalIdx+"] = "+acs);
                if(acs==null) {
                    // keep direct link (fixme: this may be because we terminated early during ACSegment precomputation)
                    nextSet.add(curr.next[j]);
                    continue;
                }

                segKey[0]=acs.getStartIdx();
                segKey[1]=acs.getEndIdx();
                DynState acsState=(DynState) stateMap.get(segKey);
                if(acsState==null) {
                    acsState=new DynState(acs, acs.getLatticeProb(), segKey[0], segKey[1]);
                    stateMap.put(segKey, acsState);
                    created++;
                }else {
                    reused++;
                }
                nextSet.add(acsState); // we can only get to next[j] using one extra hop
                acsState.addNext(curr.next[j], true); // DynState checks duplicity
            }

            newStateReuseCnt+=reused;
            newStateCnt+=created;

            // update curr state
            if(reused>0||created>0) {
                if(curr.next==null || nextSet.size()!=curr.next.length) {
                    curr.next=new State[nextSet.size()];
                }
                Iterator<State> it=nextSet.iterator();
                int j=0;
                while(it.hasNext()) {
                    curr.next[j++]=it.next();
                }
            }
        }

        // finalize next[] arrays of new DynStates
        Iterator it=stateMap.iterator();
        while(it.hasNext()) {
            DynState s=(DynState) it.next();
            s.commit();
        }

        // incorporate AC segments into graph
        GState[] newLattice=new GState[states.length + newStateCnt];
        it=stateMap.iterator();
        GState nextNew=it.hasNext()? (GState)it.next(): null;
        int j=0;
        for(int i=0; i<states.length; i++) {
            GState ic=states[i]; // orig state: IC state, start or end state
            if(nextNew==null || docOrdCmp.compare(ic, nextNew)<=0) {
                newLattice[j++]=ic;
            }else {
                newLattice[j++]=nextNew;
                nextNew=it.hasNext()? (GState)it.next(): null;
                i--;
            }
        }

        // populate state.prev[]
        Lattice.populatePrev(newLattice);

        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Inserted "+newStateCnt+" extra-IC acSegment states; reuse count="+newStateReuseCnt);

        return newLattice;
    }
}

class DocOrderComparator implements Comparator<GState> {
    public int compare(GState a, GState b) {
        return a.startIdx - b.startIdx;
    }
    public boolean equals(GState a, GState b) {
        return (a.startIdx==b.startIdx);
    }
}
