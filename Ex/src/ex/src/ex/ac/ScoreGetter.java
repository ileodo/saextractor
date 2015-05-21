// $Id: ScoreGetter.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

public interface ScoreGetter {
    
    /* gets score of an extractable entity */
    public double getScore(Extractable ex);
}
