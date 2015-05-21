// $Id: ModelElement.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import java.text.NumberFormat;
import uep.util.Logger;

import ex.ac.Annotable;
import ex.ac.TokenPattern;
import ex.reader.Label;
import ex.util.pr.PR_Class;

public abstract class ModelElement {
    public String name;
    protected String color;
    /* binary class representing this attribute in PR model:
    token sequence either is a value of this attribute/class (positive), 
    or is something else (negative); plus an estimated prior probability 
    of encountering value of this attribute/class */
    public PR_Class prClass;
    private int melId;
    private static int nextMelId=1;

    protected static NumberFormat floatFmt=NumberFormat.getInstance();
    static {
        floatFmt.setMaximumFractionDigits(4);
    }
    
    public ModelElement() {
        melId=nextMelId++;
    }

    public int getElementId() {
        return melId;
    }
    
    public String getName() {
        return name;
    }

    public PR_Class getPR_Class() {
        return prClass;
    }
    
    public abstract String getFullName();
    public abstract boolean addPattern(ScriptPattern sp);
    public abstract boolean addPattern(DefaultPattern dp);
    public abstract Model getModel();
    
    public void genStyle(Annotable an, Label lab) {
        String lst="dotted";
        int t=an.getType();
        String tp=""; // "UNK_TYPE"; for some reason t is not TYPE_AC for ACs...
        switch(an.getType()) {
        case Annotable.TYPE_AC: lst="solid"; break;
        case Annotable.TYPE_IC: lst="solid"; break;
        default: // TokenPattern
            if(t<=TokenPattern.PAT_LAST_TYPE)
                tp=TokenPattern.type2string(t);
            else
                Logger.LOG(Logger.ERR,"Unknown pattern type annotated for attribute '"+name+"'");
        }
        lab.title=getName()+tp+" p="+getFormattedProb(an.getProb());
        lab.style="background:"+getColor()+";border-style:"+lst+";border-color:black;border-width:1px;cursor:pointer";
    }
    
    public String getColor() {
        return color;
    }
    
    public void setColor(String newColor) {
        color=newColor;
        getModel().colorsUsed.put(color,null);
    }
    
    public static String getFormattedProb(double p) {
        return floatFmt.format(p);
    }
    
    public void prepare() throws ModelException {
        /* get next available color */
        while(color==null)
            color=getModel().getNextColor();
    }
}
