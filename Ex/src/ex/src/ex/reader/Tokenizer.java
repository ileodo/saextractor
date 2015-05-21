// $Id: Tokenizer.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

import java.lang.String;
import java.io.Reader;
import java.io.IOException;

/** An abstract Tokenizer splits input to TokenAnnots.
  <p>
  Input source can either be a String or a Reader.
  Call the next() method to get the next token.
 */

public abstract class Tokenizer {
    protected Reader inputReader;
    protected String inputString;

    /** Construct a tokenizer. */
    protected Tokenizer() {}

    /** Set input source. */
    public void setInput(Reader input) throws IOException {
        this.inputReader = input;
        this.inputString = null;
    }

    public void setInput(String input) {
        this.inputString = input;
        this.inputReader = null;
    }

    public abstract TokenAnnot next();
}
