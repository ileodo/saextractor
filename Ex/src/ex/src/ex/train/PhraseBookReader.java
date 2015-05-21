// $Id: PhraseBookReader.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.IOException;

public interface PhraseBookReader {
    public PhraseBook read(String fileName, byte bookType) throws IOException;
}
