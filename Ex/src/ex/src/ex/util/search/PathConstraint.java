// $Id: PathConstraint.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.search;

/** This interface is to be implemented in order to perform lattice search
 * with custom constraints imposed on the path(s) to be found. 
 * E.g. to allow only up to N objects of a certain type on a path, 
 * PathConstraint will need to track the number of these objects on the 
 * path fragment created so far. Only 1-best is supported for constrained search. */

public interface PathConstraint extends Comparable<PathConstraint> {
    public static final PathConstraint FORBIDDEN=new ForbiddenConstraint();
    
    /** Returns true if and only if this constraint could have been created by a call to 
     * PathConstraintFactory.createNextConstraint(currObject, prevConstraint). */
//    public boolean canBacktrackTo(PathConstraint currConstraint, Object currObject, PathConstraint prevConstraint);
}

/** Singleton */
class ForbiddenConstraint implements PathConstraint {
//    public PathConstraint createNextConstraint(Object nextObject, PathConstraint prevConstraint) {
//        return this;
//    }
//    public boolean canBacktrackTo(PathConstraint currConstraint, Object currObject, PathConstraint prevConstraint) {
//        return false;
//    }
    public int compareTo(PathConstraint o) {
        return (o==PathConstraint.FORBIDDEN)? 0: -1;
    }
}
