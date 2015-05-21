// $Id: IntFeature.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

public interface IntFeature {
    public String toString(int value);   // e.g. 1->"LC" (or null)
    // public int valueOf(Object obj); // finds feature value for obj (e.g. TokenAnnot, PhraseAnnot, TagAnnot)
}
