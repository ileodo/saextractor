// $Id: DataModelImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.generic;

import java.util.*;

import medieq.iet.model.*;

public class DataModelImpl implements DataModel {
	protected String name;
	protected String url;
    protected List<ClassDef> classDefs;
    protected List<AttributeDef> attDefs;
    private List<ExtractionModel> extractionModels;
    
	public DataModelImpl(String url, String name) {
		this.url=url;
		this.name=name;
        classDefs=new ArrayList<ClassDef>(4);
        attDefs=new ArrayList<AttributeDef>(16);
        extractionModels=new ArrayList<ExtractionModel>(2);
	}
    
	public String getName() { return name; }
	public String getUrl() { return url; }
	public List<ClassDef> getClasses() { return classDefs; }
    public List<AttributeDef> getAttributes() { return attDefs; }
    
    public ClassDef getClass(String clsName) {
        for(int i=0;i<classDefs.size();i++) {
            if(classDefs.get(i).getName().equals(clsName)) {
                return classDefs.get(i);
            }
        }
        return null;
    }
    
    public boolean addClass(ClassDef cls) {
        for(int i=0;i<classDefs.size();i++) {
            if(classDefs.get(i).getName().equals(cls.getName())) {
                return false;
            }
        }
        for(AttributeClassLink acl: cls.getAttributeLinks()) {
            addAttribute(acl.getAttributeDef());
        }
        classDefs.add(cls);
        return true;
    }
    
    public AttributeDef getAttribute(String attName) {
        for(int i=0;i<attDefs.size();i++) {
            if(attDefs.get(i).getName().equals(attName)) {
                return attDefs.get(i);
            }
        }
        return null;
    }
    
    public boolean addAttribute(AttributeDef att) {
        for(int i=0;i<attDefs.size();i++) {
            if(attDefs.get(i).getName().equals(att.getName())) {
                return false;
            }
        }
        attDefs.add(att);
        return true;
    }

    public List<ExtractionModel> getExtractionModels() {
        return extractionModels;
    }

    public int getExtractionModelsForDocument(Document doc, List<ExtractionModel> exModels) {
        // TODO: filter based on doc class
        exModels.addAll(extractionModels);
        return extractionModels.size();
    }
}
