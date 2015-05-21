// $Id: AttributeClassLinkImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.generic;

import medieq.iet.model.AttributeClassLink;
import medieq.iet.model.AttributeDef;
import medieq.iet.model.ClassDef;

public class AttributeClassLinkImpl implements AttributeClassLink {
    protected AttributeDef attDef;
    protected ClassDef clsDef;
    protected int minCard;
    protected int maxCard;
    
    public AttributeClassLinkImpl(AttributeDef attDef, ClassDef clsDef, int minCard, int maxCard) {
        this.attDef=attDef;
        this.clsDef=clsDef;
        this.minCard=minCard;
        this.maxCard=maxCard;
    }
    
    public AttributeDef getAttributeDef() {
        return attDef;
    }

    public ClassDef getClassDef() {
        return clsDef;
    }

    public int getMaxCard() {
        return maxCard;
    }

    public int getMinCard() {
        return minCard;
    }
}
