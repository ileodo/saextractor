// $Id: MicroResult.java 1853 2009-03-08 20:57:05Z labsky $
package medieq.iet.model;

import java.util.Map;
import java.util.TreeMap;

import medieq.iet.generic.AttributeDefImpl;

class MicroAttRecord {
    AttributeDef ad; // which attribute
    double p; // average precision of this attribute, micro-averaged over all documents 
              // in which at least one AUTO annotation of this attribute was observed
    double r; // average recall of this attribute, micro-averaged over all documents 
              // in which at least one GOLD annotation of this attribute was observed
    double f; // average f-measure of this attribute, micro-averaged over all documents
              // where at least 1 GOLD or 1 AUTO annotation exists
    int pDocCnt; // cnt of documents used to compute p 
    int rDocCnt; // cnt of documents used to compute r
    int fDocCnt; // cnt of documents used to compute f
    public MicroAttRecord(AttributeDef ad) {
        this.ad=ad; 
        this.p=0; this.r=0; this.f=0;
        this.pDocCnt=0; this.rDocCnt=0; this.fDocCnt=0;
    }
    public void commit() {
        p = (pDocCnt>0)? (p/(double)pDocCnt): 0.0;
        r = (rDocCnt>0)? (r/(double)rDocCnt): 0.0;
        f = (fDocCnt>0)? (f/(double)fDocCnt): 0.0;
    }
}

public class MicroResult {
    public String name;
    public Map<AttributeDef,MicroAttRecord> strictRecords;
    public Map<AttributeDef,MicroAttRecord> looseRecords;
    public MicroAttRecord avgAttRecordStrict;
    public MicroAttRecord avgAttRecordLoose;
    public MicroAttRecord avgInstRecord;
    public int docCnt; // total number of documents processed

    protected static AttributeDef AVG_ATT_STRICT=new AttributeDefImpl("att_avg-strict", "");
    protected static AttributeDef AVG_ATT_LOOSE =new AttributeDefImpl("att_avg-loose", "");
    protected static AttributeDef AVG_INST=new AttributeDefImpl("inst_avg", "");
    
    public MicroResult(String name) {
        this.name=name;
        strictRecords=new TreeMap<AttributeDef,MicroAttRecord>();
        looseRecords=new TreeMap<AttributeDef,MicroAttRecord>();
        avgAttRecordStrict=new MicroAttRecord(AVG_ATT_STRICT);
        avgAttRecordLoose=new MicroAttRecord(AVG_ATT_LOOSE);
        avgInstRecord=new MicroAttRecord(AVG_INST);
        docCnt=0;
    }

    private MicroAttRecord getMar(AttributeDef ad, Map<AttributeDef,MicroAttRecord> records) {
        MicroAttRecord mar=records.get(ad);
        if(mar==null) {
            mar=new MicroAttRecord(ad);
            records.put(ad, mar);
        }
        return mar;
    }

    private void addEar(EvalAttRecord ear, MicroAttRecord mars, MicroAttRecord marl) {
        // strict
        if(ear.autoCnt>0) { mars.p += ear.getPrecStrict(); mars.pDocCnt++; }
        if(ear.goldCnt>0) { mars.r += ear.getRecallStrict(); mars.rDocCnt++; }
        if(ear.goldCnt>0 || ear.autoCnt>0) {
            mars.f += EvalAttRecord.fmeasure(ear.getPrecStrict(), ear.getRecallStrict());
            mars.fDocCnt++;
        }
        // loose
        if(ear.autoCnt>0) { marl.p += ear.getPrecLoose(); marl.pDocCnt++; }
        if(ear.goldCnt>0) { marl.r += ear.getRecallLoose(); marl.rDocCnt++; }
        if(ear.goldCnt>0 || ear.autoCnt>0) {
            marl.f += EvalAttRecord.fmeasure(ear.getPrecLoose(), ear.getRecallLoose());
            marl.fDocCnt++;
        }
    }
    
    public void add(EvalResult der) {
        for(EvalAttRecord ear: der.attRecords.values()) {
            MicroAttRecord mars = getMar(ear.ad, strictRecords); // strict
            MicroAttRecord marl = getMar(ear.ad, looseRecords); // loose
            addEar(ear, mars, marl);
        }
        // avg att & avg inst - strict & loose
        addEar(der.getAvgAttRecord(), avgAttRecordStrict, avgAttRecordLoose);
        EvalInstRecord eir = der.getAvgInstRecord();
        if(eir.autoInstCnt>0) { avgInstRecord.p += eir.getPrec(); avgInstRecord.pDocCnt++; }
        if(eir.goldInstCnt>0) { avgInstRecord.r += eir.getRecall(); avgInstRecord.rDocCnt++; }
        if(eir.goldInstCnt>0 || eir.autoInstCnt>0) {
            avgInstRecord.f += EvalAttRecord.fmeasure(eir.getPrec(), eir.getRecall());
            avgInstRecord.fDocCnt++;
        }
        docCnt++;
    }

    public void commit() {
        avgAttRecordStrict.commit();
        avgAttRecordLoose.commit();
        avgInstRecord.commit();
        for(MicroAttRecord mar: strictRecords.values()) {
            mar.commit();
        }
        for(MicroAttRecord mar: looseRecords.values()) {
            mar.commit();
        }
    }
    
    public String toString() {
        StringBuffer b=new StringBuffer(1024);
        for(MicroAttRecord mar: strictRecords.values()) {
            b.append(String.format("%20s", mar.ad.getName()+"-strict"));
            b.append(EvalAttRecord.partnf.format(mar.p)+" ("+mar.pDocCnt+")\t");
            b.append(EvalAttRecord.partnf.format(mar.r)+" ("+mar.rDocCnt+")\t");
            b.append(EvalAttRecord.partnf.format(mar.f)+" ("+mar.fDocCnt+")\t");
            b.append("\n");
        }
        for(MicroAttRecord mar: looseRecords.values()) {
            b.append(String.format("%20s", mar.ad.getName()+"-loose"));
            b.append(EvalAttRecord.partnf.format(mar.p)+" ("+mar.pDocCnt+")\t");
            b.append(EvalAttRecord.partnf.format(mar.r)+" ("+mar.rDocCnt+")\t");
            b.append(EvalAttRecord.partnf.format(mar.f)+" ("+mar.fDocCnt+")\t");
            b.append("\n");
        }
        MicroAttRecord[] mars = {avgAttRecordStrict, avgAttRecordLoose, avgInstRecord};
        for(MicroAttRecord mar: mars) {
            b.append(String.format("%20s", mar.ad.getName()));
            b.append(EvalAttRecord.partnf.format(mar.p)+" ("+mar.pDocCnt+")\t");
            b.append(EvalAttRecord.partnf.format(mar.r)+" ("+mar.rDocCnt+")\t");
            b.append(EvalAttRecord.partnf.format(mar.f)+" ("+mar.fDocCnt+")\t");
        }
        return b.toString();
    }
}
