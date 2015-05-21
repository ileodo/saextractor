// $Id: ACSegmentCache.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

/**
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.ArrayList;
import uep.util.Logger;
import uep.util.Util;

import ex.reader.*;
import ex.parser.GState;
import ex.util.*;
import ex.util.search.*;

public class ACSegmentCache {
    private IntTrie trie;
    private Document doc;
    private int[] segKey;
    private Lattice latUtils;
    private ACLattice inifinStates;
    private ArrayList<Path> results;
    private Logger log;
    private static final ACSegment empty=
        new ACSegment(new Path(new GState("EMPTY ACSegment", 0.0, -1, -1)));
    private static final ACSegment impossible=
        new ACSegment(new Path(new GState("IMPOSSIBLE ACSegment", -Double.MAX_VALUE, -1, -1)));
    
    public ACSegmentCache(Document doc, ACLattice acLattice) {
        this.doc=doc;
        this.inifinStates=acLattice;
        trie=new IntTrie(null, -1);
        segKey=new int[2];
        latUtils=new Lattice();
        results=new ArrayList<Path>(8);
        log=Logger.getLogger("Parser");
    }
    
    void clear() {
        for(Object o: trie) {
            if(o!=null && o!=empty && o!=impossible) {
                ((ACSegment)o).clear();
            }
        }
        trie.clear();
        doc=null;
        latUtils.clear();
        inifinStates.clear();
        if(results!=null) {
            for(Path p: results)
                p.clear();
            results.clear();
            results=null;
        }
    }
    
    public ACSegment get(int startIdx, int endIdx, boolean create) {
        segKey[0]=startIdx;
        segKey[1]=endIdx;
        String msg=null;
        ACSegment seg=(ACSegment) trie.get(segKey);
        if(seg==null && create) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Computing path between ["+startIdx+","+endIdx+"]");
            if(startIdx>=endIdx || (endIdx>=doc.tokens.length && endIdx!=Integer.MAX_VALUE)) {
                msg="Invalid range; doc size="+doc.tokens.length;
            }
            GState iniState=null;
            if(startIdx==-1) {
                iniState=inifinStates.ini;
            }else if(doc.tokens[startIdx].acStates!=null && doc.tokens[startIdx].acStates.size()>0) {
                iniState=doc.tokens[startIdx].acStates.get(0);
                if(iniState.type!=GState.ST_BG) {
                    msg="Invalid initial state type "+iniState.label();
                }
            }else {
                msg="No initial state found";
            }
            GState finState=null;
            if(endIdx==Integer.MAX_VALUE) {
                finState=inifinStates.end;
            }else if(doc.tokens[endIdx].acStates!=null && doc.tokens[endIdx].acStates.size()>0) {
                finState=doc.tokens[endIdx].acStates.get(0);
                if(finState.type!=GState.ST_BG) {
                    msg="Invalid final state type "+finState.label();
                }
            }else {
                msg="No final state found";
            }
            if(msg!=null) {
                log.LG(Logger.ERR,"ACSegmentCache.get("+startIdx+","+endIdx+"): "+msg);
                throw new IllegalArgumentException(msg);
            }
            int rc=-5;
            if(true) {
//                if(iniState.myScore<-Double.MAX_VALUE+1) { // ! iniState and finState scores are NOT included in path computation
//                    rc=6; // path not possible
//                }else {
                    int iniStateIdx=inifinStates.findStateIdx(iniState);
                    if(iniStateIdx>=0) {
                        rc=latUtils.findBestPathBetween(inifinStates.states, iniStateIdx, finState, results);
                    }else {
                        log.LG(Logger.ERR,"Initial state "+iniState+" index not found: idx="+iniStateIdx);
                    }
//                }
            }else {
                rc=latUtils.backtrackSubLattice(finState, iniState, 1, results);
            }
            if(rc<0) {
                log.LG(Logger.WRN,"No path found rc="+rc+" in range ("+startIdx+","+endIdx+") back from "+finState.label()+" to "+iniState.label());
                // no ACs or their parts were found between iniState and finState; cache this also
                trie.put(segKey, empty);
            }else if(rc==6) {
                seg=impossible;
                trie.put(segKey, seg);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Path improbable rc="+rc+" in range ("+startIdx+","+endIdx+") back from "+finState.label()+" to "+iniState.label());
            }else {
                // create ACSegment, cache it, return it
                Path bestSegPath=results.get(0);
                results.clear();
                boolean includeBorderStatesInSegment=false;
                if(!includeBorderStatesInSegment) {
                    State s=bestSegPath.states.removeFirst();
                    // bestSegPath.score-=s.myScore; // now the first state's score is excluded from computation
                    s=bestSegPath.states.removeLast();
                    // bestSegPath.score-=s.myScore; // now the last state's score is excluded from computation
                }
                if(bestSegPath.states.size()==0) {
                    trie.put(segKey, empty);
                }else {
                    // The segment was found based on the most probable path through ACs.
                    // Now re-score it based on a probability that either:
                    // - the ACs / AC parts on the path were false positive extractions (MIXED_DENY)
                    // - same as above but allow some of the whole ACs on the path become standalone (MIXED_ALLOW)
                    double rescored=ACSegment.getErrorOrOrphanProb(bestSegPath, Parser.MIXED_MODE==Parser.MIXED_ALLOW);
                    if (Parser.MIXED_MODE!=Parser.MIXED_ALLOW && Util.equalsApprox(rescored, 0)) {
                        trie.put(segKey, empty); // segment only contains very improbable ACs - treat as empty
                        bestSegPath.clear();
                    }else {
                        bestSegPath.score=rescored;
                        seg=new ACSegment(bestSegPath);
                        trie.put(segKey, seg);
                    }
                }
            }
        }else if(seg==empty) {
            seg=null;
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"get ["+startIdx+","+endIdx+"] = "+seg);
        return seg;
    }
    
    public void put(int startIdx, int endIdx, ACSegment seg) {
        segKey[0]=startIdx;
        segKey[1]=endIdx;
        trie.put(segKey, seg);
    }
    
    public void setACLattice(ACLattice acl) {
        inifinStates=acl;
    }
}
