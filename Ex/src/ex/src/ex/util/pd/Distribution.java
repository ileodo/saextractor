// $Id: Distribution.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.pd;

public interface Distribution {
    public static final char TYPE_UNKNOWN=0;
    public static final char TYPE_MINMAX=1;
    public static final char TYPE_TABLE=2;
    public static final char TYPE_NORMAL=3;
    public static final char TYPE_MIXTURE=4;

    public static final char RANGE_UNKNOWN=0;
    public static final char RANGE_INT=1;
    public static final char RANGE_FLOAT=2;
    public static final char RANGE_BOOL=3;
    public static final char RANGE_ENUM=4;
    
    public static final double ERROR_DELTA=1e-12;
    
    public char getType();       // dist type above
    public char getRangeType();  // range above
    public int getDimension();   // 1..N-dimensional
    
    public double getMinValue();
    public double getMaxValue();
}
