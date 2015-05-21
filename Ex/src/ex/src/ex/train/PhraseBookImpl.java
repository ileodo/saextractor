// $Id: PhraseBookImpl.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.*;
import java.io.*;

import uep.util.*;

import ex.reader.Document;
import ex.features.*;
import ex.reader.Tokenizer;
// for testing in main:
import ex.reader.TokenAnnot;

public class PhraseBookImpl implements PhraseBook, Serializable {
    private static final long serialVersionUID = -1539221200562196987L;
    protected PhraseTrie trie;
    protected Object[] phrases; // stores PhraseInfo if type=STATIC_PHRASEINFO, or last token's PhraseTrie if DYNAMIC_PHRASEINFO
    protected int keyCount;
    protected int maxLen;
    protected Vocab vocab;
    protected int storageMode;
    protected String name;
    protected int matchMode; // MATCH_EXACT, MATCH_IGNORECASE or MATCH_LEMMA
    protected int initCap;

    private transient boolean addingGen;
    private transient ObjectRef oarg;

    protected static final Object PHRASE_PLACEHOLDER="PP";
    /*
    public static final int OK=0;
    public static final int MATCH_EXACT=1;
    public static final int MATCH_IGNORECASE=2;
    public static final int MATCH_LEMMA=3;
    public static final int ERR=-1;
    public static final int NOT_FOUND=-2;
    public static final int ALREADY_EXISTS=-3;
     */

    public static Logger log;
    public static boolean logOn=true;

    public PhraseBookImpl(String name, int capacity, int type, int matchMode, Vocab voc) {
        this.name=name;
        log=Logger.getLogger("Book");
        this.storageMode=type;
        this.matchMode=matchMode;
        initCap=capacity;
        vocab=voc;
        init();
    }
    
    private void init() {
        trie=new PhraseTrie(null, 0);
        phrases=new Object[initCap];
        keyCount=0;
        maxLen=0;
        addingGen=false;
        oarg=new ObjectRef<Object>(null);
    }
    
    public void clear() {
        init();
    }
    
    public void writeTo(BufferedWriter out) throws IOException {
        PhraseBookWriter w=new PhraseBookWriterImpl();
        w.write(this, out);
    }

    public int add(PhraseInfo pi) {
        return put(pi, null, false);
    }
    
    /** Add new PhraseInfo. Used to:
	- insert new PhraseInfo related to a specific attribute during training (global KB creation)
	- insert unseen PhraseInfos that are attribute candidates (ACs) found when parsing documents 
	during extraction (update of working KB)
	@param pi phrase to be added; if pi.id<0 then id is updated when phrase is successfully added.
	@return {@link #OK} when successfully added, {@link #ALREADY_EXISTS} or {@link #ERR} otherwise.
     */
    public int put(PhraseInfo pi, Object data, boolean overwrite) {
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"book.add('"+pi.toPhrase()+"')");

        if(data==null)
            data=(storageMode==STATIC_PHRASEINFO)? pi: PHRASE_PLACEHOLDER;
        ObjectRef lastTrie=null;
        if(storageMode==DYNAMIC_PHRASEINFO) {
            lastTrie=oarg;
            lastTrie.data=null;
        }
        int rc=trie.put(pi.tokens, data, 0, -1, overwrite, lastTrie);
        if(rc==PhraseTrie.ALREADY_EXISTS) {
            log.LG(Logger.WRN,"Phrase '"+pi.toString()+"' already exists! Not adding.");
            return PhraseBook.ALREADY_EXISTS;
        }
        int phraseId=pi.intValues[FM.PHRASE_ID];
        if(phraseId<0) {
            phraseId=keyCount;
            pi.intValues[FM.PHRASE_ID]=phraseId;
        }else if(storageMode==STATIC_PHRASEINFO) {
            log.LG(Logger.WRN,"Adding phrase with already assigned id='"+phraseId+"' to static phrasebook.");
        }
        if(phraseId<phrases.length && phrases[phraseId]!=null) {
            log.LG(Logger.WRN,"Phrase '"+get(phraseId).toString()+"' already has the same id="+phraseId+
                    " as the inserted phrase '"+pi.toString()+"'! Not adding.");
            return PhraseBook.ALREADY_EXISTS;
        }
        storePhraseInArray((storageMode==STATIC_PHRASEINFO)? pi: lastTrie.data, phraseId);
        if(maxLen<pi.tokens.length)
            maxLen=pi.tokens.length;

        /* done instead by PhraseInfo.computeFeatures(PhraseBook)
        if(!addingGen && (matchMode==MATCH_IGNORECASE || matchMode==MATCH_LEMMA)) {
            addingGen=true;
            addGeneralized(pi.tokens, 0, pi.tokens.length, PHRASE_PLACEHOLDER); // will always be added dynamically
            addingGen=false;
        }
         */

        return PhraseBook.OK;
    }

    // assumes DYNAMIC_PHRASEINFO; stores PHRASE_PLACEHOLDER
    public int put(AbstractToken[] toks, int start, int len, Object data, boolean overwrite) {
        if(storageMode==STATIC_PHRASEINFO)
            throw new IllegalArgumentException("Cannot use PhraseBook.add(AbstractToken[]) for phrasebook type=STATIC; use add(PhraseInfo) instead");
        if(data==null)
            data=PHRASE_PLACEHOLDER;
        ObjectRef lastTrie=new ObjectRef(null);
        int rc=trie.put(toks, data, start, len, overwrite, lastTrie);
        int cnt=(len==-1)? toks.length-start: len;
        if(rc==PhraseTrie.ALREADY_EXISTS) {
            log.LG(Logger.WRN,"Phrase '"+PhraseInfo.toString(toks,start,len)+"' already exists! Not adding.");
            return PhraseBook.ALREADY_EXISTS;
        }
        storePhraseInArray(lastTrie.data, keyCount);
        if(maxLen<cnt)
            maxLen=cnt;

        if(!addingGen && (matchMode==MATCH_IGNORECASE || matchMode==MATCH_LEMMA)) {
            addingGen=true;
            addGeneralized(toks, start, len, data);
            addingGen=false;
        }
        return PhraseBook.OK;
    }

    protected void addGeneralized(AbstractToken[] toks, int start, int len, Object phraseRep) {
        int[] lcIds=TokenLCF.toLC(toks, start, len);
        ObjectRef lastTrie=oarg;
        int rc;
        if(lcIds!=null) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Adding LCased phrase from '"+PhraseInfo.toString(toks,start,len)+"'");
            lastTrie.data=null;
            rc=trie.put(lcIds, phraseRep, start, len, false, lastTrie);
            if(rc==PhraseTrie.OK) // != PhraseTrie.ALREADY_EXISTS
                storePhraseInArray(lastTrie.data, keyCount);
        }
        if(matchMode==MATCH_LEMMA) {
            int[] lemmaIds=TokenLemmaF.toLemma(toks, start, len);
            if(lemmaIds!=null) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Adding lemmatized phrase from '"+PhraseInfo.toString(toks,start,len)+"'");
                lastTrie.data=null;
                rc=trie.put(lemmaIds, phraseRep, start, len, false, lastTrie);
                if(rc==PhraseTrie.OK)
                    storePhraseInArray(lastTrie.data, keyCount);
            }
        }
    }

    protected void storePhraseInArray(Object phraseRepresentation, int phraseId) {
        keyCount++;
        if(phraseId>=phrases.length) {
            Object[] old=phrases;
            phrases=new Object[phraseId*2];
            System.arraycopy(old,0,phrases,0,old.length);
            log.LG(Logger.WRN,"Increasing PhraseBook capacity to "+phrases.length+" (phraseId="+phraseId+")");
        }
        phrases[phraseId]=phraseRepresentation;
    }

    /** Get PhraseInfo with the given ID, or null.
     */
    public PhraseInfo get(int id) {
        if(id<0 || id>=phrases.length)
            return null;
        if(storageMode==STATIC_PHRASEINFO) 
            return (PhraseInfo) phrases[id];
        // DYNAMIC_PHRASEINFO:
        return new PhraseInfo((PhraseTrie) phrases[id], vocab); // without features initialized
    }

    /** Get PhraseInfo with the given tokens, or null.
	Tokens may either be specified fully by their TOKEN_ID, 
	or by the following features: TOKEN_LC, TOKEN_LEMMA 
	(taken into account when TOKEN_ID==-1).
     */
    public int get(AbstractToken[] toks, NBestResult result) {
        int rc=trie.get(toks, 0, toks.length, false, matchMode, result);
        if(storageMode==DYNAMIC_PHRASEINFO && result.length>0)
            postprocessResult(result);
        return rc;
    }

    public int get(AbstractToken[] toks, int startIdx, int length, boolean getPrefixes, NBestResult result) {
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"get("+startIdx+","+length+",["+Document.toString(toks, startIdx, length, " ")+"],prefix="+getPrefixes+")");
        int rc=trie.get(toks, startIdx, length, getPrefixes, matchMode, result);
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"rc="+rc+", cnt="+result.length);
        if(storageMode==DYNAMIC_PHRASEINFO && result.length>0)
            postprocessResult(result);
        return rc;
    }

    protected void postprocessResult(NBestResult res) {
        for(int i=0; i<res.length; i++)
            res.items[i]=new PhraseInfo((PhraseTrie) res.data[i], vocab);
    }

    /** Get PhraseInfo composed of lemmas of the given tokens.
	Returns null If no such phrase exists.
     */
    public PhraseInfo getLemma(AbstractToken[] toks) {
        return trie.getLemma(toks);
    }

    /** Get PhraseInfos of the nbest most similar phrases to toks.
     */
    public int search(AbstractToken[] toks, NBestResult result) {
        return PhraseBook.ERR;
    }

    /** {@inheritDoc} */
    public int size() {
        return keyCount;
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
    public int getMaxLen() {
        return maxLen;
    }

    /** {@inheritDoc} */
    public String toString() {
        return trie.toString();
    }

    /** {@inheritDoc} */
    public Vocab getVocab() {
        return vocab;
    }

    public static PhraseBook readFrom(PhraseBook base, String fileName, String enc, Tokenizer tokenizer, Vocab voc) throws IOException {
        File f=new File(fileName);
        int sz=(int)f.length();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f),enc), 512);
        return readFrom(base, fileName, br, 32*(1+sz), tokenizer, voc);
    }
    
    public static PhraseBook readFrom(PhraseBook base, String bookName, BufferedReader br, int expectedSz, Tokenizer tokenizer, Vocab voc) throws IOException {
        PhraseBook book=base;
        if(book==null)
            book=new PhraseBookImpl(bookName, expectedSz, PhraseBook.DYNAMIC_PHRASEINFO, PhraseBook.MATCH_LEMMA, voc);
        String line;
        int lno=0;
        ArrayList<TokenAnnot> lst=new ArrayList<TokenAnnot>(16);
        AbstractToken[] tas=new AbstractToken[8];
        while((line=br.readLine())!=null) {
            lno++;
            tokenizer.setInput(line);
            TokenAnnot token;
            while((token=tokenizer.next())!=null) {
                token.setFeatures(voc);
                lst.add(token);
            }
            if(lst.size()>0) {
                if(lst.size()>tas.length)
                    tas=new AbstractToken[lst.size()*2];
                lst.toArray(tas);
                int rc=book.put(tas, 0, lst.size(), null, false);
                switch(rc) {
                case PhraseBook.OK:
                    break;
                case PhraseBook.ALREADY_EXISTS:
                    log.LG(Logger.WRN,"Duplicate line in "+bookName+":"+lno+" "+line);
                    break;
                default:
                    log.LG(Logger.ERR,"Error adding "+bookName+":"+lno+" "+line);
                break;
                }
            }
            lst.clear();
        }
        return book;   
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        // read in defaults
        s.defaultReadObject();
        if(log==null)
            log=Logger.getLogger("Book");
        addingGen=false;
        oarg=new ObjectRef(null);
    }

    /** Class to iteratate over all phrases in book. 
     * Only used for PhraseBooks of type STATIC_PHRASEINFO which directly contain PhraseInfo objects. */
    private class PitSimple implements Iterator<AbstractPhrase>{
        protected int idx; // idx of object to return during the next next()
        PitSimple() {
            idx=0; 
        }
        public boolean hasNext() {
            return (idx<phrases.length);
        }
        public AbstractPhrase next() {
            AbstractPhrase rc=null;
            try {
                rc = (AbstractPhrase) phrases[idx];
                idx++;
            }catch (ArrayIndexOutOfBoundsException e) {
                throw new NoSuchElementException("No more phrases; idx="+idx+": "+e);
            }
            return rc;
        }
        public void remove() {
            throw new UnsupportedOperationException("Not supported since nobody needed it");
        }
    }

    /** Class used as return type for the PitTrie iterator. */
    private class PitPhrase implements AbstractPhrase {
        AbstractToken[] tokens;
        int len=0;
        Object data;
        PitPhrase(int maxLen) {
            tokens=new AbstractToken[maxLen];
        }
        public int getLength() {
            return len;
        }
        public AbstractToken getToken(int idx) {
            return tokens[idx];
        }
        public Object getData() {
            return data;
        }
        public void populate(PhraseTrie lastToken, Vocab voc) {
            data = lastToken;
            len = lastToken.depth;
            int i = len - 1;
            do {
                tokens[i]=voc.get(lastToken.label);
                if(tokens[i]==null)
                    throw new IllegalArgumentException("Cannot create PitPhrase from unknown token ID="+lastToken.label+" trie level="+i);
                lastToken=(PhraseTrie) lastToken.parent;
                i--;
            }while(i>=0);
        }
    }
    
    /** Class to iteratate over all phrases in book. 
     * Only used for PhraseBooks of type DYNAMIC_PHRASEINFO which contain trie node references
     * instead of PhraseInfo objects. */
    private class PitTrie implements Iterator<AbstractPhrase>{
        protected int idx; // idx of object to return during the next next()
        AbstractPhrase phrase;
        PitTrie() {
            idx=0;
            phrase=new PitPhrase(maxLen); 
        }
        public boolean hasNext() {
            return (idx<phrases.length);
        }
        public AbstractPhrase next() {
            try {
                PhraseTrie node = (PhraseTrie) phrases[idx];
                // phrase = new PhraseInfo(node, vocab); // too wasteful
                ((PitPhrase)phrase).populate(node, vocab); // better
                idx++;
            }catch (ArrayIndexOutOfBoundsException e) {
                throw new NoSuchElementException("No more phrases; idx="+idx+": "+e);
            }
            return phrase;
        }
        public void remove() {
            throw new UnsupportedOperationException("Not supported since nobody needed it");
        }
    }

    public Iterator<AbstractPhrase> iterator() {
        return (storageMode==STATIC_PHRASEINFO)? new PitSimple(): new PitTrie();
    }
    
    public static void main(String[] args) {
        String cfg="config.cfg";
        Options o=Options.getOptionsInstance();
        try {
            o.load(new FileInputStream(cfg));
        }catch(Exception ex) { 
            Logger.LOG(Logger.WRN,"Cannot find "+cfg+": "+ex.getMessage());
        }
        Logger.init("book.log", -1, -1, null);
        Logger log=Logger.getLogger("main");

        FM.getFMInstance(); // make sure feature manager is initialized
        Vocab voc=new VocabImpl(200,true);
        PhraseBook book=new PhraseBookImpl("test", 1000, DYNAMIC_PHRASEINFO, PhraseBook.MATCH_LEMMA, voc);

        // known phrases
        TokenAnnot t1=new TokenAnnot(0, -1, -1, null, -1, -1, "Charlie");
        t1.setFeatures(voc);
        TokenAnnot[] phr1={t1};
        book.put(phr1,0,1,null,false);
        TokenAnnot t2=new TokenAnnot(0, -1, -1, null, -1, -1, "Wilson");
        t2.setFeatures(voc);
        TokenAnnot[] phr2={t2};
        book.put(phr2,0,1,null,false);

        // doc
        String[] doc={"Yesterday","I","met","Charlie","Wilson","going","to","a","bar","charlie","wilson","again"};
        TokenAnnot[] tokens=new TokenAnnot[doc.length];
        for(int i=0;i<doc.length;i++) {
            TokenAnnot ta=new TokenAnnot(0, -1, -1, null, -1, -1, doc[i]);
            ta.setFeatures(voc);
            tokens[i]=ta;
        }

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
    }

    public int getLookupMode() {
        return matchMode;
    }

    public void setLookupMode(int mode) {
        matchMode=mode;
    }

    public byte getType() {
        return TYPE_PHRASE;
    }
}
