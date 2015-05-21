// $Id: ACLattice.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

import java.util.Arrays;
import java.util.Comparator;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

public class ACLattice {
    public ACLattice(GState ini, GState end) {
        this.ini=ini;
        this.end=end;
    }
    public GState ini;
    public GState end;
    public GState[] states;
    private static Comparator<GState> stIdxComp=new StateIdxComparator();
    
    public void clear() {
        if(ini!=null)
            ini.clear();
        ini=null;
        if(end!=null)
            end.clear();
        end=null;
        if(states!=null) {
            for(GState st: states) {
                st.clear();
            }
            states=null;
        }
    }
    
    /** TODO: implement effectively using bsearch. */
    public int findStateIdx(GState st) {
//        int idx=-1;
//        for(int i=0;i<states.length;i++) {
//            if(states[i]==st) {
//                idx=i;
//                break;
//            }
//        }
        int idx=Arrays.binarySearch(states, st, stIdxComp);
        if(idx>=0) {
            // careful: any of the states starting at st.startIdx might have been found; roll back first then forward
            int idx2=idx;
            while(states[idx]!=st && idx>0 && states[idx-1].startIdx==st.startIdx) {
                idx--;
            }
            if(states[idx]!=st && idx2+1<states.length) {
                idx=idx2+1;
                while(states[idx]!=st && idx+1<states.length && states[idx+1].startIdx==st.startIdx) {
                    idx++;
                }
            }
            if(states[idx]!=st)
                idx=-(idx+1)-1; // ins point at the end of states with given startIdx
        }
        return idx;
    }
}

class StateIdxComparator implements Comparator<GState> {
    public int compare(GState o1, GState o2) {
        int rc=o1.startIdx-o2.startIdx;
        return rc;
    }
}
