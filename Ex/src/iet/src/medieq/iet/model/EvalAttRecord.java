// $Id: EvalAttRecord.java 1871 2009-03-28 13:10:41Z labsky $
package medieq.iet.model;

import java.util.*;

public class EvalAttRecord {
    public static java.text.NumberFormat nf=java.text.NumberFormat.getInstance(Locale.ENGLISH);
    public static java.text.NumberFormat partnf=java.text.NumberFormat.getInstance(Locale.ENGLISH);    
    public static int COL_WIDTH1=8;
    public static int COL_WIDTH2=12;
    static {
        nf.setMaximumFractionDigits(5);
        nf.setGroupingUsed(false);
        partnf.setMaximumFractionDigits(2);
        partnf.setGroupingUsed(false);
    }
    public static String fmtNum(double x) {
        return nf.format(x).trim();
    }
    public static String fmtNum2(java.text.NumberFormat nf, double x) {
        if(x==100)
            return "100";
        else if(x==0)
            return "0";
        else
            return nf.format(x).trim();
    }
    public static double fmeasure(double p, double r) {
        return (p==0.0 || r==0.0)? 0.0: ((2*p*r)/(p+r));
    }

    public EvalAttRecord(AttributeDef ad) {
        this.ad=ad;
        goldCnt=autoCnt=0;
        goldExactMatchCnt=autoExactMatchCnt=0;
        goldPartialMatchCnt=autoPartialMatchCnt=0;
        confusionMap=new TreeMap<AttributeDef,EvalAttConfusion>();
    }
    
    public AttributeDef ad;
    public int goldCnt;
    public int autoCnt;
    public int goldExactMatchCnt; // how many times gold annots were matched exactly by autos
    public double goldPartialMatchCnt;
    public int autoExactMatchCnt; // how many times auto annots were matched exactly by golds
    public double autoPartialMatchCnt;
    public Map<AttributeDef,EvalAttConfusion> confusionMap;

    public double getPrecStrict() {
        return (autoCnt==0)? 0.0: ((double) autoExactMatchCnt/autoCnt); // or 1.0: ...
    }
    
    public double getRecallStrict() {
        if(goldCnt==0)
            return (autoCnt==0)? 0.0: 0.0; // or 1.0: 0.0
        return (double) goldExactMatchCnt/goldCnt;
    }
    
    public double getPrecLoose() {
        return (autoCnt==0)? 0.0: ((double) (autoExactMatchCnt+autoPartialMatchCnt)/autoCnt); // or 1.0: ...
    }
    
    public double getRecallLoose() {
        if(goldCnt==0)
            return (autoCnt==0)? 0.0: 0.0; // or 1.0: 0.0
        return (double) (goldExactMatchCnt+goldPartialMatchCnt)/goldCnt;
    }
    
    public void clear() {
        goldCnt=autoCnt=0;
        goldExactMatchCnt=autoExactMatchCnt=0;
        goldPartialMatchCnt=autoPartialMatchCnt=0;
        confusionMap.clear();
    }
    
    public void add(EvalAttRecord other) {
        // add counts
        goldCnt+=other.goldCnt;
        autoCnt+=other.autoCnt;
        goldExactMatchCnt+=other.goldExactMatchCnt;
        autoExactMatchCnt+=other.autoExactMatchCnt;
        goldPartialMatchCnt+=other.goldPartialMatchCnt;
        autoPartialMatchCnt+=other.autoPartialMatchCnt;
        if(other.confusionMap==null)
            return;
        // add confusion matrix counts
        for(EvalAttConfusion conf: other.confusionMap.values()) {
            EvalAttConfusion myConf=confusionMap.get(conf.ad);
            if(myConf==null) {
                myConf=new EvalAttConfusion(conf.ad);
                confusionMap.put(conf.ad, myConf);
            }
            myConf.add(conf);
        }
    }

    public EvalAttConfusion getConfusion(AttributeDef other, boolean create) {
        EvalAttConfusion oac=confusionMap.get(other);
        if(oac==null && create) {
            oac=new EvalAttConfusion(other);
            confusionMap.put(other, oac);
        }
        return oac;
    }
    
    public String toString() {
        String ret=ad.getName()+": gold="+goldCnt+", auto="+autoCnt+
            ", strict: prec="+autoExactMatchCnt+"/"+autoCnt+"="+fmtNum(getPrecStrict())+
            ", recall="+goldExactMatchCnt+"/"+goldCnt+"="+fmtNum(getRecallStrict())+
            ", F="+fmtNum(fmeasure(getPrecStrict(),getRecallStrict()))+
            ", loose: prec="+(fmtNum(autoExactMatchCnt+autoPartialMatchCnt))+"/"+autoCnt+"="+fmtNum(getPrecLoose())+
            ", recall="+(fmtNum(goldExactMatchCnt+goldPartialMatchCnt))+"/"+goldCnt+"="+fmtNum(getRecallLoose())+
            ", F="+fmtNum(fmeasure(getPrecLoose(),getRecallLoose()))+"\n";

        String examples=examplesToString();
        if(examples.length()>0) {
            ret+="\n"+examples;
        }
        
        return ret;
    }
    
    public static final int PRINT_TABULAR=1;
    public static final int PRINT_EXACT=2;
    public static final int PRINT_LOOSE=4;
    public static final int PRINT_PERCENTAGES=8;
    public static final int PRINT_GOLDCOUNTS=16;
    public static final int PRINT_AUTOCOUNTS=32;
    public static final int PRINT_AUTOMATCHCOUNTS=64;
    public static final int PRINT_GOLDMATCHCOUNTS=128;
    public static final int PRINT_EXAMPLES=256;
    public static final int PRINT_MICRO=512;
    
    public String toString(int flags, int fractDigits, MicroAttRecord mars, MicroAttRecord marl) {
        if((flags & PRINT_TABULAR)==0) {
            return toString();
        }
        java.text.NumberFormat nf=java.text.NumberFormat.getInstance(Locale.ENGLISH);
        nf.setMaximumFractionDigits(fractDigits);
        nf.setMinimumFractionDigits(fractDigits);
        boolean useStrict=(flags & PRINT_EXACT)!=0;
        boolean useLoose=(flags & PRINT_LOOSE)!=0;
        StringBuffer s=new StringBuffer(128);
        if(useStrict) {
            s.append(printTabular(flags, true, nf, mars));
        }
        if(useLoose) {
            s.append(printTabular(flags, false, nf, marl));
        }
        return s.toString();
    }

    private String printTabular(int flags, boolean strict, java.text.NumberFormat nf, MicroAttRecord mar) {
        double p=strict? getPrecStrict(): getPrecLoose();
        double r=strict? getRecallStrict(): getRecallLoose();
        double f=fmeasure(p, r);
        StringBuffer s=new StringBuffer(32);
        int factor=1;
        if((flags & PRINT_PERCENTAGES)!=0) {
            p*=100; r*=100; f*=100;
            factor=100;
        }
        int colWidth = COL_WIDTH1;
        if((flags & PRINT_MICRO)!=0) {
            colWidth = COL_WIDTH2;
        }
        String colFmt="%"+colWidth+"s";
        
        String data=String.format("%20s", ad.getName()+"-"+(strict?"strict":"loose "));
        s.append(data);
        data = fmtNum2(nf,p); if(mar!=null) data += "/"+fmtNum2(nf,mar.p*factor);
        s.append(String.format(colFmt, data));
        data = fmtNum2(nf,r); if(mar!=null) data += "/"+fmtNum2(nf,mar.r*factor);
        s.append(String.format(colFmt, data));
        data = fmtNum2(nf,f); if(mar!=null) data += "/"+fmtNum2(nf,mar.f*factor);
        s.append(String.format(colFmt, data));
        if((flags & PRINT_GOLDCOUNTS) != 0) {
            s.append("\t"+goldCnt);
        }
        if((flags & PRINT_AUTOCOUNTS) != 0) {
            s.append("\t"+autoCnt);
        }
        if((flags & PRINT_AUTOMATCHCOUNTS) != 0) {
            s.append("\t"+(strict? autoExactMatchCnt: partnf.format(autoPartialMatchCnt).trim()));
        }
        if((flags & PRINT_GOLDMATCHCOUNTS) != 0) {
            s.append("\t"+(strict? goldExactMatchCnt: partnf.format(goldPartialMatchCnt).trim()));
        }
        s.append("\n");
        return s.toString();
    }
    
    /** Returns a table header if (flags & PRINT_TABULAR), the empty string otherwise. */
    public static String printHeader(int flags) {
        if((flags & PRINT_TABULAR)==0) {
            return ""; // no header if not TABULAR
        }
        int colWidth = COL_WIDTH1;
        if((flags & PRINT_MICRO)!=0) {
            colWidth = COL_WIDTH2;
        }
        String colFmt="%"+colWidth+"s";
        StringBuffer s=new StringBuffer(32);
        s.append(String.format("%20s"," ")+String.format(colFmt,"P")+String.format(colFmt,"R")+String.format(colFmt,"F"));
        if((flags & PRINT_GOLDCOUNTS) != 0) {
            s.append("\tGOLD");
        }
        if((flags & PRINT_AUTOCOUNTS) != 0) {
            s.append("\tAUTO");
        }
        if((flags & PRINT_AUTOMATCHCOUNTS) != 0) {
            s.append("\tAMAT");
        }
        if((flags & PRINT_GOLDMATCHCOUNTS) != 0) {
            s.append("\tGMAT");
        }
        s.append("\n");
        return s.toString();
    }
    
    protected String examplesToString() {
        StringBuffer b=new StringBuffer(64);
        for(EvalAttConfusion c: confusionMap.values()) {
            b.append(ad.getName()+"->"+c);
        }
        return b.toString();
    }
    
    /** @return number of annotation errors for the associated attribute,
    *  computed as: (gold - gold_exact_match_cnt) + (auto - auto_exact_match_cnt) */
    public int getErrorCount() {
        int missed = goldCnt - goldExactMatchCnt;
        int extra = autoCnt - autoExactMatchCnt;
        return missed + extra;
    }
}
