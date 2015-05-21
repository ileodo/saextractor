// $Id: AbstractToken.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import ex.util.IntTrieItem;

public interface AbstractToken extends IntTrieItem {
    public int getTrieKey();
    public int getTokenId();
    public int getLemmaId();
    public int getLCId();
    public int getUnaccentedId();
    public int getMostGeneralId();
    public String getToken();
}
