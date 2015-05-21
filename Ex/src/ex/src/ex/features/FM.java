// $Id: FM.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import java.util.*;
import uep.util.Logger;
import ex.train.KB;
import ex.train.NBestResult;
import ex.train.PhraseBook;
import ex.train.TokenInfo;
import ex.train.PhraseInfo;
import ex.model.Model;

/** Feature Manager
 */
public class FM {
    public static FM singleton;

    // ids for standard token features
    public static final int TOKEN_ID   =0; // unique token id
    public static final int TOKEN_LC   =1; // id of this token lowercased (-1 if lc is identical)
    public static final int TOKEN_UNACC=2; // id of this token unaccented (-1 if no unaccented version found or if it is the same)
    public static final int TOKEN_LEMMA=3; // id of this token lemmatized (-1 if no lemma found or if lemma is identical)
    public static final int TOKEN_TYPE =4; // token type as assigned by tokenizer
    public static final int TOKEN_CAP  =5; // token capitalization type as assigned by TokenCapF 
    // standard tag features
    public static final int TAG_NAME   =0; // unique id of tag name as specified in NekoHTML parser
    public static final int TAG_TYPE   =1; // tag type as specified by TagTypeF
    // ids for standard phrase features
    public static final int PHRASE_ID   =0; // unique phrase id
    public static final int PHRASE_LEMMA=1; // id of a phrase built from lemmatized tokens from this phrase
    public static final int PHRASE_LENGTH=2; // length of phrase in tokens
    
    public static int nextFeatureId=1001;
    
    // current Model
    public Model model;
    
    private List<Feature> tokenFeatures;
    private List<Feature> tagFeatures;
    private List<Feature> phraseFeatures;
    private List<Feature> instanceFeatures;
    Logger log;

    protected KB kb;
    
    public static FM getFMInstance() {
        if(singleton==null)
            singleton=new FM();
        return singleton;
    }

    protected FM() {
        log=Logger.getLogger("FM");
        // init all TokenFeatures
        tokenFeatures=new ArrayList<Feature>(10);
        // add standard singleton features
        // (TOKEN_ID is the same as index into TokenFeature.tokenFeatures[])

        /*
	  which methods will features support:
	  - NOT int valueOf(String) <- looks up TokenInfo & gets {ID, lowercased ID, lemmatized ID, token type, capital type}
	    for this purpose, use TokenInfo=KB.vocab.get(String)
	  - String valueOf(String) for TokenLCF, TokenLemmaF
	  - int valueOf()
         */

        tokenFeatures.add(new TokenIdF(TOKEN_ID, "ID")); // int valueOf(String) <- looks up TokenInfo & gets ID
        tokenFeatures.add(new TokenLCF(TOKEN_LC, "LC")); // int valueOf(String) <- looks up TokenInfo of lowercased String & gets ID
        tokenFeatures.add(new UnaccentedF(TOKEN_UNACC, "UNACC")); // int valueOf(String) <- looks up TokenInfo of unaccented String & gets ID
        tokenFeatures.add(new TokenLemmaF(TOKEN_LEMMA, "LEMMA")); // int valueOf(String) <- looks up TokenInfo of lemmatized String & gets ID
        tokenFeatures.add(new TokenTypeF(TOKEN_TYPE, "TYPE")); // int valueOf(String) <- looks up TokenInfo & gets Type
        tokenFeatures.add(new TokenCapF(TOKEN_CAP, "CAP"));
        // more features may be added by registerFeature()

        // init all TagFeatures
        tagFeatures=new ArrayList<Feature>(10);
        tagFeatures.add(new TagNameF(TAG_NAME, "T_NAME"));
        tagFeatures.add(new TagTypeF(TAG_TYPE, "T_TYPE"));

        // init all PhraseFeatures
        phraseFeatures=new ArrayList<Feature>(10); // no standard features
        phraseFeatures.add(new PhraseIdF(PHRASE_ID, "PHRASE_ID"));
        phraseFeatures.add(new PhraseLemmaF(PHRASE_LEMMA, "PHRASE_LEMMA"));
        phraseFeatures.add(new PhraseLengthF(PHRASE_LENGTH, "PHRASE_LENGTH"));

        // init all InstanceFeatures
        instanceFeatures=new ArrayList<Feature>(10); // no standard features
    }

    /** Add new custom feature */
    public void registerFeature(Feature f) {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Registering feature "+f.name+" object="+f.objectType);
        switch(f.objectType) {
        case Feature.OBJ_TOKEN:
            f.id=tokenFeatures.size();
            tokenFeatures.add(f);
            break;
        case Feature.OBJ_TAG:
            f.id=tagFeatures.size();
            tagFeatures.add(f);
            break;
        case Feature.OBJ_PHRASE:
            f.id=phraseFeatures.size();
            phraseFeatures.add(f);
            break;
        case Feature.OBJ_INSTANCE:
            f.id=instanceFeatures.size();
            instanceFeatures.add(f);
            break;
        default:
            log.LG(Logger.ERR,"Cannot register feature "+f.name+" (unknown objectType)");
        }
    }

    public void registerKB(KB kb) {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Registering KB "+kb.toString());
        // register master vocab with features
        TokenIdF.getSingleton().vocab=kb.vocab;
        TokenLCF.getSingleton().vocab=kb.vocab;
        UnaccentedF.getSingleton().vocab=kb.vocab;
        TokenLemmaF.getSingleton().vocab=kb.vocab;
        // add UNK_TOKEN with id=0 to master vocab
        TokenInfo uw=kb.vocab.get(TokenIdF.valueNames[TokenIdF.UNK_TOKEN]);
        if(uw==null) {
            uw=new TokenInfo(TokenIdF.valueNames[TokenIdF.UNK_TOKEN], 0); // 0 is arbitrary token type
            uw.intVals.set(PhraseIdF.getSingleton().id, 0); // ID=0, all other=-1
            kb.vocab.add(uw);
        }
        // register master vocab with features
        PhraseIdF.getSingleton().book=kb.phraseBook;
        PhraseLemmaF.getSingleton().book=kb.phraseBook;
        // add UNK_PHRASE with id=0 to master book
        TokenInfo[] uws={uw};
        NBestResult res=new NBestResult(1);
        if(kb.phraseBook.get(uws, res)!=PhraseBook.MATCH_EXACT) {
            PhraseInfo uph=new PhraseInfo(uws);
            uph.initFeatures();
            uph.intValues[PhraseIdF.getSingleton().id]=PhraseIdF.UNK_PHRASE; // ID=0
            uph.intValues[PhraseLemmaF.getSingleton().id]=-1; // LEMMAID=-1
            kb.phraseBook.add(uph);
        }
        this.kb=kb;
    }
    
    public KB getKB() {
        return kb;
    }

    public void prepare() {
        ;
    }

    public TokenF getTokenFeature(int id) {
        return (TokenF) tokenFeatures.get(id);
    }

    public TagF getTagFeature(int id) {
        return (TagF) tagFeatures.get(id);
    }

    public PhraseF getPhraseFeature(int id) {
        return (PhraseF) phraseFeatures.get(id);
    }

    public InstanceF getInstanceFeature(int id) {
        return (InstanceF) instanceFeatures.get(id);
    }

    public void deinit() {
        int i=0;
        for(i=0;i<tokenFeatures.size();i++)
            ((Feature)tokenFeatures.get(i)).deinit();
        for(i=0;i<tagFeatures.size();i++)
            ((Feature)tagFeatures.get(i)).deinit();
        for(i=0;i<phraseFeatures.size();i++)
            ((Feature)phraseFeatures.get(i)).deinit();
    }
    
    public synchronized int getNextFeatureId() {
        return nextFeatureId++;
    }
}
