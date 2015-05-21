// $Id: HashVocabImpl.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.Serializable;
import java.util.*;

import ex.features.FM;
import uep.util.Logger;

public class HashVocabImpl implements Serializable, Vocab {
    private static final long serialVersionUID = -4832674809397759441L;
    protected boolean ignCase; // store all tokens *only* as lowercase, all tokens searched for are first lowercased
    protected boolean isMaster; // this is the singleton master vocabulary
    protected TokenInfo[] tokens;
    protected int size; // number of different tokenInfos stored
    protected HashMap<String,TokenInfo> tokenMap;
    //protected HashMap<String,TokenInfo> lcTokenMap; // both maps are used when not ignCase
    protected transient Logger log;

    /** Creates a Vocabset based on hash maps. Case will be ignored in all put and search operations if ignCase */
    public HashVocabImpl(int capacity, boolean ignCase, boolean isMaster) {
        if(log==null)
            log=Logger.getLogger("HVoc");
        this.ignCase=ignCase;
        this.isMaster=isMaster;
        tokenMap=new HashMap<String,TokenInfo>(capacity);
//        if(!ignCase)
//            lcTokenMap=new HashMap<String,TokenInfo>(capacity);
        tokens=new TokenInfo[capacity];
        size=0;
    }
    
    /** Adds new TokenInfo. If ti.id<0, an id is assigned automatically to it. */ 
    public int add(TokenInfo ti) {
        String key=ti.token;
        if(ignCase)
            key=key.toLowerCase();
        if(ti.token==null)
            throw new IllegalArgumentException("HashVocabImpl: TokenInfo must have token specified");
        TokenInfo ex=tokenMap.get(key);
        if(ex!=null) {
            log.LG(Logger.WRN,"Token '"+ti.token+"' already exists! Not adding.");
            return Vocab.ALREADY_EXISTS;
        }
        tokenMap.put(key, ti);
        int tokenId=isMaster? -1: ti.intVals.get(FM.TOKEN_ID);
        if(tokenId<0) {
            tokenId=size;
            ti.intVals.set(FM.TOKEN_ID, tokenId);
        }
        if(tokenId<tokens.length && tokens[tokenId]!=null) {
            log.LG(Logger.WRN,"Token '"+tokens[tokenId].token+"' already has the same id="+tokenId+
                    " as the inserted token '"+ti.token+"'! Not adding.");
            return Vocab.ALREADY_EXISTS;
        }
        size++;
        if(tokenId>=tokens.length) {
            TokenInfo[] old=tokens;
            tokens=new TokenInfo[old.length*2];
            System.arraycopy(old,0,tokens,0,old.length);
            log.LG(Logger.USR,"Increasing Vocab capacity to "+tokens.length+" (tokenId="+tokenId+")");
        }
        tokens[tokenId]=ti;
        return Vocab.OK;
    }

    public TokenInfo get(int id) {
        if(id<0 || id>=tokens.length)
            return null;
        return tokens[id];
    }

    /** Get TokenInfo equal to the given @token, or null.
     */
    public TokenInfo get(String token) {
        if(ignCase)
            token=token.toLowerCase();
        return (TokenInfo) tokenMap.get(token);
    }

    public int search(String token, int nbest, NBestResult res) {
        if(ignCase)
            token=token.toLowerCase();
        TokenInfo ti=tokenMap.get(token);
        if(ti==null)
            return 0;
        res.rc=Vocab.OK;
        res.items[0]=ti;
        res.scores[0]=1.0;
        return 1;
    }

    public int search(TokenInfo ti, int nbest, NBestResult res) {
        String token=ti.token;
        if(token==null)
            throw new IllegalArgumentException("HashVocabImpl: TokenInfo must have token specified");
        if(ignCase)
            token=token.toLowerCase();
        TokenInfo ti2=tokenMap.get(token);
        if(ti2==null)
            return 0;
        res.rc=Vocab.OK;
        res.items[0]=ti2;
        res.scores[0]=1.0;
        return 1;
    }

    public int size() {
        return this.size;
    }
    
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        // read in defaults
        s.defaultReadObject();
        if(log==null)
            log=Logger.getLogger("Vocab");
    }
}
