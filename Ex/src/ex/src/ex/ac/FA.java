// $Id: FA.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 * FA (compiled pattern) consisting of FAStates
 * @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

public class FA {
    public FAState startState;
    public FAState finalState;

    public FA(FAState s, FAState f) {
        startState=s;
        finalState=f;
    }

    public FA() {
        this(null,null);
    }

    public String toString() {
        Set<FAState> visited=new TreeSet<FAState>();
        StringBuffer s=new StringBuffer(512);
        StringBuffer t=new StringBuffer(512);
        s.append("digraph FA {\n");
        s.append(" orientation=\"landscape\"; size=\"10.693,8.068\"; margin=\"0.1\"; ratio=\"auto\";\n");
        s.append(" node [shape = box];\n");
        s.append(" rankdir=LR;\n");
        startState.toString(s,t,visited);
        s.append(t);
        s.append("}");
        return s.toString();
    }
    
    public void makeStartFinalNull() {
        if(startState.type!=FAState.ST_NULL) {
            FAState tmp=startState;
            startState=new FAState(FAState.ST_NULL);
            startState.addArcTo(tmp);
        }
        if(finalState.type!=FAState.ST_NULL) {
            FAState tmp=finalState;
            finalState=new FAState(FAState.ST_NULL);
            tmp.addArcTo(finalState);
        }
    }

    protected void disposePrev() {
        Set<FAState> visited=new TreeSet<FAState>();
        startState.disposePrev(visited);
    }
}
