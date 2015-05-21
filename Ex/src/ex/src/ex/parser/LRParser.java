// $Id: LRParser.java 2038 2009-05-21 00:23:51Z labsky $
package ex.parser;

import java.util.*;
import uep.util.Logger;
import uep.util.Util;
import ex.model.*;
import ex.reader.*;
import ex.util.search.*;
import ex.ac.*;
import ex.features.TagNameF;
import ex.features.TagTypeF;
import ex.wrap.*;

/** Implements a parser that:
    1. builds singleton ICs (from each AC)
    2. takes ICs in sequence either from:
       - the best scoring IC,
       - left to right,
    3. builds a triangle trellis in a bottom-up manner containing ICs and orphaned AC groups.
 */

class ICStartIdxCmp implements Comparator<ICBase> {
    public int compare(ICBase o1, ICBase o2) {
        return o1.getStartIdx() - o2.getStartIdx();
    }
}
class ICEndIdxCmp implements Comparator<ICBase> {
    public int compare(ICBase o1, ICBase o2) {
        return o1.getEndIdx() - o2.getEndIdx();
    }
}
class ICQueueComparator implements Comparator<ICBase> {
    public int compare(ICBase o1, ICBase o2) {
        double d=o1.getCombinedProb() - o2.getCombinedProb();
        if(d>0)
            return 1;
        else if(d<0)
            return -1;
        else {
            if(o1.attCount()<o2.attCount())
                return 1;
            else if(o1.attCount()>o2.attCount())
                return -1;
            return (o1==o2)? 0: 1; // not 0 since Sets would not accept 2 elements whose compareTo()==0
        }
    }
}
public class LRParser extends Parser {

    protected Document doc;
    protected Trellis trellis;
    //protected int[] segKey=new int[2];
    protected ACSegment lastLeftSeg;
    protected ACSegment lastRightSeg;
    protected int notAddableCnt;
    protected int crossBlockCnt;
    protected GState[] extractableLattice; 
           
    public static short LEFT=1;
    public static short RIGHT=2;

    public static ICQueueComparator icQueueCmp=new ICQueueComparator();
    
    public LRParser(Model model) {
        super(model);
        doc=null;
        extractableLattice=null;
    }
    
    public void clear() {
        if(extractableLattice!=null) {
            for(GState st: extractableLattice) {
                st.clear();
            }
            extractableLattice=null;
        }
        if(trellis==null) {
            trellis.clear();
            trellis=null;
        }
        if(doc!=null) {
            doc.clear();
            doc=null;
        }
        lastLeftSeg=null;
        lastRightSeg=null;
        super.clear();
    }

    public int parse(Document d, int nbest, List<Path> paths) {
        long startTime=System.currentTimeMillis();
        
        // determine which ACs might have co-referring values 
        resolveReferences(d);
        
        doc=d;
        trellis=new Trellis(doc);
        // instance candidate queue; contains ICs which could be possibly extended
        TreeSet<ICBase> icq=new TreeSet<ICBase>(icQueueCmp);
        // all valid instances
        TreeSet<ICBase> validICs=new TreeSet<ICBase>(icQueueCmp);

        ArrayList<AttributeDef> candAtts=new ArrayList<AttributeDef>(8);
        getCandidateAttributes(doc, candAtts);
        int mandAtts=0;
        for(int i=0;i<candAtts.size();i++) {
            if(candAtts.get(i).minCard>0)
                mandAtts++;
        }
        
        /* generate singleton ICs (scored) and keep them sorted */
        TreeSet<ICBase> icsForFPI = (INDUCE_FMT_PATTERNS>0 && mandAtts>0)? new TreeSet<ICBase>(icQueueCmp): null;
        int singletonCnt = 0;
        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot ta=doc.tokens[i];
            if(ta.acs==null)
                continue;
            for(int j=0;j<ta.acs.length;j++) {
                AC ac=ta.acs[j];
                TIC1 singleton=new TIC1(ac);
                singletonCnt++;
                trellis.add(ac.startToken.idx, ac.startToken.idx+ac.len-1, singleton);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Derived new singleton "+singleton);
                /*
                if(true) {
                    TrellisRecord p1=trellis.get (ac.startToken.idx, ac.startToken.idx+ac.len-1);
                    TrellisRecord p2=trellis.get2(ac.startToken.idx, ac.startToken.idx+ac.len-1);
                    if(p1==null || p1!=p2) {
                        log.LG(Logger.ERR,"Fatal error adding IC to trellis - just added point not present: "+p1+","+p2);
                    }else if(!p1.contains(singleton)) {
                        log.LG(Logger.ERR,"Fatal error adding IC to trellis - point does not contain just added IC");
                    }
                }
                */
                if(icsForFPI!=null) {
                    icsForFPI.add(singleton);
                }
                // only consider ACs of mandatory attributes as seeds if at least 1 AC of a mandatory attribute is found
                if(mandAtts>0 && ac.getAttribute().getMinCardOfRootAttribute()<=0)
                    continue;
                if(!icq.add(singleton))
                    log.LG(Logger.ERR,"Error adding IC to queue");
            }
        }
        int cnt1=icq.size();
        if(log.IFLG(Logger.TRC)) { log.LG(Logger.TRC,"Seed/all singleton ICs before containment="+cnt1+"/"+singletonCnt+":"); dumpICs(icq); }
        
        if(INDUCE_FMT_PATTERNS>0) {
            int frc=induceApplyFmtPatterns(doc, (icsForFPI!=null)? icsForFPI:icq, icq, mandAtts>0); // only icq was here before
            if(frc!=0) {
                log.LG(Logger.ERR,"Error inducing patterns in "+doc.id);
            }
            if(icsForFPI!=null) {
                icsForFPI.clear();
                icsForFPI=null;
            }
        }

        /* solve contained ACs in 1 pass prior to parsing:
           create ICs representing a single complex AC composed of contained ICs */
        int cnt2=addContainedICs(icq);
        if(log.IFLG(Logger.TRC)) { log.LG(Logger.TRC,"Singleton ICs after containment ("+icq.size()+"):"); dumpICs(icq); }
        
        /* precompute best paths through orphaned ACs (embedded in all ICs created so far) 
           so that we can only add their scores during further IC derivation
           all possible ACSegments indexed by different [firstAC.endIdx, secondAC.startIdx] */
        acSegments=new ACSegmentCache(doc, null);
        
        if(PRECOMPUTE_ALL_ACSEGMENTS) {
            int acSegmentCnt=precomputeACSegments(trellis, acSegments);
        }else {
            int acrc=createACLattice(trellis, acSegments);
            if(acrc<0) {
                log.LG(Logger.ERR,"Error computing AC lattice");
            }else {
                log.LG(Logger.USR,"Created AC lattice states="+acrc);
            }
        }
        
        boolean DEBUG=true;
        int dbgStep=10;
        int dbgNext=dbgStep;
        
        int cnt3=0;
        int startICID=ICBase.icidCnt;
        if(GENERATE_INSTANCES>0) {
            long startTime2=System.currentTimeMillis();
            double memUsed=0;
            /* extend each IC in (almost) all possible ways */
            while(!icq.isEmpty()) {
                // remove best and register if valid
                ICBase ic=icq.last();
                icq.remove(ic);
                if(ic.isPruned())
                    continue;
                if(ic.isValid(false, this))
                    validICs.add(ic);
                // extend IC by adding left and/or right ICs, possibly skipping orphaned ICs in between
                cnt3+=deriveICs(ic, icq);

                memUsed=((double) (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())) / (double) Runtime.getRuntime().maxMemory();
                if(interruptRequest>0 || (cnt1+cnt2+cnt3)>=maxICs || 
                   System.currentTimeMillis()-startTime2>=maxParseTime || memUsed>0.85) {
                    interruptRequest=0;
                    if(DEBUG) System.err.print("\n");
                    log.LG(Logger.USR,"Terminating: IC queue size="+icq.size()+", ICs="+(cnt1+cnt2+cnt3)+"(max "+maxICs+"), parsetime="+(System.currentTimeMillis()-startTime2)+", mem used="+String.format("%.3f", memUsed));
                    icq.clear();
                    break;
                }
                if(DEBUG && cnt3>=dbgNext) {
                    //System.err.print("\rDerived ICs="+cnt3+" valid="+validICs.size()+" queue="+icq.size());
                    System.err.print("\rDerived ICs="+cnt3+" valid="+validICs.size()+" queue="+icq.size()+
                        " used="+String.format("%.3f", memUsed)+" free="+Runtime.getRuntime().freeMemory()+" total="+Runtime.getRuntime().totalMemory()+" max="+Runtime.getRuntime().maxMemory());
                    dbgNext+=dbgStep;
                }
            }
            if(DEBUG) {
                System.err.print("\rDerived ICs="+cnt3+" valid="+validICs.size()+" queue="+icq.size()+
                    " used="+String.format("%.3f", memUsed)+" free="+Runtime.getRuntime().freeMemory()+" total="+Runtime.getRuntime().totalMemory()+" max="+Runtime.getRuntime().maxMemory());
            }
        }
        if(DEBUG) System.err.print("\n");
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Generation done");
        
        /* dump valid ICs */
        if(log.IFLG(Logger.TRC)) { log.LG(Logger.TRC,"Valid ICs (partial scores):"); dumpICs(validICs); }
        
        LinkedList<ICBase> prunedValidICs=new LinkedList<ICBase>();
        for(ICBase ic: validICs) {
            ic.applyClassEvidence(false, doc);
            if(ic.combinedProb>ic.clsDef.pruneProb)
                prunedValidICs.add(ic);
            else
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Valid pruned IC="+ic.getIdScore());
        }
        int validCnt=validICs.size();
        validICs.clear();
        if(log.IFLG(Logger.TRC)) {
            StringBuffer b=new StringBuffer(64);
            b.append("Valid pruned ICs (final scores); thresholds: ");
            for(int k=0;k<model.classArray.length;k++)
                b.append(((k>0)?", ":"")+model.classArray[k].name+"="+model.classArray[k].pruneProb);
            log.LG(Logger.TRC, b.toString());
            dumpICs(prunedValidICs);
        }

        log.LG(Logger.USR,"Singleton ICs="+cnt1+", Nested ICs="+cnt2+", LR ICs="+cnt3+", total="+(cnt1+cnt2+cnt3)+", valid="+validCnt+", valid after pruning="+prunedValidICs.size());
        
        /* merge generated IC set into ACLattice */
        if(MERGE_ACS_INTO_ICS) {
            extractableLattice=mergeACLatticeWithICSet(prunedValidICs, doc, acSegments); // AC segment cache needed
        }else {
            // if(log.IFLG(Logger.TRC)) createDumpICLattice(prunedValidICs, doc);
            extractableLattice=mergeACLatticeWithICSetII(prunedValidICs, doc); // acLattice is part of document; cache is not needed
            // if(log.IFLG(Logger.TRC)) log.LGX(Logger.TRC, Lattice.toGraph(extractableLattice[0]) ,"ic_ac.dot");
        }
        
        /* find n best paths of extractables */
        int pathCount=findBestPaths(extractableLattice, doc, nbest, paths);

        int bestPathLen=(pathCount>0)? paths.get(0).states.size() - 2: 0; // -2 because of initial and final null states
        long elapsed=System.currentTimeMillis()-startTime;
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,bestPathLen+" extracted items on best path, time="+(elapsed/1000)+"s");
        
        // debugging only:
        if(true && log.IFLG(Logger.USR) && pathCount>0) {
            StringBuffer b=new StringBuffer(64);
            for(State st: paths.get(0).states) { 
                if(st.data instanceof ICBase) { 
                    if(b.length()>0) b.append(",");
                    b.append(((ICBase)st.data).icid-startICID);
                }
            }
            log.LG(Logger.USR,"ICIDs: "+b);
        }
        
        if(log.IFLG(Logger.TRC)) {
            GState[] icStates=ICLattice.toGraph(prunedValidICs, doc, false, null);
            Lattice lat=new Lattice();
            LinkedList<Path> icPaths=new LinkedList<Path>();
            int icPc=lat.search(icStates,1,icPaths);
            log.LG(Logger.TRC,"Found "+icPc+" pure IC paths.");
            if(icPc>0) { for(State st: icPaths.getFirst().states) { st.addFormat("blue"); } }
            log.LGX(Logger.TRC, Lattice.toGraph(icStates[0]) ,"ic.dot");
            for(GState st: icStates) { st.clear(); }
        }
        if(log.IFLG(Logger.TRC) && pathCount>0) {
            for(State st: extractableLattice) { if(st.color!=null) st.color=st.color.replaceAll(",?filled", ""); }
            for(State st: paths.get(0).states) { st.addFormat("filled"); }
            log.LGX(Logger.TRC, Lattice.toGraph(extractableLattice[0]) ,"ic_ac.dot");
        }
        
        // cleanup local data structures
        prunedValidICs.clear();
        prunedValidICs=null;
        validICs.clear();
        validICs=null;
        icq.clear();
        icq=null;
        candAtts.clear();
        candAtts=null;
        
        // return number of extractables on the best path
        return bestPathLen;
    }

    private int getCandidateAttributes(Document doc, Collection<AttributeDef> attSet) {
        int cnt=0;
        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot ta=doc.tokens[i];
            if(ta.acs==null)
                continue;
            for(int j=0;j<ta.acs.length;j++) {
                AC ac=ta.acs[j];
                if(!attSet.contains(ac.getAttribute())) {
                    attSet.add(ac.getAttribute());
                    cnt++;
                }
            }
        }
        return cnt;
    }

    public int deriveICs(ICBase seed, Set<ICBase> derived) {
        int cnt=0;
        TrellisRecord point=(TrellisRecord) seed.container;
        int seedStart=seed.getStartIdx();
        int seedEnd=seed.getEndIdx();
        crossBlockCnt=seed.getCrossTagCount();
        
        TagAnnot span=doc.getParentBlock(seedStart, seedEnd, -1);
        if(span==null)
            return 0;
        
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Going to extend seed "+seed.getId()+" ("+seedStart+","+seedEnd+") in "+span);
        
        /* Start with the span containing the seed IC.
           In that span, try to add all other neighboring addable ICs.
           Then iteratively enlarge the span according to the DOM structure and repeat. */
        TagAnnot child=null;
        int parentIdx=0;
        int parentCnt=1;
        DOMAlternative doma=null;
        int treePathDistance=0; // levels climbed up so far

        /* lists of ICs already seen during search for expansion of the seed IC,
           used to compute skip penalty for those derived ICs that cover but do not contain seen ICs 
           seen ICs include those queued below */
        //TreeSet<ICBase> seenLeft=new TreeSet<ICBase>(new ICStartIdxCmp());
        //TreeSet<ICBase> seenRight=new TreeSet<ICBase>(new ICEndIdxCmp());
        lastLeftSeg=null;
        lastRightSeg=null;
        boolean greenLeft=true;
        boolean greenRight=true;

        /* queues of ICs that could be added but that reach out of the current span
           they will be tryAdded when span is enlarged enough */
        TreeSet<ICBase> leftQueue=new TreeSet<ICBase>(new ICStartIdxCmp());
        TreeSet<ICBase> rightQueue=new TreeSet<ICBase>(new ICEndIdxCmp());

        int left=seedStart;
        int right=seedEnd;
        while(span!=null) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Searching span "+span.toString()+" ["+span.startIdx+","+span.endIdx+"]");

            // int bailout=0;
            // search left from the already explored area
            int li;
            for(li=left-1; greenLeft && li>=0 && doc.tokens[li].startIdx>span.startIdx; li--) {
                // terminate on too many skipped ICs
                /*
                if(seenLeft.size() - leftQueue.size() > MAX_SKIP_ICS) {
                    bailout++;
                    break;
                }
                 */
                TokenAnnot ta=doc.tokens[li];
                TrellisRecord[] fork=(TrellisRecord[]) ta.userData;
                if(fork==null || fork[0]==null)
                    continue;
                if(doma!=null && !span.hasDescendant(ta))
                    continue;

                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"CHECKING LEFT "+li+" having "+fork[0]);
                if(updateCheckPenalty(seed, li, seedStart, LEFT)==PRC_HIGHPENALTY) {
                    greenLeft=false;
                    break;
                }

                // follow left spoke from fork, trying to add addable ICs
                TrellisRecord tr=fork[0];
                notAddableCnt=0;
                while(tr!=null) {
                    cnt+=mergeICs(seed, tr, LEFT, span.startIdx, leftQueue, derived);
                    if(notAddableCnt==tr.size())
                        break;
                    tr=tr.upLeft;
                }
            }

            // search right from the already explored area
            int ri;
            for(ri=right+1; greenRight && ri<doc.tokens.length && doc.tokens[ri].startIdx<span.endIdx; ri++) {
                // terminate on too many skipped ICs
                /*
                if(seenRight.size() - rightQueue.size() > MAX_SKIP_ICS) {
                    bailout++;
                    break;
                }
                 */
                TokenAnnot ta=doc.tokens[ri];
                TrellisRecord[] fork=(TrellisRecord[]) ta.userData;
                if(fork==null || fork[1]==null)
                    continue;
                if(doma!=null && !span.hasDescendant(ta))
                    continue;

                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"CHECKING RIGHT "+ri+" having "+fork[1]);
                if(updateCheckPenalty(seed, seedEnd, ri, RIGHT)==PRC_HIGHPENALTY) {
                    greenRight=false;
                    break;
                }

                // follow right spoke from fork, trying to add addable ICs
                TrellisRecord tr=fork[1];
                notAddableCnt=0;
                while(tr!=null) {
                    cnt+=mergeICs(seed, tr, RIGHT, span.endIdx, rightQueue, derived);
                    if(notAddableCnt==tr.size())
                        break;
                    tr=tr.upRight;
                }
            }

            // take alternative span to the one last taken
            if(++parentIdx<parentCnt) { // keep the child and follow another parent alternative (e.g. TR or 'TC' for child TD)
                span=child.getParentBlock(parentIdx);
                doma=span.getDOMAlternative();
                continue;
            }

            // too many skipped ICs both on left & right
            //if(bailout==2) {
            if(!greenLeft && !greenRight) {
                span=null;
                continue;
            }

            /* See if the span we are expanding from is a block fmt element; if so,
               then penalize all subsequent instances for spanning out of this block element */
            switch(span.getTagType()) {
            case TagTypeF.CONTAINER:
            case TagTypeF.BLOCK:
                crossBlockCnt++;
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Spanning out of block element "+TagNameF.getSingleton().toString(span.type)+": count="+crossBlockCnt);
                break;
            default:
            }
            
            // span enlargement
            boolean useDOMA=false;
            if(useDOMA) {
                if(++parentIdx<parentCnt) { // keep the child and follow another parent alternative (e.g. TR or 'TC' for child TD)
                    span=child.getParentBlock(parentIdx);
                    doma=span.getDOMAlternative();
                    lastRightSeg=lastLeftSeg=null;
                    continue;
                }
                parentCnt=span.parentBlockCnt();
                parentIdx=0;
                child=span;
                if(parentCnt==1) { // for ambiguous DOMAlternatives, don't update left & right - all repetitive adds wil be refused
                    left=li+1;
                    right=ri-1;
                }else {
                    lastRightSeg=lastLeftSeg=null;
                }
                span=span.getParentBlock(parentIdx); // assume all alternative parents have the same parent: TD-(TR|'TC')-TABLE
                if(span!=null)
                    doma=span.getDOMAlternative();
            }else {
                span=span.getParentBlock(0);
                left=li+1;
                right=ri-1;
            }

            // stop criterion
            if(parentIdx==0) {
                treePathDistance++;
                if(MAX_DOM_CLIMB>=0 && treePathDistance>MAX_DOM_CLIMB) {
                    span=null;
                }
            }
        }
        return cnt;
    }

    /** Checks whether the segment between the seed IC and the to be added IC contains 
     * too many or too probable orphan ACs. Returns PRC_HIGHPENALTY if the orphan segment 
     * seems impossible to be contained in the merged IC; PRC_OK otherwise. 
     * TODO: use some penalty here (e.g. based on training) instead of a boolean decision. */
    public int updateCheckPenalty(ICBase seed, int startIdx, int endIdx, short dir) {
        // get or compute the worst ACSegment between the seed IC and the ICs that are going to be added to it from TrellisRecord
        boolean autoCreate = true; // to be debugged: MAX_SKIP_ICS>1 || MAX_SKIP_ICS<0;
        ACSegment acs=(ACSegment) acSegments.get(startIdx, endIdx, autoCreate);
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"QUERY (updateCheckPenalty) ["+startIdx+","+endIdx+"] = "+acs);
        
        // special more effective treatment if no orphans or only 1 orphan allowed 
//        if(acs==null && !autoCreate) {
//            double logPenalty=0; // determine the worst penalty from the partial ACs
//            AC worstAC=null;
//            for(int i=startIdx+1;i<=endIdx-1;i++) {
//                TokenAnnot ta=doc.tokens[i];
//                if(ta.acs!=null) {
//                    for(int j=0;j<ta.acs.length;j++) {
//                        AC ac=ta.acs[j];
//                        double pen;
//                        if(ac.getEndIdx()<endIdx) {
//                            pen=Math.max(ac.getOrphanProb(), ac.getMistakeProb());
//                        }else {
//                            pen=Math.max(ac.getOrphanProb(), ac.getMistakeProb()) * ((endIdx - ac.getStartIdx()) / ac.len);
//                        }
//                        if(pen < logPenalty) {
//                            logPenalty=pen;
//                            worstAC=ac;
//                        }
//                    }
//                }
//            }
//            if(worstAC!=null) {
//                if(MAX_SKIP_ICS==0 && worstAC.getEndIdx()<endIdx) {
//                    return PRC_HIGHPENALTY;
//                }else {
//                    acs=new ACSegment(new Path(null, logPenalty, false));
//                    acSegments.put(startIdx, endIdx, acs);
//                }
//            }
//        }

        // remember this segment for subsequent AC-less tokens on our way left/right 
        if(acs!=null) {
            if(dir==LEFT)
                lastLeftSeg=acs;
            else // RIGHT
                lastRightSeg=acs;
        }else {
            acs=(dir==LEFT)? lastLeftSeg: lastRightSeg;
            if(acs!=null)
                log.LG(Logger.ERR,"Internal error: missing ACSegment record ["+startIdx+","+endIdx+"] ["+
                        doc.tokens[startIdx]+","+doc.tokens[endIdx]+"] when previous acs="+acs+" dir="+dir);
        }

        // terminate if penalty too high relative to seed
        if(acs!=null) {
            // TODO: fix orphan penalty computation!
            // The following was nonsense, penaltySum was always too high for instances with probs close to 1!
//            double penaltySum=acs.getLatticeProb() + ((seed instanceof TIC2)? ((TIC2)seed).orphanPenaltySum: 0);
//            double seedSum=seed.score; //seed.acSum;
//            if(penaltySum*ORPHAN_PENALTY_FACTOR<seedSum) {
//                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"High penalty acSeg="+acs+" seed="+seed.getId()+" "+seed.score); // seed.getIdScore()
//                return PRC_HIGHPENALTY;
//            }
            if(acs.relevantElementCount>MAX_SKIP_ICS && MAX_SKIP_ICS>=0) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Skipped ICs="+acs.path.length()+" > max "+MAX_SKIP_ICS);
                return PRC_HIGHPENALTY;
            }
        }
        return PRC_OK;
    }

    public int mergeICs(ICBase seed, TrellisRecord tr, short dir, int boundaryIdx, SortedSet<ICBase> queue, Set<ICBase> derived) {
        int cnt=0;
        Iterator<ICBase> icit=tr.iterator();
        ICBase redo=null;
        while(true) {
            ICBase ic=redo;
            if(redo!=null) {
                ic=redo;
                redo=null;
            }else {
                if(!icit.hasNext())
                    break;
                ic=icit.next();
            }
            
            if(!ic.canBeAdded()) {
                notAddableCnt++;
                continue;
            }

            int rc=ICBase.IC_OK;
            // process this IC later?
            if(dir==LEFT) {
                // check for closer ICs in queue; process first closer if any
                if(!queue.isEmpty()) {
                    ICBase queued=queue.last();
                    if(doc.tokens[queued.getStartIdx()].startIdx>=boundaryIdx) {
                        queue.remove(queued);
                        redo=ic; // do this IC in next iteration 
                        ic=queued;
                    }
                }
                // process this IC
                if(doc.tokens[ic.getStartIdx()].startIdx<boundaryIdx) {
                    queue.add(ic);
                    rc=ICBase.IC_LATER;
                }
            }else { // RIGHT
                // check for closer ICs in queue
                if(!queue.isEmpty()) {
                    ICBase queued=queue.last();
                    if(doc.tokens[queued.getEndIdx()].endIdx<=boundaryIdx) {
                        queue.remove(queued);
                        ic=queued;
                        redo=ic; // do this IC in next iteration
                    }
                }
                // process this IC
                if(doc.tokens[ic.getEndIdx()].endIdx>boundaryIdx) {
                    queue.add(ic);
                    rc=ICBase.IC_LATER;
                }
            }
            if(rc==ICBase.IC_OK) {
                // try to merge ICs now, add resulting IC to derived
                rc=merge(seed, ic, dir, derived);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"MERGE rc="+rc+": seed="+seed.getId()+" added="+ic.getId());
            }

            switch(rc) {
            case ICBase.IC_LATER:
                continue;
            default:
            }
            if(rc>0)
                cnt+=rc;
        }
        return cnt;
    }

    /** returns count of added ICs if AC can be added, otherwise negative IC_MAXCARD, IC_OTHERCLASS, IC_AXIOM_NOT_VALID */
    public int merge(ICBase base, ICBase added, short dir, Set<ICBase> derived) {
        int rc=ICBase.IC_OK;
        ACSegment orphans=(dir==LEFT)? lastLeftSeg: lastRightSeg;
        // if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"merge "+base+" + "+added+" orphans:"+orphans);
        AC ac=added.getAC();
        AttributeDef attDef=ac.getAttribute();
        // class must be the same
        if(attDef.myClass!=base.clsDef)
            return ICBase.IC_OTHERCLASS;
        // check whether this might be just a reference to some AC that already is present in the IC (or vice versa)
        boolean treatAsReference=false;
        AC referredBaseAC=null;
        if(ac.refersTo!=null) {
            referredBaseAC=base.getIntersection(ac.refersTo, true);
            treatAsReference=(referredBaseAC!=null);
            if(treatAsReference) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"intersect="+referredBaseAC+"\nof ACs:\n"+Arrays.toString(ac.refersTo.toArray())+"\nwith IC: "+base+"\nadded="+ac);
                // if the newly added referenced AC is better than ACs in base, the merged IC must use it to compute score,
                // otherwise the new AC's prob is not relevant:
                if(ac.getProb()<=referredBaseAC.getProb()) {
                    referredBaseAC=null;
                }
            }
        }
        // check maxCard
        if(!treatAsReference) {
            clearCards(base.clsDef.id);
            base.setCards(cards[base.clsDef.id]);
            rc=added.checkCards(cards[base.clsDef.id]);
            if(rc!=ICBase.IC_OK) { // is ICBase.IC_MAXCARD
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Hit maxcard base="+base.getId()+" added="+added.getId());
                return rc;
            }
        }
        // check whether {this IC+AC} will be valid supposing this IC is valid - 
        // only check class's axioms that apply to the AC being added
        if (!base.clsDef.canBecomeValid(base, ac))
            return ICBase.IC_AXIOM_NOT_VALID;

        if(orphans!=null && orphans.relevantElementCount==0 && Util.equalsApprox(orphans.getLatticeProb(), 0))
            orphans=null;
        
//        if(orphans!=null && Parser.MIXED_MODE!=Parser.MIXED_ALLOW && Util.equalsApprox(orphans.getLatticeProb(), 0))
//            orphans=null;
        // e.g. all states are background but score>0 due to:
        // - partially competing with other ACs, and/or
        // - whole ACs skipped since prob(AC)*(1-engaged)*constant being < 0.5 
//        if(orphans!=null && orphans.relevantElementCount==0) {
//            if(orphans.getLatticeProb()!=0)
//                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Using BG-only orphan segment with non-zero score: "+orphans+", score="+orphans.getLatticeProb());
//        }
        
        // derive merged IC
        TIC2 merged = (dir==LEFT)? 
            new TIC2(added, base, orphans, crossBlockCnt, false, treatAsReference? ICBase.REF_LEFT: ICBase.REF_NONE, referredBaseAC): 
            new TIC2(base, added, orphans, crossBlockCnt, false, treatAsReference? ICBase.REF_RIGHT: ICBase.REF_NONE, referredBaseAC);
        
        // add derived IC to trellis and to agenda 
        if(log.IFLG(Logger.TRC)) {
            log.LG(Logger.TRC,"Derived new "+merged);
            recomputeICScore(merged);
        }

        rc=0;
        if(merged.combinedProb < merged.clsDef.pruneProb) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Pruned IC "+merged);
        }else {
            if(trellis.add(merged)) {
                if(derived.add(merged))
                    rc=1;
                else
                    log.LG(Logger.ERR,"Error adding IC to queue");
            }
        }
        return rc;
    }

    private void recomputeICScore(TIC2 merged) {
        ArrayList<AC> acs=new ArrayList<AC>(16);
        //TreeMap<Integer,AC> bestACforRefGroup=new TreeMap<Integer,AC> 
        double acSum=0.0;
        int acnt=merged.getACs(acs, ICBase.ACS_MEMBERS | ICBase.ACS_NONREF); // | ICBase.ACS_REF 
        for(int i=0;i<acnt;i++) {
            AC ac1=acs.get(i);
            acSum+=ac1.getEngagedProb();
        }
        acs.clear();
        // correct reference score usage:
        double corr=0;
        LinkedList<ICBase> todo=new LinkedList<ICBase>();
        todo.add(merged);
        while(todo.size()>0) {
            ICBase ic=todo.removeFirst();
            if(ic instanceof TIC2) {
                TIC2 ic2=(TIC2) ic;
                if(ic2.referencedBaseAC!=null) {
                    corr-=ic2.referencedBaseAC.getEngagedProb();
                    switch(ic2.reference) {
                    case ICBase.REF_LEFT: corr+=((TIC1)ic2.left).ac.getEngagedProb(); break;
                    case ICBase.REF_RIGHT: corr+=((TIC1)ic2.right).ac.getEngagedProb(); break;
                    default: throw new IllegalArgumentException("No reference when rba="+ic2.referencedBaseAC);
                    }
                }
                todo.add(ic2.left);
                todo.add(ic2.right);
            }
        }
        acSum+=corr;
        //merged.getACs(acs, ICBase.ACS_ORPHANS);
        //for(int i=0;i<acs.size();i++) {
        //    AC ac1=acs.get(i);
        //    acSum+=ac1.getOrphanProb();
        //}
        
        ArrayList<ACSegment> orpSegs=new ArrayList<ACSegment>(16);
        int ocnt=merged.getOrphanSegments(orpSegs);
        for(int i=0;i<ocnt;i++) {
            ACSegment seg=orpSegs.get(i);
            acSum+=seg.getLatticeProb();
        }
        orpSegs.clear();
        
        int attCnt=merged.getACCount();
        double prob=acSum/attCnt;
        double prob2=merged.acSum/attCnt;
        log.LG((merged.acSum==acSum || Math.abs(acSum-merged.acSum)<0.00001)? Logger.TRC: Logger.WRN,
                "recomputed p="+Math.exp(prob)+"("+acSum+
                "), existing="+Math.exp(prob2)+"("+merged.acSum+") acs="+attCnt+" orpSegs="+ocnt);
    
        acs.clear();        
    }

    /* takes all TIC1 ICs and merges them with their nested TIC1s, 
       creating new TIC2s in encapsulating TIC1's TrellisPoint  */
    public int addContainedICs(Set<ICBase> derived) {
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Adding contained ICs");
        int cnt=0;
        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot ta=doc.tokens[i];
            if(ta.userData==null)
                continue;
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Token: "+ta);

            TrellisRecord tr=((TrellisRecord[]) ta.userData)[1];
            while(tr!=null) {
                for(ICBase icb: tr) {
                    if(!(icb instanceof TIC1)) // we could have already added some TIC2 here
                        continue;
                    TIC1 ic=(TIC1) icb;
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Containing: "+ic.getIdScore());
                    int endIdx=ic.getEndIdx();
                    for(int j=i; j<=endIdx; j++) {
                        TokenAnnot ta2=doc.tokens[j];
                        if(ta2.userData==null)
                            continue;
                        TrellisRecord tr2=((TrellisRecord[]) ta2.userData)[1];
                        while(tr2!=null && tr2.rightIdx<=tr.rightIdx) {
                            for(ICBase icb2: tr2) {
                                if(!(icb2 instanceof TIC1)) // we could have already added some TIC2 here
                                    continue;
                                TIC1 ic2=(TIC1) icb2;
                                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Child: "+ic2.getIdScore());
                                if(ic==ic2 || ic2.getEndIdx()>endIdx) // if same or ic2 not child of ic
                                    continue;
                                cnt+=mergeContainedICs(ic, ic2, tr, derived);
                            }
                            tr2=tr2.upRight;
                        }
                    }
                }
                tr=tr.upRight;
            }
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Added "+cnt+" contained ICs");
        return cnt;
    }


    /* merge 2 nested ICs that both represent one AC 
       e.g. product_description AC containing a detailed feature AC
       merged IC2 will be stored in TrellisRecord complex.container */
    public int mergeContainedICs(TIC1 complex, TIC1 child, TrellisRecord tr, Set<ICBase> derived) {
        AttributeDef ad1=complex.getAC().getAttribute();
        AttributeDef ad2=child.getAC().getAttribute();

        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Try merge contained "+complex+" + "+child);

        int maxCount=ad1.canContain(ad2);
        if(maxCount==0)
            return 0;

        // check maxCard
        clearCards(complex.clsDef.id);
        complex.setCards(cards[complex.clsDef.id]);
        int rc=child.checkCards(cards[complex.clsDef.id]);
        if(rc!=ICBase.IC_OK)
            return rc; // is ICBase.IC_MAXCARD;
        // check whether {this IC+AC} will be valid supposing this IC is valid - 
        // only check class's axioms that apply to the AC being added
        if (!complex.clsDef.canBecomeValid(complex, child.getAC()))
            return ICBase.IC_AXIOM_NOT_VALID;

        ICBase merged=new TIC2(complex, child, null, 0, true, ICBase.REF_NONE, null);
        merged.setAC(complex.getAC());
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Derived new container "+merged);
        tr.add(merged);
        if(!derived.add(merged))
            log.LG(Logger.ERR,"Error adding IC to queue");
        return 1;
    }

    /** 1. Induce common formatting patterns present in singleton ICs with prec & recall based on 
        - prec and recall of the induced pattern in existing singleton ICs
        - AC scores
        Formatting pattern is evidence whose
        - precision indicates P(matched area of fmt pattern contains class instance | formatting pattern matches segment of this doc)
        - recall indicates P(formatting pattern matches | instance of class in this doc)
     */
    protected int induceApplyFmtPatterns(Document doc, Set<ICBase> sourceICs, Set<ICBase> icq, boolean queueMandAttrsOnly) {
        /* based on identified ACs, induce local formatting patterns; first for singleton ICs; later for valid ICs */
        LinkedList<FmtPattern> fmtPats=new LinkedList<FmtPattern>();
        FmtPatInducer fpInducer=new FmtPatInducer();
        int fpCnt=fpInducer.induce(sourceICs, doc, fmtPats);
        
        /* update model with new local evidence:
           for single-att patterns, update AttributeDef.prClass (add new local evidence) 
           for multi-attribute patterns, update ClassDef.prClass (dtto) */
        // model.addLocalPatterns(fmtPats); // currently done by adding features to individual ACs
        
        /* create new ACs and rescore existing affected ACs based on new formatting patterns */
        LinkedList<AC> affectedACs=new LinkedList<AC>();
        LinkedList<AC> newACs=new LinkedList<AC>();
        int cnt=fpInducer.applyPatterns(doc, fmtPats, affectedACs, newACs);
        
        /* rescore ICs which have at least one affected AC, re-insert all affected in IC queue */
        int rescoredCnt=0;
        ArrayList<ICBase> rescored=new ArrayList<ICBase>(16);
        HashSet<AC> affectedMap=new HashSet<AC>(affectedACs);
        Iterator<ICBase> it=sourceICs.iterator();
        while(it.hasNext()) {
            ICBase ic=it.next();
            double oldScore=ic.getLatticeProb();
            if(ic.recomputeScore(affectedMap)) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Fmt-boosted/suppressed IC="+ic.getText()+" from "+oldScore+" to "+ic.getLatticeProb());
                if(icq==sourceICs) {
                    it.remove(); // we will add it again below
                    rescored.add(ic);
                }else {
                    if(icq.remove(ic)) {
                        rescored.add(ic);
                    }
                }
                rescoredCnt++;
            }
        }
        for(ICBase ic: rescored) {
            icq.add(ic);
        }
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Fmt-rescored "+rescoredCnt+"("+rescored.size()+" in queue) new ACs (reinserted)");
        //rescored.clear();
        rescored=null;
        //affectedMap.clear();
        affectedMap=null;
        //affectedACs.clear();
        affectedACs=null;

        /* create singleton ICs for the newly created ACs */
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Fmt-induced "+newACs.size()+" new ACs");
        for(AC ac: newACs) {
            boolean addToQueue = queueMandAttrsOnly? (ac.getAttribute().getMinCardOfRootAttribute()>0): true;
            TIC1 singleton=new TIC1(ac);
            trellis.add(ac.startToken.idx, ac.startToken.idx+ac.len-1, singleton);
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Adding to trellis"+(addToQueue?" and queue":"")+" newly discovered singleton IC="+singleton);
            if(addToQueue) {
                if(!icq.add(singleton))
                    log.LG(Logger.ERR,"Error adding new IC to queue");
            }
        }
        return 0;
    }
}
