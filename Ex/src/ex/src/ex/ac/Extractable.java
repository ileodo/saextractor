// $Id: Extractable.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

public interface Extractable {
    /** probability of this entity being extracted as a standalone object */
    public double getLatticeProb();
    public void setLatticeProb(double prob);

    /** index of this entity's first token within Document's tokens */
    public int getStartIdx();

    /** index of this entity's last token within Document's tokens */
    public int getEndIdx();
}
