// $Id: DataModel.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

import java.util.*;

/** A generic data model defining possible attributes and classes.
 *  A data model may link to multiple ExtractionModels which 'implement' 
 *  the data model for specific IE engines. */ 
public interface DataModel {
	public String getName();
	public String getUrl();
	public List<ClassDef> getClasses();
    
    public AttributeDef getAttribute(String attName);
    public boolean addAttribute(AttributeDef att);

    public ClassDef getClass(String clsName);
    public boolean addClass(ClassDef cls);
    
    /** Returns a list of extraction models which implement this 
     *  DataModel for a specific IE engine. */
    public List<ExtractionModel> getExtractionModels();
    
    /** Chooses a set of extraction models applicable to a given Document.
     * The selection of extraction models is based on the document's classification. */
    public int getExtractionModelsForDocument(Document doc, List<ExtractionModel> exModels);
}
