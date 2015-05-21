// $Id: AxiomException.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import org.mozilla.javascript.EvaluatorException;

/** report errors while parsing or executing javascript axioms */
public class AxiomException extends EvaluatorException {
    private static final long serialVersionUID = 8054870911490069120L;
    public AxiomException(String detail) {
        super(detail);
    }
    public AxiomException(String detail, String sourceName, int lineNumber) {
        super(detail, sourceName, lineNumber);
    }
    public AxiomException(String detail, String sourceName, int lineNumber, String lineSource, int columnNumber) {
        super(detail, sourceName, lineNumber, lineSource, columnNumber);
    }
}
