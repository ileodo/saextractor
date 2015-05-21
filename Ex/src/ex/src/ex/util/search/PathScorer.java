// $Id: PathScorer.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.search;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

/**
   Rescores a path of states.
 */
public interface PathScorer {
    public double rescore(Path p);
}
