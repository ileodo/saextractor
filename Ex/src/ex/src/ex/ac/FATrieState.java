package ex.ac;

/** 
 * State accepting 1 prase from PhraseBook
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

import ex.reader.TokenAnnot;
import ex.train.PhraseBook;
import ex.train.PhraseInfo;
import ex.train.NBestResult;
import uep.util.Logger;

/** Matches a single phrase from PhraseBook */
public class FATrieState extends FAState {
    protected PhraseBook book;
    protected boolean ignoreLemma;
    protected boolean ignoreCase;

    public FATrieState(PhraseBook book, boolean ignoreCase, boolean ignoreLemma, Object data) {
        super(ST_TRIE,data);
        this.book=book;
        this.ignoreCase=ignoreCase;
        this.ignoreLemma=ignoreLemma;
    }

    public int accept(TokenAnnot[] tokens, int startIdx, TNode prev, List<TNode> newNodes, int matchFlags) {
        if(PatMatcher.log.IFLG(Logger.MML)) PatMatcher.log.LG(Logger.MML,"Matching "+book.getName()+" at "+startIdx+"-"+prev.pathLen);
        if(startIdx+prev.pathLen>=tokens.length) {
            PatMatcher.log.LG(Logger.WRN,"TrieState partial match continues behind doc");
            return 0;
        }

        NBestResult res=new NBestResult(10); // get max 10-best matched phrases
        int rc=book.get(tokens, startIdx, -1, true, res); // accept partial match (otherwise we would be matching the full doc phrase)
        switch(rc) {
        case PhraseBook.MATCH_EXACT:
            break;
        case PhraseBook.MATCH_IGNORECASE:
            if(ignoreCase)
                break;
            return 0;
        case PhraseBook.MATCH_LEMMA:
            if(ignoreLemma)
                break;
            return 0;
        case PhraseBook.MATCH_PREFIX:
        case PhraseBook.NOMATCH:
            return 0;
        }
        // add matched phrases
        for(int i=0;i<res.length;i++) {
            PhraseInfo matched=(PhraseInfo) res.items[i];
            if(PatMatcher.log.IFLG(Logger.TRC)) PatMatcher.log.LG(Logger.TRC,"TrieState matched "+i+": "+matched.toString());
            // newNodes.add(new TNode(this,prev,matched.tokens.length));
            newNodes.add(PatMatcher.newNode(this, prev, matched.tokens.length));
        }
        return res.length;
    }
}
