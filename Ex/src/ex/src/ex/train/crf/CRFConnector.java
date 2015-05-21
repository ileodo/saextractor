// $Id: CRFConnector.java 2034 2009-05-17 10:57:49Z labsky $
package ex.train.crf;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uep.data.CONLLFormat;
import uep.data.Sample;
import uep.data.SampleFeature;
import uep.data.SampleSet;
import uep.util.Logger;
import ex.features.ClassificationF;
import ex.train.DataSample;
import ex.train.SampleSetFactory;
import ex.train.DataSource;
import ex.train.SampleClassifier;
import ex.train.SampleIterator;

public class CRFConnector implements SampleClassifier {
    SampleSet dataSet;
    Properties cfg; // keys: model_file, crf_dir, tmp_dir, encoding;
    Process crfProc;
    private int cntr;
    static String defModelFile="tmp_model.crf";
    static Logger log;
    private SampleCFM globalCfm;

    public CRFConnector() {
        if(log==null) {
            log=Logger.getLogger("crf");
        }
        cfg=new Properties();
        cfg.setProperty("crf_dir","crf");
        cfg.setProperty("tmp_dir","tmp_crf");
        cfg.setProperty("encoding","utf-8");
        cfg.setProperty("model_file",getDefModelFile());
        dataSet=new SampleSet("crf"+this.hashCode(), false);
        crfProc=null;
        globalCfm=new SampleCFM(); // FIXME: should be resetable from outside somehow
    }
    
    public void addSamples(DataSource src, byte featureFilter, boolean uniq) {
        if(uniq) {
            throw new UnsupportedOperationException("Uniq mode of adding samples not supported. Only accepts sequences of sample occurrences.");
        }
        int cnt=SampleSetFactory.addToSampleSet(src, dataSet, featureFilter);        
        log.LG(Logger.USR,"\nAdded "+cnt+" samples from data source to CRF data set ("+dataSet.getFeatures().size()+" features, "+dataSet.size()+" samples cum., "+dataSet.getFeature(0).getValues().length+" classes)");        
    }

    public void addSamples(SampleSet sampleSet, boolean uniq) {
        if(uniq) {
            throw new UnsupportedOperationException("Uniq mode of adding samples not supported. Only accepts sequences of sample occurrences.");
        }
        dataSet.addAll(sampleSet);
    }

    public void classify(DataSample x, boolean cache, int nbest) throws Exception {
        throw new UnsupportedOperationException("Single sample classification not supported. Only accepts sequences of sample occurrences.");
    }

    /** Checks that tmp_dir and crf_dir exist. */
    private void checkDirs() throws IOException {
        File ft=new File(cfg.getProperty("tmp_dir"));
        if(!ft.exists()) {
            ft.mkdirs();
        }else if(!ft.isDirectory()) {
            throw new IOException(ft.getAbsolutePath()+" is not a directory");
        }
        File fc=new File(cfg.getProperty("crf_dir"));
        if(!fc.exists() || !fc.isDirectory()) {
            throw new IOException(fc.getAbsolutePath()+" is not a directory");
        }
    }
    
    public void classifyCurrentDataSet() throws Exception {
        // dump data set to a CRF-compatible file
        checkDirs();
        CONLLFormat conll=new CONLLFormat();
        File ft=new File(cfg.getProperty("tmp_dir"));
        String tmpTestIn ="crf_tmp_testin.conll";
        String tmpTestOut="crf_tmp_testout.conll";
        File fin=new File(ft, tmpTestIn);
        File fout=new File(ft, tmpTestOut);
        conll.save(dataSet, fin.getAbsolutePath());
        
        // run CRF test
        File fc=new File(cfg.getProperty("crf_dir"));
        String cmd="\""+fc.getAbsolutePath()+"/crf_test\" -m \""+cfg.getProperty("model_file")+"\" \""
            +fin.getAbsolutePath()+"\""; // pipe will not work like this: + "> \""+fout.getAbsolutePath()+"\"";
        BufferedWriter stdoutDumper = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fout), cfg.getProperty("encoding")));
        executeProcess(cmd, stdoutDumper, null);
        stdoutDumper.flush();
        stdoutDumper.close();

        // read output and set sample classes accordingly
        SampleSet outSet=CONLLFormat.load(fout.getAbsolutePath());
        if(outSet.size()!=dataSet.size()) {
            if((outSet.size()==1+dataSet.size()) && outSet.getSamples().get(outSet.getSamples().size()-1)==Sample.EOS) {
                ; // just a newline (extra sentence break EOS) at the end of outSet
            }else {
                String msg="Classified dataset samples="+outSet.size()+", expected="+dataSet.size();
                log.LG(Logger.ERR, msg);
                throw new IllegalArgumentException(msg);
            }
        }
        int cnt=dataSet.size();
        int clsIdx=dataSet.getClassIdx();
        if(clsIdx<0) {
            clsIdx=0;
            log.LG(Logger.WRN,"classifyCurrentDataSet: Assuming class idx="+clsIdx);
        }
        SampleCFM cfm=new SampleCFM();
        for(int i=0;i<cnt;i++) {
            Sample so=outSet.getSamples().get(i);
            Sample si=dataSet.getSamples().get(i);
            String predCls=so.getFeatureValue(clsIdx);
            if(predCls!=null && !predCls.equals(ClassificationF.BG)) {
                log.LG(Logger.INF,"Classified current sample as "+predCls+", samples: \n"+so+"\n"+si);
            }
            cfm.add(si, so, 0);
            si.setFeatureValue(clsIdx, predCls);
        }
        log.LG(Logger.USR,"SampleCFM: "+cfm);
        globalCfm.add(cfm);
        cfm.clear();
        log.LG(Logger.USR,"SampleCFM (cum): "+globalCfm);
        
        if(false) {
            if(!fin.delete() || !fout.delete()) {
                log.LGERR("Error deleting CRF tmp files "+fin+","+fout);
            }
        }
    }

    public void classifyDataSet(DataSource src, byte featureFilter, boolean cache, int nbest) throws Exception {
        if(cache) {
            throw new UnsupportedOperationException("Cache not supported. Classifies at the level of sequences of sample occurrences.");
        }
        clearDataSet();
        initEmptyDataSet(src, featureFilter);
        addSamples(src, featureFilter, false);
        classifyCurrentDataSet();
        int clsIdx=dataSet.getClassIdx();
        if(clsIdx<0) {
            clsIdx=0;
            log.LG(Logger.WRN,"classifyDataSet: Assuming class index="+clsIdx);
        }
        
        // propagate classes back to datasource:
        SampleIterator it=src.getDataIterator(featureFilter);
        int cnt=0;
        int exCnt=0;
        while(it.hasNext()) {
            DataSample x=it.next();
            Sample s=dataSet.getSamples().get(cnt);
            String cls=s.getFeatureValue(clsIdx); //.toLowerCase();
            if(cls==null || cls.equals(ClassificationF.BG)) {
                ; // nothing
            }else {
                log.LG(Logger.INF,"Classified as "+cls+": "+x);
                // String c;
                if(cls.startsWith("B-") || cls.startsWith("I-")) {
                    // c=cls.substring(2);
                    exCnt++;
                }else {
                    throw new IllegalArgumentException("Unexpected word class "+cls);
                }
                // char type=cls.charAt(0);
                it.setClass(cls, 0.75, this);
            }
            cnt++;
        }
        if(cnt!=dataSet.size()) {
            throw new IllegalArgumentException("Data source sample count="+cnt+" internal data set size="+dataSet.size());
        }
        log.LG(Logger.USR,"classifyDataSet: "+cnt+" words classified, "+exCnt+" words extracted ("+dataSet.getFeatures().size()+" features, "+dataSet.getFeature(0).getValues().length+" classes)");
        // finalize class information: datasource may want to convert single word classes 
        // like "B-speaker" to multi-word classes like "speaker"
        src.commitClasses();
    }

    public void clearDataSet() {
        dataSet.clear();
    }

    public int getFeatureCount() {
        return dataSet.getFeatures().size();
    }

    public double[] getLastClassDist() {
        throw new UnsupportedOperationException("Only classifies whole sample sequences");
    }

    public double getLastClassValue() {
        throw new UnsupportedOperationException("Only classifies whole sample sequences");
    }

    public int getSampleCount() {
        return dataSet.size();
    }

    public double getWeightedSampleCount() {
        return dataSet.size();
    }

    public void initEmptyDataSet(DataSource src, byte featureFilter) {
        SampleSetFactory.updateDataSetFeatures(src, featureFilter, dataSet);
    }

    public void initEmptyDataSet(SampleSet src) {
        clearDataSet();
        dataSet.setFeatures(src.getFeatures());
        dataSet.setClassIdx(src.getClassIdx());
    }

    public void loadClassifier(String modelFile) throws IOException, ClassNotFoundException {
        modelFile=modelFile.trim();
        File f=new File(modelFile);
        if(!f.exists() || !f.isFile()) {
            throw new FileNotFoundException("Can't open CRF trained model file "+modelFile);
        }
        cfg.setProperty("model_file", modelFile);
    }

    public void loadSamples(String dataFile, boolean treatMissingValuesAsZero) throws IOException {
        if(!treatMissingValuesAsZero) {
            log.LG(Logger.WRN,"CRF does not handle missing values: always treated as 0");
        }
        SampleSet tmp = SampleSet.readXrff(dataFile, false, false);
        dataSet.clear();
        dataSet.addAll(tmp);
        tmp.clear();
    }

    public void newClassifier() throws Exception {
        cfg.setProperty("model_file", getDefModelFile());
    }

    public void saveClassifier(String modelFile) throws IOException {
        if(cfg.getProperty("model_file")==null) {
            throw new IllegalArgumentException("No model to save as "+modelFile);
        }
        File src=new File(cfg.getProperty("model_file"));
        if(!src.exists()) {
            throw new IOException("Internal trained model "+cfg.getProperty("model_file")+" not found!");
        }
        File dst=new File(modelFile);
        if(dst.exists()) {
            log.LG(Logger.WRN,"Overwriting model file "+modelFile);
            dst.delete();
        }
        src.renameTo(dst);
        // remember the saved model as my "currently loaded" model
        cfg.setProperty("model_file", modelFile);
    }

    public void setClassifierOptions(String[] options) throws Exception {
        for(String line: options) {
            line=line.trim();
            if(line.length()==0)
                continue;
            String[] av=line.trim().split("\\s*=\\s*");
            if(av.length!=2)
                throw new IllegalArgumentException("Error parsing options: "+line);
            cfg.setProperty(av[0], av[1]);
        }
    }

    private File createTemplate() throws IOException {
        File ftmp=new File(cfg.getProperty("tmp_dir"));

        // read the template to create the CRF template file:
        String tpl1=cfg.getProperty("template");
        InputStream is=null;
        try {
            is=new FileInputStream(tpl1);
        }catch(IOException ex) {
            // file does not exist in filesystem -> look in the jar
            is=this.getClass().getResourceAsStream(tpl1);
        }
        if(is==null) {
            throw new IOException("CRF template file not found in filesystem and jar: "+tpl1);
        }
        BufferedReader r=new BufferedReader(new InputStreamReader(is,"utf-8"));

        // create the CRF template file based on its template:
        File fout=new File(ftmp, (new File(tpl1)).getName()+".gen");
        BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fout),"utf-8"));
        w.write("# CRF template file generated by Ex\n");
        String line;
        cntr=0;
        while((line=r.readLine())!=null) {
            line=line.trim();
            if(line.trim().startsWith("U") || line.trim().startsWith("B")) {
                instantiateFeatureTemplate(line, w);
            }else {
                w.write(line);
                w.write("\n");
            }
        }
        w.close();
        r.close();
        return fout;
    }

    private static final Pattern patFNum=Pattern.compile("[UB][0-9]+");
    
    /** Transforms "U99 something" to "U123 something" for cntr==123, increments cntr afterwards. */
    private String updateFeatName(String line) {
        Matcher m=patFNum.matcher(line);
        if(m.find() && m.start()==0) {
            line=line.charAt(0)+""+String.format("%02d", cntr)+line.substring(m.end());
            cntr++;
        }
        return line;
    }

    /** For each feature type referred to by the line, 
     a list of columns where values for each individual feature of that type are stored
     e.g. U20:%x[-2,$tokentype]/%x[-1,$tokentype]
     creates 2 featureColumn records, each having a single feature column index
     e.g. U30:%x[0,$value.pattern]
     creates 1 featureColumn record, with as many feature column indices as there are value pattern features */
    private static Pattern fnPat=Pattern.compile("\\$([a-zA-Z_.]+)");
    private int instantiateFeatureTemplate(String line, BufferedWriter w) throws IOException {
        ArrayList<ArrayList<Integer>> featureColumns=new ArrayList<ArrayList<Integer>>();
        List<String> pieces=new ArrayList<String>();
        Matcher m=fnPat.matcher(line);
        int gcnt=0;
        int li=0;
        while(m.find()) {
            ArrayList<Integer> cols=null;
            String fn=m.group(1).toLowerCase();
            for(int fi=0;fi<dataSet.getFeatures().size();fi++) {
                SampleFeature sf=dataSet.getFeatures().get(fi);
                String sfn=sf.getName().toLowerCase();
                if(featureMatchesMask(sfn, fn)) {
                    if(cols==null) {
                        cols=new ArrayList<Integer>();
                    }
                    // -1 because the 0-th feature is the class feature which is moved
                    // to the last position in the CONLL format file used by CRF
                    cols.add(fi-1);
                }
            }
            if(cols==null) {
                log.LG(Logger.WRN,"Skipping feature template "+line+" since feature "+fn+" is not present in dataset");
                return 0;
            }
            gcnt+=cols.size();
            featureColumns.add(cols);
            pieces.add(line.substring(li, m.start()));
            li=m.end();
        }
        
        int cnt;
        if(featureColumns.size()==0) {
            w.write(updateFeatName(line));
            w.write("\n");
            cnt=1;
        }else {
            pieces.add(line.substring(li));
            // all feature types from feature template were found in dataset; now instantiate the feature template: 
            // int[] idxs=new int[featureColumns.size()];
            String left="";
            cnt=genRight(left, featureColumns, pieces, /*idxs,*/ 0, w);
        }
        
        return cnt;
    }

    protected static Map<String,String> fnMap;
    static {
        fnMap=new HashMap<String, String>();
        fnMap.put("id", "tokenid");
        fnMap.put("lc", "tokenlc");
        fnMap.put("unacc", "tokenunacc");
        fnMap.put("lemma", "tokenlemma");
        fnMap.put("type", "tokentype");
        fnMap.put("cap", "tokencap");
    }
    
    /** @return true if the feature of the given name is matched by the specified mask. 
     *  Now implemented using simple substring. */
    private boolean featureMatchesMask(String name, String mask) {
        boolean rc=false;
        String trn=fnMap.get(name);
        if(trn!=null) {
            rc=trn.contains(mask);
        }else {
            rc=name.contains(mask);
        }
        return rc;
    }

    /** Recursively generates CRF feature template lines. */
    private int genRight(String left, 
            ArrayList<ArrayList<Integer>> featureColumns, List<String> pieces,
            /*int[] idxs,*/ int idx, BufferedWriter w) throws IOException {
        ArrayList<Integer> cols=featureColumns.get(idx);
        String pc=pieces.get(idx);
        for(int i=0;i<cols.size();i++) {
            int colId=cols.get(i);
            String part=left+pc+colId;
            if(idx+1<featureColumns.size()) {
                // idxs[idx]=i;
                genRight(part, featureColumns, pieces, /*idxs,*/ idx+1, w);
            }else {
                part+=pieces.get(idx+1)+"\n"; // there are n+1 pieces for n featureColumns
                w.write(updateFeatName(part));
            }
        }
        return 0;
    }

    public void trainClassifier() throws Exception {
        if(dataSet.size()==0) {
            throw new IllegalArgumentException("Empty training set");
        }
        // dump data set to a CRF-compatible file, create feature template file
        checkDirs();
        CONLLFormat conll=new CONLLFormat();
        File ft=new File(cfg.getProperty("tmp_dir"));
        String tmpTestIn = "crf_tmp_training.conll";
        File fin=new File(ft, tmpTestIn);
        conll.save(dataSet, fin.getAbsolutePath());
        File ftpl=createTemplate();
        
        // run CRF trainer
        File fc=new File(cfg.getProperty("crf_dir"));
        String opts="";
        if(cfg.containsKey("args"))
            opts+=" "+cfg.getProperty("args");
        if(cfg.containsKey("-c"))
            opts+=" -c "+cfg.getProperty("-c");
        if(cfg.containsKey("-a"))
            opts+=" -a "+cfg.getProperty("-a");
        String cmd="\""+fc.getAbsolutePath()+"/crf_learn\""+opts+" \""
            +ftpl.getAbsolutePath()+"\" \""
            +fin.getAbsolutePath()+"\" \""
            +cfg.getProperty("model_file")+"\"";
        executeProcess(cmd, null, null);
        
        if(false) {
            if(!fin.delete()) {
                log.LGERR("Error deleting CRF tmp train file "+fin);
            }
        }
    }

    /** Utility method to execute process. */    
    private int executeProcess(String cmd, BufferedWriter stdoutDumper, BufferedWriter stderrDumper) {
        File workDir=new File("."); // cfg.getProperty("workDir")
        String[] envp=null; //new String[0];
        int rc=0;
        try {
            if(!workDir.exists() || !workDir.isDirectory()) {
                throw new IOException("Working dir "+workDir+" does not exist or is not a directory.");
            }
            log.LG(Logger.USR, "Executing:\n"+ cmd);            
            crfProc=Runtime.getRuntime().exec(cmd, envp, workDir);
            BufferedReader stdout=new BufferedReader(new InputStreamReader(crfProc.getInputStream()));
            OutputDumper outDumper = new OutputDumper(stdout, stdoutDumper);
            Thread stdoutDump=new Thread(outDumper);
            BufferedReader stderr=new BufferedReader(new InputStreamReader(crfProc.getErrorStream()));
            OutputDumper errDumper = new OutputDumper(stderr, stderrDumper);
            Thread stderrDump=new Thread(errDumper);
            log.LG(Logger.USR, "Starting reading threads");
            stdoutDump.start();
            stderrDump.start();
            log.LG(Logger.USR, "Reading threads started, waiting for both to finish");
            // wait for the output readers to terminate which happens 
            // after the process has closed its stdout & stderr
            outDumper.waitFor();
            log.LG(Logger.USR, "STDOUT read");
            errDumper.waitFor();
            log.LG(Logger.USR, "STDERR read");
            // wait for the process to fully terminate
            crfProc.waitFor();
            log.LG(Logger.USR, "Process terminated");
        }catch(IOException ex) {
            log.LGERR("Error executing "+cmd+": "+ex);
            rc=-1;
        }catch(InterruptedException ex) {
            log.LGERR("Interrupted waiting for completion of "+cmd+": "+ex);
            rc=-1;
        }
        crfProc=null;
        return rc;
    }
    
    protected class OutputDumper implements Runnable {
        BufferedReader reader;
        BufferedWriter dumper;
        boolean active;
        public OutputDumper(BufferedReader reader, BufferedWriter writer) {
            this.reader=reader;
            this.dumper=writer;
            active=true;
        }
        public void run() {
            active=true;
            log.LG(Logger.USR,"Reader started");
            String line;
            int lno=0;
            int retries=0;
            try {
                while(lno==0) {
                    while((line=reader.readLine())!=null) {
                        lno++;
                        if(dumper!=null) {
                            dumper.write(line);
                            dumper.write("\n");
                        }else {
                            log.LG(Logger.USR,line);
                        }
                    }
                    if(lno==0) {
                        if(retries++<10) {
                            log.LG(Logger.USR,"Wating for process to initialize its stdout or stderr...");
                            synchronized(this) {
                                try {
                                    wait(250);
                                }catch(InterruptedException ex) {
                                    log.LGERR("Dumper interrupted waiting for stream to initialize: "+ex);
                                }
                            }
                        }else {
                            log.LG(Logger.ERR,"Giving up wating for process to initialize its stdout or stderr");
                            break;
                        }
                    }
                }
            }catch(IOException ex) {
                log.LGERR("Error reading CRF process output: "+ex);
            }
            synchronized (this) {
                active=false;
                log.LG(Logger.USR,"Reader ended, notifying main thread");
                notify();
            }
        }
        protected synchronized boolean isActive() {
            return active;
        }
        protected synchronized void waitFor() {
            while(active) {
                try {
                    log.LG(Logger.WRN,"Waiting for stream to close...");
                    wait();
                }catch(InterruptedException ex) {
                    log.LGERR("Dumper interrupted waiting for stream to close: "+ex);
                }
            }
        }
    }

    public String getParam(String name) {
        return cfg.getProperty(name);
    }

    public void setParam(String name, String value) {
        if(value!=null)
            value=value.trim();
        cfg.setProperty(name, value);
    }
    
    private String getDefModelFile() {
        return cfg.getProperty("tmp_dir")+"/"+defModelFile;
    }
}

class CStr {
    String name;
    int cnt;
    public CStr(String name, int cnt) {
        this.name=name;
        this.cnt=cnt;
    }
}

class CStrMap extends TreeMap<String,CStr> {
    private static final long serialVersionUID = 1450688068357104563L;
    int cnt;
    public CStrMap(int cnt) {
        this.cnt=cnt;
    }
    protected CStr getToRecord(String to) {
        CStr myCs=get(to);
        if(myCs==null) {
            myCs=new CStr(to,0);
            put(myCs.name, myCs);
        }
        return myCs;
    }
}

class SampleCFM {
    Map<String,CStrMap> cfm; // contains gold->auto confusion counts, serves to compute recall 
//    SampleCFM invCfm; // contains auto->gold confusion counts, serves to compute precision
    int sampleCnt;
    public SampleCFM() {
        cfm=new TreeMap<String,CStrMap>();
//        invCfm=new SampleCFM(true);
    }
//    private SampleCFM(boolean aux) {
//        invCfm=null;
//    }
    public void clear() {
        cfm.clear();
        sampleCnt=0;
//        if(invCfm!=null) {
//            invCfm.clear();
//        }
    }
    private CStrMap getFromMap(String from) {
        CStrMap myRec=cfm.get(from);
        if(myRec==null) {
            myRec=new CStrMap(0);
            cfm.put(from, myRec);
        }
        return myRec;
    }
    public void add(SampleCFM other) {
        // add gold->auto mappings
        for(Map.Entry<String, CStrMap> from: other.cfm.entrySet()) {
            CStrMap myRec=getFromMap(from.getKey());
            CStrMap otherRec=from.getValue();
            sampleCnt+=otherRec.cnt;
            myRec.cnt+=otherRec.cnt;
            for(Map.Entry<String,CStr> to: otherRec.entrySet()) {
                CStr myCs=myRec.getToRecord(to.getKey());
                myCs.cnt+=to.getValue().cnt;
            }
        }
//        // add auto->gold mappings
//        if(invCfm!=null) {
//            invCfm.add(other.invCfm);
//        }
    }
    public void add(Sample gold, Sample auto, int clsFeatureId) {
        String cls1=trimClassName(gold.getFeatureValue(clsFeatureId));
        String cls2=trimClassName(auto.getFeatureValue(clsFeatureId));
        // add gold->auto mappings
        CStrMap myRec=getFromMap(cls1);
        sampleCnt++;
        myRec.cnt++;
        CStr myCs=myRec.getToRecord(cls2);
        myCs.cnt++;
//        // add auto->gold mappings
//        if(invCfm!=null) {
//            invCfm.add(auto, gold, clsFeatureId);
//        }
    }
    private String trimClassName(String name) {
        name=name.toLowerCase();
        if(name.startsWith("b_") || name.startsWith("b-") || name.startsWith("i_") || name.startsWith("i-")) {
            name=name.substring(2);
        }
        return name;
    }
    protected List<String> getClassList() {
        List<String> lst=new ArrayList<String>(16);
        for(Map.Entry<String, CStrMap> from: cfm.entrySet()) {
            if(!lst.contains(from.getKey())) {
                lst.add(from.getKey());
            }
            CStrMap toMap=from.getValue();
            for(String to: toMap.keySet()) {
                if(!lst.contains(to)) {
                    lst.add(to);
                }
            }
        }
        Collections.sort(lst);
        return lst;
    }
    public String toString() {
        StringBuffer s=new StringBuffer(128);
        List<String> lst=getClassList();
        s.append("\n");
        s.append(String.format("%12s", "gold\\auto"));
        for(int col=0;col<lst.size();col++) {
            s.append(String.format("%12s", lst.get(col)));
        }
        s.append(String.format("%12s", "recall"));
        s.append("\n");
        int gerrCnt=0;
        int gcnt=0;
        ArrayList<Integer> colErrCnts=new ArrayList<Integer>(lst.size());
        ArrayList<Integer> colCnts=new ArrayList<Integer>(lst.size());
        for(int col=0;col<lst.size();col++) {
            colErrCnts.add(0);
            colCnts.add(0);
        }
        for(int row=0;row<lst.size();row++) {
            String from=lst.get(row);
            s.append(String.format("%12s", from));
            int errCnt=0;
            CStrMap toMap=cfm.get(from);
            for(int col=0;col<lst.size();col++) {
                String to=lst.get(col);
                int cnt=0;
                if(toMap!=null) {
                    CStr rec=toMap.get(to);
                    if(rec!=null) {
                        cnt=rec.cnt; // gold->auto
                        colCnts.set(col, colCnts.get(col)+rec.cnt); // auto->gold
                        if(!from.equals(to)) {
                            errCnt+=rec.cnt; // gold->auto
                            colErrCnts.set(col, colErrCnts.get(col)+rec.cnt); // auto->gold
                        }
                    }
                }
                s.append(String.format("%12d", cnt));
            }
            // gold->auto
            if(!lst.get(row).equals(ClassificationF.BG)) {
                gerrCnt+=errCnt;
                if(toMap!=null) {
                    gcnt+=toMap.cnt;
                }
            }
            float recall;
            int toCnt;
            if(toMap!=null) {
                toCnt=toMap.cnt;
                recall=(float)(toCnt-errCnt)/(float)toCnt;
            }else {
                toCnt=0;
                recall=-1;
            }
            s.append(String.format("%2s %.2f = (%d-%d)/%d\n", "", recall, toCnt, errCnt, toCnt));
        }
//        // last row contains per-attribute precisions:
//        if(invCfm!=null) {
//            s.append(String.format("%12s", "precision"));
//            int icnt=0;
//            int ierrCnt=0;
//            for(int i=0;i<lst.size();i++) {
//                String to=lst.get(i);
//                for(Map.Entry<String, CStrMap> invRec: invCfm.cfm.entrySet()) {
//                    String from=invRec.getKey();
//                }
//            }
//            s.append(String.format("precis microavg = %.2f\n", (icnt-ierrCnt)/icnt));
//        }
        s.append(String.format("%12s", "precision"));
        int icnt=0;
        int ierrCnt=0;
        for(int col=0;col<lst.size();col++) {
            float prec=(float)(colCnts.get(col)-colErrCnts.get(col))/(float)colCnts.get(col);
            s.append(String.format("%7s %.2f", "", prec));
            if(!lst.get(col).equals(ClassificationF.BG)) {
                icnt+=colCnts.get(col);
                ierrCnt+=colErrCnts.get(col);
            }
        }
        s.append("\n");
        
        float prec=(float)(icnt-ierrCnt)/(float)icnt;
        float recall=(float)(gcnt-gerrCnt)/(float)gcnt;
        s.append(String.format("precis microavg = %.4f = (%d-%d)/%d\n", prec, icnt, ierrCnt, icnt));
        s.append(String.format("recall microavg = %.4f = (%d-%d)/%d\n", recall,gcnt,gerrCnt, gcnt));
        return s.toString();
    }
}
