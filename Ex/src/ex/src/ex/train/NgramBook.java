// $Id: NgramBook.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.TreeMap;

import uep.data.AddableDataSet;
import uep.util.Logger;
import uep.util.ObjectRef;
import uep.util.Options;

import ex.features.FM;
import ex.features.TokenNgramF;
import ex.model.AttributeDef;
import ex.model.ModelElement;
import ex.reader.TokenAnnot;
import ex.train.NgramInfo.LLI;
import ex.train.NgramInfo.LLI2;

public class NgramBook<INTERNAL,EXTERNAL> implements PhraseBook<INTERNAL,EXTERNAL>, AddableDataSet {
    NgramCounts counts;
    PhraseBookValueAdapter<INTERNAL,EXTERNAL> valueAdapter;
    int maxLen;
    String name;
    TrieItem root;
    TrieItem matchedItem; // last trie item matched
    int matchedCnt;  // count of items matched
    int lookupMode;
    boolean fillMatchedPhrases;

    // read/write operations store here the trie items matched on the way to matchedItem
    // we need this is because we do not want to track parent for each trie element
    // this makes NgramBook thread-unsafe
    // private transient TrieItem[] matchedItems; // can't allocate generic array of TrieItem<T> in java
    private transient Object[] matchedItems;
    
    static TokenCmp tokCmp=new TokenCmp();

    public NgramBook(String name) {
        this(name, new DefaultPhraseBookAdapter<INTERNAL,EXTERNAL>());  
    }
    
    public NgramBook(String name, PhraseBookValueAdapter<INTERNAL,EXTERNAL> valueAdapter) {
        this.name=name;
        this.valueAdapter=valueAdapter;
        init();
    }
    
    private void init() {
        matchedItem=null;
        matchedCnt=0;
        maxLen=0;
        counts=new NgramCounts();
        root=new TrieItem(null,null);
        lookupMode=PhraseBook.MATCH_EXACT;
        fillMatchedPhrases=true;
        // how can this be done in java?
        // 1. can't do generic array like new TrieItem<T>[16] in java, 
        // neither new NgramBook<T>.TrieItem[16]
        //  matchedItems = new NgramBook<INTERNAL,EXTERNAL>.TrieItem[16];
        // 2. can't cast - we get ClassCastException: [Ljava.lang.Object for:
        //  matchedItems = (NgramBook<INTERNAL,EXTERNAL>.TrieItem[]) new Object[16];
        // 3. the only thing left - there must be some other solution but I do not see it now
        matchedItems = new Object[16];
    }
    
    public void clear() {
        init();
    }
    
    public String toString() {
        return "ngrambook_"+name+"_sz"+counts.ngrCnt;
    }
    
    public void writeTo(BufferedWriter out) throws IOException {
        PhraseBookWriter w=new PhraseBookWriterImpl();
        w.write(this, out);
    }
    
    public byte getType() {
        return TYPE_NGRAM;
    }
    
    public void setLookupMode(int mode) {
        lookupMode=mode;
    }
    
    public int getLookupMode() {
        return lookupMode;
    }
    
    public void setFillMatchedPhrases(boolean fill) {
        fillMatchedPhrases=fill;
    }
    
    public boolean getFillMatchedPhrases() {
        return fillMatchedPhrases;
    }
    
    /** Adds all phrases from source dataSet, which must be an NgramBook, 
     *  to this NgramBook. User data associated with the added phrases, 
     *  accessible via AbstractPhrase.getData(), is added by reference 
     *  unless this NgramBook's PhraseBookValueAdapter does not copy it. */
    public void addAll(AddableDataSet dataSet) {
        NgramBook<INTERNAL,EXTERNAL> toAdd = (NgramBook<INTERNAL,EXTERNAL>) dataSet;
        for(AbstractPhrase phr: toAdd) {
            // TODO: NgramBook methods should accept AbstractPhrase not PhraseInfo.
            // But we know that AbstractPhrases coming from NgramBook are 
            // of the GenericPhrase class, so we use its AbstractToken array.
            GenericPhrase gp=(GenericPhrase) phr;
            this.put(gp.tokens, 0, gp.getLength(), (EXTERNAL) phr.getData(), false);
        }
    }

    /** Adds a phrase to this NgramBook with null user data. */
    public int add(PhraseInfo phraseInfo) {
        return put(phraseInfo.tokens, 0, phraseInfo.tokens.length, null, false);
    }
    
    /** Adds a phrase to this NgramBook. User data associated with the added phrase, 
     *  accessible via AbstractPhrase.getData(), is added by reference 
     *  unless this NgramBook's PhraseBookValueAdapter does not copy it. */
    public int put(PhraseInfo phraseInfo, EXTERNAL data, boolean overwrite) {
        return put(phraseInfo.tokens, 0, phraseInfo.tokens.length, data, overwrite);
    }

    /** Adds a phrase to this NgramBook. User data is added by reference 
     *  unless this NgramBook's PhraseBookValueAdapter does not copy it. */
    public int put(AbstractToken[] toks, int start, int len, EXTERNAL data, boolean overwrite) {
        // find trie node (adding a chain of new trie nodes if needed) 
        TrieItem it = root.addChildren(toks, start, len);

        // for new key, update the max length of contained phrase
        if(data!=null && it.stats==null) {
            if(toks.length > maxLen) {
                maxLen=toks.length;
                if(maxLen > matchedItems.length) {
                    //TrieItem[] old=matchedItems;
                    Object[] old=matchedItems;
                    //matchedItems=(TrieItem[]) new Object[2*maxLen];
                    matchedItems=new Object[2*maxLen];
                    System.arraycopy(old, 0, matchedItems, 0, old.length);
                }
            }
        }

        // store the new data
        it.stats=valueAdapter.addValue(it.stats, data, counts);
        return 0;
    }

    public PhraseInfo get(int id) {
        throw new UnsupportedOperationException("get phrase by id not implemented"); 
    }

    public int get(AbstractToken[] toks, NBestResult result) {
        return get(toks, 0, toks.length, false, result);
    }

    private void addResultItem(TrieItem it, int length, double score, NBestResult res, int idx) {
        if(fillMatchedPhrases) {
            GenericPhrase p;
            Object o=res.items[idx];
            if(o!=null && o instanceof GenericPhrase) {
                p=(GenericPhrase) o;
            }else {
                p=new GenericPhrase(maxLen);
                res.items[idx]=p;
            }
            populatePhrase(p, length);
        }else {
            res.data[idx]=null;
        }
        res.data[idx]=it.stats;
        res.scores[idx]=score;
    }
    
    public int get(AbstractToken[] toks, int startIdx, int length, boolean getPrefixes, NBestResult res) {
        res.length=0;
        TrieItem it = root.getChild(toks, startIdx, length);
        if(it==null) {
            if(matchedItem==root) {
                res.rc=NOMATCH;
            }else {
                res.rc=MATCH_PREFIX;
                for(int i=matchedCnt;i>0;i--) {
                    it=(TrieItem) matchedItems[i];
                    if(it.stats!=null) {
                        addResultItem(it, i, 0.5, res, res.length);
                        res.length++;
                    }
                }
            }
        }else {
            res.rc=MATCH_EXACT;
            for(int i=matchedCnt;i>0;i--) {
                it=(TrieItem) matchedItems[i];
                if(it.stats!=null) {
                    if(res.getCapacity()<res.length)
                    addResultItem(it, i, 1, res, res.length);
                    res.length++;
                }
            }
        }
        return res.rc;
    }

    public PhraseInfo getLemma(AbstractToken[] toks) {
        throw new UnsupportedOperationException("getLemma not implemented");
    }

    public int getMaxLen() {
        return maxLen;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name=name;
    }

    public Vocab getVocab() {
        throw new UnsupportedOperationException("No vocab associated with ngram trie");
    }

    public int search(AbstractToken[] toks, NBestResult result) {
        throw new UnsupportedOperationException("search not implemented");
    }

    /** Returns the key count, that is, the number of distinct ngrams. */
    public int size() {
        return counts.ngrCnt;
    }
    
    /** Returns the total occurence count of the contained ngrams as seen in source documents. */
    public int getOccCount() {
        return counts.occCnt;
    }
    
    public double getMaxPWMI(NgramInfo ngramStats, ObjectRef<ModelElement> bestElem) {
        double maxPwmi=-1;
        ModelElement maxEl=null;
        LLI it=ngramStats.elems;
        while(it!=null) {
            int elId=(it.elem==null)? 0: it.elem.getElementId();
            int elOcc=counts.elemOccCnts[elId];
            // can be zero even though the NgramInfo exists and points to element; 
            // NgramInfo may only contain e.g. suffix position and there may be no equals position 
            // since we might have just a fragment of corpus 
            if(elOcc>0) {
                int denominator = ngramStats.getOccCnt() * elOcc;
                LLI2 it2=it.counts;
                while(it2!=null) {
                    double pwmi=(double) (this.getOccCount() * it2.count) / (double) denominator;
                    if(pwmi>maxPwmi) {
                        maxPwmi=pwmi;
                        maxEl=it.elem;
                    }
                    it2=it2.next;
                }
            }
            it=it.next;
        }
        if(bestElem!=null)
            bestElem.data=maxEl;
        return maxPwmi;
    }

    /** Represents a trie node + labeled incoming arc */
    class TrieItem {
        AbstractToken ti; // token of the incoming arc
        INTERNAL stats; // data stored at this item
        TreeMap<AbstractToken,TrieItem> children; // child TrieItems sorted by the incoming arc's tokenId
        
        TrieItem(AbstractToken ti, INTERNAL stats) {
            this.ti=ti;
            this.stats=stats;
            this.children=null;
        }
        
        /** Returns the child node of this node reachable by traversing an 
         * arc labeled with nextToken. Returns null if no such node exists. */
        TrieItem getChild(AbstractToken nextToken) {
            if(nextToken instanceof TokenAnnot)
                nextToken = ((TokenAnnot)(nextToken)).ti;
            if(children==null)
                return null;
            TrieItem ch=null;
            switch(lookupMode) {
            case PhraseBook.MATCH_EXACT:
                ch=children.get(nextToken);
                break;
            case PhraseBook.MATCH_IGNORECASE:
                ch=children.get(nextToken);
                if(ch==null) {
                    Iterator<Map.Entry<AbstractToken,TrieItem>> tit=children.entrySet().iterator();
                    while(tit.hasNext()) {
                        Map.Entry<AbstractToken,TrieItem> entry=tit.next();
                        int lcIdBook=entry.getKey().getLCId();
                        if(lcIdBook<=0)
                            lcIdBook=entry.getKey().getTokenId();
                        int lcIdDoc=nextToken.getLCId();
                        if(lcIdDoc<=0)
                            lcIdDoc=nextToken.getTokenId();
                        if(lcIdBook == lcIdDoc) {
                            ch=entry.getValue();
                            break;
                        }
                    }
                }
                break;
            case PhraseBook.MATCH_LEMMA:
                ch=children.get(nextToken);
                if(ch==null) {
                    Iterator<Map.Entry<AbstractToken,TrieItem>> tit=children.entrySet().iterator();
                    while(tit.hasNext()) {
                        Map.Entry<AbstractToken,TrieItem> entry=tit.next();
                        int lmIdBook=entry.getKey().getLemmaId();
                        if(lmIdBook<=0)
                            lmIdBook=entry.getKey().getTokenId();
                        int lmIdDoc=nextToken.getLemmaId();
                        if(lmIdDoc<=0)
                            lmIdDoc=nextToken.getTokenId();
                        if(lmIdBook == lmIdDoc) {
                            ch=entry.getValue();
                            break;
                        }
                    }
                }
                break;
            }
            return ch;
        }
        
        /** Adds a new child to this node as well as a connecting 
         * arc labeled with nextToken. Returns the newly added child node.
         * If the child node already exists, throws IllegalArgumentException
         * in case exc is true, and returns the existing node otherwise. */
        TrieItem addChild(AbstractToken nextToken, boolean exc) {
            if(nextToken instanceof TokenAnnot)
                nextToken = ((TokenAnnot)(nextToken)).ti;
            TrieItem rc=null;
            if(children==null) {
                children=new TreeMap<AbstractToken, TrieItem>(tokCmp);
                rc=new TrieItem(nextToken, null);
                children.put(nextToken, rc);
            }else {
                TrieItem existing=children.get(nextToken);
                if(existing!=null) {
                    if(exc)
                        throw new IllegalArgumentException("Child node for token="+nextToken+" already exists.");
                    else
                        rc=existing;
                }
                TrieItem it=new TrieItem(nextToken, null);
                if(rc==null)
                    rc=it;
                children.put(nextToken, it);
            }
            return rc;
        }
        
        /** Returns the trie node corresponding to the final token
         * of the given token sequence, or null if the whole sequence is 
         * not in the trie. In any case, leaves the last reached trie 
         * item under NgramBook.matchedItem and the count of matched tokens 
         * under NgramBook.matchedCnt. */
        TrieItem getChild(AbstractToken[] tokens, int idx, int len) {
            matchedItem=this;
            matchedCnt=0;
            matchedItems[matchedCnt]=matchedItem; // remove this if we decide to track parent for trie nodes
            TrieItem it=this;
            while((matchedCnt<len || len==-1) && (idx+matchedCnt)<tokens.length) {
                it=it.getChild(tokens[idx+matchedCnt]);
                if(it==null) {
                    break;
                }else {
                    matchedCnt++;
                    matchedItem=it;
                    matchedItems[matchedCnt]=matchedItem; // remove this if we decide to track parents for trie nodes
                }
            }
            return (matchedCnt==len)? matchedItem: null;
        }

        /** Adds the given token sequence to trie, adding trie items as necessary.
         * Returns the trie item corresponding to last token. */
        TrieItem addChildren(AbstractToken[] tokens, int idx, int len) {
            TrieItem it=getChild(tokens, idx, len);
            if(it==null) {
                it=matchedItem.addChildrenInternal(tokens, idx+matchedCnt, len-matchedCnt);
            }
            return it;
        }
        
        /** Behaves in the same way as addChildren() but throws an IllegalArgumentException 
         * in case any left prefix of the token sequence exists. */
        TrieItem addChildrenInternal(AbstractToken[] tokens, int idx, int len) {
            TrieItem it=this;
            int cnt=0;
            while(cnt<len) {
                it=it.addChild(tokens[idx+cnt], true);
                cnt++;
            }
            return it;
        }
    }
    
    private void populatePhrase(GenericPhrase phrase, int length) {
        phrase.len = length;
        // first matchedItems is always root (uninteresting), 
        // length (max. = matchedCnt) is already without root
        phrase.data = ((TrieItem)matchedItems[length]).stats;
        for(int i=1;i<=length;i++) {
            phrase.tokens[i-1] = ((TrieItem)matchedItems[i]).ti;
        }
        // TrieItem it=lastTit;
        // len=0;
        // while(it!=root) {
        //     len++;
        //     it=it.parent;
        // }        
    }
    
    private class NgramPhraseIterator implements Iterator<AbstractPhrase> {
        GenericPhrase phr;
        int idx; // number of ngrams seen so far; or the index of the object returned by the next next()
        Stack<TrieItem> path; // current path ending with the trie item to be returned by the next next()
        Stack<Iterator<Map.Entry<AbstractToken, TrieItem>>> path2; // child item taken last time for each trie level
        NgramPhraseIterator(int maxLen) {
            phr=new GenericPhrase(maxLen);
            idx=0;
            path=new Stack<TrieItem>();
            path2=new Stack<Iterator<Map.Entry<AbstractToken, TrieItem>>>();
            path.push(root);
            findNextNode(false);
        }
        
        public boolean hasNext() {
            return path.size()>1;
        }

        public AbstractPhrase next() {
            if(!hasNext()) {
                throw new NoSuchElementException("No more phrases; idx="+idx);
            }
            // populate phrase using the last node in path (found by previous findNextNode call)
            phr.data=path.lastElement().stats;
            phr.len=path.size()-1;
            for(int i=0;i<phr.len;i++) {
                phr.tokens[i]=path.get(i+1).ti;
            }
            // find the next ngram to return
            if(findNextNode(false))
                idx++;
            return phr;
        }
        
        /** Finds the next ngram node, updates the path so that the next ngram item 
         * becomes its last item. */
        protected boolean findNextNode(boolean useCurrent) {
            TrieItem node=path.lastElement();
            // if the node represent an ngram, stop here
            if(useCurrent && node.stats!=null) {
                // phrase corresponding to the last node in path 
                // will be populated as part of the next next() call 
                return true;
            }
            // if the node has children, follow them
            if(node.children!=null) {
                Iterator<Map.Entry<AbstractToken, TrieItem>> childIt=node.children.entrySet().iterator();
                while(childIt.hasNext()) {
                    Map.Entry<AbstractToken, TrieItem> entry=childIt.next();
                    path2.push(childIt);
                    path.push(entry.getValue());
                    if(findNextNode(true)) {
                        return true;
                    }
                }
            }
            // if no child was found or all children that corresponded to ngrams were visited,
            // keep going back up until we find ngram or until we get back to root
            while(path2.size()>0) {
                Iterator<Map.Entry<AbstractToken, TrieItem>> parentsChildIt=path2.pop();
                path.pop(); // seen node
                while(parentsChildIt.hasNext()) {
                    Map.Entry<AbstractToken, TrieItem> entry=parentsChildIt.next();
                    path2.push(parentsChildIt);
                    path.push(entry.getValue());
                    if(findNextNode(true)) {
                        return true;
                    }
                }
            }
            // back at root, all nodes were visited
            return false;
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported since nobody needed it");
        }
    }
    
    public Iterator<AbstractPhrase> iterator() {
        return new NgramPhraseIterator(maxLen);
    }
    
    public static void main(String[] args) throws IOException {
        String cfg="config.cfg";
        Options o=Options.getOptionsInstance();
        try {
            o.load(new FileInputStream(cfg));
        }catch(Exception ex) { 
            Logger.LOG(Logger.WRN,"Cannot find "+cfg+": "+ex.getMessage());
        }
        Logger.init("book.log", -1, -1, null);
        Logger log=Logger.getLogger("main");

        FM fm=FM.getFMInstance(); // make sure feature manager is initialized
        Vocab voc=new VocabImpl(200,true);
        PhraseBook<NgramInfo,NgramInfo> book=
            new NgramBook<NgramInfo,NgramInfo>("test", new CountPhraseBookAdapter());
        book.setLookupMode(PhraseBook.MATCH_IGNORECASE);
        KB modelKb=new KB("ModelKB",voc,book);
        fm.registerKB(modelKb);
        
        // test attribute
        AttributeDef ad=new AttributeDef("person", AttributeDef.TYPE_NAME, null);
        
        // known phrases
        NgramInfo stats=new NgramInfo();

        TokenAnnot t1=new TokenAnnot(0, -1, -1, null, -1, -1, "Charlie");
        t1.setFeatures(voc);
        TokenAnnot[] phr1={t1};
        stats.setCount(ad, TokenNgramF.NGR_PREFIX, 1);
        book.put(phr1,0,1,stats,false);

        TokenAnnot t2=new TokenAnnot(0, -1, -1, null, -1, -1, "Wilson");
        t2.setFeatures(voc);
        TokenAnnot[] phr2={t2};
        stats.clear();
        stats.setCount(ad, TokenNgramF.NGR_SUFFIX, 1);
        book.put(phr2,0,1,stats,false);

        TokenAnnot[] phr3={t1,t2};
        stats.clear();
        stats.setCount(ad, TokenNgramF.NGR_EQUALS, 1);
        book.put(phr3,0,2,stats,false);
        
        // doc
        String[] doc={"Yesterday","I","met","Charlie","Wilson","going","to","a","bar",
                "charlie","wilson","again"};
        TokenAnnot[] tokens=new TokenAnnot[doc.length];
        for(int i=0;i<doc.length;i++) {
            TokenAnnot ta=new TokenAnnot(0, -1, -1, null, -1, i, doc[i]);
            ta.setFeatures(voc);
            tokens[i]=ta;
            System.out.println(ta);
        }
        System.out.println("---");
        System.out.println(t1);
        System.out.println(t2);

        // search for phrases...
        NBestResult res=new NBestResult(1);

        // ...in the wrong place
        int rc=book.get(tokens,0,1,true,res);
        log.LG(Logger.WRN,"rc="+rc+" res="+res.toString());
        res.clear();

        // ...in the right place
        rc=book.get(tokens,3,-1,true,res);
        log.LG(Logger.WRN,"rc="+rc+" res="+res.toString());
        res.clear();

        // ...in the right place if ignorecase
        rc=book.get(tokens,9,-1,true,res);
        log.LG(Logger.WRN,"rc="+rc+" res="+res.toString());
        res.clear();
        
        // add all ngrams from doc to book
        for(int i=0;i<tokens.length;i++) {
            int ml=Math.min(3,tokens.length-i);
            for(int j=1;j<=ml;j++) {
                stats.clear();
                stats.setCount(null, TokenNgramF.NGR_EQUALS, 1);
                book.put(tokens, i, j, stats, false);
            }
        }
        
        // iterate over book, dump each ngram record
        Iterator<AbstractPhrase> it=book.iterator();
        StringBuffer b=new StringBuffer(128);
        while(it.hasNext()) {
            AbstractPhrase p=it.next();
            for(int i=0;i<p.getLength();i++) {
                if(i>0)
                    b.append(' ');
                b.append(p.getToken(i).getToken());
            }
            b.append(" -> ");
            b.append(p.getData());
            System.out.println(b);
            b.setLength(0);
        }

        // Charlie Wilson going
        rc=book.get(tokens,3,-1,true,res);
        log.LG(Logger.WRN,"rc="+rc+" res="+res.toString());
        res.clear();

        // Charlie Wilson
        rc=book.get(tokens,3,2,true,res);
        log.LG(Logger.WRN,"rc="+rc+" res="+res.toString());
        res.clear();

        // Charlie
        rc=book.get(tokens,3,1,true,res);
        log.LG(Logger.WRN,"rc="+rc+" res="+res.toString());
        res.clear();

        // wilson
        rc=book.get(tokens,4,1,true,res);
        log.LG(Logger.WRN,"rc="+rc+" res="+res.toString());
        res.clear();
        
        PhraseBookWriter w=new PhraseBookWriterImpl();
        w.write(book, "test.book");
        
        PhraseBookReader r=new PhraseBookReaderImpl();
        PhraseBook copy = r.read("test.book", PhraseBook.TYPE_NGRAM);
        w.write(copy, "copy.book");
    }
}

class TokenCmp implements Comparator<AbstractToken> {
    public int compare(AbstractToken o1, AbstractToken o2) {
        return o1.getTokenId() - o2.getTokenId();
    }
}

class NgramCounts {
    public int occCnt;
    public int ngrCnt;
    public int[] elemOccCnts;
    public NgramCounts() {
        ngrCnt=0;
        occCnt=0;
        elemOccCnts=new int[64];
    }
    public void incElemCount(int elemId, int count) {
        if(elemId>elemOccCnts.length) {
            Logger.LOGERR("Growing model element array for element id="+elemId+" to fit");
            int[] bup=elemOccCnts;
            elemOccCnts=new int[elemId+64];
            System.arraycopy(bup, 0, elemOccCnts, 0, bup.length);
        }
        elemOccCnts[elemId]+=count;
    }
}

interface PhraseBookValueAdapter<INTERNAL,EXTERNAL> {
    INTERNAL addValue(INTERNAL oldValue, EXTERNAL newValue, NgramCounts bookCounts);
}

/** The default adapter behaves like Map - it overwrites any existing value. 
 * It adds 1 occurrence per value (since it does not understand the value). 
 * EXTERNAL type must be the same as INTERNAL for this adapter. */
class DefaultPhraseBookAdapter<INTERNAL,EXTERNAL> implements PhraseBookValueAdapter<INTERNAL,EXTERNAL> {
    public INTERNAL addValue(INTERNAL oldValue, EXTERNAL newValue, NgramCounts bookCounts) {
        if(oldValue==null) {
            if(newValue!=null) {
                bookCounts.occCnt+=1;
            }
        }else {
            if(newValue==null) {
                bookCounts.occCnt-=1;
            }
        }
        return (INTERNAL) newValue;
    }
}
