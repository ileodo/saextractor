// $Id: DocumentSet.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

import java.util.*;

public interface DocumentSet {
	public String getName();
	public List<Document> getDocuments();
	public int size();
    public String getBaseDir();
    public void setBaseDir(String baseDir);
// Specified for each doc separately:
//    public String getEncoding();
//    public void setEncoding(String enc);
//    public boolean getForceEncoding();
//    public void setForceEncoding(boolean force);
}
