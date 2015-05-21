// $Id: AttributeDefImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.generic;

import medieq.iet.model.AttributeDef;

public class AttributeDefImpl implements medieq.iet.model.AttributeDef {
	protected String name;
	protected String datatype; // string, text, int, float, boolean ... 

	public AttributeDefImpl(String name, String datatype) {
		this.name=name;
		this.datatype=datatype;
	}
	
	/* AttributeDef impl */
	public String getName() {
		return name;
	}

	public String getDatatype() {
		return datatype;
	}
	
	/* write access methods */
	public void getName(String name) {
		this.name=name;
	}

	public void setDatatype(String datatype) {
		this.datatype=datatype;
	}
    
    public String toString() {
        return name+" "+datatype;
    }

    public int compareTo(AttributeDef o) {
        return name.compareTo(o.getName());
    }
}
