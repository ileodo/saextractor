// $Id: $
package uep.data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import uep.util.Logger;

/** Utility class to save and load SampleSets in CONLL format. */
public class CONLLFormat {
    public static final String BG="bg"; // ex.features.ClassificationF.BG == "bg"
    public static final String BEGIN="B-"; // ex.features.ClassificationF.BEGIN == "B-"
    public static final String INNER="I-"; // ex.features.ClassificationF.BEGIN == "I-"
    // whether input classifications look like "phone" or "B-phone" and "I-phone"
    public static final boolean addClassPrefixes=false; 
    static Logger log;
    
    public CONLLFormat() {
        if(log==null)
            log=Logger.getLogger("conll");
    }
    
    /** @return bytes written. */
    public int save(SampleSet set, String file) throws IOException {
        File f=new File(file);
        BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),"utf-8"));
        int fc=set.getFeatures().size();
        if(fc==0) {
            throw new IllegalArgumentException("No features in sample set");
        }
        int clsFeatIdx=set.getClassIdx();
        if(clsFeatIdx<0) {
            clsFeatIdx=0;
            log.LG(Logger.WRN, "Assuming class idx="+clsFeatIdx);
        }
        log.LG(Logger.USR, "Saving "+file+", samples="+set.size()+", features="+fc+", class="+clsFeatIdx);
        if(set.size()==0) {
            throw new IllegalArgumentException("No samples in sample set");
        }
        String lastClass=null;
        for(Sample s: set) {
            // store EOS or EOF as an empty line
            if(s==Sample.EOS || s==Sample.EOF) {
                w.write('\n');
                continue;
            }
            // store non-class features
            int j=0;
            for(int i=0;i<fc;i++) {
                if(i==clsFeatIdx) {
                    continue; // write the class feature as the last one
                }
                String fv=s.getFeatureValue(i);
                if(j>0)
                    w.write(' ');
                if(fv==null) {
                    w.write('0');
                }else {
                    w.write(fv);
                }
                j++;
            }
            // class feature
            if(fc>1)
                w.write(' ');
            String fv=s.getFeatureValue(clsFeatIdx);
            if(fv==null) {
                fv=BG;
            }
            String clsName=fv;
            if(addClassPrefixes) {
                if(!clsName.equals(BG)) {
                    clsName=(clsName.equals(lastClass)? INNER: BEGIN)+clsName;
                }
            }
            w.write(clsName);
            w.write('\n');
            lastClass=fv;
        }
        w.close();
        int sz=(int) f.length();
        return sz;
    }
    
    public static SampleSet load(String file) throws IOException {
        log.LG(Logger.USR, "Loading "+file);
        SampleSet set=new SampleSet(file, false);
        File f=new File(file);
        BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),"utf-8"));
        int fc=-1;
        int lno=0;
        String line;
        while((line=r.readLine())!=null) {
            lno++;
            line=line.trim();
            if(line.length()==0) {
                set.addSample(Sample.EOS);
                continue;
            }
            String[] vals=line.trim().split("[\t ]");
            if(fc==-1) {
                fc=vals.length;
            }else if(fc!=vals.length) {
                throw new IOException(file+":"+line+": Number of values="+fc+", expected "+vals.length);
            }
            Sample s=new SampleImpl();
            for(int i=0;i<fc;i++) {
                String val=vals[i];
                // last feature is the class which we store at index 0, the rest is shifted by 1: 
                int j = (i<fc-1)? (i+1): 0;
                if(j==0 || !val.equals("0")) {
                    s.setFeatureValue(j, val);
                }
            }
            set.addSample(s);
        }
        return set;
    }
}
