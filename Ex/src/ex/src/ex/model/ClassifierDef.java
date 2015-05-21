// $Id: ClassifierDef.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uep.data.SampleSet;
import uep.util.Logger;

import ex.features.ClassificationF;
import ex.reader.SemAnnot;
import ex.train.CountPhraseBookAdapter;
import ex.train.DataSource;
import ex.train.NgramBook;
import ex.train.NgramFeatureBook;
import ex.train.NgramInfo;
import ex.train.PhraseBook;
import ex.train.PhraseBookReader;
import ex.train.PhraseBookReaderImpl;
import ex.train.SampleClassifier;

public class ClassifierDef {
//    public static final byte REPR_PHRASE=1;
//    public static final byte REPR_GAP=2;
//    public static final byte REPR_WORD=3;
//    static private Map<String,Integer> _reprs=new HashMap<String,Integer>();
//    static { _reprs.put("phrase", new Integer(REPR_PHRASE));
//             _reprs.put("gap", new Integer(REPR_GAP));
//             _reprs.put("word", new Integer(REPR_WORD));
//           }
//    public byte representation;
    // now this holds the class name of DataSource implementation instead of the above enum
    public String representation; 
    
    public static final byte CLS_ATTRIBUTE=1;
    public static final byte CLS_CLASS=2;
    public static final byte CLS_ATTRIBUTE_CLASS=3;
    static private Map<String,Integer> _clss=new HashMap<String,Integer>();
    static { _clss.put("attribute", new Integer(CLS_ATTRIBUTE));
             _clss.put("class", new Integer(CLS_CLASS));
             _clss.put("attribute-class", new Integer(CLS_ATTRIBUTE_CLASS)); }
    public byte classType;
    
    public String id; // id to reference this classifier
    public String name; // name identifying the classification algorithm
    public String modelFile; // file that stores the trained model
    public Map<String,String> params; // custom parameters of the classifier
    public double confidenceThreshold; // minimal threshold to take classifications into account
    public List<FeatureDef> features; // list of features, used by DataSource implementations to create and populate features 
    public List<ModelElement> classes;
    protected String classesStr;
    protected ModelElement parentElement;
    public String options;
    public SampleClassifier samec;
    public boolean canClassify;

    // source of samples according to document representation for this classifier
    protected DataSource dataSource;
    // cumulative set of samples for all processed documents
    protected SampleSet sset;
    // ngram book to hold token ngram counts in different positions wrt. 
    // annotated attribute values relevant to this classifier
    protected PhraseBook<NgramInfo, NgramInfo> ngrams;
    // ngram settings:
    int maxNgramSize=2;
    boolean roteLearning=true;
    
    public ClassifierDef(String id, String name, double minConfidence, 
            String modelFile, String repStr, 
            String clsTypeStr, String classesStr, ModelElement parElem) throws ModelException {
        this.id=id;
        this.name=name;
        this.params=new HashMap<String,String>();
        this.confidenceThreshold=minConfidence;
        this.modelFile=modelFile;
//      this.representation=readType(repStr, REPR_PHRASE, _reprs, "sampletype");
        if(repStr==null || (repStr=repStr.trim()).length()==0)
            throw new ModelException("Classifier "+id+" datasource attribute missing or empty");
        this.representation=repStr;
        this.classType=readType(clsTypeStr, CLS_ATTRIBUTE, _clss, "classtype");
        features=null;
        classes=null;
        this.classesStr=classesStr;
        this.parentElement=parElem;
        this.options=null;
        ngrams=new NgramBook<NgramInfo, NgramInfo>("Book for "+name, new CountPhraseBookAdapter());
        canClassify=false;
    }
    
    protected byte readType(String strVal, byte defVal, Map<String,Integer> map, String name) throws ModelException {
        byte type=-1;
        if(strVal==null || strVal.length()==0)
            type=defVal;
        else if(map.containsKey(strVal)) {
            type=(byte)(int)map.get(strVal);
        }else {
            throw new ModelException("Unknown value for classifier "+name+": "+strVal);
        }
        return type;
    }
    
    protected void addFeature(String type, String len, String occ, String mi, String maxCnt, String ignoreList, 
            String positions, String srcSpec, String book, String id) throws ModelException {
        String fid=name+"_"+id;
        // we will create 1 feature definition, possibly for several positions
        FeatureDef fd=new FeatureDef(fid, type, positions,
                len, occ, mi, maxCnt, ignoreList, srcSpec, book);
        if(features==null) {
            features=new ArrayList<FeatureDef>(8);
        }
        features.add(fd);
        // load book if specified
        if(fd.bookFile!=null && fd.bookFile.length()>0) {
            Logger.LOG(Logger.USR,"Reading NgramFeatureBook "+fd.bookFile);
            PhraseBookReader r=new PhraseBookReaderImpl();
            try {
                fd.book=(NgramFeatureBook) r.read(fd.bookFile, PhraseBook.TYPE_NGRAM_FEATURE);
            }catch(IOException ex) {
                Logger.LOG(Logger.WRN,"Error reading NgramFeatureBook "+fd.bookFile+": skipping this feature");
            }
        }
//        int fidx=0;
//        String[] posVals=positions.trim().toLowerCase().split("[\\s,;]+");
//        for( ; fidx<posVals.length; fidx++) {
//            FeatureDef fd=new FeatureDef(fid+"_"+fidx, type, posVals[fidx],
//                                         len, mi, maxCnt, ignoreList, srcSpec);
//            features.add(fd);
//        }
//        if(fidx==0) {
//            FeatureDef fd=new FeatureDef(fid, type, "",
//                                         len, mi, maxCnt, ignoreList, srcSpec);
//            features.add(fd);
//        }
    }

    public void prepare(Model m) throws ModelException {
        String[] clsNames=classesStr.trim().split("[|,; \t\n\r]+");
        classes=new ArrayList<ModelElement>(clsNames.length);
        for(int i=0;i<clsNames.length;i++) {
            if(clsNames[i].length()==0)
                continue;
            if(clsNames[i].equals("*")) {
                if(parentElement!=null) {
                    if(parentElement instanceof ClassDef) {
                        AttributeDef[] atts=((ClassDef)parentElement).attArray;
                        for(int j=0;j<atts.length;j++) {
                            classes.add(atts[j]);
                        }
                    }
                }else {
                    for(int j=0;j<m.classArray.length;j++) {
                        classes.add(m.classArray[j]);
                    }
                }
                continue;
            }
            ModelElement melem=m.getElementByName(clsNames[i]);
            if(melem==null) {
                if(parentElement!=null) {
                    melem=m.getElementByName(parentElement.getFullName()+"."+clsNames[i]);
                }
                if(melem==null) {
                    throw new ModelException("Unknown classifier class '"+clsNames[i]+"': "+classesStr);
                }
            }
            if(!classes.contains(melem))
                classes.add(melem);
        }
        if(classes.size()==0) {
            if(parentElement!=null)
                classes.add(parentElement);
            else
                throw new ModelException("No classes for classifier: '"+classesStr);
        }
    }

    /** Data source of samples used by this classifier. */
    public DataSource getDataSource() {
        return dataSource;
    }

    /** Sets the data source of samples used by this classifier. */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** @return book of assembled ngrams, labeled according to this 
     * classifier's classes. */
    public PhraseBook<NgramInfo, NgramInfo> getNgramBook() {
        return ngrams;
    }

    /** Sets the book of assembled ngrams for this classifier's classes. */
    public void setNgramBook(PhraseBook<NgramInfo, NgramInfo> ngrams) {
        this.ngrams = ngrams;
    }

    public int getMaxNgramSize() {
        return maxNgramSize;
    }

    public void setMaxNgramSize(int maxNgramSize) {
        this.maxNgramSize = maxNgramSize;
    }

    public boolean getRoteLearning() {
        return roteLearning;
    }

    public void setRoteLearning(boolean roteLearning) {
        this.roteLearning = roteLearning;
    }
    
    public SampleSet getSampleSet() { 
        return sset;
    }

    public void setSampleSet(SampleSet sset) {
        this.sset = sset;
    }

    /** Returns true if the classifier handles the given 
     *  model element as on of its target classes. */
    public boolean supportsClass(ModelElement melem) {
        boolean rc=classes.contains(melem);
        return rc;
    }
     
    /** Returns true if the classifier wants to use features that
     *  belong to the given ModelElement. Typically this returns
     *  true whenever supportsClass() returns true, and also for
     *  specializations and generalizations of supported classes. */
    public boolean supportsFeaturesOf(ModelElement melem) {
        if(supportsClass(melem))
            return true;
        boolean rc=false;
        if(melem instanceof AttributeDef) {
            AttributeDef ad=(AttributeDef) melem;

            // search for some supported generalization of melem
            AttributeDef par=ad.parent;
            while(par!=null) {
                if(classes.contains(par)) {
                    rc=true;
                    break;
                }
                par=par.parent;
            }

            // search for some supported specialization of melem
            if(!rc) {
                for(ModelElement sup: classes) {
                    if(sup instanceof AttributeDef) {
                        AttributeDef supa=(AttributeDef) melem;
                        while(supa!=null) {
                            if(supa==ad) {
                                rc=true;
                                break;
                            }
                            supa=supa.parent;
                        }
                    }
                }
            }
        }
        return rc;
    }
    
    public boolean supportsFeatureInduction() {
        if(features!=null) {
            for(FeatureDef fd: features) {
                if(fd.type==FeatureDef.TYPE_NGRAM) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /** @return true for all annotations that do not originate from IET and 
     *  for all annotations of attributes not handled by this classifier. */
    public boolean skipAnnotation(SemAnnot sa, ClassificationF clsF) {
        if(! (sa.data instanceof medieq.iet.model.AttributeValue)) {
            // only use gold standard annotations that come from training data;
            // do not use output by runtime classifiers which has been
            // created for previous phrases on the left
            return true;
        }
        medieq.iet.model.AttributeValue ietAV = (medieq.iet.model.AttributeValue) sa.data; 
        String clsName=ietAV.getAttributeDef().getName();
        // if(!csd.supportsClass(((medieq.iet.model.AttributeValue)sa.data).getAttributeDef()))
        if(clsF.toValue(clsName) == 0) {
            // skip if the classifier does not support this attribute
            return true;
        }
        String author=ietAV.getAuthor();
        if(author==null || !author.equalsIgnoreCase("gold")) {
            // only use gold annotations from IET as phrase class values
            return true;
        }
        return false;
    }
}
