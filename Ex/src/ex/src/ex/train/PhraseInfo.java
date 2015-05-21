// $Id: PhraseInfo.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.*;
import ex.util.Const;
import uep.util.Logger;
import ex.features.*;

/** PhraseInfo as stored in PhraseBook */
public class PhraseInfo implements AbstractPhrase, Serializable {
    private static final long serialVersionUID = -8967429462101835629L;
    public int[] intValues;      // values of int features, idx is feature.id
    public double[] floatValues; // values of float features, idx is (feature.id - PhraseFeature.intCount)
    public TokenInfo[] tokens;
    // public TokenPattern[] patterns; // matching patterns (TokenPattern knows its AttributeDef, context/content, precision/recall or counts)

    transient public Object data; // general purpose pointer to any associated data  

    public PhraseInfo(TokenInfo[] toks) {
        tokens=toks;
        data=null;
    }

    public PhraseInfo(PhraseTrie lastToken, Vocab voc) {
        if(lastToken.depth<1)
            throw new IllegalArgumentException("Cannot create PhraseInfo from PhraseTrie level<1");

        tokens=new TokenInfo[lastToken.depth];
        int i=lastToken.depth-1;
        do {
            tokens[i]=voc.get(lastToken.label);
            if(tokens[i]==null)
                throw new IllegalArgumentException("Cannot create PhraseInfo from unknown token ID="+lastToken.label+" trie level="+i);
            lastToken=(PhraseTrie) lastToken.parent;
            i--;
        }while(i>=0);
        data=null;
    }

    public void initFeatures() {
        // feature values
        intValues=new int[PhraseF.intFeatures.size()];
        if(PhraseF.floatFeatures.size()>0)
            floatValues=new double[PhraseF.floatFeatures.size()];
        // set known features to undefined
        intValues[PhraseIdF.getSingleton().id]=-1;
        intValues[PhraseLemmaF.getSingleton().id]=-1;
    }
    
    public String toString() {
        return "'"+toPhrase()+"' "+featuresToString();
    }

    public String toPhrase() {
        String s="";
        for(int i=0;i<tokens.length;i++) {
            if(i>0)
                s+=" ";
            s+= tokens[i].token;
        }
        return s;
    }

    public String featuresToString() {
        if(intValues==null)
            return "(features not set)";
        StringBuffer buff=new StringBuffer(512);
        for(int i=0;i<PhraseF.intFeatures.size();i++) {
            PhraseF pf=PhraseF.intFeatures.get(i);
            if(pf.id>FM.PHRASE_LEMMA && intValues[i]==0) // skip 0 attribute counts
                continue;
            if(i>0)
                buff.append(",");
            buff.append(pf.name);
            buff.append("=");
            buff.append(((IntFeature)pf).toString(intValues[i]));
        }
        for(int i=0;i<PhraseF.floatFeatures.size();i++) {
            PhraseF pf=PhraseF.floatFeatures.get(i);
            if(pf.id>FM.PHRASE_LEMMA && intValues[i]==0)
                continue;
            if(i>0)
                buff.append(",");
            buff.append(pf.name);
            buff.append("=");
            buff.append(((FloatFeature)pf).toString(floatValues[i]));
        }
        return buff.toString();
    }

    public int computeFeatures(PhraseBook phraseBook) {
        //PhraseIdF idf=PhraseIdF.getSingleton();
        //PhraseLemmaF lmf=PhraseLemmaF.getSingleton();
        //PhraseListF lif=PhraseListF.getSingleton();
        //PhrasePatternF paf=PhrasePatternF.getSingleton();

        // add to book - automatically updates PHRASE_ID
        int rc=phraseBook.add(this);
        if(rc!=PhraseBook.OK) {
            Logger log=Logger.getLogger("PhraseInfo");
            log.LG(log.ERR,"Error adding new phrase "+this);
        }

        // set other token features:
        // TBD: check matches for all lists and patterns belonging to any attribute in Model

        // insert lemmatized version if not available
        Vocab vocab=phraseBook.getVocab();
        PhraseInfo lemmaPhrase=phraseBook.getLemma(this.tokens);
        if(lemmaPhrase==null) {
            TokenInfo[] tokLemmas=new TokenInfo[tokens.length];
            boolean isDifferent=false;
            for(int i=0;i<tokLemmas.length;i++) {
                tokLemmas[i]=vocab.get(tokens[i].getMostGeneralId());
                if(tokLemmas[i]==null) {
                    Logger log=Logger.getLogger("PhraseInfo");
                    log.LG(log.ERR,"Error adding new lematized phrase: lemma not found for token "+tokens[i]);
                    return Const.EX_ERR;
                }
                if(tokLemmas[i]!=tokens[i])
                    isDifferent=true;
            }
            if(isDifferent) {
                lemmaPhrase=new PhraseInfo(tokLemmas);
                lemmaPhrase.initFeatures();
                rc=phraseBook.add(lemmaPhrase);
                if(rc!=PhraseBook.OK) {
                    Logger log=Logger.getLogger("PhraseInfo");
                    log.LG(log.ERR,"Error adding new lemmatized phrase "+lemmaPhrase);
                    return Const.EX_ERR;
                }
            }
        }
        if(lemmaPhrase!=null)
            intValues[FM.PHRASE_LEMMA]=lemmaPhrase.intValues[FM.PHRASE_ID];
        return Const.EX_OK;
    }

    public static String toString(AbstractToken[] toks, int start, int len) {
        int cnt=(len==-1)? toks.length-start: len;
        StringBuffer buff=new StringBuffer(cnt*16);
        for(int i=start;i<start+cnt;i++) {
            if(i>0)
                buff.append(' ');
            buff.append(toks[i]);
        }
        return buff.toString();
    }
    
    public int getLength() {
        return tokens.length;
    }
    
    public AbstractToken getToken(int idx) {
        return tokens[idx];
    }

    public Object getData() {
        return this;
    }    
}
