// $Id: BestParser.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

import java.util.*;
import uep.util.Logger;
import ex.model.*;
import ex.reader.*;
import ex.util.search.*;
import ex.ac.*;

/** Implements a parser that takes the best AC first,
    and successively adds neighboring ACs, creating still larger ICs.
    This is repeated for all ACs.
 */
public class BestParser extends Parser {

    // agendas
    protected ArrayList<ICBase> workingSet;
    protected ArrayList<ICBase> validSet;
    protected ArrayList<AC> acList;
    // ICs generated so far
    protected ICTrie icTrie;

    // currently chosen DOMAlternative if ambiguous
    protected DOMAlternative doma;

    // element path up from the currently examined AC (e.g. span-td-tr-table or span-td-column-table)
    protected Stack tagSpan;

    protected Document doc;

    public BestParser(Model model) {
        super(model);
        workingSet=new ArrayList<ICBase>(256);
        validSet=new ArrayList<ICBase>(256);
        acList=new ArrayList<AC>(256);
        icTrie=new ICTrie();
        doc=null;
        doma=null;
    }

    public int parse(Document d, int nbest, List<Path> paths) {
        doc=d;
        doma=null;
        int cnt=0;
        validSet.clear();
        acList.clear();

        /* sort ACs by their conditional class probs */
        for(int i=0;i<doc.tokens.length;i++) {
            TokenAnnot ta=doc.tokens[i];
            if(ta.acs==null)
                continue;
            for(int j=0;j<ta.acs.length;j++) {
                AC ac=ta.acs[j];
                acList.add(ac);
            }
        }
        Collections.sort(acList);

        /* for each AC, derive a group of ICs based on that AC */
        for(int aci=0; aci<acList.size(); aci++) {
            AC ac=(AC) acList.get(aci);
            /* create ICs based on ac and its closest neighboring ACs */
            workingSet.clear();
            cnt+=deriveICs(ac, workingSet);

            /* dump ICs */
            int fc=workingSet.size();
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"ICs derived from AC "+ac.toString()+":");
            for(int i=0;i<fc;i++) {
                IC ic=(IC) workingSet.get(i);
                String v="";
                if(ic.isValid(false, this)) {
                    validSet.add(ic);
                    v=" valid";
                }
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"IC"+(i+1)+v+": "+ic.toString());
            }
        }

        log.LG(Logger.WRN,"Total "+cnt+" ICs, "+validSet.size()+" of them valid.");
        dumpICs(validSet);

        /* merge generated IC set into ACLattice */
        GState[] extractableLattice=mergeACLatticeWithICSet(validSet, doc, acSegments);
        
        /* find n best paths of extractables */
        int pathCount=findBestPaths(extractableLattice, doc, nbest, paths);

        int bestPathLen=(pathCount>0)? paths.get(0).states.size() - 2: 0; // -2 because of initial and final null states
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,bestPathLen+" valid instances on best path");
        return bestPathLen;
    }

    public int deriveICs(AC seed, List<ICBase> focusSet) {
        int cnt=0;
        int left=seed.startToken.idx, right=left+seed.len;
        TagAnnot span=doc.getParentBlock(seed.startToken, seed.getEndToken(), -1);
        if(span==null)
            return 0;
        IC seedIC=new IC(seed);
        seedIC.setScript();
        icTrie.put(seedIC, true);
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Seed is "+seed.toString());
        focusSet.add(seedIC);
        /*
	  Start with the span containing the seed AC.
	  In that span, try to add all other ACs to seedIC.
	  Then iteratively enlarge the span according to the DOM structure,
	  and for each span, try to add its acs: ic2=ic1+ac.
         */
        TagAnnot child=null;
        int parentIdx=0;
        int parentCnt=1;
        doma=null;
        int treePathDistance=0; // levels climbed up so far
        while(span!=null) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Searching span "+span.toString()+" ["+span.startIdx+","+span.endIdx+"]");
            int li, ri;
            // search left from the already explored area
            for(li=left-1; li>=0 && doc.tokens[li].startIdx>span.startIdx; li--) {
                TokenAnnot ta=doc.tokens[li];
                if(ta.acs==null)
                    continue;
                if(doma!=null && !span.hasDescendant(ta))
                    continue;
                cnt+=deriveICsFromToken(doc.tokens[li], focusSet);
            }
            // search right from the already explored area
            for(ri=right+1; ri<doc.tokens.length && doc.tokens[ri].startIdx<span.endIdx; ri++) {
                TokenAnnot ta=doc.tokens[ri];
                if(ta.acs==null)
                    continue;
                if(doma!=null && !span.hasDescendant(ta))
                    continue;
                cnt+=deriveICsFromToken(doc.tokens[ri], focusSet);
            }
            // span enlargement
            if(++parentIdx<parentCnt) { // keep the child and follow another parent alternative (e.g. TR or 'TC' for child TD)
                span=child.getParentBlock(parentIdx);
                doma=span.getDOMAlternative();
                continue;
            }
            parentCnt=span.parentBlockCnt();
            parentIdx=0;
            child=span;
            if(parentCnt==1) { // for ambiguous DOMAlternatives, don't update left & right - all repetitive adds wil be refused
                left=li+1;
                right=ri-1;
            }
            span=span.getParentBlock(parentIdx); // assume all alternative parents have the same parent: TD-(TR|'TC')-TABLE
            if(span!=null)
                doma=span.getDOMAlternative();
            if(parentIdx==0) {
                treePathDistance++;
                if(MAX_DOM_CLIMB>=0 && treePathDistance>MAX_DOM_CLIMB) {
                    span=null;
                }
            }
        }
        return cnt;
    }

    public int deriveICsFromToken(TokenAnnot ta, List<ICBase> focusSet) {
        int cnt=0;
        for(int j=0;j<focusSet.size();j++) { // all ICs we generated here so far
            IC ic=(IC) focusSet.get(j);
            for(int k=0;k<ta.acs.length;k++) { // all ACs at token
                AC ac=ta.acs[k];
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Found for adding: "+ac.toString());
                // see if we have this IC+AC already (having another AC as seed)
                IC derivedIC=icTrie.get(ic,ac);
                if(derivedIC!=null)
                    continue; // don't create another IC just because the seed is different (to be discussed)

                /* can we add this IC+AC?
		        int rc=ic.canAdd(ac);
		        if(rc!=IC.IC_OK)
		            continue;
		        derivedIC=new IC(ic, ac); 
                 */

                // add IC+AC if possible
                int rc=ic.tryAdd(ac, focusSet);
                if(rc<=0) {
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Refused rc="+IC.RC2STR[-rc]);
                    continue;
                }
                if(rc!=1)
                    log.LG(Logger.ERR,"Unexpected number of generated ICs="+rc);
                derivedIC=(IC) focusSet.get(focusSet.size()-1);
                cnt++;
                icTrie.put(derivedIC, true);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Derived IC="+derivedIC.toString());
            }
        }
        return cnt;
    }


}
