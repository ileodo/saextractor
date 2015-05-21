// $Id: SampleClassifier.java 1873 2009-03-28 13:13:45Z labsky $
package ex.train;

import java.io.IOException;

import uep.data.SampleSet;

/** An interface to be implemented by external classifiers in order to 
 * be integrated into ex. */
public interface SampleClassifier {
    /** Creates an empty dataset of samples with feature vector based on DataSource. 
     *  If given, a filter will be set to prune unwanted features from being 
     *  communicated to the classifier. */
    public void initEmptyDataSet(DataSource src, byte featureFilter);
    /** Prepares an empty dataset containing feature definitions from given SampleSet. */
    public void initEmptyDataSet(SampleSet src);
    /** Adds all samples in DataSource to the current dataset. */
    public void addSamples(DataSource src, byte featureFilter, boolean uniq);
    /** Adds all samples in SampleSet to the current dataset. */
    public void addSamples(SampleSet sampleSet, boolean uniq);
    /** Loads samples from dataFile, replacing any previous content of the current data set. */
    public void loadSamples(String dataFile, boolean treatMissingValuesAsZero) throws IOException;
    /** Clears the current dataset, keeps feature vector. */
    public void clearDataSet();
    /** Returns the number of features used by the current dataset */
    public int getFeatureCount();
    /** Returns the number of samples in the current dataset. This does not take into account instance weights. */
    public int getSampleCount();
    /** Returns the sum of weights of all instances. */
    public double getWeightedSampleCount();
    
    /** Creates a new (typically untrained) classifier.
     * @throws ClassNotFoundException */
    public void newClassifier() throws Exception;
    /** Loads (typically trained) classifier from file.
     * @throws IOException, ClassNotFoundException */
    public void loadClassifier(String modelFile) throws IOException, ClassNotFoundException;
    /** Sets classifier options. */
    public void setClassifierOptions(String[] options) throws Exception;
    /** Sets a custom parameter of the classifier. */
    public void setParam(String name, String value);
    /** @return custom parameter value. */
    public String getParam(String name);
    /** Classifies all samples in the current dataset, sets class attribute of all DataSamples accordingly. 
     * @throws Exception */
    public void classifyCurrentDataSet() throws Exception;
    /** Classifies all samples in the given dataset, sets their class attribute accordingly, 
     *  updates class information in the data source. */
    public void classifyDataSet(DataSource src, byte featureFilter, boolean cache, int nbest) throws Exception;
    /** Classifies a single given sample, stores class information so that it can be retrieved
     * from getLastClassDist() (for nbest>1) or getLastClass() (otherwise). */
    public void classify(DataSample x, boolean cache, int nbest) throws Exception;
    /** Returns the last result of classify (nbest>1) */
    public double[] getLastClassDist();
    /** Returns the last result of classify (nbest<=1) */
    public double getLastClassValue();
    /** Optional. Trains a classifier using the current dataset. */
    public void trainClassifier() throws Exception;
    /** Optional. Saves (typically trained) classifier to a file. 
     * @throws IOException */
    public void saveClassifier(String modelFile) throws IOException;
    /** Optional. Saves currently contained samples to a file in native format. */
    // public void dumpSamples(String string); 
}
