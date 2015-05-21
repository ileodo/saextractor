// $Id: AbstractPhrase.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

/** Represents phrase consisting of abstract tokens and 
 * additional information accessible using getData(). */
public interface AbstractPhrase {
    public int getLength();
    public AbstractToken getToken(int idx);
    public Object getData();
}
