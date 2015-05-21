// $Id: TestConstraintFactory.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.search;

/** Test case for constrained search over lattice. Only allows paths 
 * of states whose names have the same casing. */
class TestConstraintFactory implements PathConstraintFactory {
    /** Returns PathConstraint.FORBIDDEN when the object reached is of different casing
     *  than other objects on the path so far. */
    public PathConstraint createNextConstraint(Object nextObject, PathConstraint prevConstraint) {
        PathConstraint con=prevConstraint; // by default do not change the previous constraint
        if(nextObject!=null) {
            String s=nextObject.toString();
            if(s.length()>0) {
                char c=s.charAt(0);
                TestConstraint tc=null;
                if(Character.isUpperCase(c)) {
                    tc=TestConstraint.UPPER;
                }else if(Character.isLowerCase(c)) {
                    tc=TestConstraint.LOWER;
                }
                if(tc==null) {
                    ; // keep current
                }else if(prevConstraint==null) {
                    con=tc;
                }else if(prevConstraint==tc) {
                    con=tc;
                }else {
                    con=PathConstraint.FORBIDDEN;
                }
            }
        }
        return con;
    }

    /** No special requirements for the whole path; this always returns true. */
    public boolean isValidFinal(PathConstraint finalConstraint) {
        return true;
    }
}

/** Test constraint expressing the casing of the path leading to this constraint. */
class TestConstraint implements PathConstraint {
    public static final int UPPER_CASE=1;
    public static final int LOWER_CASE=2;
    public static final TestConstraint UPPER=new TestConstraint(UPPER_CASE);
    public static final TestConstraint LOWER=new TestConstraint(LOWER_CASE);
    
    public int casing;
    
    public TestConstraint(int casing) {
        this.casing=casing;
    }
    
    /** Instances of this test constraint do not have any special ordering that 
     * would express our preference during the search. Always return 0. */
    public int compareTo(PathConstraint o) {
        return 0;
    }
    
    public String toString() {
        return (casing==UPPER_CASE)? "UC": "LC";
    }
}
