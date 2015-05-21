// $Id: ACSegment.java 1641 2008-09-12 21:53:08Z labsky $
package ex.parser;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

import java.util.*;
import uep.util.*;

import ex.util.search.*;
import ex.ac.*;

public class ACSegment implements Extractable {
    public Path path;
    public int relevantElementCount;
    
    public ACSegment(Path p) {
        path=p;
        if(getStartIdx()>getEndIdx()) {
            Logger.LOG(Logger.ERR,"Internal error: wrong ACSegment boundaries "+toString());
        }else {
            if(Logger.IFLOG(Logger.TRC)) Logger.LOG(Logger.TRC,"ACSegment created: "+toString());
        }
        relevantElementCount=getRelevantElementCount(path);
    }

    public void clear() {
        if(path!=null) {
            path.clear();
            path=null;
        }
    }
    
    /** Returns the number of AC and IC states on path. */
    public static int getRelevantElementCount(Path path) {
        int relCount=0;
        for(State st: path.states) {
            switch(((GState)st).type) {
            case GState.ST_AC:
                relCount++;
                break;
            case GState.ST_IC:
                relCount+=((ICBase)st.data).attCount();
                break;
            }
        }
        return relCount;
    }

    /** Computes a score for a path using error (false positive) probabilities.
     * If allowed, will use orphan probabilities for some of the whole ACs on the path if these are better then error probs.
     * Takes into account AC states' orphan scores and BG state's scores. */
    public static double getErrorOrOrphanProb(Path path, boolean allowOrphans) {
        double sum=0;
        int orpCnt=0;
        for(State st: path.states) {
            switch(((GState)st).type) {
            case GState.ST_AC:
                AC ac=(AC) st.data;
                double sc=ac.getMistakeProb();
                if(allowOrphans) {
                    double sc2=ac.getOrphanProb();
                    if(sc2>=sc) {
                        if(Parser.log.IFLG(Logger.TRC)) Parser.log.LG(Logger.TRC, "Allowed intra-orphan "+ac);
                        sc=sc2;
                        orpCnt++;
                    }
                }
                sum+=sc;
                break;
            case GState.ST_BG:
                sum+=st.myScore;
                break;
            default:
                if(st.myScore!=0) {
                    Logger.LOG(Logger.ERR, "Unexpected scored state "+st+" found on path "+path);
                }
            }
        }
        if(Parser.log.IFLG(Logger.TRC)) Parser.log.LG(Logger.TRC, "Rescored path seg["+((GState) path.states.getFirst()).startIdx+","+
                ((GState) path.states.getLast()).endIdx+"] from score="+path.score+
                " to error"+(allowOrphans?("+"+orpCnt+" orphan"):"")+" score "+sum);
        return sum;
    }

    /** Adds all standalone ACs and all ACs from ICs on the segment's path.
     * If filter has either ACS_ORPHANS xor ACS_FALSE set, only those ACs for which 
     * ac.getOrphanProb>=ac.getErrorProb resp. ac.getOrphanProb<ac.getErrorProb are included. */
    public static int getACs(Path path, Collection<AC> acs, int filter) {
        int cnt=0;
        for(State st: path.states) {
            switch(((GState)st).type) {
            case GState.ST_AC:
                AC ac=(AC) st.data;
                boolean inc=false;
                boolean incOrph =(filter & ICBase.ACS_ORPHANS)!=0;
                boolean incFalse=(filter & ICBase.ACS_FALSE)!=0;
                if((incOrph && !incFalse) || (!incOrph && incFalse)) {
                    double sc=ac.getMistakeProb();
                    double sc2=ac.getOrphanProb();
                    if(sc2>=sc) {
                        inc=incOrph;
                    }else {
                        inc=incFalse;
                    }
                }else {
                    inc=true;
                }
                if(inc) {
                    acs.add(ac);
                    cnt++;
                }
                break;
            case GState.ST_IC:
                ac=((ICBase) st.data).getAC();
                acs.add(ac);
                cnt++;
                break;
            }
        }
        return cnt;
    }
    
    /** Adds all standalone ACs and all ACs from ICs on the segment's path.
     * If filter has either ACS_ORPHANS xor ACS_FALSE set, only those ACs for which 
     * ac.getOrphanProb>=ac.getErrorProb resp. ac.getOrphanProb<ac.getErrorProb are included. */
    public int getACs(Collection<AC> acs, int filter) {
        return getACs(path, acs, filter);
    }
    
    public double getLatticeProb() {
        return path.score;
    }
    
    public void setLatticeProb(double newScore) {
        path.score=newScore;
    }

    public int getStartIdx() {
        return ((GState) path.states.getFirst()).startIdx;
    }

    public int getEndIdx() {
        return ((GState) path.states.getLast()).endIdx;
    }

    public String toString() {
        return "seg["+getStartIdx()+","+getEndIdx()+"] "+pathToString(); // path.toString()
    }

    public String toTableRow() {
        return "<tr><td colspan=\"2\"><span style=\"color:green\">orphans: ["+
        getStartIdx()+","+getEndIdx()+"] "+pathToString()+"</span></td></tr>\n"; // path.toString()
    }
    
    public String pathToString() {
        int len=path.states.size();
        StringBuffer buff=new StringBuffer(64*len);
        Iterator<State> it=path.states.iterator();
        int i=0;
        buff.append(String.format("Path score=%.3f <br>\n",path.score));
        while(it.hasNext()) {
            GState st=(GState) it.next();
            if(st.type!=GState.ST_AC) {
                int showBg=Options.getOptionsInstance().getIntDefault("parser_show_bg", 0);
                if(showBg>0) {
                    buff.append("<span style=\"color:green;font-size:smaller\">");
                }else {
                    continue;
                }
            }
            buff.append(st.label());
            buff.append(" ");
            buff.append((st.data==null)? "(null)": st.data.toString());
            buff.append(" [");
            buff.append(String.format("%.3f",st.myScore));
            buff.append(']');
            if(st.type!=GState.ST_AC) {
                buff.append("</span>");
            }
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

    public static int[] path2ids(Path p) {
        int len=p.length();
        int[] ids=new int[len];
        Iterator<State> it=p.states.iterator();
        int i=0;
        while(it.hasNext()) {
            GState st=(GState) it.next();
            ids[i]=st.id;
            i++;
        }
        return ids;
    }

    public String getText() {
        List<AC> acs=new ArrayList<AC>(8);
        int cnt=getACs(acs, 0);
        StringBuffer s=new StringBuffer(cnt*16);
        for(int i=0;i<cnt;i++) {
            if(i>0)
                s.append(',');
            s.append(acs.get(i).getText(new StringBuffer(16)));
        }
        return s.toString();
    }
}
