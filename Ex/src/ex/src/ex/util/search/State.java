// $Id: State.java 1986 2009-04-22 13:05:03Z labsky $
package ex.util.search;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.Logger;

/** State in a graph to be searched for best path
 */

public class State implements Comparable<State> {
    public static AscStateComparator asc=new AscStateComparator();

    public int id;
    public Object data; // user data bound to this graph state
    public State[] next; // where we can go next

    public double myScore; // probability/score/weight of solely this state 
    public double sum; // score of the path from the initial node here, incl. this node's score (known as g in astar)
    //public double estimate; // estimated score of the best path from here to the final node (h in astar)
    //public double score; // sum + estimate (f in astar)

    public State[] prev; // for backtracking of n-best paths (was TreeSet<State>)
    public int pc;

    // for debugging only; to be removed
    public String color;

    public State(Object dt, double score) {
        id=nextId();
        data=dt;
        myScore=score;
        next=null;
        prev=null;
        pc=0;
    }
    
    public void clear() {
        data=null;
        prev=null;
        next=null;
        color=null;
    }

    /** returns expected score from here, not including this state, to target */
    // public abstract double estimate(State target);

    /** adds a parent for backtracking if possibly needed */
    public boolean addPrev(State st, int maxNBest) {
        if(prev==null) {
            prev=new State[pc==0? maxNBest: Math.min(pc, maxNBest)];
            prev[0]=st;
            pc=1;
        }else {
            if(pc==maxNBest) {
                State last=prev[pc-1];
                if(st.sum<=last.sum) {
                    return false;
                }
            }
            int ins=bsearch(prev, 0, pc-1, st.sum);
            if(ins>=0) {
                Logger.LOG(Logger.ERR,"State already contained in prev!");
                sum = st.sum + myScore;
                return true;
            }else {
                ins=-ins-1;
                System.arraycopy(prev,ins,prev,ins+1,prev.length-ins-1);
                prev[ins]=st;
                if(pc<maxNBest)
                    pc++;
            }
        }
        // update my scores if the new predecessor is the best one
        if(prev[0]==st) {
            sum = st.sum + myScore;
            return true;
        }
        return false;
    }

    protected int bsearch(State[] arr, int low, int high, double key) {
        int lo=low;
        int hi=high;
        while(lo<=hi) {
            int cur=(lo+hi)/2;
            double val=arr[cur].sum;
            if(key>val)
                hi=cur-1;
            else if(key<val)
                lo=cur+1;
            else
                return cur;
        }
        return -lo-1;
    }

    public int compareTo(State b) {
        if(sum>b.sum)
            return -1;
        if(sum<b.sum)
            return 1;
        if(b==this)
            return 0;
        return this.hashCode()-b.hashCode();
    }

    public String label() {
        return Integer.toString(id);
    }

    public void toGraph(StringBuffer s, StringBuffer t, Set<State> visited) {
        visited.add(this);
        s.append(" s"+id+" [label=\""+label().replace('"', '\'')+"\""+
                ((color!=null)? dotFormat(color): "")+
                "];\n");
        if(next==null)
            return;
        t.append(" s"+id+" -> {");
        for(int i=0;i<next.length;i++) {
            t.append(" s");
            t.append(next[i].id);
        }
        t.append(" };\n");
        // recurse
        for(int i=0;i<next.length;i++) {
            if(!visited.contains(next[i])) {
                next[i].toGraph(s,t,visited);
            }
        }
    }
    
    public void addFormat(String s) {
        if(color==null) {
            color=s;
        }else {
            color = (color+","+s).intern();
        }
    }

    protected static String[] styleKeywords={"bold","dashed","dotted","filled","solid"};
    public static String dotFormat(String format) {
        String[] parts = format.split("\\s*,\\s*");
        String fillcolor = "";
        String color = "";
        String style = "";
        for(int i=parts.length-1;i>=0;i--) {
            String p = parts[i];
            int j = Arrays.binarySearch(styleKeywords, p);
            if(j>=0) {
                style=(style.length()>0)? (style+","+p): p;
            }else {
                if(p.startsWith("fill")) {
                    if(fillcolor.length()==0) {
                        fillcolor=p.substring(4);
                    }
                }else {
                    if(color.length()==0) {
                        color=p;
                    }
                }
            }
        }
        String dot = "";
        if(style.length()>0) {
            dot+=" style=\""+style+"\"";
        }
        if(color.length()>0) {
            dot+=" color=\""+color+"\"";
        }
        if(fillcolor.length()>0) {
            dot+=" fillcolor=\""+fillcolor+"\"";
        }
        return dot;
    }
    
    private static int nxtId=1;
    private static synchronized int nextId() {
        return nxtId++;
    }
}
