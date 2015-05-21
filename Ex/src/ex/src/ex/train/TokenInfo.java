// $Id: TokenInfo.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.*;
import ex.util.Const;
import uep.util.Logger;
import ex.util.IntTrieItem;
import ex.ac.TokenPattern;
import ex.features.*;

/** TokenInfo as stored in Vocab */
public class TokenInfo implements Serializable, AbstractToken, IntTrieItem {
    private static final long serialVersionUID = 9205018326849182272L;
    //public int[] intValues;      // values of int features, idx is feature.id
    //public double[] floatValues; // values of float features, idx is (feature.id - TokenFeature.intCount)
    public String token; // token string
    public FIntList intVals;

    // the above data may be moved elsewhere for effectiveness and here replaced by getters
    public TokenInfo() {
        // Logger log=Logger.getLogger("TokenInfo");
        // log.LG(log.ERR,"XX '"+TokenF.intCount+"'"+TokenF.floatCount);

        // intValues=new int[TokenF.intCount];
        // floatValues=new double[TokenF.floatCount];
        intVals=new FIntList();
    }

    public TokenInfo(String tok, int tokType) {
        this();
        token=tok;
        //intValues[TokenIdF.getSingleton().id]=-1;
        //intValues[TokenTypeF.getSingleton().id]=tokType;
        intVals.set(TokenTypeF.getSingleton().id, tokType);
    }

    public String toString() {
        StringBuffer s=new StringBuffer(intVals.size()*32);
        s.append("'"+token+"'");
        intVals.toString(s);
        /*
        for(int i=0;i<TokenF.intCount;i++) {
            TokenF tf=TokenF.intFeatures[i];
            s+= ","+tf.name+"="+((IntFeature)tf).toString(intValues[i]);
        }
        for(int i=0;i<TokenF.floatCount;i++) {
            TokenF tf=TokenF.floatFeatures[i];
            s+= ","+tf.name+"="+((FloatFeature)tf).toString(floatValues[i]);
        }
        */
        return s.toString();
    }

    public int getMostGeneralId() {
        int tokenId=intVals.get(FM.TOKEN_LEMMA);
        if(tokenId==-1) {
            tokenId=intVals.get(FM.TOKEN_UNACC);
            if(tokenId==-1) {
                tokenId=intVals.get(FM.TOKEN_LC);
                if(tokenId==-1) {
                    tokenId=intVals.get(FM.TOKEN_ID);
                }
            }
        }
        return tokenId;
        /*
        if(intValues[FM.TOKEN_LEMMA]!=-1)
            return intValues[FM.TOKEN_LEMMA];
        if(intValues[FM.TOKEN_LC]!=-1)
            return intValues[FM.TOKEN_LC];
        return intValues[FM.TOKEN_ID];
        */
    }

    public int computeFeatures(Vocab voc, boolean recursive) {
        TokenIdF idf=TokenIdF.getSingleton();
        TokenTypeF tpf=TokenTypeF.getSingleton();
        TokenCapF cpf=TokenCapF.getSingleton();
        TokenLCF lcf=TokenLCF.getSingleton();
        UnaccentedF uaf=UnaccentedF.getSingleton();
        TokenLemmaF lmf=TokenLemmaF.getSingleton();
        //TokenListF lif=TokenListF.getSingleton();
        //TokenPatternF paf=TokenPatternF.getSingleton();

        // add to vocab - automatically updates TOKEN_ID
        int rc=voc.add(this);
        if(rc!=Vocab.OK) {
            Logger.LOG(Logger.ERR,"Error adding new token '"+token+"'");
        }

        // set other token features
        // intVals.set(tpf.id, tpf.valueOf(token)); // already set by tokenizer

        // set capitalization
        // intValues[cpf.id]=cpf.valueOf(token);
        int cap=cpf.valueOf(token);
        intVals.set(cpf.id, cap);

        // insert lowercased version if not available
        String lc=token;
        int lcId=-1;
        if(recursive && cap!=TokenCapF.LC && cap!=TokenCapF.NA) {
            lc=TokenLCF.apply(token);
            if(!lc.equals(token)) {
                TokenInfo ti2=voc.get(lc);
                if(ti2==null) {
                    //ti2=new TokenInfo(lc, intValues[tpf.id]); // suppose lc has same type as this
                    ti2=new TokenInfo(lc, intVals.get(tpf.id)); // suppose lc has same type as this
                    ti2.computeFeatures(voc, false);
                    if(rc!=Vocab.OK) {
                        Logger.LOG(Logger.ERR,"Error adding new lowercased token '"+lc+"'");
                    }
                }
                // lcId=ti2.intValues[idf.id];
                lcId=ti2.intVals.get(idf.id);
            }
        }
        //intValues[lcf.id]=lcId;
        if(lcId!=-1)
            intVals.set(lcf.id, lcId);

        // insert unaccented and lowercased version if not available
        int unaccId=-1;
        if(recursive) {
            String unacc=UnaccentedF.apply(lc);
            if(!unacc.equals(lc)) {
                TokenInfo ti2=voc.get(unacc);
                if(ti2==null) {
                    ti2=new TokenInfo(unacc, intVals.get(tpf.id)); // suppose unaccented has same type as this
                    ti2.computeFeatures(voc, false);
                    if(rc!=Vocab.OK) {
                        Logger.LOG(Logger.ERR,"Error adding new unaccented token '"+unacc+"'");
                    }
                }
                unaccId=ti2.intVals.get(idf.id);
            }
        }
        if(unaccId!=-1)
            intVals.set(uaf.id, unaccId);
        
        // insert lemmatized version if not available
        int lemmaId=-1;
        if(recursive) {
            String lemma=lmf.apply(token);
            if(lemma!=null && !lemma.equals(token)) {
                TokenInfo ti2=voc.get(lemma);
                if(ti2==null) {
                    //ti2=new TokenInfo(lemma, intValues[tpf.id]); // suppose lemma has same type as this
                    ti2=new TokenInfo(lemma, intVals.get(tpf.id)); // suppose lemma has same type as this
                    ti2.computeFeatures(voc, false);
                    if(rc!=Vocab.OK) {
                        Logger.LOG(Logger.ERR,"Error adding new lemmatized token '"+lemma+"'");
                    }
                }
                //lemmaId=ti2.intValues[idf.id];
                lemmaId=ti2.intVals.get(idf.id);
            }
        }
        //intValues[lmf.id]=lemmaId;
        if(lemmaId!=-1)
            intVals.set(lmf.id,lemmaId);

        return Const.EX_OK;
    }

    // IntTrieItem
    public int getTrieKey() {
        //return intValues[FM.TOKEN_ID];
        return intVals.get(FM.TOKEN_ID);
    }

    // AbstractToken
    public int getTokenId() {
        //return intValues[FM.TOKEN_ID];
        return intVals.get(FM.TOKEN_ID);
    }
    public int getLemmaId() {
        //return intValues[FM.TOKEN_LEMMA];
        return intVals.get(FM.TOKEN_LEMMA);
    }
    public int getLCId() {
        //return intValues[FM.TOKEN_LC];
        return intVals.get(FM.TOKEN_LC);
    }
    public int getUnaccentedId() {
        return intVals.get(FM.TOKEN_UNACC);
    }
    public String getToken() {
        return token;
    }

    private int tryGetLemma(int curId, int retId, int matchFlags) {
        if(curId!=-1) {
            retId=curId;
            if((matchFlags & TokenPattern.MATCH_IGNORE_LEMMA)!=0) {
                TokenInfo lc=FM.getFMInstance().getKB().vocab.get(retId);
                if(lc!=null) {
                    curId=lc.getLemmaId();
                    if(curId!=-1) {
                        retId=curId;
                    }
                }
            }
        }else {
            if((matchFlags & TokenPattern.MATCH_IGNORE_LEMMA)!=0) {
                curId=getLemmaId();
                if(curId!=-1) {
                    retId=curId;
                }
            }
        }
        return retId;
    }
    
    /** Returns the tokenId to match this token against a pattern compiled with the given matchFlags. */
    public int getTokenIdToMatch(int matchFlags) {
        int tokId=getTokenId();
        
        if((matchFlags & TokenPattern.MATCH_IGNORE_ACCENT)!=0) {
            int id=getUnaccentedId();
            if((matchFlags & TokenPattern.MATCH_IGNORE_CASE)!=0) {
                if(id==-1) {
                    id=getLCId();
                }else {
                    TokenInfo una=FM.getFMInstance().getKB().vocab.get(id);
                    if(una==null) {
                        id=getLCId();
                    }else {
                        int unaLcId=una.getLCId();
                        if(unaLcId!=-1) {
                            id=unaLcId;
                        }
                    }
                }
            }
            tokId=tryGetLemma(id, tokId, matchFlags);
            
        }else if((matchFlags & TokenPattern.MATCH_IGNORE_CASE)!=0) {
            int id=getLCId();
            tokId=tryGetLemma(id, tokId, matchFlags);
        
        }else if((matchFlags & TokenPattern.MATCH_IGNORE_LEMMA)!=0) {
            int id=getLemmaId();
            if(id!=-1) {
                tokId=id;
            }
        }
        
        return tokId;
    }
}
