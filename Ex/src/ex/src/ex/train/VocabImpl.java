// $Id: VocabImpl.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.*;
import uep.util.Logger;
import ex.util.Trie;
import ex.features.*;

/** Vocab stores words, their ids, and ids of their lemmas (which are other words in this or another Dict).
    The major purpose is simply to enable other algorithms to work with ids rather than strings. 
*/
public class VocabImpl implements Vocab, Serializable {
    private static final long serialVersionUID = -1283919972424240266L;
    int baseTokenId;    // id to be assigned to the next token added
    boolean ignoreCase; // tokens are stored case-independent in the underlying trie
    Trie trie;
    TokenInfo[] tokens;
    int keyCount;

    public static Logger log;
    public static boolean logOn=false;

    public VocabImpl(int capacity, boolean ignCase) {
        if(log==null)
            log=Logger.getLogger("Vocab");
        ignoreCase=ignCase;
        trie=new Trie(null, (char)0);
        tokens=new TokenInfo[capacity];
        keyCount=0;
    }

    //HashMap check=new HashMap();

    /* if ti.id<0, an id is assigned automatically */ 
    public int add(TokenInfo ti) {
        int rc=trie.put(ti.token, ti, false);
        if(rc==Trie.ALREADY_EXISTS) {
            log.LG(Logger.WRN,"Token '"+ti.token+"' already exists! Not adding.");
            return Vocab.ALREADY_EXISTS;
        }
        //int tokenId=ti.intValues[FM.TOKEN_ID];
        int tokenId=ti.intVals.get(FM.TOKEN_ID);
        if(tokenId<0) {
            tokenId=keyCount;
            //ti.intValues[FM.TOKEN_ID]=tokenId;
            ti.intVals.set(FM.TOKEN_ID, tokenId);
        }
        if(tokenId<tokens.length && tokens[tokenId]!=null) {
            log.LG(Logger.WRN,"Token '"+tokens[tokenId].token+"' already has the same id="+tokenId+
                    " as the inserted token '"+ti.token+"'! Not adding.");
            return Vocab.ALREADY_EXISTS;
        }
        keyCount++;
        if(tokenId>=tokens.length) {
            TokenInfo[] old=tokens;
            tokens=new TokenInfo[old.length*2];
            System.arraycopy(old,0,tokens,0,old.length);
            log.LG(Logger.USR,"Increasing Vocab capacity to "+tokens.length+" (tokenId="+tokenId+")");
        }

        /*
        if(false) {
            // log.LG(log.ERR,"vadd "+ti.token);
            if(check.containsKey(ti.token)) {
                log.LG(log.ERR,"Adding twice "+ti.token);
            }
            check.put(ti.token,null);
        }
         */

        tokens[tokenId]=ti;
        return Vocab.OK;
    }

    /** Get TokenInfo with the given ID, or null.
     */
    public TokenInfo get(int id) {
        if(id<0 || id>=tokens.length)
            return null;
        return tokens[id];
    }

    /** Get TokenInfo equal to the given @token, or null.
     */
    public TokenInfo get(String token) {
        return (TokenInfo) trie.get(token, ignoreCase);
    }

    /** Get TokenInfo equal to the given @token, or null. 
        The first matching TokenInfo is returned when @ignCase=true
     */
    public TokenInfo get(String token, boolean ignCase) {
        return (TokenInfo) trie.get(token, ignCase);
    }

    /** Get TokenInfos of the tokens most (stringwise) similar to @token.
     */
    public int search(String token, int nbest, NBestResult res) {
        TokenInfo ti=(TokenInfo) trie.get(token, ignoreCase);
        if(ti==null)
            return 0;
        res.rc=Vocab.OK;
        res.items[0]=ti;
        res.scores[0]=1.0;
        return 1;
    }

    /** Get TokenInfos of the tokens most (perhaps not only stringwise) similar to @ti.
     */
    public int search(TokenInfo ti, int nbest, NBestResult res) {
        TokenInfo ti2=(TokenInfo) trie.get(ti.token, ignoreCase);
        if(ti2==null)
            return 0;
        res.rc=Vocab.OK;
        res.items[0]=ti2;
        res.scores[0]=1.0;
        return 1;
    }

    /** Number of TokenInfos stored.
     */
    public int size() {
        return keyCount;
    }

    public String toString() {
        return trie.toString();
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        // read in defaults
        s.defaultReadObject();
        if(log==null)
            log=Logger.getLogger("Vocab");
    }
}
