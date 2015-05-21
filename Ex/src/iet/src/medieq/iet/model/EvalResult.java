// $Id: EvalResult.java 1871 2009-03-28 13:10:41Z labsky $
package medieq.iet.model;

import java.util.*;
import java.util.Map.Entry;

import uep.util.Options;

import medieq.iet.generic.*;

public class EvalResult {
    public String name;
    public Map<AttributeDef,EvalAttRecord> attRecords;
    protected EvalAttRecord avgAttRecord;
    protected EvalInstRecord avgInstRecord;
    public int docCnt;
    protected MicroResult micro;
    
    protected static AttributeDef AVG_ATT=new AttributeDefImpl("avg", "");
    protected static String AVG_INST="avg";
    
    // configurable:
    public static int SHOW_MICRO=1;
    
    public EvalResult(String name) {
        this.name=name;
        attRecords=new TreeMap<AttributeDef,EvalAttRecord>();
        avgAttRecord=new EvalAttRecord(AVG_ATT);
        avgInstRecord=new EvalInstRecord(AVG_INST);
        docCnt=0;
        micro=null;
    }
    
    public EvalAttRecord getRecord(AttributeDef ad, boolean create) {
        EvalAttRecord ear=attRecords.get(ad);
        if(ear==null) {
            ear=new EvalAttRecord(ad);
            attRecords.put(ad, ear);
        }
        return ear;
    }
    
    public void clear() {
        Iterator<EvalAttRecord> it=attRecords.values().iterator();
        while(it.hasNext()) {
            EvalAttRecord ear=it.next();
            ear.clear();
        }
        avgAttRecord.clear();
        avgInstRecord.clear();
    }
    
    public boolean isEmpty() {
        return attRecords.size()==0;
    }
    
    public void add(EvalResult other) {
        for(Entry<AttributeDef, EvalAttRecord> en: other.attRecords.entrySet()) {
            AttributeDef ad=en.getKey();
            EvalAttRecord otherEar=en.getValue();
            EvalAttRecord myEar=attRecords.get(ad);
            if(myEar==null) {
                myEar=new EvalAttRecord(ad);
                attRecords.put(ad, myEar);
            }
            //Logger.LOGERR("Before add: main=\n"+myEar+"\nadded=\n"+otherEar);
            myEar.add(otherEar);
            //Logger.LOGERR("After add: main=\n"+myEar);
        }
        this.docCnt+=other.docCnt;
        this.avgInstRecord.add(other.getAvgInstRecord());
    }
    
    public String toString() {
        return toString(0, 5);
    }
    
    public String toString(int flags, int fractDigits) {
        StringBuffer buff=new StringBuffer(256);
        Collection<EvalAttRecord> recs=attRecords.values();
        buff.append("Eval results docs="+docCnt+", "+name+"("+recs.size()+" attributes):\n");
        buff.append(EvalAttRecord.printHeader(flags));
        for(EvalAttRecord ear: recs) {
            MicroAttRecord mars = (micro!=null)? micro.strictRecords.get(ear.ad): null;
            MicroAttRecord marl = (micro!=null)? micro.looseRecords.get(ear.ad): null;
            buff.append(ear.toString(flags, fractDigits, mars, marl));
        }
        computeAvg();
        // buff.append("overall:\n");
        buff.append(avgAttRecord.toString(flags, fractDigits, 
                (micro!=null)? micro.avgAttRecordStrict: null, (micro!=null)? micro.avgAttRecordLoose: null));
        if(Options.getOptionsInstance().getIntDefault("eval_instances",1)>0) {
            buff.append(avgInstRecord.toString());
        }
        return buff.toString();
    }
    
    public void computeAvg() {
        avgAttRecord.clear();
        Iterator<EvalAttRecord> it=attRecords.values().iterator();
        while(it.hasNext()) {
            EvalAttRecord ear=it.next();
            avgAttRecord.goldCnt+=ear.goldCnt;
            avgAttRecord.goldExactMatchCnt+=ear.goldExactMatchCnt;
            avgAttRecord.goldPartialMatchCnt+=ear.goldPartialMatchCnt;
            avgAttRecord.autoCnt+=ear.autoCnt;
            avgAttRecord.autoExactMatchCnt+=ear.autoExactMatchCnt;
            avgAttRecord.autoPartialMatchCnt+=ear.autoPartialMatchCnt;
        }
    }
    
    public EvalAttRecord getAvgAttRecord() {
        return avgAttRecord;
    }
    
    public EvalInstRecord getAvgInstRecord() {
        return avgInstRecord;
    }
    
    /** @return total number of annotation errors in this document summed over attributes,
     *  computed as: (gold - gold_exact_match_cnt) + (auto - auto_exact_match_cnt) */
    public int getErrorCount() {
        int cnt=0;
        for(EvalAttRecord ear: attRecords.values()) {
            cnt+=ear.getErrorCount();
        }
        return cnt;
    }
    
    /** @return number of annotation errors for the given attribute,
     *  computed as: (gold - gold_exact_match_cnt) + (auto - auto_exact_match_cnt) */
    public int getErrorCount(AttributeDef ad) {
        EvalAttRecord ear=attRecords.get(ad);
        if(ear==null) {
            return -1;
        }
        return ear.getErrorCount();        
    }
    
    public void setMicroResult(MicroResult micro) {
        this.micro=micro;
    }
    
    public MicroResult getMicroResult() {
        return micro;
    }
}
