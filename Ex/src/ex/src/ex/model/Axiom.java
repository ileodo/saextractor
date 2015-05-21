// $Id: Axiom.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import java.util.regex.*;
import org.mozilla.javascript.Script;

import uep.util.Logger;

/* axiom about instances or instance attributes, expressed in javascript */
public class Axiom {
    public String name;
    public String src;
    public int lno;
    public byte type;
    public Script contentScript; // compiled axiom source
    public Script condScript; // returns true when axiom is to be applied for an instance: 
                              // cond=none: no condition [default]
                              // cond=all: all variables in src are defined
                              // cond=any: at least one variable in src is defined
                              // cond element inside axiom|pattern: condScript is defined by the user
    public byte condType;
    public String condText;
    
    protected static final Pattern atThisPat=Pattern.compile("\\$(?![a-z_/])", Pattern.CASE_INSENSITIVE);
    protected static final Pattern atPat=Pattern.compile("\\$[a-z0-9_.]*", Pattern.CASE_INSENSITIVE);
    
    protected static final byte TYPE_AXIOM=0;
    protected static final byte TYPE_VALUE_PATTERN=1;
    protected static final byte TYPE_TRANSFORM=2;
    protected static final byte TYPE_REFERS=3;
    
    public static final byte AXIOM_COND_NONE=0;
    public static final byte AXIOM_COND_ALL=1;
    public static final byte AXIOM_COND_ANY=2;
    public static final byte AXIOM_COND_CUSTOM=3;
    
    public Axiom(String name, String src, int lno, String attName, byte type, byte condType, String cond) {
        this.name=name;
        this.lno=lno;
        this.type=type;
        this.condType=condType;
        if(cond!=null && cond.length()==0)
            cond=null;
        this.condText=cond;
        contentScript=null;
        condScript=null;
        if(attName==null)
            return;
        // search for standalone $ and replace it
        Matcher mat=atThisPat.matcher(src);
        this.src=mat.replaceAll("\\$"+attName);
    }

    public void prepare(Model model, ClassDef clsDef) throws AxiomException {
        // see if we need to build cond expr
        StringBuffer condSrc=null;
        String op=null, cmp=null;
        switch(condType) {
        case AXIOM_COND_CUSTOM:
            if(condText==null)
                throw new AxiomException("<cond> child element must be specified for axiom cond=custom");
        case AXIOM_COND_NONE:
            break;
        default:
            condSrc=new StringBuffer(src.length()+32);
            switch(condType) {
            case AXIOM_COND_ANY:
                op="||"; cmp="!=";
                break;
            case AXIOM_COND_ALL:
                op="&&"; cmp="!=";
                break;
            }
        }
        // register this axiom with all attributes it applies to 
        Matcher mat=atPat.matcher(src);
        AttributeDef ad=null;
        switch(type) {
        case TYPE_AXIOM:
        case TYPE_VALUE_PATTERN:
            while(mat.find()) {
                if(mat.group(0).equals("$")) {
                    String err="$ can only be used in axioms defined at attribute scope";
                    // throw new AxiomException(err);
                    Logger.LOG(Logger.WRN,err+"; treating as text");
                    continue;
                }
                if(condSrc!=null) {
                    if(condSrc.length()>0)
                        condSrc.append(op);
                    condSrc.append(mat.group(0));
                    condSrc.append(cmp);
                    condSrc.append("undefined");
                }
                String attName=mat.group(0).substring(1);
                ad=(AttributeDef) clsDef.attributes.get(attName);
                if(ad==null) {
                    String err="No attribute named '"+attName+"' found in class "+clsDef.name+" axiom="+src;
                    // throw new AxiomException(err);
                    Logger.LOG(Logger.WRN,err+"; treating as text");
                    continue;
                }
                ad.addAxiom(this);
            }
            break;
        case TYPE_REFERS:
        case TYPE_TRANSFORM:
            break;
        }
        model.context.enter();
        // compile condScript and script
        if(condSrc!=null && condSrc.length()>0)
            condText=condSrc.toString();
        if(condText!=null && condText.length()>0)
            condScript=model.context.compileString(condText, name+"_cond", lno, null);
        contentScript=model.context.compileString(src, name, lno, null);
        model.context.exit();
    }

    public String toString() {
        return name+": "+src;
    }
}
