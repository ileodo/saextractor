// $Id: Path.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.search;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;

/**
   Represents a path of States in a graph.
 */
public class Path {
    public LinkedList<State> states;
    public double score; // score of this path - intended as sum over all states' scores
    public Path(Collection<State> sts, double sc, boolean copy) {
        if(copy)
            states=new LinkedList<State>(sts); // replace this by a reference to a right-to-left Trie of states with scores
        else
            states=(LinkedList<State>) sts;
        score=sc;
    }
    public Path(State st) {
        states=new LinkedList<State>();
        states.add(st);
        score=st.myScore;
    }
    public void clear() {
        states.clear();
    }
    public String toString() {
        int len=states.size();
        StringBuffer buff=new StringBuffer(64*len);
        Iterator<State> it=states.iterator();
        int i=0;
        buff.append(String.format("Path score=%.3f <br>\n",score));
        while(it.hasNext()) {
            State st=it.next();
            buff.append(st.label());
            buff.append(" ");
            buff.append((st.data==null)? "(null)": st.data.toString());
            buff.append(" [");
            buff.append(String.format("%.3f",st.myScore));
            buff.append(']');
            if(i<len-1) {
                buff.append(",");
            }
            buff.append(" <br>\n");
            i++;
        }
        //buff.append(" ");
        //buff.append(String.format("(%.3f)",score));
        return buff.toString();
    }
    public int length() {
        return states.size();
    }
    public Object[] toArray() {
        int len=length();
        Object[] objs=new Object[len];
        Iterator<State> it=states.iterator();
        int i=0;
        while(it.hasNext()) {
            State st=it.next();
            objs[i]=(Object) st.data;
            i++;
        }
        return objs;
    }
}
