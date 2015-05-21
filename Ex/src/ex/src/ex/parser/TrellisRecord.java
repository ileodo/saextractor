// $Id: TrellisRecord.java 1933 2009-04-12 09:14:52Z labsky $
package ex.parser;

import java.util.*;

import uep.util.Logger;

/* Record in Triangle Trellis */
public class TrellisRecord implements Iterable<ICBase> {
    // object seated here
    protected SortedMap<ICBase,ICBase> ics;

    // next occupied position on the left spoke reaching up from this record
    protected TrellisRecord upLeft;

    // next occupied position on the left spoke reaching up from this record
    protected TrellisRecord upRight;

    // linear position in trellis (span in tokens)
    protected int leftIdx;
    protected int rightIdx;
    
    /** Sorts primarily by score, then by contentCode.
     *  TODO: enforce that score does not change for ICs during this Trellis lifetime. */ 
    static final Comparator<ICBase> icTrCmpr=new Comparator<ICBase>() {
        public int compare(ICBase o1, ICBase o2) {
            int rc;
            // double scoreDiff=o1.combinedProb-o2.combinedProb;
            // floats may differ slightly, introduce an epsilon if needed
            double scoreDiff = 0;
            if(scoreDiff==0) {
                rc=o1.contentCode()-o2.contentCode();
                // TODO: we should check the contents here if rc==0 and not rely on contentCode
            }else {
                rc=(scoreDiff>0)? -1: 1;
            }
            return rc;
        }
    };

    protected TrellisRecord(int left, int right) {
        leftIdx=left;
        rightIdx=right;
        this.ics=null;
    }
    
    public void clear() {
        if(ics!=null) {
            for(ICBase ic: ics.keySet()) {
                ic.clear();
            }
            ics.clear();
            ics=null;
        }
        if(upLeft!=null)
            upLeft.clear();
        if(upRight!=null)
            upRight.clear();
    }

    public boolean add(ICBase ic) {
        if(ics==null) {
            ics=new TreeMap<ICBase,ICBase>(icTrCmpr);
        }else { // check for duplicity (is needed)
            ICBase ex=ics.get(ic);
            if(ex==null) {
                ICBase best=ics.firstKey();
                if(ic.combinedProb < best.combinedProb * Parser.BEAM_WIDTH_REL) {
                    if(Parser.log.IFLG(Logger.TRC)) Parser.log.LG(Logger.TRC,"Rel-beam Pruned IC="+ic.getIdScore());
                    return false;
                }
                if(ics.size()>=Parser.BEAM_WIDTH_ABS) {
                    ICBase worst=ics.lastKey();
                    if(ic.combinedProb <= worst.combinedProb) {
                        if(Parser.log.IFLG(Logger.TRC)) Parser.log.LG(Logger.TRC,"Abs-beam Pruned IC="+ic.getIdScore());
                        return false;
                    }else {
                        // Pruned ICs will not be in trellis but they may still figure in larger ICs
                        // and also in IC queue to be processed. We thus need to set a flag in the IC for parser not to follow it.
                        worst.prune();
                        ics.remove(worst);
                    }
                }
            }else {
                if(Parser.log.IFLG(Logger.TRC)) Parser.log.LG(Logger.TRC,"Cannot add duplicate content ic="+ic.getIdScore()+" existing="+ex);
                return false;
            }
        }
        ics.put(ic,ic);
        ic.container=this;
        return true;
    }

    public int size() {
        return ics.size();
    }

    public String toString() {
        if(ics==null)
            return "(empty)";
        int i=0;
        StringBuffer buff=new StringBuffer(128);
        for(ICBase ic: this) {
            buff.append((++i)+". ");
            buff.append(ic.getIdScore());
            buff.append("\n");
        }
        return buff.toString();
    }

    // this does not use equals()
    public boolean contains(ICBase ic) {
        if(ics==null)
            return false;
        int sz=ics.size();
        for(int i=0;i<sz;i++) {
            ICBase ic2=ics.get(i);
            if(ic==ic2) {
                return true;
            }
        }
        return false;
    }
    
    public TrellisRecord getUpLeft() {
        return upLeft;
    }
    
    public TrellisRecord getUpRight() {
        return upRight;
    }

    public Iterator<ICBase> iterator() {
        return ics.keySet().iterator();
    }    
}
