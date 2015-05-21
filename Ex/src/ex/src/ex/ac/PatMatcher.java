// $Id: PatMatcher.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * @author Martin Labsky labsky@vse.cz
 */

import java.io.*;
import java.util.*;

import uep.util.*;

import ex.reader.TokenAnnot;
import ex.reader.Document; // only for logging

public class PatMatcher {
    // matchers available for matching subpatterns
    protected static ArrayList<PatMatcher> freeMatchers=new ArrayList<PatMatcher>(8);

    ArrayList<TNode> openNodes; // the last added TNode is added by FAState.accept() on success, if FAState has outgoing links
    ArrayList<TNode> openNodes2;
    SortedSet<TNode> finalNodes; // as above, but only if some of the next FAStates is marked final (actually stores pre-final states)
    //protected TNode startNode;
    protected FASimpleTokenState query;
    protected FAState[] temp=new FAState[1];
    protected TNode longestFoundNode;
    
    protected static ArrayList<TNode> freeNodes=new ArrayList<TNode>(16);
    protected static Logger log;

    public static synchronized PatMatcher getMatcher() {
        int n=freeMatchers.size();
        if(n>0)
            return (PatMatcher) freeMatchers.remove(n-1);
        return new PatMatcher();
    }
    public static synchronized TNode newNode(FAState st, TNode prev, int advance) {
        TNode n;
        if(freeNodes.size()>0) { 
            n=freeNodes.remove(freeNodes.size()-1);
            n.set(st, prev, advance);
        }else {
            n=new TNode(st, prev, advance);
        }
        return n;
    }
    public static synchronized void freeNode(TNode n) {
        n.clear();
        freeNodes.add(n);
    }
    public static synchronized void freeNodes(Collection<TNode> nodes) {
        for(TNode n: nodes) {
            n.clear();
        }
        freeNodes.addAll(nodes);
        nodes.clear();
    }

    public static synchronized void disposeMatcher(PatMatcher mat) {
        freeMatchers.add(mat);
    }

    public PatMatcher() {
        Logger.init("patmatcher.log", -1, -1, null);
        if(log==null)
            log=Logger.getLogger("PatMatcher");
        openNodes=new ArrayList<TNode>(16);
        openNodes2=new ArrayList<TNode>(16);
        finalNodes=new TreeSet<TNode>(new Comparator<TNode>() {
            public int compare(TNode n1, TNode n2) {
                return n1.hashCode()-n2.hashCode();
            }
        } );
        query=new FASimpleTokenState(-1);
        longestFoundNode=null;
        //startNode=new TNode(null, null, 0);
    }

    public int match(TokenAnnot[] tokens, int startIdx, TokenPattern pat) {
        // start with pattern's start node at startIdx
        if(startIdx>=tokens.length) {
            log.LG(Logger.WRN,"Pattern '"+pat.toString()+"' tried to match offset "+startIdx+" of doc len= "+tokens.length+"");
            return 0;
        }
        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"matching '"+pat.id+"' at "+startIdx+" ("+tokens[startIdx]+")");
        // TNode startNode=new TNode(pat.fa.startState, null, 0);
        longestFoundNode=null;
        TNode startNode=PatMatcher.newNode(pat.fa.startState, null, 0);
        finalNodes.clear(); // clear any leftovers from last run
        openNodes.clear();
        openNodes2.clear();
        openNodes.add(startNode);
        while(!openNodes.isEmpty()) {
            /* for each open node, follow all next FAStates that match the token(s) 
	           positioned at startIdx+openNode.pathLen+1 */
            if(openNodes2.size()>0) {
                log.LG(Logger.ERR,"openNodes2.size="+openNodes2.size());
                openNodes2.clear();
            }
            int n=openNodes.size();
            for(int i=0;i<n;i++) {
                TNode prev=(TNode)openNodes.get(i);
                if(prev==null)
                    continue;
                if(prev.state.nextArcs==null)
                    continue;
                int offset = startIdx + prev.pathLen;
//                if(offset >= tokens.length && prev.state.type!=FAState.ST_NULL)
//                    continue; // we reached end of doc with this prev node
                if(pat.maxLen>0 && prev.pathLen>pat.maxLen)
                    continue; // match too long
                
                int from=0;
                
                // 1. search efficiently through following simple tokens (if any); these may include the loopback arc
                FASimpleTokenState matchedTokenState=null;
                if(prev.state.nextArcs.tokenStateCount()>0 && offset<tokens.length) {
                    query.tokenId=tokens[offset].ti.getTokenIdToMatch(pat.matchFlags);
                    matchedTokenState=prev.state.nextArcs.findState(query);
                    if(matchedTokenState!=null) {
                        from=-1;
                        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"Found "+prev.state+"->"+matchedTokenState);
                    }else {
                        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"No path from "+prev.state);
                    }
                }
                
                // 2. search inefficiently through all other following states
                FAState[] next=prev.state.nextArcs.getNextStates();
//              if(next==null)
//                  continue;
                // int from=0;
                // int to=prev.state.next.length-1;
                // boolean delCon=false;

                // are we in a looping state?
                int firstArcLoopCnt=-1;
                boolean denyLoop=false;
                boolean denyNonLoop=false;
                if(prev.state.minCnt!=-2) {
//                    int loopCnt=0;
//                    if(prev.constraints==null) {
//                        // followed loop 0 times so far; add this constraint to all next nodes after taking the loop
//                        prev.constraints=new TConstraints(4);
//                        prev.constraints.add(prev.state, 0);
//                    }else {
//                        loopCnt=prev.constraints.get(prev.state);
//                        if(loopCnt==-1) { // constraints exist but do not yet contain info about this node
//                            prev.constraints.add(prev.state, 0);
//                            loopCnt=0;
//                        }
//                    }
                    firstArcLoopCnt=0;
                    if(prev.loopBackCnt < prev.state.minCnt) { // must only follow the loop, cannot exit
                        // all next nodes will get prev's constraints
                        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"looparc{"+prev.state.minCnt+","+prev.state.maxCnt+"} from "+prev.state.data+" to "+prev.state.nextArcs.getLoopArc().data+" forced, loopcnt="+prev.loopBackCnt);
                        // to=0;
                        denyNonLoop=true;
                        FAState curr=prev.state.nextArcs.getLoopArc();
                        if(curr==null) {
                            log.LG(Logger.ERR, "Internal matcher error 1");
                            continue;
                        }
                        temp[0]=curr;
                        next=temp;
                        firstArcLoopCnt=prev.loopBackCnt+1;
                        // prev.constraints.inc(prev.state);
                    }
                    else if(prev.loopBackCnt >= prev.state.maxCnt) { // must exit, cannot follow the loop
                        // loose the constraint for all next nodes
                        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"looparc{"+prev.state.minCnt+","+prev.state.maxCnt+"} from "+prev.state.data+" to "+prev.state.nextArcs.getLoopArc().data+" exiting, loopcnt="+prev.loopBackCnt);
                        // from=1;
                        denyLoop=true;
                        // prev.constraints.remove(prev.state);
                    }
                    else { // can exit, can follow the loop
                        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"looparc{"+prev.state.minCnt+","+prev.state.maxCnt+"} from "+prev.state.data+" to "+prev.state.nextArcs.getLoopArc().data+" optional, loopcnt="+prev.loopBackCnt);
                        // prev.constraints.inc(prev.state);
                        firstArcLoopCnt=prev.loopBackCnt+1;
                        // delCon=true;
                    }
                }

                // try expanding each next node of the chosen open node
                boolean eod = offset >= tokens.length;
                for(int j=from;j<next.length;j++) {
                    FAState curr;
                    if(j==-1)
                        curr=matchedTokenState;
                    else
                        curr=next[j];
                    if(eod && curr.type!=FAState.ST_NULL)
                        continue; // at end of document, only a null state can possibly match
                    boolean isLoopBack = false;
                    if(firstArcLoopCnt!=-1) {
                        isLoopBack = (curr==prev.state.nextArcs.getLoopArc());
                        if(isLoopBack) {
                            // do not follow this loop anymore
                            if(denyLoop)
                                continue;
                        }else {
                            // exiting nodes: clear loopBackCnt
                            prev.loopBackCnt=0;                            
                        }
                    }
                    int prevCnt=openNodes2.size();
                    // curr.accept() adds all matching paths' final nodes; next can be e.g. FATokenNode, FAGroupNode, or FATrieNode
                    int rc=curr.accept(tokens, startIdx, prev, openNodes2, pat.matchFlags);
                    if(rc>=0 && rc!=openNodes2.size()-prevCnt) {
                        log.LG(Logger.ERR,"State's accept rc="+rc+" but new nodes="+(openNodes2.size()-prevCnt)+":"+curr);
                        rc=openNodes2.size()-prevCnt;
                    }
                    if(rc>0) {
                        // set loopBackCnt of all new TNodes
                        if(firstArcLoopCnt!=-1) { // && prev.loopBackCnt>0) {
                            // looped nodes: 1 higher than their predecessor
                            // exited nodes: clear loopBackCnt
                            for(int k=0;k<rc;k++) {
                                TNode loopedNode=openNodes2.get(prevCnt+k);
                                if(isLoopBack)
                                    loopedNode.loopBackCnt=firstArcLoopCnt;
                                else
                                    loopedNode.loopBackCnt=0;
                            }
                        }
                        // acceptor was FA's finalNode: add accepted nodes to finalNodes
                        if(curr==pat.fa.finalState) {
                            for(int k=0;k<rc;k++) {
                                TNode justFoundNode=openNodes2.get(prevCnt+k);
                                if(justFoundNode.pathLen<=pat.maxLen && justFoundNode.pathLen>=pat.minLen) {
                                    finalNodes.add(justFoundNode);
                                    if(longestFoundNode==null || longestFoundNode.pathLen < justFoundNode.pathLen)
                                        longestFoundNode=justFoundNode;
                                    if(pat.logLevel!=0) {
                                        log.LG(Logger.USR,"M "+pat.id+"["+finalNodes.size()+"]: "+Document.toStringDbg(tokens, startIdx, justFoundNode.pathLen, " ")+"["+startIdx+","+justFoundNode.pathLen+"]");
                                    }
                                }
                            }
                        }
                    }
                    //if(delCon) { // delete constraint after processing 0th loop arc
                    //    prev.constraints.remove(prev.state);
                    //    delCon=false;
                    //}
                }
                
                if(!finalNodes.contains(prev)) {
                    freeNode(prev);
                }
            }
            // reuse:
            // freeNodes(openNodes);
            openNodes.clear();
            ArrayList<TNode> tmp=openNodes;
            openNodes=openNodes2;

            // looks like the following typically helps (unsignificantly)
            //if(log.IFLG(Logger.TRC)) {
                int len=openNodes.size();
                //HashMap<TNode,Integer> dups=new HashMap<TNode,Integer>(8);
                //ArrayList<TNode> copy=new ArrayList<TNode>(len);
                //copy.addAll(openNodes);
                for(int i=0;i<len;i++) {
                    //TNode n1=copy.get(i);
                    TNode n1=openNodes.get(i);
                    if(n1==null)
                        continue;
                    //int cnt=0;
                    for(int j=i+1;j<len;j++) {
                        //TNode n2=copy.get(j);
                        TNode n2=openNodes.get(j);
                        if(n2==null)
                            continue;
                        if(n1.loopBackCnt==n2.loopBackCnt &&
                           n1.state==n2.state &&
                           n1.pathLen==n2.pathLen &&
                           n1.annots==n2.annots) {
                            //cnt++;
                            //copy.set(j, null);
                            openNodes.set(j, null);
                        }
                    }
                    //if(cnt>0) {
                        //dups.put(n1, cnt);
                        //log.LG(Logger.TRC,"TNodeDUPL\n"+cnt+"x:"+pat.id+"."+n1.state+"["+n1.pathLen+"]L"+n1.loopBackCnt);
                    //}
                }
            //}
            
            openNodes2=tmp;
        }
        // cleanup non-final nodes
        // freeNodes(openNodes); // is empty since we exited the loop above
        // openNodes.clear();
        return finalNodes.size();
    }

    /* adds last found matches to TokenAnnots that correspond to the TokenInfos used in match */
    public int applyMatches(TokenAnnot[] tokens, int startIdx, TokenPattern pat, List<PatMatch> matches) {
        int cnt=0;
        if((pat.flags & TokenPattern.FLAG_GREEDY) !=0) {
            if(longestFoundNode!=null)
                cnt+=addMatch(longestFoundNode, cnt+1, tokens, startIdx, pat, matches);
        }else {
            for(TNode finalNode: finalNodes) {
                cnt+=addMatch(finalNode, cnt+1, tokens, startIdx, pat, matches);
            }
        }
        longestFoundNode=null;
        freeNodes(finalNodes); // reuse final nodes
        return cnt;
    }

    private int addMatch(TNode finalNode, int no, TokenAnnot[] tokens, int startIdx,
            TokenPattern pat, List<PatMatch> matches) {
        int cnt=0;
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"PATMATCH["+no+"] = "+Document.toStringDbg(tokens, startIdx, finalNode.pathLen, " ")+" ("+pat+")");
        if(!pat.isMatchValid(tokens, startIdx, finalNode)) {
            return 0;
        }
        PatMatch match=new PatMatch(pat,startIdx,finalNode.pathLen);
        cnt+=Document.addMatch(match, tokens, startIdx);
        // check if any subannots exist
//        TNode n=finalNode;
        // We collect all annots from finalNode only, no backtracking over the tree
        // since we do not keep the tree to startNode, we only assemble annots and
        // constraints in the last Node.
        // while(n!=null) {
//        if(n!=null) {
//            if(n.annots!=null && n.annots.annotations!=null) {
//                for(int j=0;j<n.annots.annotations.length;j++) {
//                    Annotable an=n.annots.annotations[j];
//                    if(an instanceof PatSubMatch) {
//                        PatSubMatch psm=(PatSubMatch) an;
//                        psm.setParent(match);
//                        if(psm.getType()==0) { // $ not $1 $2 etc.
//                            int idxSubStart=n.pathLen - an.getLength();
//                            cnt+=Document.addMatch(psm, tokens, idxSubStart);
//                        }
//                    }
//                    children.add(an);
//                }
//            }
//            // n=n.prevNode;
//        }
        TAnnots ans=finalNode.annots;
        int anc=0;
        while(ans!=null) {
            anc++;
            ans=ans.prev;
        }
        if(anc>0) {
            // match.addChildren(children);
            match.children=new Annotable[anc];
            ans=finalNode.annots;
            int anc2=anc;
            while(ans!=null) {
                if(ans.annot instanceof PatSubMatch) {
                    PatSubMatch psm=(PatSubMatch) ans.annot;
                    psm.setParent(match);
                    if(psm.getType()==0) { // $ not $1 $2 etc.
                        int idxSubStart=psm.getStartIdx();
                        cnt+=Document.addMatch(psm, tokens, idxSubStart);
                    }
                }
                match.children[--anc2]=ans.annot;
                ans=ans.prev;
            }
            if(anc2!=0) {
                throw new IllegalArgumentException("Illegal annotation count: expected "+anc+" found="+(anc-anc2));
            }
            // derive match weight based on how probable child annotations are 
            match.computeMatchLevel();
        }
        if(matches!=null) {
            matches.add(match);
        }
        return cnt;
    }
    
    public static void main(String args[]) {
        Options o=Options.getOptionsInstance();
        if ((args.length >= 2) && args[0].toLowerCase().equals("-cfg")) {
            try { o.load(new FileInputStream(args[1])); }
            catch(Exception ex) { }
        }
        o.add(0, args);

        TokenAnnot[] tokens=null;
        PatMatcher m=new PatMatcher();
        TokenPattern pat=new TokenPattern("pat_karel", "karel");
        pat.minLen=1;
        pat.maxLen=5;
        FA fa=new FA();
        fa.startState=new FATokenState();
        pat.fa=fa;
        m.match(tokens, 0, pat);
        m.applyMatches(tokens, 0, pat, null);
    }

}
