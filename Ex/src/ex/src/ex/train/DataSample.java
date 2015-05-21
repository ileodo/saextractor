// $Id: DataSample.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

/** DataSample represents a single sample suitable for input to machine learning classifiers. 
 * Depending on the chosen representation, this can represent e.g. a candidate phrase or 
 * a word separator. */
public interface DataSample {
    public String getClassification();
    public int[] getFeatures();
    public String toString(byte filter, byte format);
    public String toString();
    public int getWeight();
    public Object getDebugInfo();
}

/*
Example features for a candidate phrase:
- all patterns etc. from ex. ontology as binary features
- extra binary features: 
  - for set of tags X, if the sample is embedded in tag X
  - for words W:
    - if the sample starts with word W
    - if the sample ends with word W
    - if the sample is preceded by word W
    - if the sample is followed by word W
*/
