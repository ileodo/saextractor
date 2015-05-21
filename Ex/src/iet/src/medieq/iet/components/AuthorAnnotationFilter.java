// $Id: AuthorAnnotationFilter.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import medieq.iet.model.AnnotableObject;
import medieq.iet.model.Annotation;
import medieq.iet.model.AttributeDef;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.Instance;

class SelCrit {
    boolean neg;
    String author;
    String major;
    String minor;
    public SelCrit(boolean neg, String author, String major, String minor) {
        this.neg=neg;
        this.author=star2null(author);
        this.major=star2null(major);
        this.minor=star2null(minor);
    }
    private String star2null(String s) {
        if(s!=null && s.equals("*"))
            s=null;
        return s;
    }
    boolean matches(AnnotableObject obj) {
        boolean rc=false;
        if(author!=null && !author.equalsIgnoreCase(obj.getAuthor())) {
            ;
        }else if(major!=null) {
            if(obj instanceof AttributeValue) {
                AttributeDef ad=((AttributeValue) obj).getAttributeDef();
                if(minor==null) {
                    rc=major.equalsIgnoreCase(ad.getName());
                }else {
                    Instance inst=((AttributeValue) obj).getInstance();
                    rc=major.equalsIgnoreCase(inst.getClassDef().getName());
                    if(rc)
                        rc=minor.equalsIgnoreCase(ad.getName());
                }
            }else if(obj instanceof Instance) {
                if(major!=null) {
                    Instance inst=(Instance) obj;
                    rc=major.equalsIgnoreCase(inst.getClassDef().getName());
                }else {
                    rc=true;
                }
            }else {
                throw new IllegalArgumentException("Type not expected: "+obj);
            }
        }else {
            rc=true;
        }
        return rc;
    }
}

public class AuthorAnnotationFilter implements AnnotationFilter {
    List<SelCrit> crits;
    protected static Pattern patFil=Pattern.compile("^([+\\-])([\\w\\-*]+):([\\w\\-*]+)(\\.([\\w\\-*]+))?$");
        
    public AuthorAnnotationFilter(String src) {
        crits=new LinkedList<SelCrit>();
        if(src!=null) {
            String[] ss=src.trim().split("\\s+");
            for(String s: ss) {
                Matcher m=patFil.matcher(s);
                if(m.matches()) {
                    boolean neg=m.group(1).equals("-");
                    crits.add(new SelCrit(neg, m.group(2), m.group(3), m.group(5)));
                }else {
                    throw new IllegalArgumentException("Error parsing filter "+src+" (near "+s+", read "+crits.size()+")");
                }
            }
        }
    }
        
    public boolean matches(AnnotableObject obj) {
        boolean matches=true;
        for(SelCrit c: crits) {
            if(c.matches(obj)) {
                matches = !c.neg;
            }
        }
        return matches;
    }
}
