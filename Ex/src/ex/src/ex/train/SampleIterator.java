// $Id: SampleIterator.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.Iterator;
import java.util.List;

/** Iterates over document's samples. */
public interface SampleIterator extends Iterator<DataSample> {
    /** For the current sample, creates a label containing the given className, probability and author. 
     *  This label can further be used by extraction ontology patterns to create attribute and instance candidates. */
    public void setClass(String className, double prob, SampleClassifier author);

    /** For the current sample, creates at most nbest labels containing the given className, probability and author. 
     *  The input lists must be ordered by the predicted class confidence. Produced labels can further be used 
     *  by extraction ontology patterns to create attribute and instance candidates. */
    public void setClasses(List<SampleClassification> nbestItems, int nbest, SampleClassifier author);
}
