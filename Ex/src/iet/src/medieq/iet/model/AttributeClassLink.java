// $Id: AttributeClassLink.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

public interface AttributeClassLink {
	public ClassDef getClassDef();
	public AttributeDef getAttributeDef();
	public int getMinCard();
	public int getMaxCard();
}
