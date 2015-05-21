// $Id: ContainedAttribute.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

public class ContainedAttribute {
    public AttributeDef attDef;
    public int minCard;
    public int maxCard;
    public double prec;
    public double recall;
    public Object obj;

    public ContainedAttribute(AttributeDef ad, int min, int max, double p, double r) {
        attDef=ad;
        minCard=min;
        maxCard=max;
        prec=p;
        recall=r;
    }
}
