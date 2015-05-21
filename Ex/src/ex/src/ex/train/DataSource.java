// $Id: DataSource.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.Iterator;
import java.util.List;

import ex.features.Feature;
import ex.model.ClassifierDef;
import ex.model.Model;
import ex.reader.Document;


/** Converts a document to some representation suitable for input
 * to machine learning algorithms.
 */
public interface DataSource {

    /** Initializes this DataSource using a given extraction model, for the given classifier.
     *  This prepares a list of features which will be populated for all samples that 
     *  emerge from this DataSource. */
    public void initialize(Model model, ClassifierDef csd);
    /** Returns the name that identifies this data source. 
     *  This can be e.g. combination of the DataSource type and current document name. */
    public String getName();
    /** Prepares the features defined by the extraction model for use by classifier.
     *  Sets options of this data source based on classifier definition,
     *  e.g. filters classes or features to be used. */
    public void setModel(Model model, ClassifierDef csd);
    /** Prepares the document so that it can be used as a set of examples to be classified. */
    public void setDocument(Document doc);
    /** Sets the minimal confidence a classifier must return for a classified example
     * to be taken into account by the extraction process. */
    public void setConfidenceThreshold(double confidence);
    /** Iterates over data samples in the current document. */
    public SampleIterator getDataIterator(byte filter);
    /** Iterates over features defined by the current model. */
    public FeatureIterator getFeatureIterator(byte filter);
    /** Returns the number of features defined by the current model, without filtering. */
    public int getFeatureCount();
    /** Returns a read-only view of the features defined by the current model, without filtering. */
    public List<Feature> getFeatures();
    /** Whether this document representation supports SampleSets containing weighted samples. */
    public boolean supportsWeightedSamples();
    /** Finalize class information that was set for Samples of this DataSource using SampleIterator. 
     *  This propagates the class information to the underlying Document. */
    public void commitClasses();
    
    interface FeatureIterator extends Iterator<Feature> {
        public int getIdx();
    }
}
