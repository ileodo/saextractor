// $Id: Vocab.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

public interface Vocab {
    public static final int OK=0;
    public static final int ERR=-1;
    public static final int NOT_FOUND=-2;
    public static final int ALREADY_EXISTS=-3;

    /** Add new TokenInfo. Used to:
	- insert new TokenInfos during training (global KB creation)
	- insert unseen TokenInfos found when parsing documents during extraction (update of working KB)
     */
    public int add(TokenInfo tokenInfo);

    /** Get TokenInfo with the given ID, or null.
     */
    public TokenInfo get(int id);

    /** Get TokenInfo equal to the given @token, or null.
     */
    public TokenInfo get(String token);
    
    /** Get TokenInfos of the tokens most (stringwise) similar to @token.
     */
    public int search(String token, int nbest, NBestResult res);

    /** Get TokenInfos of the tokens most (perhaps not only stringwise) similar to @tokenInfo.
     */
    public int search(TokenInfo tokenInfo, int nbest, NBestResult res);

    /** Number of TokenInfos stored.
     */
    public int size();

    public String toString();
}
