// $Id: PathConstraintFactory.java 1646 2008-09-12 21:58:53Z labsky $
package ex.util.search;

public interface PathConstraintFactory {
    /** Returns the constraint created by extending the previous path described by prevConstraint
     * with currentObject. Returns null when no constraint is imposed and PathConstraint.FORBIDDEN when
     * the extension is not allowed. */
    public PathConstraint createNextConstraint(Object nextObject, PathConstraint prevConstraint);
    
    /** Should return true if finalConstraint is a valid constraint to reach at the end of a path,
     *  false otherwise. */
    public boolean isValidFinal(PathConstraint finalConstraint);
}
