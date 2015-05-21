// $Id: PhraseBook.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.Iterator;

public interface PhraseBook<INTERNAL,EXTERNAL> extends Iterable<AbstractPhrase> {
    // book types
    public static final byte TYPE_NGRAM=1;
    public static final byte TYPE_NGRAM_FEATURE=2;
    public static final byte TYPE_PHRASE=3;
    
    // general return codes
    public static final int OK=0;
    public static final int ERR=-1;
    public static final int ALREADY_EXISTS=-2;
    // rc when searching for phrases, the 1st 3 also used as read/write lookup modes
    public static final int MATCH_EXACT=1;
    public static final int MATCH_IGNORECASE=2;
    public static final int MATCH_LEMMA=3;
    public static final int MATCH_PREFIX=4; // search phrase is found, but only as a prefix of a longer phrase
    public static final int NOMATCH=5;
    // PhraseBook storage mode
    public static final int STATIC_PHRASEINFO=1; // keeps PhraseInfos, uses its features
    public static final int DYNAMIC_PHRASEINFO=2; // creates PhraseInfos as return value only, does not use freatures

    /** Add new PhraseInfo. Used to:
	- insert new PhraseInfo related to a specific attribute during training (global KB creation)
	- insert unseen PhraseInfos that are attribute candidates (ACs) found when parsing documents 
	during extraction (update of working KB)
	@param phraseInfo phrase to be added; phraseInfo.id is updated when the phrase is successfully added.
	@return {@link #OK} when successfully added, {@link #ALREADY_EXISTS} or {@link #ERR} otherwise.
     */
    public int add(PhraseInfo phraseInfo);
    
    public int put(PhraseInfo phraseInfo, EXTERNAL data, boolean overwrite);

    public int put(AbstractToken[] toks, int start, int len, EXTERNAL data, boolean overwrite);

    /** Get PhraseInfo with the given ID, or null. */
    public PhraseInfo get(int id);
    
    /** Clears the content of this book. */
    public void clear();

    /** Get PhraseInfo with the given tokens, or null.
	Tokens may either be specified fully by their TOKEN_ID, 
	or by the following features: TOKEN_LC, TOKEN_LEMMA 
	(taken into account when TOKEN_ID==-1).
	NBestResult will contain an array of found phrases and corresponding data like this:
	{ items: [ phr1, phr2 ], scores: [0.5, 0.2], data:[obj1, obj2] }
	The representation of phrase depends on the implementation; e.g. for Trie implementations
	phr1,2 can be the last trie segment of found phrase etc. 
    */
    public int get(AbstractToken[] toks, NBestResult result);

    /** Specify start index and length of the search phrase within toks,
	and whether to return any found left parts of search phrase.
     */
    public int get(AbstractToken[] toks, int startIdx, int length, boolean getPrefixes, NBestResult result);

    /** Get PhraseInfo composed of lemmas of the given tokens.
	Returns null if no such phrase exists.
     */
    public PhraseInfo getLemma(AbstractToken[] toks);

    /** Get PhraseInfos of the nbest most similar phrases to toks.
     */
    public int search(AbstractToken[] toks, NBestResult result);

    /** @return length of the longest contained phrase. */
    public int getMaxLen();

    /** @return number of phrases inside this book. */
    public int size();

    /** @return name and statistics of this phrase book. */
    public String toString();

    /** @return name of this phrase book. */
    public String getName();
    
    /** Sets name of this phrase book. */
    public void setName(String name);

    /** Get Vocab used by this PhraseBook. */
    public Vocab getVocab();
    
    /** Returns an iterator to iterate over all phrases in the phrase book. */
    public Iterator<AbstractPhrase> iterator();

    /** Sets the lookup mode to be used by subsequent read/write operations. 
     * Possible values are MATCH_EXACT, MATCH_IGNORECASE, MATCH_LEMMA. */
    public void setLookupMode(int mode);

    /** Gets the lookup mode to be used by subsequent read/write operations. 
     * Possible values are MATCH_EXACT, MATCH_IGNORECASE, MATCH_LEMMA. */
    public int getLookupMode();
    
    /** Returns type of the PhraseBook: TYPE_NGRAM, TYPE_NGRAM_FEATURE, TYPE_PHRASE */
    public byte getType();
}
