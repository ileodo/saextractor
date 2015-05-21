// $Id: SampleSetFactory.java 1676 2008-10-05 20:28:32Z labsky $
package ex.train;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import uep.data.Sample;
import uep.data.SampleFeature;
import uep.data.SampleImpl;
import uep.data.SampleSet;
import uep.util.Logger;
import ex.features.ClassificationF;
import ex.features.Feature;
import ex.features.PhraseLengthF;
import ex.features.TokenIdF;
import ex.features.TokenLCF;
import ex.features.TokenLemmaF;
import ex.features.UnaccentedF;
import ex.reader.SemAnnot;

/** Utility class that creates empty datasets with features 
 *  set according to the specified DataSource implementation.
 *  Also can add samples from DataSource to a SampleSet. */
public class SampleSetFactory {
    static Logger log=Logger.getLogger("dsf");
    
    /** Clears dataSet and sets its features according to DataSource and featureFilter. */
    public static void updateDataSetFeatures(DataSource src, byte featureFilter, SampleSet dataSet) {
        dataSet.clear();
        int classIdx=-1;
        int cnt=0;
        Iterator<Feature> it=src.getFeatureIterator(featureFilter);
        while(it.hasNext()) {
            Feature f=it.next();
            if((f instanceof ClassificationF)) {
                if(classIdx!=-1)
                    throw new IllegalArgumentException("Can't have more than 1 classification feature: "+f.name+" idx="+cnt);
                classIdx=cnt;
            }
            
            byte type=SampleFeature.DT_INT; // DT_ENUM, DT_INT, DT_FLOAT, DT_STRING
            String[] vals=null;
            // enum features
            if(f.valueCnt>1 && f.valueCnt<Short.MAX_VALUE && !(f instanceof PhraseLengthF)) {
                type=SampleFeature.DT_ENUM;
                List<String> nomVals = new ArrayList<String>(f.valueCnt);
                for(int i=0;i<f.getValues().size();i++) {
                    nomVals.add(f.getValues().get(i));
                }
                vals=new String[nomVals.size()];
                vals=nomVals.toArray(vals);
            }
            SampleFeature sf=new SampleFeature(f.name, type, vals, f);
            dataSet.addFeature(sf);
            cnt++;
        }
        if(classIdx<0) {
            classIdx=0;
            Logger.getLogger("dsf").LG(Logger.WRN, "Assuming class index="+classIdx+" in data source "+src);
        }
        dataSet.setClassIdx(classIdx);
    }
    
    /** Creates a new, empty data source and invokes updateDataSetFeatures() on it. */
    public static SampleSet initEmptyDataSet(DataSource src, String name, boolean weighted, byte featureFilter) {
        SampleSet dataSet=new SampleSet(name, weighted);
        updateDataSetFeatures(src, featureFilter, dataSet);
        return dataSet;
    }

    /** Creates a new, empty data source, weighted or not weighted as required by the DataSource,
     *  and invokes updateDataSetFeatures() on it. */
    public static SampleSet initEmptyDataSet(DataSource src, String name) {
        // TODO: derive filter from ClassifierDef (add filter or ClassifierDef as param)
        byte featureFilter = SemAnnot.TYPE_CHUNK; // (byte)-1; // do not use SemAnnot.TYPE_AV, SemAnnot.TYPE_INST
        return initEmptyDataSet(src, name, src.supportsWeightedSamples(), featureFilter);
    }
    
    /** Appends all samples from this data source to the given SampleSet, 
     *  respecting the specified filter. */
    public static int addToSampleSet(DataSource src, SampleSet sset, byte filter) {
        log.LG(Logger.INF, "Adding all samples from datasource "+src+" to "+sset);
        
        // data
        Iterator<DataSample> it=src.getDataIterator(filter);
        int cnt=0;
        while(it.hasNext()) {
            if((cnt++) % 1000 == 0)
                System.err.print("\r"+cnt);
            DataSample ds=it.next();
            Sample smp=ex2sample(ds, sset, false);
            sset.addSample(smp);
        }
        log.LG(Logger.USR, "\nAdded "+cnt+" samples from data source "+src+" to sample set ("+sset+": "+sset.getFeatures().size()+" features, "+sset.size()+" samples cum., "+sset.getFeature(sset.getClassIdx()).getValues().length+" classes)");
        return cnt;
    }
    
    /** @return Sample resulting from converted DataSample. 
     * @param parentDataSet sample set that has feature information compatible with the given DataSample. */
    public static Sample ex2sample(DataSample ds, SampleSet parentDataSet, boolean fillZeros) {
        Sample s=new SampleImpl();
        int[] feats=ds.getFeatures();
        String mostGeneralToken=null;
        if(feats.length!=parentDataSet.getFeatures().size()) {
            String err="Can't add data sample with "+feats.length+" feature values to a target sample set "+parentDataSet;
            log.LGERR(err);
            throw new IllegalArgumentException(err);
        }
        for(int i=0; i<feats.length; i++) {
            int intVal=feats[i];
            if(!fillZeros && intVal==0 && i>0) { // skip 0 non-class feature values
                continue;
            }
            SampleFeature sf=parentDataSet.getFeature(i);
            String val;
            // replace token id and lc token id by the actual tokens:
            if(sf.getData() instanceof TokenIdF) {
                // val=TokenIdF.getSingleton().toString(intVal);
                val=TokenIdF.getSingleton().vocab.get(intVal).token;
                mostGeneralToken=val;
            }else if(sf.getData() instanceof TokenLemmaF ||
                     sf.getData() instanceof TokenLCF ||
                     sf.getData() instanceof UnaccentedF) {
                if(intVal!=-1) {
                    // val=TokenIdF.getSingleton().toString(intVal);
                    val=TokenIdF.getSingleton().vocab.get(intVal).token;
                    mostGeneralToken=val;
                }else {
                    val=mostGeneralToken;
                }
            }else {
                val=sf.indexToValue(feats[i]);
            }
            s.setFeatureValue(i, val);
        }
        if(log.IFLG(Logger.INF)) {
            s.addDebugInfo(ds.getDebugInfo().toString());
        }
        return s;
    }
}
