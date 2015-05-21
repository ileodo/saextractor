// $Id: TrainableExtractor.java 1759 2008-11-28 01:08:36Z labsky $
package medieq.iet.components;

import java.util.List;

import uep.data.AddableDataSet;
import uep.data.SampleSet;
import medieq.iet.model.DataModel;
import medieq.iet.model.Document;
import medieq.iet.model.DocumentSet;

public interface TrainableExtractor {
    /** Collects training/testing samples from all documents in docSet. See dumpSamples().
     * @param docSet documents to process. */
    public void dumpSamples(DocumentSet docSet, DataModel model, List<SampleSet> sampleSets);
    
    /** Collects training/testing samples from all documents in docSet.
     * @param doc document to process
     * @param model IET data model
     * @param sampleSets[out] list of sample sets to be populated with one SampleSet
     *        for each classifier used by the current extraction model of this engine.
     *        If null, sample sets are kept internally with each classifier definition inside 
     *        the IE engine. */
    public void dumpSamples(Document doc, DataModel model, List<SampleSet> sampleSets);
    
    /** Dumps each sample set in sampleSets to disk.
     * @param sampleSets sample sets to dump. If null, sample sets bound to each classifier definition
     * are dumped. */
    public void dumpSamplesCumulative(List<SampleSet> sampleSets);

    /** Collects training/testing samples from all documents in docSet. See dumpFIData().
     * @param docSet documents to process. */
    public void dumpFIData(DocumentSet docSet, DataModel model, List<AddableDataSet> fiSets);
    
    /** Collects training/testing samples from all documents in docSet.
     * @param docSet documents to process
     * @param model IET data model
     * @param fiSets[out] list of feature induction data sets to be populated with one data set
     *        for each classifier used by the current extraction model of this engine. 
     *        If null, data sets are kept internally with each classifier definition inside 
     *        the IE engine. */
    public void dumpFIData(Document doc, DataModel model, List<AddableDataSet> fiSets);
    
    /** Dumps each feature induction set in fiSets to disk. 
     * @param fiSets FI sets to dump. If null, FI sets bound to each classifier definition
     * are dumped. */
    public void dumpFIDataCumulative(List<AddableDataSet> fiSets);
    
    /** Collects training samples from all documents in docSet to train classifiers 
     *  used by the current extraction model of this engine. */
    public int train(DocumentSet docSet, DataModel model);
    
    /** Collects training samples from doc to train classifiers used by the current 
     *  extraction model of this engine. */
    public int train(Document doc, DataModel model);
    
    /** Completes the training of classifiers used by the current extraction model 
     *  of this engine using training samples collected during previous calls 
     *  to any of the train() methods. */
    public int trainCumulative();

    /** Trains all classifiers used by the current extraction model 
     *  of this engine using the given sample sets.
     *  @throws IllegalArgumentException if the number of classifiers differs 
     *          from the number of sample sets. */
    public void trainClassifiers(List<SampleSet> sampleSets);
    
    /** Trains a single classifier used by the current extraction model of this engine, 
     *  identified by 0-based classifierIdx, using the given sample set.
     *  @throws IllegalArgumentException if classifierIdx is out of range. */
    public void trainSingleClassifier(int classifierIdx, SampleSet sampleSet, SampleSet optionalTestSet);

    /** Induces features for each classifier used by the model, based on the given dataSets
     *  that were assembled for these classifiers. */
    public void induceFeaturesForClassifiers(List<AddableDataSet> fiSets);
    
    /** Induces features for use by the specified classifier, based on the given dataSet
     *  that was assembled using this classifier. */
    public void induceFeaturesForClassifier(int classifierIdx, AddableDataSet fiSet);
    
    /** Creates an empty data set for feature induction for use with the given classifier. */
    public AddableDataSet createEmptyFISet(String name, int classifierIdx);
    
    /** Creates an empty sample set for training the specified classifier defined by the current IE model. */
    public SampleSet createEmptySampleSet(String name, int classifierIdx);
    
    /** @return true if this engine, with the currently loaded model, supports feature extraction. */
    public boolean supportsFeatureInduction();

    /** @return text label used to name dumps and logs generated by the IE Engine. */
    public String getDumpName();

    /** Sets a text label used to name dumps and logs generated by the IE Engine. */
    public void setDumpName(String dumpName);
}