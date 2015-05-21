// $Id: OrphanScoreGetter.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import ex.ac.ScoreGetter;
import ex.ac.Extractable;

/**
   Reads orphan probability from IC (Extractable)
 */
class OrphanScoreGetter implements ScoreGetter {
    public double getScore(Extractable ex) {
        return ((ICBase)ex).getOrphanProb();
    }
}

class ACScoreGetter implements ScoreGetter {
    public double getScore(Extractable ex) {
        return ((ICBase)ex).getACProb();
    }
}
