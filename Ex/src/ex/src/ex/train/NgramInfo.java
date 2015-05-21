// $Id: NgramInfo.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uep.util.Logger;

import ex.features.ClassificationF;
import ex.features.TokenNgramF;
import ex.model.AttributeDef;
import ex.model.Model;
import ex.model.ModelElement;
import ex.model.ModelException;
import ex.util.pd.IntDistribution;

/** Holds statistics about a particular ngram of tokens. */
public class NgramInfo {
    protected LLI elems;
    protected int entryCnt;
    protected int elemCnt;
    protected int occCnt;
    
    class LLI {
        ModelElement elem;
        LLI2 counts;
        LLI next;
        public LLI(ModelElement elem, LLI next) {
            this.elem=elem;
            this.next=next;
        }
    }
    class LLI2 {
        byte pos;
        int count;        
        LLI2 next;
        public LLI2(byte pos, int count, LLI2 next) {
            this.pos=pos;
            this.count=count;
            this.next=next;
        }
    }
    
    /** Creates an empty NgramInfo. */ 
    public NgramInfo() {
        elems=null;
        entryCnt=0;
        elemCnt=0;
        occCnt=0;
    }
    
    /** Creates an NgramInfo which is a deep copy of orig. */
    public NgramInfo(NgramInfo orig) {
        add(orig);
    }
    
    /** Gets the number of distinct non-zero ngram+position combinations. */
    public int getEntryCount() {
        return entryCnt;
    }

    /** Gets the number of distinct ModelElements involved in the contained ngram+position combinations. */
    public int getElementCount() {
        return elemCnt;
    }
    
    /** Returns the number of times the ngram was seen in training data. */
    public int getOccCnt() {
        return occCnt;
    }
    
    /** Adds occurrence count to this NgramInfo. */
    public void addOccCnt(int occCnt) {
        this.occCnt+=occCnt;
    }
    
    /** Adds all counts in info to this NgramInfo. */
    public void add(NgramInfo info) {
        LLI it=info.elems;
        while(it!=null) {
            LLI2 it2=it.counts;
            while(it2!=null) {
                addCount(it.elem, it2.pos, it2.count);
                it2=it2.next;
            }
            it=it.next;
        }
    }
    
    /** Clears all information stored in this NgramInfo. */
    public void clear() {
        elems=null;
        entryCnt=0;
        elemCnt=0;
        occCnt=0;
    }
    
    /** Returns whether any counts are set. */
    public boolean isEmpty() {
        return elems==null;
    }

    /** Gets the count of this ngram observed in the given position 
     * for the given attribute. */
    public int getCount(AttributeDef elem, byte position) {
        int cnt=0;
        LLI it=elems;
        while(it!=null) {
            if(it.elem==elem) {
                LLI2 it2=it.counts;
                while(it2!=null) {
                    if(it2.pos==position) {
                        cnt=it2.count;
                        break;
                    }
                    it2=it2.next;
                }
                break;
            }
            it=it.next;
        }
        return cnt;
    }
    
    /** Recomputes occCnt, elemCnt, entryCnt based on content. */
    public void resetCounts() {
        occCnt=0;
        elemCnt=0;
        entryCnt=0;
        LLI it=elems;
        while(it!=null) {
            elemCnt++;
            LLI2 it2=it.counts;
            while(it2!=null) {
                entryCnt++;
                occCnt+=it2.count;
                it2=it2.next;
            }
            it=it.next;
        }
    }

    /** Increases the count of this ngram seen for the given
     * attribute in the given position by cnt. Returns the resulting count. */
    public int addCount(ModelElement elem, byte position, int cnt) {
        return updateCount(elem, position, cnt, true);
    }
    
    /** Sets the count of this ngram seen for the given
     * attribute in the given position to cnt. */
    public int setCount(ModelElement elem, byte position, int cnt) {
        return updateCount(elem, position, cnt, false);
    }
    
    protected int updateCount(ModelElement elem, byte position, int cnt, boolean add) {
        occCnt+=cnt;
        if(elem==null && elems!=null && elems.elem!=null) {
            elems=new LLI(elem, elems);
            elemCnt++;
            elems.counts=new LLI2(position, cnt, null);
            entryCnt++;
            return cnt;
        }
        LLI it=elems;
        LLI last=null;
        while(it!=null) {
            if(it.elem==elem) {
                LLI2 it2=it.counts;
                LLI2 last2=null;
                while(it2!=null) {
                    if(it2.pos==position) {
                        if(add) {
                            it2.count+=cnt;
                            cnt=it2.count;
                        }else {
                            int rc=it2.count;
                            it2.count=cnt;
                            cnt=rc;
                        }
                        break;
                    }else if(it2.pos>position) {
                        if(last2==null) {
                            it.counts=new LLI2(position, cnt, it.counts);
                        }else {
                            last2.next=new LLI2(position, cnt, it2);
                        }
                        entryCnt++;
                    }
                    last2=it2;
                    it2=it2.next;
                }
                if(it2==null) {
                    last2.next=new LLI2(position, cnt, null);
                    entryCnt++;
                    break;
                }
                break;
            }else if(it.elem!=null && it.elem.getElementId()>elem.getElementId()) {
                if(last==null) {
                    elems=new LLI(elem, elems);
                    it=elems;
                }else {
                    last.next=new LLI(elem, it);
                    it=last.next;
                }
                it.counts=new LLI2(position, cnt, null);
                elemCnt++;
                entryCnt++;
                break;
            }
            last=it;
            it=it.next;
        }
        if(it==null) {
            if(last!=null) {
                last.next=new LLI(elem, null);
                last.next.counts=new LLI2(position, cnt, null);
            }else {
                elems=new LLI(elem, null);
                elems.counts=new LLI2(position, cnt, null);
            }
            elemCnt++;
            entryCnt++;
        }
        return cnt;
    }
    
    public StringBuffer toString(StringBuffer b, byte format) {
        LLI it=elems;
        short cnt=0;
        while(it!=null) {
            LLI2 it2=it.counts;
            while(it2!=null) {
                if(cnt>0)
                    b.append(' ');
                b.append((it.elem==null)? ClassificationF.BG: it.elem.name);
                b.append('-');
                b.append(TokenNgramF.pos2string(it2.pos));
                b.append('=');
                b.append(it2.count);
                it2=it2.next;
                cnt++;
            }
            it=it.next;
        }
        return b;
    }
    
    public String toString() {
        return toString(new StringBuffer(128), (byte)0).toString();
    }
    
    public static NgramInfo fromString(CharSequence str, int spos, int epos, 
            Model model, boolean updateModel) {
        NgramInfo ni=new NgramInfo();
        fromString(ni, str, spos, epos, model, updateModel);
        return ni;
    }
    
    static final Pattern dataPat=Pattern.compile("\\s*([^\\s=]+)\\s*=\\s*([^\\s=]+)\\s*");
    
    /** parses ngram count information, e.g. "name-NGR_PREFIX=1 bg-NGR_EQUALS=1" */
    public static void fromString(NgramInfo ni, CharSequence str, int spos, int epos, 
            Model model, boolean updateModel) {
        int p=spos;
        while(p<epos) {
            Matcher m=dataPat.matcher(str);
            while(m.find(p)) {
                String feat = m.group(1);
                String val = m.group(2);
                AttributeDef ad = null;
                int i=feat.indexOf('-');
                if(i>0) {
                    String an=feat.substring(0,i);
                    feat=feat.substring(i+1);
                    if(model!=null) {
                        ad=(AttributeDef) model.getElementByName(an);
                        if(ad==null && updateModel && !an.equalsIgnoreCase(ClassificationF.BG)) {
                            ad=new AttributeDef(an, AttributeDef.TYPE_NAME, model.standaloneAtts);
                            ad.cardDist=new IntDistribution(0, Integer.MAX_VALUE);
                            ad.minCard=0;
                            ad.maxCard=Integer.MAX_VALUE;
                            try {
                                model.addStandaloneAttribute(ad);
                            }catch (ModelException e) {
                                throw new IllegalArgumentException("Internal error: "+e);
                            }
                        }
                    }
                    if(ad==null && !an.equalsIgnoreCase(ClassificationF.BG))
                        Logger.LOGERR("Treating "+an+" as bg");
                }
                byte fid = TokenNgramF.string2pos(feat);
                int intVal = -1;
                try {
                    intVal = Integer.parseInt(val);
                }catch(NumberFormatException e) {
                    throw new IllegalArgumentException("Error reading ngram data: "+e);
                }
                ni.setCount(ad, fid, intVal);
                p=m.end();
            }
        }
        ni.resetCounts();
    }
}
