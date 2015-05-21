// $Id: PhraseTrie.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import uep.util.Logger;
import ex.util.*;

/** A PhraseTrie extends the get capabilities of IntTrie.
 */
public class PhraseTrie extends IntTrie {
    private static final long serialVersionUID = -6014435175653031613L;

    public PhraseTrie(IntTrie par, int lab) {
        super(par, lab);
    }

    protected IntTrie newChild(int label) {
        return new PhraseTrie(this, label);
    }

    public int get(AbstractToken[] s, int startIdx, int length, boolean getPrefixes, int matchMode, NBestResult res) {
        if(length==-1)
            length=s.length-startIdx;
        if(startIdx<0 || startIdx>=s.length || length>s.length-startIdx) {
            Logger.LOG(Logger.ERR,"Invalid parameters startIdx="+startIdx+",length="+length+",tokens="+s.length);
            return PhraseBook.ERR;
        }
        return findLongestPrefix(s, startIdx, length, matchMode, matchMode, getPrefixes, res);
    }

    public PhraseInfo getLemma(AbstractToken[] s) {
        return findLongestLemmaPrefix(s, 0);
    }

    public int findLongestPrefix(AbstractToken[] s, int pos, int maxLen, int matchMode, int matchLevel, boolean getPrefixes, NBestResult res) {
        if(Logger.IFLOG(Logger.MML)) Logger.LOG(Logger.MML,"flp pos="+pos+",maxLen="+maxLen+",matchLevel="+matchLevel);
        // end of the prefix: we found the full string
        if(maxLen==0) {
            if(data==null)
                matchLevel=PhraseBook.MATCH_PREFIX;
            res.add(data, matchLevel, this);
            return matchLevel;
        }

        // we accept as exact match also keys shorter than maxLen
        if(getPrefixes && data!=null) {
            res.add(data, matchLevel, this);
            if(next==null)
                return matchLevel;
        }

        // end of the prefix: there is nowhere to go
        if(next==null) {
            return (res.length>0)? PhraseBook.MATCH_PREFIX: PhraseBook.NOMATCH;
        }

        int rc=PhraseBook.NOMATCH;
        // search for the exact word and its right extension
        int i=binarySearch(s[pos].getTokenId());
        if(i>=0) {
            matchLevel=PhraseBook.MATCH_EXACT; // hack
            rc=((PhraseTrie)next[i]).findLongestPrefix(s, pos+1, maxLen-1, matchMode, matchLevel, getPrefixes, res);
        }
        // (rc==PhraseBook.MATCH_EXACT && res.items.length>1) || (matchMode>PhraseBook.MATCH_EXACT)
        if(matchMode==PhraseBook.MATCH_EXACT || (rc==PhraseBook.MATCH_EXACT && res.items.length==1))
            return rc;

        // search for lowercased word and its right extension
        i=binarySearch(s[pos].getLCId());
        if(i>=0) {
            if(matchLevel<PhraseBook.MATCH_IGNORECASE)
                matchLevel=PhraseBook.MATCH_IGNORECASE;
            int rc2=((PhraseTrie)next[i]).findLongestPrefix(s, pos+1, maxLen-1, matchMode, matchLevel, getPrefixes, res);
            if(rc2<rc)
                rc=rc2;
        }
        if(matchMode<=PhraseBook.MATCH_IGNORECASE)
            return rc;

        // search for lemmatized word and its right extension
        i=binarySearch(s[pos].getLemmaId());
        if(i>=0) {
            if(matchLevel<PhraseBook.MATCH_LEMMA)
                matchLevel=PhraseBook.MATCH_LEMMA;
            int rc2=((PhraseTrie)next[i]).findLongestPrefix(s, pos+1, maxLen-1, matchMode, matchLevel, getPrefixes, res);
            if(rc2<rc)
                rc=rc2;
        }
        return rc;
    }

    public PhraseInfo findLongestLemmaPrefix(AbstractToken[] s, int pos) {
        // end of the prefix: we found the full string
        if(pos>=s.length)
            return (PhraseInfo)data; // return null;
        // end of the prefix: there is nowhere to go
        if(next==null)
            return (PhraseInfo)data; // return null;
        // search for the exact word and its right extension
        int lemmaId=s[pos].getMostGeneralId();
        int i=binarySearch(lemmaId);
        if(i>=0) {
            return ((PhraseTrie)next[i]).findLongestLemmaPrefix(s, pos+1);
        }
        return (PhraseInfo)data; // return null;
    }
}
