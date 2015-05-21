// $Id: GrammarTokenizer.java 1641 2008-09-12 21:53:08Z labsky $
package ex.reader;

import java.io.Reader;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import ex.reader.tokenizer.*;
import uep.util.Logger;

/** A GrammarTokenizer uses a JavaCC-generated tokenizer.
  <p>
  Edit the jj file to customize.
 */

public class GrammarTokenizer extends Tokenizer {
    GrmTokenizer gt;
    Logger log;

    public GrammarTokenizer() {
	log=Logger.getLogger("GrammarTokenizer");
	gt=new GrmTokenizer(new StringReader(""));
	// fills static TokenTypeF with token types recognized by GrmTokenizer
	gt.copyConstants();
    }
    
    public void setInput(String input) {
	super.setInput(input);
	inputReader=new BufferedReader(new StringReader(inputString));
	inputString=null;
	gt.ReInit(inputReader);
    }

    public void setInput(Reader input) throws IOException {
	super.setInput(input);
	gt.ReInit(inputReader);
    }
    
    public TokenAnnot next() {
	try {
	    return gt.next();
	}catch(IOException ex) {
	    log.LG(Logger.ERR,"Tokenizer cannot read input data: "+ex.getMessage());
	}catch(ParseException ex) {
	    log.LG(Logger.ERR,"Error tokenizing input: "+ex.getMessage());
	}
	return null;
    }
}
