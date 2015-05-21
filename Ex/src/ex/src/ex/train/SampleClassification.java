// $Id: SampleClassification.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

/** Contains information about the classified class and classification confidence. */
public interface SampleClassification extends Comparable<SampleClassification> {
    /** @return name of the class. */
    public String getClassName();
    /** @return classification confidence in range 0..1 inclusive. */
    public double getConfidence();
}
