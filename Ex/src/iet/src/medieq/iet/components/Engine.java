// $Id: Engine.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.io.IOException;

public interface Engine extends Configurable, AttributeExtractor {
	// loadModel return codes
	public static final int MODEL_OK=0;
	public static final int MODEL_NOTFOUND=-1;
	public static final int MODEL_SYNTAX=-2;
	
	// cancel types
	public static final int CANCEL_CURR_DOC=1;
	public static final int CANCEL_CURR_DOCSET=2;
	
	public int loadModel(String modelFile) throws IOException;
	public String getModel();
}
