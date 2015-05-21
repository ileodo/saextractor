// $Id: GState.java 1986 2009-04-22 13:05:03Z labsky $
package ex.parser;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.Logger;
import ex.util.search.*;
import ex.ac.*;

/* generic state only with id, start and end indices */
public class GState extends State {
    public int startIdx;
    public int endIdx;
    public char type;
    public static final String[] type2str={"n/a","ac","ini","fin","bg","nbg","ic"};
    public static final char ST_AC=1;
    public static final char ST_INI=2;
    public static final char ST_FIN=3;
    public static final char ST_BG=4;
    public static final char ST_NBG=5;
    public static final char ST_IC=6;
    
    public GState(Object dt, double sc, int si, int ei) {
        super(dt,sc);
        startIdx=si;
        endIdx=ei;
        type=0;
    }
    
    public GState(Object dt, double sc, int si, int ei, char type) {
        this(dt,sc,si,ei);
        this.type=type;
        switch(type) {
        case ST_AC: 
            break;
        case ST_IC:
            addFormat("bold");
            break;
        case ST_INI:
        case ST_FIN:
        case ST_BG:
        case ST_NBG:
            addFormat("dashed");
            break;
        }
    }
    
    // to be used by heuristic search
    public double estimate(State target) {
        return 0;
    }
    
    public String label() {
        String text="";
        if(data instanceof AC) {
            AC ac=(AC) data;
            text="\\n"+ac.getAttribute().name+"="+ac.getText(new StringBuffer(16))+"\\n"+String.format("%.3f", myScore); // ac.getLatticeProb()
            if(Parser.SCORING_METHOD==Parser.SM_LOGSUM_SUM)
                text+="("+String.format("%.3f", Math.exp(myScore))+")";
        }else if(data instanceof ACSegment) {
            ACSegment seg=(ACSegment) data;
            text="\\n"+seg.getText()+"\\n"+String.format("%.3f", myScore); // seg.getLatticeProb()
        }else if(data instanceof ICBase) {
            ICBase ic=(ICBase) data;
            text="\\n"+ic.getText()+"\\n"+String.format("%.3f", myScore); // ic.getLatticeProb()
            if(Parser.SCORING_METHOD==Parser.SM_LOGSUM_SUM)
                text+="("+String.format("%.3f", Math.exp(myScore))+")";
        }
        text+=String.format(" %.3f", sum);
        String ret = id+((type!=0)? (" "+type2str[type]+" "):"");
        if((startIdx==-1 && endIdx==-1) ||
           (startIdx==Integer.MAX_VALUE && endIdx==Integer.MAX_VALUE)) {
            ;
        }else {
            ret += "["+startIdx+","+endIdx+"]";
        }
        return ret+text;
    }

    public String toString() {
        StringBuffer buff=new StringBuffer(512);
        buff.append(id+". ["+startIdx+", "+endIdx+"] "+type2str[type]+" ");
        buff.append(data==null? "(null)": data.toString());
        buff.append("\nnext: ");
        for(int i=0;next!=null && i<next.length;i++) {
            if(i>0)
                buff.append(",");
            buff.append(((GState) next[i]).id);
        }
        buff.append("\nprev: ");
        for(int i=0;prev!=null && i<prev.length;i++) {
            if(i>0)
                buff.append(",");
            buff.append(((GState) prev[i]).id);
        }
        return buff.toString();
    }

    public int getLength() {
        return endIdx-startIdx+1;
    }

    public boolean containsNext(State st, int limit) {
        for(int i=0;i<limit;i++)
            if(next[i]==st)
                return true;
        return false;
    }
    
    public int hashCode() {
        return startIdx + endIdx;
    }
}

class DynState extends GState {
    public LinkedList<State> nextArcs;

    public DynState(Object dt, double sc, int si, int ei) {
        super(dt,sc,si,ei);
    }

    public DynState(Object dt, double sc, int si, int ei, char type) {
        super(dt,sc,si,ei,type);
    }
    
    public void clear() {
        if(nextArcs!=null)
            nextArcs.clear();
        nextArcs=null;
        super.clear();
    }

    public boolean addNext(State s, boolean checkForDuplicates) {
        if(Logger.IFLOG(Logger.TRC)) Logger.LOG(Logger.TRC, "addNext "+label()+" -> "+s.label());
        if(nextArcs==null)
            nextArcs=new LinkedList<State>();
        else if(checkForDuplicates) {
            Iterator<State> it=nextArcs.iterator();
            while(it.hasNext()) {
                if(s==it.next())
                    return false;
            }
        }
        nextArcs.add(s);
        return true;
    }

    public void commit() {
        if(nextArcs==null || nextArcs.size()==0)
            return;
        int cnt=nextArcs.size();
        if(Logger.IFLOG(Logger.TRC)) Logger.LOG(Logger.TRC, "commit "+label()+": "+cnt+" new arcs, "+((next!=null)? next.length: 0));
        cnt+=(next!=null)? next.length: 0;
        State[] nxt=new State[cnt];
        cnt=0;
        if(next!=null) {
            System.arraycopy(next, 0, nxt, 0, next.length);
            cnt=next.length;
        }
        Iterator<State> it=nextArcs.iterator();
        while(it.hasNext()) {
            nxt[cnt++]=it.next();
        }
        next=nxt;
        nextArcs=null;
    }
}
