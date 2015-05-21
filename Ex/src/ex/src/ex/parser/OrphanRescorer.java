// $Id: OrphanRescorer.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import ex.ac.AC;
import ex.util.search.*;

/**
   Rescores a path of states, previously chosen using different scores.
 */
public class OrphanRescorer implements PathScorer {
    public double rescore(Path p) {
        double sum=0.0;
        ListIterator<State> it=p.states.listIterator();
        int i=0;
        while(it.hasNext()) {
            State st=it.next();
            ICBase ic=(ICBase) st.data;
            if(ic==null)
                continue;
            AC ac=ic.getAC();
            sum+=ac.getOrphanProb();
            i++;
        }
        return sum;
    }
}
