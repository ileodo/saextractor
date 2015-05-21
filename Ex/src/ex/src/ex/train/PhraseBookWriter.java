// $Id: PhraseBookWriter.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.BufferedWriter;
import java.io.IOException;

public interface PhraseBookWriter {
    /** Dumps content of book to a file. */
    public void write(PhraseBook book, String fileName) throws IOException;
    /** Dumps content of book to a writer. */
    public void write(PhraseBook book, BufferedWriter writer) throws IOException;
}
