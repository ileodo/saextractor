// $Id: WekaConnector.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train.weka;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import uep.data.Sample;
import uep.data.SampleFeature;
import uep.data.SampleImpl;
import uep.data.SampleSet;
import uep.util.Logger;
import weka.core.converters.AbstractFileSaver;
import weka.core.converters.XRFFSaver;

import ex.features.ClassificationF;
import ex.features.Feature;
import ex.features.PhraseLengthF;
import ex.reader.SemAnnot;
import ex.train.DataSample;
import ex.train.DataSource;
import ex.train.DataSourceWriter;
import ex.train.SampleClassification;
import ex.train.SampleClassifier;
import ex.train.SampleIterator;

public class WekaConnector implements SampleClassifier {
    protected weka.classifiers.Classifier classifier;
    protected String classifierName;
    protected String[] classifierOptions;
    protected weka.core.Instances dataSet;
    protected Map<String,weka.core.Instance> sampleMap;
    protected Map<String,double[]> nbestCache;
    protected double[] lastNBest;
    protected double lastClass;
    protected ArrayList<SampleClassification> lastNBestItems;
    protected String lastKey;
    protected Logger log;
    
    public WekaConnector() {
        classifier=null;
        classifierName="weka.classifiers.functions.SMO";
        classifierOptions=null;
        dataSet=null;
        sampleMap=new HashMap<String,weka.core.Instance>();
        nbestCache=new HashMap<String,double[]>();
        lastNBest=null;
        lastClass=-1;
        lastNBestItems=null;
        log=Logger.getLogger("wc");
    }
    
    /** {@inheritDoc} */
    public void clearDataSet() {
        if(dataSet!=null)
            dataSet.delete();
        sampleMap.clear();
        nbestCache.clear();
    }
    
    /** Prepares an empty dataset containing just feature definitions based on 
     * the given DataSource. */
    public void initEmptyDataSet(DataSource src, byte featureFilter) {
        int classIdx=-1;
        int cnt=0;
        clearDataSet();
        weka.core.FastVector features=new weka.core.FastVector(64);
        Iterator<Feature> it=src.getFeatureIterator(featureFilter);
        while(it.hasNext()) {
            Feature f=it.next();
            weka.core.FastVector nomVals = null;
            
            if((f instanceof ClassificationF)) {
                if(classIdx!=-1)
                    throw new IllegalArgumentException("Can't have more than 1 classification feature: "+f.name+" idx="+cnt);
                classIdx=cnt;
            }
            // enum features
            if(f.valueCnt>1 && f.valueCnt<Short.MAX_VALUE && !(f instanceof PhraseLengthF)) {
                nomVals=new weka.core.FastVector(f.valueCnt);
                for(int i=0;i<f.getValues().size();i++) {
                    nomVals.addElement(f.getValues().get(i));
                }
            }
            weka.core.Attribute wat=null;
            if(nomVals!=null)
                wat=new weka.core.Attribute(f.name, nomVals);
            else
                wat=new weka.core.Attribute(f.name);
            features.addElement(wat);
            cnt++;
        }
        dataSet=new weka.core.Instances(src.getName(), features, 1024);
        if(classIdx<0) {
            classIdx=0;
            log.LG(Logger.WRN, "Assuming class index="+classIdx+" in data source "+src);
        }
        dataSet.setClassIndex(classIdx);
    }
    
    /** Prepares an empty dataset containing feature definitions from given SampleSet. */
    public void initEmptyDataSet(SampleSet src) {
        clearDataSet();
        weka.core.FastVector features=new weka.core.FastVector(64);
        for(SampleFeature sf: src.getFeatures()) {
            weka.core.FastVector nomVals = null;
            int type = -1;
            switch(sf.getType()) {
            case SampleFeature.DT_ENUM:
            case SampleFeature.DT_STRING:
                type = weka.core.Attribute.NOMINAL;
                nomVals = new weka.core.FastVector(sf.getValues().length);
                for(int i=0;i<sf.getValues().length;i++) {
                    nomVals.addElement(sf.getValues()[i]);
                }
                break;
            case SampleFeature.DT_FLOAT:
            case SampleFeature.DT_INT:
                type = weka.core.Attribute.NUMERIC;
                break;
            default:
                throw new IllegalArgumentException("Unknown sample feature type");
            }
            weka.core.Attribute wat=null;
            if(nomVals!=null) {
                wat=new weka.core.Attribute(sf.getName(), nomVals); // enum or string att
            }else {
                wat=new weka.core.Attribute(sf.getName()); // numeric att
            }
            features.addElement(wat);
        }
        dataSet=new weka.core.Instances(src.getName(), features, 1024);
        int classIdx=src.getClassIdx();
        if(classIdx<0) {
            classIdx=0;
            log.LG(Logger.WRN, "Assuming class index="+classIdx+" in sample set "+src);
        }
        dataSet.setClassIndex(classIdx);
    }
    
    /** Adds all samples from a data source as weighted unique weka instances. */
    public void addSamples(DataSource src, byte featureFilter, boolean uniq) {
        Iterator<DataSample> it=src.getDataIterator(featureFilter);
        int cnt=0;
        while(it.hasNext()) {
            DataSample x=it.next();
            sample2instance(x, uniq);
            cnt++;
        }
        log.LG(Logger.USR,"\nAdded "+cnt+" samples from data source to Weka data set ("+dataSet.numAttributes()+" features, "+dataSet.numInstances()+" samples cum., "+dataSet.numClasses()+" classes)");
    }
    
    class SampleWrapper implements DataSample {
        Sample smp;
        int[] feats;
        public SampleWrapper(int featureCount) {
            smp=null;
            feats=new int[featureCount];
        }
        public void setSample(Sample sample, SampleSet sampleSet) {
            if(smp!=null) {
                for(Entry<Integer, String> fv: smp) {
                    feats[fv.getKey()]=0;
                }
            }
            // debug only: slows down!!!
            if(log.IFLG(Logger.TRC)) {
                for(int i=0;i<feats.length;i++) {
                    if(feats[i]!=0)
                        throw new IllegalArgumentException("Internal error: feats["+i+"]="+feats[i]);
                }
            }
            // translate features
            smp=sample;
            for(Entry<Integer, String> fv: smp) {
                int fidx=fv.getKey(); // DataSample and SampleSet index features from 0, Sample from 1
                SampleFeature sf=sampleSet.getFeature(fidx);
                switch(sf.getType()) {
                case SampleFeature.DT_INT:
                    feats[fidx]=Integer.parseInt(fv.getValue());
                    break;
                case SampleFeature.DT_ENUM:
                    int valIdx = sf.valueToIndex(fv.getValue());
                    feats[fidx] = valIdx;
                    break;
                default:
                    throw new IllegalArgumentException("Not implemented: features other than int, enum");
                }
            }
        }
        public String getClassification() {
            return smp.getFeatureValue(0);
        }
        public Object getDebugInfo() {
            return smp.getDebugInfo();
        }
        public int[] getFeatures() {
            return feats;
        }
        public int getWeight() {
            return smp.getWeight();
        }
        public String toString(byte filter, byte format) {
            return smp.toString();
        }
    }
    
    /** {@inheritDoc} */
    public void addSamples(SampleSet sampleSet, boolean uniq) {
        int origCnt=dataSet.numInstances();
        if(dataSet.numAttributes()!=sampleSet.getFeatures().size()) {
            throw new IllegalArgumentException("Different feature counts: weka dataset="+dataSet.numAttributes()+", sample set to be added="+sampleSet.getFeatures().size());
        }
        SampleWrapper wrapper=new SampleWrapper(dataSet.numAttributes());
        for(Sample smp: sampleSet) {
            wrapper.setSample(smp, sampleSet);
            sample2instance(wrapper, uniq);
        }
        int addedCnt=dataSet.numInstances() - origCnt;
        log.LG(Logger.USR,"\nAdded "+sampleSet.size()+"/"+addedCnt+" samples from sample set to Weka data set ("+dataSet.numAttributes()+" features, "+dataSet.numInstances()+" samples cum., "+dataSet.numClasses()+" classes)");
    }

    /** Adds samples from an ARFF or XRFF data file. 
     * Loaded samples replace any previous contents of the Weka data set.
     * @param dataFile data file to load 
     * @param treatMissingValuesAsZero for sparse datasets in xrff format, whether to set missing feature values to 0. */
    public void loadSamples(String dataFile, boolean treatMissingValuesAsZero) throws IOException {
        clearDataSet();
        if(treatMissingValuesAsZero && dataFile.toLowerCase().contains("xrff")) {
            SampleSet sset=SampleSet.readXrff(dataFile, false, true);
            initEmptyDataSet(sset);
            addSamples(sset, false); // is uniq already
        }else {
            weka.core.converters.ConverterUtils.DataSource trainSource;
            try {
                trainSource = new weka.core.converters.ConverterUtils.DataSource(dataFile);
                dataSet = trainSource.getDataSet();
                if(dataSet.classIndex()<0) {
                    dataSet.setClassIndex(0); // the first attribute is the class by default
                }
            }catch(Exception ex) {
                throw new IOException("Error loading dataset from "+dataFile+": "+ex);
            }
        }
    }
    
    /** Registers instance with dataset. */
    protected weka.core.Instance sample2instance(DataSample x, boolean uniq) {
        final boolean useSparse=false;
        weka.core.Instance inst=null;
        if(uniq) {
            //log.LGERR("dataSet size="+dataSet.numInstances()+"/"+this.getWeightedSampleCount());
            lastKey=x.toString((byte)-1,DataSourceWriter.FMT_AV);
            inst=sampleMap.get(lastKey);
        }
        if(inst!=null) {
            //log.LGERR("Known instance "+lastKey+": inc weight "+inst.weight()+" by "+x.getWeight());
            inst.setWeight(inst.weight()+x.getWeight());
            //log.LGERR("dataSet size="+dataSet.numInstances());
        }else {
            int[] vals=x.getFeatures();
            if(vals.length!=dataSet.numAttributes()) {
                String msg="Document sample has "+vals.length+" features, weka dataset has "+dataSet.numAttributes();
                log.LG(Logger.ERR,msg);
                throw new IllegalArgumentException(msg);
            }
            if(useSparse) {
                int on=0;
                for(int i=0;i<vals.length;i++) {
                    if(i!=0)
                        on++;
                }
                double[] tmpVals=new double[on];
                int[] tmpIdxs=new int[on];
                on=0;
                for(int i=0;i<vals.length;i++) {
                    if(i!=0) {
                        tmpVals[on]=vals[i];
                        tmpIdxs[on]=i;
                        on++;
                    }
                }
                inst=new weka.core.SparseInstance(x.getWeight(), tmpVals, tmpIdxs, vals.length);
            }else {
                inst=new weka.core.Instance(vals.length);
                inst.setWeight(x.getWeight());
                for(int i=0;i<vals.length;i++) {
                    inst.setValue(i, vals[i]);
                }
            }
            dataSet.add(inst);
            inst.setDataset(dataSet);
            if(uniq) {
                //log.LGERR("Unknown instance registered w="+inst.weight()+": "+lastKey);
                sampleMap.put(lastKey, inst);
                //log.LGERR("dataSet size="+dataSet.numInstances()+"/"+this.getWeightedSampleCount());
            }
        }
        return inst;
    }
    
    /** Loads a trained serialized classifier from file. */
    public void loadClassifier(String modelFile) throws IOException, ClassNotFoundException {
        classifier=null;
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File(modelFile)));
        classifier = (weka.classifiers.Classifier) ois.readObject();
        ois.close();
    }
    
    /** Classifies all samples from the current dataset using 1-best. */
    public void classifyCurrentDataSet() throws Exception {
        for(int i=0;i<dataSet.numInstances();i++) {
            weka.core.Instance inst=dataSet.instance(i);
            try {
                double cls=classifier.classifyInstance(inst);
                if(cls>0) {
                    Logger.LOG(Logger.USR,"Classified instance as "+(int)cls);
                }
                inst.setClassValue(cls);
            } catch(Exception e) {
                PrintWriter w=new PrintWriter(new StringWriter());
                e.printStackTrace(w);
                Logger.LOGERR("Error classifying instance "+inst+":\n"+w);
                throw e;
            }
        }
    }

    /** Classifies a single sample. */
    public void classify(DataSample x, boolean cache, int nbest) throws Exception {
        weka.core.Instance inst=sample2instance(x, cache);
        try {
            // check if inst or its nbest were not found in cache - only then need to classify
            if(nbest>1) {
                lastNBest=null;
                if(cache) {
                    lastNBest=nbestCache.get(lastKey);
                }
                if(lastNBest==null) {
                    lastNBest=classifier.distributionForInstance(inst);
                    if(cache) {
                        nbestCache.put(lastKey, lastNBest);
                    }
                }
            }else {
                if(false && cache && inst.weight()>1) {
                    ; // use cached (abusing weight of the to-be-classified samples to see if they have been classified)
                }else {
                    double cls=classifier.classifyInstance(inst);
                    inst.setClassValue(cls);
                    if(cls>0) {
                        if(Logger.IFLOG(Logger.USR)) Logger.LOG(Logger.USR,"Classified as "+(int)cls+": "+x.toString(SemAnnot.TYPE_ALL, DataSourceWriter.FMT_LOG));
                    }
                }
                lastClass=inst.classValue(); // returns 0-based class index
            }
            // sample2instance has added the instance to dataSet (otherwise it cannot be classified by Weka)
            // remove it to prevent leak
            dataSet.delete(0);
        } catch(Exception e) {
            StringWriter sw=new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Logger.LOGERR("Error classifying DataSample "+x+":\n "+sw.toString()+"\n nbest="+nbest+" using classifier:\n"+classifier);
            Logger.LOGERR("Classified instance: "+inst.toString());
            throw e;
        }
    }
    
    public double[] getLastClassDist() {
        return lastNBest;
    }
    
    public double getLastClassValue() {
        return lastClass;   
    }
    
    public void saveClassifier(String modelFile) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(modelFile)));
        oos.writeObject(classifier); // classifier is exactly like Classifier.makeCopy(classifier)
        oos.close();
        if(true) {
            dumpDataset(modelFile+"_internal.xrff");
        }
    }

    public void dumpDataset(String fileName) throws IOException {
//        for(Map.Entry<String, weka.core.Instance> en: sampleMap.entrySet()) {
//            System.out.println(en.getKey() + " -> " + en.getValue().toString());
//        }
        boolean useWekaSaver=false; // beware: can't save large files
        if(useWekaSaver) {
            AbstractFileSaver saver = weka.core.converters.ConverterUtils.getSaverForFile(fileName);
            if(saver==null)
                saver=new XRFFSaver();
            saver.setInstances(dataSet);
            saver.setFile(new File(fileName));
//          saver.setDestination(new File(fileName));
//          saver.setDestination(new FileOutputStream(new File(fileName)));
            saver.writeBatch();
        }else {
            SampleSet sset=new SampleSet(fileName, true);
            addDataSetToSampleSet(sset);
            sset.writeXrff(fileName, true);
            sset.clear();
        }
        log.LG(Logger.USR, "Wrote Weka dataset to "+fileName);
    }
    
    /** Converts the current Weka dataset into the given SampleSet. 
     *  This requires the SampleSet to have its features compatible with 
     *  the Weka dataset. */
    protected void addDataSetToSampleSet(SampleSet sset) {
        if(dataSet==null || sset==null) {
            throw new NullPointerException("No data set or sample set");
        }
        if(sset.getFeatures().size()==0) {
            for(int i=0;i<dataSet.numAttributes();i++) {
                weka.core.Attribute att=dataSet.attribute(i);
                String[] vals=new String[att.numValues()];
                for(int j=0;j<vals.length;j++) {
                    vals[j]=att.value(j);
                }
                byte dataType;
                switch(att.type()) {
                case weka.core.Attribute.NOMINAL:
                case weka.core.Attribute.STRING:
                    dataType=SampleFeature.DT_ENUM;
                    break;
                case weka.core.Attribute.NUMERIC:
                    dataType=SampleFeature.DT_INT;
                    break;
                case weka.core.Attribute.DATE:
                case weka.core.Attribute.RELATIONAL:
                default:
                    throw new IllegalArgumentException();
                }
                SampleFeature sf=new SampleFeature(att.name(), dataType, vals);
                sset.addFeature(sf);
            }
        }else if(sset.getFeatures().size() != dataSet.numAttributes()) {
            throw new IllegalArgumentException("SampleSet features="+sset.getFeatures().size()+" DataSet attributes="+dataSet.numAttributes());
        }
        int fc=sset.getFeatures().size();
        for(int i=0;i<dataSet.numInstances();i++) {
            weka.core.Instance ins=dataSet.instance(i);
            Sample smp=new SampleImpl();
            smp.setWeight((int)Math.round(ins.weight()));
            for(int j=0;j<fc;j++) {
                double val=ins.value(j);
                String strVal;
                SampleFeature f=sset.getFeature(j);
                if(f.getType()==SampleFeature.DT_ENUM)
                    strVal=f.indexToValue((int)Math.round(val));
                else
                    strVal=String.valueOf((int)Math.round(val));
                if(val!=0 || j==0) {
                    smp.setFeatureValue(j, strVal);
                }
            }
            sset.addSample(smp);
        }
    }
    
    public void trainClassifier() throws Exception {
        classifier.buildClassifier(dataSet);
    }

    public void newClassifier() throws Exception {
        classifier=weka.classifiers.Classifier.forName(classifierName, classifierOptions);
    }
    
    public void setClassifierOptions(String[] options) throws Exception {
        classifier.setOptions(options);
    }
    
    private void prepareNBestItems() {
        // prepare storage for nbest items:
        weka.core.Attribute clsWat=dataSet.classAttribute();
        int cc=clsWat.numValues();
        if(lastNBestItems==null || lastNBestItems.size()!=lastNBest.length || lastNBest.length!=cc) {
            lastNBestItems=new ArrayList<SampleClassification>(clsWat.numValues());
            for(int i=0;i<cc;i++) {
                String name=clsWat.value(i);
                lastNBestItems.add(new SampleClassificationImpl(name, 0));
            }
        }
        if(lastNBestItems.size()!=lastNBest.length || lastNBest.length!=cc) {
            throw new IllegalArgumentException("lastNBestItems="+lastNBestItems.size()+" lastNBest="+lastNBest.length+" cc="+cc+" ("+clsWat+")");
        }
        // copy name and confidences
        for(int i=0;i<cc;i++) {
            SampleClassificationImpl item=(SampleClassificationImpl) lastNBestItems.get(i);
            item.name=clsWat.value(i);
            item.conf=lastNBest[i];
        }
        // sort
        Collections.sort(lastNBestItems);
    }
   
    public void classifyDataSet(DataSource src, byte featureFilter, boolean cache, int nbest) throws Exception {
        SampleIterator it=src.getDataIterator(featureFilter);
        while(it.hasNext()) {
            DataSample x=it.next();
            classify(x, cache, nbest);
            if(nbest>0) {
                prepareNBestItems();
                it.setClasses(lastNBestItems, nbest, this);
            }else if(lastClass!=0) { // ignore BG class (0-based index, BG is 0)
                String lastClassString=dataSet.classAttribute().value((int)lastClass);
                it.setClass(lastClassString, 0.75, this); // don't know confidence when no nbest is used
            }
        }
    }

    public int getFeatureCount() {
        return (dataSet!=null)? dataSet.numAttributes(): 0;
    }

    public double getWeightedSampleCount() {
        double weightedNum=0;
        for(int i=0;i<dataSet.numInstances();i++) {
            weightedNum+=dataSet.instance(i).weight();
        }
        return weightedNum;
    }
    
    public int getSampleCount() {
        return dataSet.numInstances();
    }

    public String getParam(String name) {
        if(name.equals("algorithm")) {
            return classifierName;
        }
        else if(name.equals("options")) {
            if(classifierOptions==null) {
                return null;
            }else {
                StringBuffer s=new StringBuffer(128);
                for(int i=0;i<classifierOptions.length;i++) {
                   s.append(classifierOptions[i]);
                   s.append("\n");
                }
                return s.toString();
            }
        }
        else {
            throw new IllegalArgumentException(name);
        }
    }

    public void setParam(String name, String value) {
        if(name.equals("algorithm")) {
            classifierName=value;
        }
        else if(name.equals("options")) {
            if(value==null) {
                classifierOptions=null;
            }else {
                classifierOptions=value.trim().split("\\s*\n\\s*");
            }
        }
        else {
            throw new IllegalArgumentException(name);
        }
    }
}

class SampleClassificationImpl implements SampleClassification {
    protected String name;
    protected double conf;
    public SampleClassificationImpl(String name, double conf) {
        this.name=name;
        this.conf=conf;
    }
    public String getClassName() {
        return name;
    }
    public double getConfidence() {
        return conf;
    }
    public int compareTo(SampleClassification o) {
        double diff=o.getConfidence()-conf;
        if(diff==0) {
            return name.compareTo(o.getClassName());
        }
        return (diff<0)? -1: 1;
    }
}
