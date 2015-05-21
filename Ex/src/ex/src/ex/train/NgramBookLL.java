// $Id: NgramBookLL.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Stack;

import uep.util.Logger;
import uep.util.Options;

import ex.features.FM;
import ex.features.TokenNgramF;
import ex.model.AttributeDef;
import ex.reader.TokenAnnot;

public class NgramBookLL implements PhraseBook {
    int ngrCnt;
    int occCnt;
    int maxLen;
    String name;
    TrieItem root;
    TrieItem matchedItem; // last trie item matched
    int matchedCnt;  // count of items matched
    int lookupMode;

    // read/write operations store here the trie items matched on the way to matchedItem
    // we need this is because we do not want to track parent for each trie element
    // this makes NgramBook thread-unsafe
    TrieItem[] matchedItems;
    
    public NgramBookLL(String name) {
        init();
    }
    
    private void init() {
        matchedItem=null;
        matchedCnt=0;
        maxLen=0;
        ngrCnt=0;
        occCnt=0;
        root=new TrieItem(null,null,null);
        lookupMode=PhraseBook.MATCH_EXACT;
        matchedItems=new TrieItem[16];
    }
    
    public void clear() {
        init();
    }
    
    public void setLookupMode(int mode) {
        lookupMode=mode;
    }
    
    public int getLookupMode() {
        return lookupMode;
    }
    
    public int add(PhraseInfo phraseInfo) {
        return put(phraseInfo.tokens, 0, phraseInfo.tokens.length, null, false);
    }
    
    public int put(PhraseInfo phraseInfo, Object data, boolean overwrite) {
        return put(phraseInfo.tokens, 0, phraseInfo.tokens.length, data, overwrite);
    }

    public int put(AbstractToken[] toks, int start, int len, Object data, boolean overwrite) {
        NgramInfo inf=(NgramInfo) data;
        TrieItem it = root.addChildren(toks, start, len);
        if(inf==null) {
            it.stats=null;
        }else if(it.stats==null) {
            it.stats=new NgramInfo(inf);
            ngrCnt++;
            if(toks.length > maxLen) {
                maxLen=toks.length;
                if(maxLen > matchedItems.length) {
                    TrieItem[] old=matchedItems;
                    matchedItems=new TrieItem[2*maxLen];
                    System.arraycopy(old, 0, matchedItems, 0, old.length);
                }
            }
        }else {
            it.stats.add(inf);
        }
        return 0;
    }

    public PhraseInfo get(int id) {
        throw new UnsupportedOperationException("get phrase by id not implemented"); 
    }

    public int get(AbstractToken[] toks, NBestResult result) {
        return get(toks, 0, toks.length, false, result);
    }

    protected void addResultItem(TrieItem it, double score, NBestResult res, int idx) {
        NgramPhrase p;
        Object o=res.items[idx];
        if(o!=null && o instanceof NgramPhrase) {
            p=(NgramPhrase) o;
        }else {
            p=new NgramPhrase(maxLen);
            res.items[idx]=p;
        }
        p.populate(it);
        res.data[idx]=it.stats;
        res.scores[idx]=score;
    }
    
    public int get(AbstractToken[] toks, int startIdx, int length,  boolean getPrefixes, NBestResult res) {
        TrieItem it = root.getChild(toks, startIdx, length);
        if(it==null) {
            if(matchedItem==root) {
                res.rc=NOMATCH;
                res.length=0;
            }else {
                res.rc=MATCH_PREFIX;
                addResultItem(matchedItem, 0.5, res, 0);
                res.length=1;
            }
        }else {
            res.rc=MATCH_EXACT;
            addResultItem(it, 1, res, 0);
            res.length=1;
        }
        return res.rc;
    }

    public PhraseInfo getLemma(AbstractToken[] toks) {
        throw new UnsupportedOperationException("getLemma not implemented");
    }

    /** {@inheritDoc} */
    public int getMaxLen() {
        return maxLen;
    }

    /** {@inheritDoc} */
    public String getName() {
        return name;
    }
    
    /** {@inheritDoc} */
    public void setName(String name) {
        this.name=name;
    }

    /** {@inheritDoc} */
    public Vocab getVocab() {
        throw new UnsupportedOperationException("No vocab associated with ngram trie");
    }

    /** {@inheritDoc} */
    public int search(AbstractToken[] toks, NBestResult result) {
        throw new UnsupportedOperationException("search not implemented");
    }

    /** {@inheritDoc} */
    public int size() {
        return ngrCnt;
    }

    /** Represents a trie node + labeled incoming arc */
    class TrieItem {
        AbstractToken ti; // token of the incoming arc
        NgramInfo stats; // data stored at this item
        LI3 child; // linked list of child arcs, sorted by token id
        
        TrieItem(AbstractToken ti, NgramInfo stats, LI3 child) {
            this.ti=ti;
            this.stats=stats;
            this.child=child;
        }
        
        /** Returns the child node of this node reachable by traversing an 
         * arc labeled with nextToken. Returns null if no such node exists. */
        TrieItem getChild(AbstractToken nextToken) {
            if(nextToken instanceof TokenAnnot)
                nextToken = ((TokenAnnot)(nextToken)).ti;
            LI3 ch=child;
            switch(lookupMode) {
            case PhraseBook.MATCH_EXACT:
                while(ch!=null) {
                    if(ch.it.ti == nextToken) {
                        break;
                    }
                    if(nextToken.getTokenId() > ch.it.ti.getTokenId()) {
                        ch=null;
                        break;
                    }
                    ch=ch.next;
                }
                break;
            case PhraseBook.MATCH_IGNORECASE:
                while(ch!=null) {
                    if(ch.it.ti == nextToken) {
                        break;
                    }
                    int lcIdBook=ch.it.ti.getLCId();
                    if(lcIdBook<=0)
                        lcIdBook=ch.it.ti.getTokenId();
                    int lcIdDoc=nextToken.getLCId();
                    if(lcIdDoc<=0)
                        lcIdDoc=nextToken.getTokenId();
                    if(lcIdBook == lcIdDoc) {
                        break;
                    }
                    ch=ch.next;
                }
                break;
            case PhraseBook.MATCH_LEMMA:
                while(ch!=null) {
                    if(ch.it.ti == nextToken) {
                        break;
                    }
                    int lemmaIdBook=ch.it.ti.getLemmaId();
                    if(lemmaIdBook<=0)
                        lemmaIdBook=ch.it.ti.getTokenId();
                    int lemmaIdDoc=nextToken.getLemmaId();
                    if(lemmaIdDoc<=0)
                        lemmaIdDoc=nextToken.getTokenId();
                    if(lemmaIdBook == lemmaIdDoc) {
                        break;
                    }
                    ch=ch.next;
                }
                break;
            }
            return (ch!=null)? ch.it: null;
        }
        
        /** Adds a new child to this node as well as a connecting 
         * arc labeled with nextToken. Returns the newly added child node.
         * If the child node already exists, throws IllegalArgumentException
         * in case exc is true, and returns the existing node otherwise. */
        TrieItem addChild(AbstractToken nextToken, boolean exc) {
            if(nextToken instanceof TokenAnnot)
                nextToken = ((TokenAnnot)(nextToken)).ti;
            TrieItem rc=null;
            if(child==null) {
                rc=new TrieItem(nextToken, null, null);
                child=new LI3(rc, null);
            }else {
                LI3 last=null;
                LI3 ch=child;
                while(ch!=null) {
                    if(ch.it.ti == nextToken) {
                        throw new IllegalArgumentException("Child node for token="+ch.it.ti+" already exists.");
                        // rc=ch.it;
                        // break;
                    }
                    if(nextToken.getTokenId() > ch.it.ti.getTokenId()) {
                        rc=new TrieItem(nextToken, null, null);
                        if(last==null) {
                            child=new LI3(rc, child);
                        }else {
                            last.next=new LI3(rc, ch);
                        }
                        break;
                    }
                    last=ch;
                    ch=ch.next;
                }
                if(rc==null) {
                    rc=new TrieItem(nextToken, null, null);
                    last.next=new LI3(rc, null);
                }
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
    
    class LI3 {
        TrieItem it;
        LI3 next;
        LI3(TrieItem it, LI3 next) {
            this.it=it;
            this.next=next;
        }
    }

    /** Class used as return type for the ngram trie iterator. */
    private class NgramPhrase extends GenericPhrase {
        public NgramPhrase(int maxLen) {
            super(maxLen);
        }
        public void populate(TrieItem lastTit) {
            data = lastTit.stats;
            len = matchedCnt;
            // first matchedItems is always root (uninteresting), matchedCnt is already without root
            for(int i=1;i<=matchedCnt;i++) {
                tokens[i-1]=matchedItems[i].ti;
            }
            // TrieItem it=lastTit;
            // len=0;
            // while(it!=root) {
            //     len++;
            //     it=it.parent;
            // }
        }
    }
    
    private class NgramPhraseIterator implements Iterator<AbstractPhrase> {
        NgramPhrase phr;
        int idx; // number of ngrams seen so far; or the index of the object returned by the next next()
        Stack<TrieItem> path; // current path ending with the trie item to be returned by the next next()
        Stack<LI3> path2; // link list item taken last time for each trie level
        NgramPhraseIterator(int maxLen) {
            phr=new NgramPhrase(maxLen);
            idx=0;
            path=new Stack<TrieItem>();
            path2=new Stack<LI3>();
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
            LI3 childLi=node.child;
            while(childLi!=null) {
                path2.push(childLi);
                path.push(childLi.it);
                if(findNextNode(true)) {
                    return true;
                }
            }
            // if no child was found or all children that corresponded to ngrams were visited,
            // keep going back up until we find ngram or until we get back to root
            while(path2.size()>0) {
                LI3 parLi=path2.pop();
                path.pop(); // seen node
                parLi=parLi.next;
                while(parLi!=null) {
                    path2.push(parLi);
                    path.push(parLi.it);
                    if(findNextNode(true)) {
                        return true;
                    }
                    parLi=parLi.next;
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
        PhraseBook book=new NgramBook("test");
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

    public byte getType() {
        return TYPE_NGRAM;
    }
}
