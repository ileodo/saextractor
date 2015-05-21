// $Id: Parser.java 1986 2009-04-22 13:05:03Z labsky $
package ex.parser;

import java.util.*;

import uep.util.*;

import ex.model.*;
import ex.reader.*;
import ex.util.*;
import ex.util.search.*;
import ex.ac.*;
import ex.features.TokenTypeF;

/** Groups document's ACs into ICs.
 */
public abstract class Parser {

    public int GENERATE_INSTANCES = 1;
    public int MAX_DOM_CLIMB = -1;
    public int MAX_SKIP_ICS  =  2;
    public double ORPHAN_PENALTY_FACTOR = 1.0;
    public int INDUCE_FMT_PATTERNS = 1;
    public int ACSEG_PRUNE_FACTOR  =  10;
    public int ACSEG_INC_PART_ACS = 1;
    public int MAX_IC_CNT = 5000; // max 5000 IC candidates to be generated
    public int MAX_PARSE_TIME = 1*60*1000; // max. 1 minute
    public boolean PRECOMPUTE_ALL_ACSEGMENTS=false; // keep this false from now on
    
    public boolean MERGE_ACS_INTO_ICS = false;

    /** Beam width per trellis point: relative to the best IC confidence in point and max absolute number. */
    public static double BEAM_WIDTH_REL=0.4;
    public static int BEAM_WIDTH_ABS=10;

    /** Whether to allow standalone attribute occurrences inside instances. */
    public static final byte MIXED_DENY=0;
    public static final byte MIXED_ALLOW=1;
    public static byte MIXED_MODE=MIXED_DENY;
    
    // can be trained if training data:
    public static double IC_SPAN_BLOCK_TAG_PENALTY = 0.95; // P(attribute values span over one more block element | class) 

    // IC score=geomean(AC scores) using logsum/n, path score=sum(IC scores), don't care about orphans outside path ICs
    public static final int SM_GEOMEAN_SUM=1;
    // IC score=logsum(AC scores), path score=sum(IC scores) + sum(log(AC scores)) for ACs outside path, using subtraction 
    public static final int SM_LOGSUM_SUM=2;

    public static int SCORING_METHOD=SM_LOGSUM_SUM;
    // public static int SCORING_METHOD=SM_GEOMEAN_SUM;
    
    public static final int CM_MEAN=1;
    public static final int CM_GEOMEAN=2;
    public static final int CM_PR=3;
    public static final int CM_PSEUDOBAYES=4;
    public static int COMBINE_METHOD=CM_PSEUDOBAYES;
    
    // parser search strategy
    public static final int PS_BEST=1;
    public static final int PS_LR=2;

    // parsing routine return codes (positive values are reserved for returning counts)
    public static final int PRC_OK          = -1;
    // penalty exceeded when deriving ICs from a seed IC; i.e. too many orphaned ACs between seed IC and ICs being added
    public static final int PRC_HIGHPENALTY = -2;
    // too many ICs recently examined for adding to seed are not addable
    public static final int PRC_NOTADDABLE  = -3;

    protected Model model;
    // reusable data structures
    protected short[][] cards;    
    protected static Logger log;
    protected ACSegmentCache acSegments;
    protected ACScoreGetter acProbGetter=new ACScoreGetter();

    // >0 means external interrupt, we should terminate the parse and return what we have
    protected int interruptRequest;
    protected int maxICs;
    protected long maxParseTime; // ms
    
    protected CountConstraintFactory searchConstraints;

    public Parser(Model model) {
        this.model=model;
        if(log==null)
            log=Logger.getLogger("Parser");
        Options o=Options.getOptionsInstance();
        GENERATE_INSTANCES = o.getIntDefault("parser_gen_inst", GENERATE_INSTANCES);
        MAX_DOM_CLIMB = o.getIntDefault("parser_max_dom_climb", MAX_DOM_CLIMB);
        INDUCE_FMT_PATTERNS = o.getIntDefault("induce_fmt_patterns", INDUCE_FMT_PATTERNS);
        ACSEG_PRUNE_FACTOR = o.getIntDefault("acsegment_prune_factor", ACSEG_PRUNE_FACTOR);
        ACSEG_INC_PART_ACS = o.getIntDefault("acsegment_inc_part_acs", ACSEG_INC_PART_ACS);
        MAX_SKIP_ICS = o.getIntDefault("parser_max_skip", MAX_SKIP_ICS);
        ORPHAN_PENALTY_FACTOR = o.getDoubleDefault("parser_orphan_penalty_factor", ORPHAN_PENALTY_FACTOR);
        MIXED_MODE=(byte)o.getIntDefault("parser_mixed_mode", MIXED_MODE);
        maxICs=o.getIntDefault("max_ic_cnt", MAX_IC_CNT);
        maxParseTime=o.getIntDefault("max_parse_time", MAX_PARSE_TIME);
        BEAM_WIDTH_REL=o.getDoubleDefault("parser_beam_width_rel", BEAM_WIDTH_REL);
        BEAM_WIDTH_ABS=o.getIntDefault("parser_beam_width_abs", BEAM_WIDTH_ABS);
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Initialized with MAX_DOM_CLIMB="+MAX_DOM_CLIMB);

        cards=new short[model.classArray.length][];
        for(int i=0;i<model.classArray.length;i++) {
            ClassDef cd=model.classArray[i];
            cards[i]=new short[cd.attArray.length];
        }
        searchConstraints=new CountConstraintFactory(model);
        interruptRequest=0;
    }
    
    /** Frees references to all intermediate data structures related to the current document 
     *  and invokes garbage collector. */
    public void clear() {
        acSegments.clear();
        searchConstraints=new CountConstraintFactory(model);
    }

    // factory method
    public static Parser getParser(int strategy, Model model) {
        Parser p=null;
        switch(strategy) {
        case PS_BEST:
            p=new BestParser(model);
            break;
        case PS_LR:
            p=new LRParser(model);
            break;
        }
        return p;
    }

    // to be implemented by children
    public abstract int parse(Document d, int nbest, List<Path> paths);

    public void dumpICs(Collection<ICBase> ics) {
        int i=0;
        for(ICBase ic: ics) {
            log.LG(Logger.TRC,(++i)+". "+ic.getIdScore());
        }
    }
    
    public GState[] createDumpICLattice(Collection<ICBase> icSet, Document doc) {
        // whole ICs may be skipped by arcs in lattice (gaps may be filled later in insertOrphanStates)
        GState[] icStates=ICLattice.toGraph(icSet, doc, false, null);
        if(log.IFLG(Logger.TRC)) {
            String graph=Lattice.toGraph(icStates[0]);
            log.LG(Logger.TRC,"Valid IC Lattice graph:\n"+graph);
            graph=CaseUtil.removeAccents(graph);
            log.LGX(Logger.TRC,graph,"ic.dot");
        }
        return icStates;
    }

    /** Creates a lattice of non-overlapping ICs from an IC set, merges it with ACSegments lattice */
    public GState[] mergeACLatticeWithICSet(Collection<ICBase> icSet, Document doc, ACSegmentCache acSegCache) {
        GState[] icStates=createDumpICLattice(icSet, doc);

        boolean addOrphans=(SCORING_METHOD==SM_LOGSUM_SUM)? true: false;
        GState[] extractableStates;
        if(addOrphans) {
            // icStates=icl.insertOrphanStates(icStates, doc);
            extractableStates=ICLattice.insertOrphanStatesII(icStates, acSegments);
            if(log.IFLG(Logger.TRC)) {
                String graph=Lattice.toGraph(icStates[0]);
                log.LG(Logger.TRC,"Valid IC+AC Lattice graph:\n"+graph);
                graph=CaseUtil.removeAccents(graph);
                log.LGX(Logger.TRC,graph,"ic_ac.dot");
            }
        }else {
            extractableStates=icStates;
        }
        return extractableStates;
    }
    
    public int findBestPaths(GState[] extractableLattice, Document doc, int nbest, List<Path> paths) {
        // search for n-best paths
        Lattice lat;
        if(searchConstraints.hasConstraints()) {
            lat=new ConstrainedLattice(searchConstraints);
        }else {
            lat=new Lattice();
        }
        int pc=lat.search(extractableLattice,nbest,paths);
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Found "+pc+" best paths.");
        if(SCORING_METHOD==SM_LOGSUM_SUM) { // convert from log domain
            // ICLattice.fromLogDomain(extractableLattice);
        }
        for(int i=0;i<pc;i++) {
            Path path=paths.get(i);
            if(SCORING_METHOD==SM_LOGSUM_SUM) { // convert from log domain
                // path.score=Math.exp(path.score);
            }
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Path "+i+"/"+pc+":\n"+path.toString());
        }
        return pc;
    }

    // creates html table representation of the given path
    public static String pathToTable(Path instPath) {
        int len=instPath.length();
        if(len<=2)
            return "<div>No instances found!</div>";
        StringBuffer buff=new StringBuffer(len);
        buff.append("<table style=\"border-color:black;border-style:solid\"><tr><th>Path score "+
                String.format("%.4f",Math.exp(instPath.score/ACSegment.getRelevantElementCount(instPath)))+"/"+
                String.format("%.4f",instPath.score)+"</th></tr>\n");
        int i=0;
        for(State st: instPath.states) {
            i++;
            if(i==1 || i==len)
                continue; // ignore 1st and last null states
            //Extractable obj=(Extractable) st.data;
            Object obj=(Object) st.data;
            if(obj instanceof ICBase) {
                buff.append(((ICBase) obj).toTable());
            }else { // AC or a sequence of ACs (ACSegment or in alt setup Path)
                buff.append("<tr><td colspan=\"2\">"+obj+"</td></tr>\n");
            }
        }
        buff.append("</table>\n");
        return buff.toString();
    }

    public static int addPathComponents(Path instPath, Collection<ICBase> ics, Collection<AC> acs) {
        int len=instPath.length();
        if(len<=2)
            return 0;
        int cnt=0;
        for(State st: instPath.states) {
            Object obj=st.data;
            if(obj instanceof ICBase) {
                ICBase ic=(ICBase) obj;
                ics.add(ic);
                cnt++;
                if(Parser.MIXED_MODE==Parser.MIXED_ALLOW) {
                    cnt+=ic.getACs(acs, ICBase.ACS_ORPHANS);
                }
            }else if(obj instanceof AC) {
                if(((GState)st).type!=GState.ST_BG) { // exclude bg states (only include GState.ST_AC)
                    acs.add((AC)obj);
                    cnt++;
                }
            }else if(obj instanceof ACSegment) {
                cnt+=((ACSegment) obj).getACs(acs, ICBase.ACS_ORPHANS);
            }else if(obj instanceof Path) {
                cnt+=ACSegment.getACs((Path) obj, acs, ICBase.ACS_ORPHANS);
            }else {
                // ignore 1st and last null states, as well as any other bg null states
                if(obj!=null)
                    Logger.LOG(Logger.ERR,"Unknown path component "+obj);
            }
        }
        return cnt;
    }

    public void clearCards(int clsId) {
        Arrays.fill(cards[clsId], (short)0);
    }

    /** sets sum to myScore for those states which do not have predecessors
     *  within this range (i.e. for all initial states in this range) 
     */
    public static void setInitialProbs(GState[] states, int start, int end) {
        for(int i=start;i<=end;i++) {
            GState s=states[i];
            boolean initial=true;
            if(s.prev!=null) {
                for(int j=0; j<s.prev.length; j++) {
                    if(((GState)s.prev[j]).startIdx >= start) {
                        initial=false;
                        break;
                    }
                }
            }
            if(initial)
                s.sum = s.myScore;
        }
    }
    
    /** Creates a token-based lattice containing all ACs found in document so far.
     * The lattice allows for passing through each complete AC (AC's orphan prob is used) 
     * and also for avoiding it by going through the background states (the mistake prob
     * of the most probable AC at that token is used).
     */
    public int createACLattice(Trellis acTrellis, ACSegmentCache acSegments) {
        ACLattice lat=new ACLattice(
                new DynState(null, 0.0, -1, -1, GState.ST_INI),
                new DynState(null, 0.0, Integer.MAX_VALUE, Integer.MAX_VALUE, GState.ST_FIN));
        acSegments.setACLattice(lat);
        Document doc=acTrellis.doc;
        
        // LinkedList<ICBase> ics=new LinkedList<ICBase>(); // all single-AC ICs
        // int acCnt=acTrellis.getItems(0, doc.tokens.length-1, ics);

        // Document only has ACs, above we also got ICs with contained ACs
        LinkedList<Extractable> acList=new LinkedList<Extractable>();
        int acCnt=doc.getACs(acList);
        
        TokenTypeF ttf=TokenTypeF.getSingleton();
        int punctuationTokenType=ttf.fromString("P");
        
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Precomputing ACLattice; total acs="+acCnt);
        int i;
        for(i=0;i<acCnt;i++) {
            // AC ac=ics.get(i).getAC();
            AC ac=(AC) acList.get(i);
            // 1. create 1 state for this as orphaned AC; 
            // 2. create/update 2 BG states around this ac so we can connect the orphaned ac state
            // 3. create/update ac.len BG states for this AC being a mistake (score/=ac.len);
            int startIdx=ac.getStartIdx();
            int endIdx=ac.getEndIdx();
            double acProb = (GENERATE_INSTANCES>0)? ac.getOrphanProb(): ac.getLatticeProb();
            DynState sAC=new DynState(ac, acProb, startIdx, endIdx, GState.ST_AC);
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Created sac "+sAC.label());
            
            DynState preState=null;
            if(startIdx==0) {
                preState=(DynState) lat.ini;
            }else {
                TokenAnnot prevTok=doc.tokens[startIdx-1];
                if(prevTok.acStates==null) {
                    preState=new DynState(null, 0.0, startIdx-1, startIdx-1, GState.ST_BG);
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Created left "+preState.label());
                    prevTok.acStates=new ArrayList<GState>(2);
                    prevTok.acStates.add(preState);
                }else {
                    preState=(DynState) prevTok.acStates.get(0); // 1st is the single BG state
                    if(preState.type!=GState.ST_BG) {
                        log.LG(Logger.ERR,"ACLattice mismatch (preState should be bg): "+preState.label());
                    }
                }
            }
            preState.addNext(sAC, false);
            
            GState postState=null;
            if(endIdx==doc.tokens.length-1) {
                postState=lat.end;
            }else {
                TokenAnnot postTok=doc.tokens[endIdx+1];
                if(postTok.acStates==null) {
                    postState=new DynState(null, 0.0, endIdx+1, endIdx+1, GState.ST_BG);
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Created right "+postState.label());
                    postTok.acStates=new ArrayList<GState>(2);
                    postTok.acStates.add(postState);
                }else {
                    postState=(DynState) postTok.acStates.get(0); // 1st is the single BG state
                    if(postState.type!=GState.ST_BG) {
                        log.LG(Logger.ERR,"ACLattice mismatch (postState should be bg): "+postState.label());
                    }
                }
            }
            sAC.addNext(postState, false);
            
            /* When multiple ACs compete over one token, the AC with the highest per-token score 
               will provide the BG state for this token. */
            // double condProb=ac.getLatticeProb();
            double fracCondProb=ac.getLatticeProb() / (double)ac.len;
            double fracMistScore=ac.getMistakeProb() / (double)ac.len;
            for(int idx=startIdx;idx<=endIdx;idx++) {
                TokenAnnot tok=doc.tokens[idx];
                double thisTokenMistScore=fracMistScore;
                if(tok.ti.intVals.get(ttf.id)==punctuationTokenType) {
                    thisTokenMistScore=0; // do not penalize for skipping punctuation
                }
                if(tok.acStates==null) {
                    DynState sBG=new DynState(ac, thisTokenMistScore, idx, idx, GState.ST_BG);
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Created mist "+sBG.label());
                    tok.acStates=new ArrayList<GState>(2);
                    tok.acStates.add(sBG);
                }else {
                    DynState sBG=(DynState) tok.acStates.get(0);
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Examining BG "+sBG.label());
                    if(sBG.type!=GState.ST_BG) {
                        log.LG(Logger.ERR,"ACLattice mismatch (state should be bg): "+sBG.label());
                    }
                    // TODO: involves log, precompute in AC somewhere
                    double currBGProb = - Double.MAX_VALUE;
                    // currBGProb = ((AC) sBG.data).getLatticeProb()
                    if(sBG.data!=null) { // this is bg state based on another ac, or this is just a fork state with 0.0 score
                        currBGProb = ((AC) sBG.data).getLatticeProb() / ((AC) sBG.data).len;
                    }
                    if(fracCondProb > currBGProb) { // relink the BG state to the current stronger AC
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Replacing mist "+sBG.label()+" state.myScore="+String.format("%.4f", sBG.myScore));
                        sBG.data=ac;
                        sBG.myScore=thisTokenMistScore;
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"with mist "+sBG.label()+" state.myScore="+String.format("%.4f", sBG.myScore));
                    }
                }
                tok.acStates.add(sAC); // for each member token, add reference to this ac
            }
        } // for all ACs
        
        // link BG states into a line; insert bg null state between adjacent ACs 
        // (we could also directly connect adjacent ACs but this is good for ac segment precomp using single best path)
        DynState last=(DynState) lat.ini;
        int lastEndedCnt=0;
        for(int idx=0;idx<doc.tokens.length;idx++) {
            TokenAnnot tok=doc.tokens[idx];
            int curEndedCnt=0;
            if(tok.acStates==null || tok.acStates.size()==0) {
                lastEndedCnt=0;
                continue;
            }
            int sz=tok.acStates.size();
            // link BG
            DynState bg=(DynState) tok.acStates.get(0);
            if(bg.type!=GState.ST_BG) {
                log.LG(Logger.ERR,"ACLattice mismatch II (state should be bg): "+bg.label());
            }
            last.addNext(bg, false);
            // if there are ACs starting here and other ACs ending in the previous token, then 
            // - connect directly (no)
            // - insert extra "BG null" state which is between the 2 BG states
            DynState bgNullState=null;
            for(int j=1;j<sz;j++) {
                DynState st=(DynState) tok.acStates.get(j);
                if(st.endIdx==idx) {
                    curEndedCnt++;
                }
                if(st.startIdx==idx && lastEndedCnt>0) {
                    if(bgNullState==null) {
                        // idxs not relevant, position is between idx-1 and idx, let's e.g. put there idx
                        bgNullState=new DynState(null, 0.0, idx, idx, GState.ST_NBG);
                        tok.precBgNullState=bgNullState;
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Created NBG "+bgNullState.label()+" between \n"+last.label()+" and \n"+bg.label());
                        List<GState> prevTokenStates=doc.tokens[idx-1].acStates;
                        // just check
                        if(last!=prevTokenStates.get(0)) {
                            log.LG(Logger.ERR,"Internal error generating AC Lattice: "+last+"!="+prevTokenStates.get(0));
                        }
                        // steal last background states's outgoing links
                        bgNullState.nextArcs=last.nextArcs;
                        last.nextArcs=null;
                        last.addNext(bgNullState, false);
                        // redirect all previous ending states to "BG null" instead of bg
                        int psz=prevTokenStates.size();
                        for(int k=0;k<psz;k++) {
                            DynState prevState=(DynState) prevTokenStates.get(k);
                            if(prevState.endIdx==idx-1) {
                                if(prevState.nextArcs!=null)
                                    prevState.nextArcs.clear();
                                prevState.addNext(bgNullState, false);
                            }
                        }
                    }
                    // bgNullState.addNext(st, false); already present in the next copied from last
                    /*
                        TokenAnnot lastTok=doc.tokens[idx-1];
                        int lastSz=lastTok.acStates.size();
                        for(int k=1;k<lastSz;k++) {
                            DynState sAdjAC=(DynState) lastTok.acStates.get(k);
                            if(sAdjAC.endIdx==idx-1) {
                                sAdjAC.addNext(st, false);
                            }
                        }
                    */
                }
            }
            last=bg;
            lastEndedCnt=curEndedCnt;
        }
        last.addNext(lat.end, false);
        
        // commit all DynStates
        int bCnt=0, tCnt=0;
        ((DynState)lat.ini).commit();
        tCnt+=lat.ini.next.length;
        for(i=0;i<doc.tokens.length;i++) {
            TokenAnnot tok=doc.tokens[i];
            if(tok.acStates!=null) {
                bCnt++;
                int sz=tok.acStates.size();
                //aCnt+=sz-1;
                for(int j=0;j<sz;j++) {
                    DynState st=(DynState) tok.acStates.get(j);
                    if(st.type==GState.ST_AC && st.startIdx!=i) // count AC multiword AC state just once; it is referred to multiple times 
                        continue;
                    st.commit();
                    tCnt+=st.next.length;
                }
                // BG null state
                GState bg=tok.acStates.get(0);
                // if(bg.next[0].data==null && bg.next[0].next!=null) {
                if(((GState) bg.next[0]).type==GState.ST_NBG) {
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Commiting BGNULL state "+bg.next[0].label());
                    ((DynState) bg.next[0]).commit();
                    tCnt+=bg.next[0].next.length;
                    bCnt++;
                }
            }
        }
        doc.acLattice=lat;
        log.LG(Logger.USR,"ACLattice created; BG states="+bCnt+", AC states="+acCnt+", total="+(2+bCnt+acCnt)+", arcs="+tCnt);
        
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Computing best path through ACLattice");
        //LinkedList<Path> bestPath=new LinkedList<Path>();
        GState[] states=new GState[2+bCnt+acCnt];
        states[0]=lat.ini;
        states[states.length-1]=lat.end;
        int cnt=1;
        for(i=0;i<doc.tokens.length;i++) {
            TokenAnnot tok=doc.tokens[i];
            if(tok.acStates==null)
                continue;
            GState bg=tok.acStates.get(0);
            states[cnt++]=bg;

            int sz=tok.acStates.size();
            for(int j=1;j<sz;j++) {
                DynState st=(DynState) tok.acStates.get(j);
                if(st.type==GState.ST_AC && st.startIdx!=i) // count AC multiword AC state just once; it is referred multiple times 
                    continue;
                states[cnt++]=st;
            }

            if(((GState) bg.next[0]).type==GState.ST_NBG) {
                states[cnt++]=(GState) bg.next[0];
            }
        }
        cnt++;
        if(cnt!=states.length) {
            log.LG(Logger.ERR,"Internal error computing state array for ACLattice, actual state cnt="+cnt+" supposed="+states.length);
        }
        Lattice latUtils=new Lattice();
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"AC lattice graph:\n"+Lattice.toGraph(states[0]));
        Lattice.populatePrev(states);
        latUtils.forwardSearch(states, 0, states.length-1);
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"searching for path between ["+lat.ini.endIdx+","+lat.end.startIdx+"]");
        // backtrack through the whole lattice
        LinkedList<Path> acPath=new LinkedList<Path>();
        int pc=latUtils.backtrack(lat.end, false, acPath);
        if(pc!=1) {
            log.LG(Logger.INF,"Error searching for best path through ACLattice rc="+pc);
            return -1;
        }
        Path best=acPath.get(0);
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Found "+pc+" best path through ACLattice: "+best);
        // color best path and dump ac lattice
        if(log.IFLG(Logger.TRC)) {
            for(State st: best.states) {
                st.addFormat("filled");
            }
            String graph=Lattice.toGraph(states[0]);
            log.LG(Logger.TRC,"AC lattice graph with sums:\n"+graph);
            graph=CaseUtil.removeAccents(graph);
            log.LGX(Logger.TRC,graph,"aclat_sums.dot");
        }
        // save array form of lattice for later use
        doc.acLattice.states=states;
        return cnt;
    }
    
    public int precomputeACSegments(Trellis acTrellis, ACSegmentCache acSegments) {
        Document doc=acTrellis.doc;
        IntTrie acSegmentCache=new IntTrie(null,-1); // cache of unique ACSegments indexed by state ids of the segment's best path 
        //int[] segKey=new int[2];
        LinkedList<ICBase> ics=new LinkedList<ICBase>(); // all single-AC ICs
        int acCnt=acTrellis.getItems(0, doc.tokens.length-1, ics);
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Precomputing AC segment orphan probs; total acs="+acCnt);
        ICLattice icl=new ICLattice();
        // use ACScoreGetter to read conditional probability of IC's head AC (rather than IC latticeProbs)
        GState[] states=icl.toGraph(ics, doc, false, acProbGetter);
        LinkedList<Path> acPath=new LinkedList<Path>();
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Created AC lattice "+ICLattice.toString(states));
        Lattice lat=new Lattice();
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"AC lattice graph: \n"+Lattice.toGraph(states[0]));
        int uniqCnt=0, rangeCnt=0;
        OrphanRescorer resc=new OrphanRescorer();

        int lastEndIdx=-2;
        for(int i=0;i<states.length;i++) {
            GState start=states[i];
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Creating ACSegments following AC: "+start.data);
            // count each container only once
            if(start.endIdx==lastEndIdx)
                continue;
            lastEndIdx=start.endIdx;
            // container key
            // segKey[0]=start.endIdx;
            // precompute forward probs
            Lattice.clear(states, 0, i, -Double.MAX_VALUE);
            Lattice.clear(states, i+1, states.length-1, 0);
            //setInitialProbs(states, i+1, states.length-1);
            if(ACSEG_INC_PART_ACS!=0) {
                for(int k=i+1;k<states.length;k++) {
                    GState crossed=states[k];
                    if(crossed.startIdx>start.endIdx)
                        break;
                    if(crossed.endIdx>start.endIdx) {
                        double part=(double) (crossed.endIdx-start.endIdx) / (double) (crossed.endIdx-crossed.startIdx+1);
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"next state spans "+part+" of its length behind the current state; next="+crossed);
                        if(part>0.0) {
                            crossed.myScore = crossed.myScore * part;
                        }else {
                            crossed.myScore = -Double.MAX_VALUE;
                        }
                    }
                }
            }
            lat.forwardSearch(states, i, states.length-1);
            if(ACSEG_INC_PART_ACS!=0) {
                for(int k=i+1;k<states.length;k++) {
                    GState crossed=states[k];
                    if(crossed.startIdx>start.endIdx)
                        break;
                    if(crossed.endIdx>start.endIdx) {
                        crossed.myScore = ((ICBase)crossed.data).getLatticeProb();
                    }
                }
            }
            
            // find CES - ClosestEndingSuccessor, i.e. the first state that 1) follows after start and 2) ends first
            int j=i+1;
            while(j<states.length && states[j].startIdx<=start.endIdx)
                j++;
            if(j==states.length)
                break;
            GState ces=states[j];
            int idx=j;
            j++;
            while(j<states.length && states[j].startIdx <= ces.endIdx) {
                if(states[j].endIdx < ces.endIdx) {
                    ces=states[j];
                    idx=j;
                }
                j++;
            }
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Found CES: "+ces.data);
            // find the first state that follows after CES ends
            if(ACSEG_INC_PART_ACS==0) {
                while(idx<states.length && states[idx].startIdx<=ces.endIdx) {
                    idx++;
                }
            }
            if(idx==states.length)
                break;
            // try as end states all states that start after the CES ends (try just one per position)
            double baseScore=-1.0;
            int computedSegments=0;
            int lastStartIdx=-2;
            for(j=idx;j<states.length;j++) {
                GState end=states[j];
                if(end.startIdx==lastStartIdx)
                    continue;
                lastStartIdx=end.startIdx;
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"searching for path between ["+start.endIdx+","+end.startIdx+"] ["+i+","+j+"]");
                // backtrack only until one of the cleared states is reached, include it and terminate
                int cnt=lat.backtrack(end, true, acPath);
                if(cnt!=1) {
                    log.LG(Logger.ERR,"Found "+cnt+" paths between ["+i+","+j+"]");
                    continue;
                }
                Path p=acPath.removeFirst();
                // get rid of initial and/or final null states, if present
                //trimNullStates(p);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Found acseg path: "+p);
                
                // remove border states which don't belong to container's content (not included in Path.score)
                p.states.removeFirst();
                p.states.removeLast();

                if(p.states.size()==0) {
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Path has no elements; ACSegment not created");
                    continue;
                }
                
                // replace conditional AC probabilities by orphan probabilities
                if(GENERATE_INSTANCES>0) {
                    p.score = resc.rescore(p);
                }
                
                // penalize ACSegments for not explaining overlapping parts of neighoring ACs
                if(ACSEG_INC_PART_ACS!=0) {
                    // check how much of the first AC is included in this ACSegment and only
                    // include the corresponding part of its score
                    GState partialState=(GState) p.states.getFirst();
                    while(partialState.startIdx<=start.endIdx) {
                        double relPart = (double)(partialState.endIdx-start.endIdx) / (double)(partialState.endIdx-partialState.startIdx+1);
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"ACSegment includes "+relPart+" of border state "+partialState.data+"\nACSeg="+p);
                        AC ac=((ICBase)partialState.data).getAC();
                        double acProb=(GENERATE_INSTANCES>0)? ac.getOrphanProb(): ac.getLatticeProb();
                        p.score -= (1.0-relPart) * acProb;
                        if(relPart>0.0) { // can end at the border of our segment or even before it
                            break;
                        }else {
                            p.states.removeFirst();
                            if(p.states.size()==0)
                                break;
                            partialState=(GState) p.states.getFirst();
                        }
                    }                       
                    if(p.states.size()==0) {
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Path has no elements; ACSegment not created");
                        continue;
                    }
                    // check how much of the last AC is included in this ACSegment
                    // if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Cmpr lastAC.endIdx with end.startIdx: ");
                }
                
                int[] ids=ACSegment.path2ids(p);
                ACSegment seg=(ACSegment) acSegmentCache.get(ids);
                if(seg==null) {
                    seg=new ACSegment(p);
                    acSegmentCache.put(ids, seg);
                    uniqCnt++;
                }
                //segKey[1]=end.startIdx;
                acSegments.put(start.endIdx, end.startIdx, seg);
                if( (start.endIdx>=seg.getStartIdx() && ACSEG_INC_PART_ACS==0) || end.startIdx<=seg.getEndIdx()) {
                    log.LG(Logger.ERR,"Internal error: misplaced ACSegment:");
                }
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"CONTAINER ["+start.endIdx+","+end.startIdx+"] has best path "+seg);
                rangeCnt++;

                // don't compute all possible ACSegments
                computedSegments++;
                //log.LG(Logger.USR,"ACSegment space: segs="+computedSegments+", bscore="+baseScore+", score="+p.score);
                if(j==idx) {
                    baseScore=p.score;
                }else {
                    if(p.score/ACSEG_PRUNE_FACTOR < baseScore) {
                        log.LG(Logger.USR,"Pruning precomputed ACSegment space: segs="+computedSegments+", bscore="+baseScore+", score="+p.score);
                        break;
                    }
                }
            }
        }

        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Precomputed "+uniqCnt+" unique AC segments, used in "+rangeCnt+" ranges");
        return uniqCnt;
    }

    void trimNullStates(Path p) {
        GState s=(GState) p.states.getFirst();
        if(s.startIdx==-1 && s.endIdx==-1)
            p.states.removeFirst();
        s=(GState) p.states.getLast();
        if(s.startIdx==Integer.MAX_VALUE && s.endIdx==Integer.MAX_VALUE)
            p.states.removeLast();
    }

    public boolean interrupt(int req) {
        interruptRequest=req;
        return true;
    }

    public void setMaxICs(int max) {
        maxICs=max;
    }

    public void setMaxParseTime(long max) {
        maxParseTime=max;
    }
    
    /** Creates a lattice of non-overlapping ICs from an IC set, merges it with ACSegments lattice.
     * Returns a lattice of DynStates pointing to Extractables - ACs and ICs. */
    public GState[] mergeACLatticeWithICSetII(Collection<ICBase> icSet, Document doc) {
        // prune ICs - done in LRParser instead
        List<ICBase> icList=new ArrayList<ICBase>(icSet.size());
/*
        if(IC_PRUNE_THRESHOLD>0) {
            Iterator<ICBase> icit=icSet.iterator();
            while(icit.hasNext()) {
                ICBase ic=icit.next();
                if(ic.score>IC_PRUNE_THRESHOLD) {
                    icList.add(ic);
                }
            }
        }else {
*/
        icList.addAll(icSet);
/*
        }
        log.LG(Logger.WRN,"Original "+icSet.size()+" valid ICs pruned to "+icSet.size()+" ICs");
*/

        // if no ICs, return original AC lattice created in createACLattice
        if(icList.size()==0)
            return doc.acLattice.states;
        
        // sort by startIdx
        Collections.sort(icList, ICBase.icDocOrdCmp);
        
        // insert IC states into AC lattice
        log.LG(Logger.USR,"Merging "+icList.size()+" ICs into AC lattice ("+doc.acLattice.states.length+" states)");
        LinkedList<GState> justAdded=new LinkedList<GState>();
        for(int i=0;i<icList.size();i++) {
            ICBase ic=icList.get(i);
            int si=ic.getStartIdx();
            int ei=ic.getEndIdx();
            DynState icState=new DynState(ic, ic.score, ic.getStartIdx(), ic.getEndIdx(), GState.ST_IC);
            justAdded.add(icState);
            TokenAnnot sta=doc.tokens[si];
            TokenAnnot eta=doc.tokens[ei];
            // register with start token
            sta.acStates.add(icState);
            // connect to preceding and following BG or NBG
            DynState precBgState=(DynState) sta.precBgNullState;
            // TODO: store bgnulls so that we do not have to search for them using this for cycle
            if(precBgState==null) {
                if(si>0) { // TODO: (FIXED) this is wrong it leaves out the previous NBG state we must register NBGs with tokens as well
                    precBgState=(DynState) doc.tokens[si-1].acStates.get(0);
                }else {
                    precBgState=(DynState) doc.acLattice.ini;
                }
            }
            DynState follBgState;
            if(((GState) eta.acStates.get(0).next[0]).type==GState.ST_NBG) {
                follBgState=(DynState) eta.acStates.get(0).next[0];
            }else {
                if(ic.getEndIdx()<doc.tokens.length-1) {
                    follBgState=(DynState) doc.tokens[ei+1].acStates.get(0);
                }else {
                    follBgState=(DynState) doc.acLattice.end;
                }
            }
            precBgState.addNext(icState, false);
            icState.addNext(follBgState, false);
        }
        // insert new elements into state array
        GState[] oldStates=doc.acLattice.states;
        GState[] states=new GState[oldStates.length+justAdded.size()];
        Iterator<GState> justAddedIt=justAdded.iterator();
        GState nextICState=justAddedIt.next();
        int j=0;
        for(int i=0;i<oldStates.length;i++) {
            GState st=oldStates[i];
            // insert as the last state in token's sequence
            while(nextICState!=null && nextICState.startIdx<st.startIdx) {
                ((DynState)nextICState).commit();
                states[j++]=nextICState;
                nextICState=justAddedIt.hasNext()? justAddedIt.next(): null;
            }
            ((DynState)st).commit();
            states[j++]=st;
        }
        // recompute prev links, clear all forwards left from AC only computations
        Lattice.populatePrev(states);
        Lattice.clear(states, 0);
        
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Merge done");
        return states;
    }
    
    /** For each AC ac, populates ac.refersTo sets with possible references found in the document.
     * Returns the number of found reference groups (consisting of two or more referenced ACs). */
    public int resolveReferences(Document d) {
        int cnt=0;
        LinkedList<AC> induced=new LinkedList<AC>();
        for(int i=0; i<d.tokens.length; i++) {
            AC[] acs=d.tokens[i].acs;
            if(acs==null)
                continue;
            for(AC first: acs) {
                if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"First="+first.getNameText()+"["+first.getStartIdx()+","+first.getEndIdx()+"]");
                for(int k=first.getEndIdx()+1; k<d.tokens.length; k++) {
                    AC[] acs2=d.tokens[k].acs;
                    if(acs2==null)
                        continue;
                    for(AC second: acs2) {
                        byte rc=first.references(second, induced);
                        switch(rc) {
                        case AC.REF_YES:
                        case AC.REF_INVERSE:
                        case AC.REF_BIDIRECTIONAL:
                            first.addReferenceTo(second);
                            second.addReferenceTo(first);
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Reference "+first+" <-> "+second);
                            cnt++;
                            break;
                        case AC.REF_NO:
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"! Reference "+first+" <-> "+second);
                            break;
                        case AC.REF_SPECIALIZED:
                            AC newAC=induced.getLast();
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Reference-specialized: "+newAC+" from "+first+" <-> "+second);
                            break;
                        default:
                            if(log.IFLG(Logger.ERR)) log.LG(Logger.ERR,"Unknown reference rc="+rc);
                            break;
                        }
                    }
                }
            }
        }
        // re-run only for the induced ACs and add them 
        if(induced.size()>0) {
            for(int i=0; i<d.tokens.length; i++) {
                AC[] acs=d.tokens[i].acs;
                if(acs==null)
                    continue;
                for(AC existing: acs) {
                    for(AC specialized: induced) {
                        byte rc=specialized.references(existing, null);
                        switch(rc) {
                        case AC.REF_YES:
                        case AC.REF_INVERSE:
                        case AC.REF_BIDIRECTIONAL:
                            specialized.addReferenceTo(existing);
                            existing.addReferenceTo(specialized);
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Reference-specialized2: "+specialized.getNameText()+" <-> "+existing.getNameText());
                            cnt++;
                            break;
                        case AC.REF_NO:
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"! Reference "+specialized.getNameText()+" <-> "+existing.getNameText());
                            break;
                        default:
                            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Unknown reference rc="+rc);
                            break;
                        }
                    }
                }
            }
            d.addAC(induced);
        }
        return cnt;
    }
}

/*
class ICDocOrderComparator implements Comparator<ICBase> {
    public int compare(ICBase a, ICBase b) {
        return a.getStartIdx() - b.getStartIdx();
    }
    public boolean equals(ICBase a, ICBase b) {
        return (a.getStartIdx()==b.getStartIdx());
    }
}
*/

/*
GState lastBGState=lat.ini; 
for(int i=0;i<doc.tokens.length;i++) {
    AC[] acs=doc.tokens[i].acs;
    if(acs==null || acs.length==0)
        continue;
    // ArrayList<ACPtr> aps=doc.tokens[i].acPtrs;
    // if(aps==null || aps.length==0)
    //    continue;
    if()
}
*/