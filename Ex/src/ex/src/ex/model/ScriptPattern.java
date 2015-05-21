// $Id: ScriptPattern.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import ex.util.pr.PR_Evidence;

public class ScriptPattern {
    public Axiom axiom;
    public PR_Evidence evidence;
    
    public ScriptPattern(Axiom axiom, PR_Evidence evidence) {
        this.axiom=axiom;
        this.evidence=evidence;
    }
    
    public String toString() {
        return axiom.toString();
    }
}
