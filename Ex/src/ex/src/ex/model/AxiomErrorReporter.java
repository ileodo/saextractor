// $Id: AxiomErrorReporter.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import uep.util.Logger;

/** report errors while parsing or executing javascript axioms */
public class AxiomErrorReporter implements ErrorReporter {
    protected Logger log;

    public AxiomErrorReporter(Logger lg) {
        log=lg;
    }

    public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
        log.LG(log.ERR,"Axiom syntax error '"+message+"' in "+sourceName+"("+line+","+lineOffset+"): "+lineSource);
    }

    public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset) {
        log.LG(log.ERR,"Axiom runtime error '"+message+"' in "+sourceName+"("+line+","+lineOffset+"): "+lineSource);
        return new AxiomException(message, sourceName, line, lineSource, lineOffset);
    }

    public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
        log.LG(log.WRN,"Axiom warning '"+message+"' in "+sourceName+"("+line+","+lineOffset+"): "+lineSource);
    }
}
