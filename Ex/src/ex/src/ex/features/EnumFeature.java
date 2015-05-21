// $Id: EnumFeature.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

public interface EnumFeature extends IntFeature {
    public int fromString(String value); // e.g. "LC"->1 (or -1)
}
