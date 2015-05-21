// $Id: AttributeValueImpl.java 1649 2008-09-15 09:31:56Z labsky $
package medieq.iet.generic;

import java.util.*;
import medieq.iet.model.*;

public class AttributeValueImpl implements medieq.iet.model.AttributeValue {
	protected String text; // normalized value of this attribute
	protected double conf;
	protected AttributeDef attDef;
	protected List<Annotation> annots;
	protected Instance inst;
    protected double score;
	
	public AttributeValueImpl(AttributeDef attDef, String text, double conf, Instance inst) {
		this.attDef=attDef;
		this.text=text;
		this.conf=conf;
		this.inst=inst;
		annots=new ArrayList<Annotation>(2);
        score=-1;
	}
	
	public AttributeValueImpl(AttributeDef attDef, String text, double conf, Instance inst, int offset, int len, String author) {
		this.attDef=attDef;
		this.text=text;
		this.conf=conf;
		this.inst=inst;
		Annotation single=new AnnotationImpl(offset, len, text, author);
		annots=new ArrayList<Annotation>(1);
		annots.add(single);
        score=-1;
	}

    public AttributeValueImpl(AttributeDef attDef, String text, double conf, Instance inst, List<Annotation> annots) {
        this.attDef=attDef;
        this.text=text;
        this.conf=conf;
        this.inst=inst;
        this.annots=annots;
        score=-1;
    }
    
	/* AttributeValue implementation */
	public List<Annotation> getAnnotations() {
		return annots;
	}

	public AttributeDef getAttributeDef() {
		return attDef;
	}

	public Instance getInstance() {
		return inst;
	}

	public String toXML() {
		String fullName=attDef.getName();
		String insAtt="";
		if(inst!=null && inst.getClassDef()!=null && inst.getClassDef().getName()!=null) {
			fullName=inst.getClassDef().getName() + "." + attDef.getName();
			insAtt=" ins=\""+inst.getId()+"\"";
		}
        String scoreAtt="";
        if(score!=-1)
            scoreAtt=" p=\""+String.format("%.4f",score)+"\"";
        String authors=getAuthor();
        if(authors!=null) {
            authors=" a=\""+authors+"\"";
        }else {
            authors="";
        }
		return "<lab att=\""+fullName+"\""+insAtt+scoreAtt+authors+">"+getText()+"</lab>";
	}
    
    public String toString() {
        return toXML();
    }

	public double getConfidence() {
		return conf;
	}

	public String getText() {
		return text;
	}
	
	/* write access methods */
	public void setText(String text) {
		this.text=text;
	}
	
	public void setConfidence(double conf) {
		this.conf=conf;
	}
	
	public void setInstance(Instance inst) {
		this.inst=inst;
	}
	
	public void setAttributeDef(AttributeDef attDef) {
		this.attDef=attDef;
	}
    
    public void setScore(double score) {
        this.score=score;
    }
    
    public double getScore() {
        return score;
    }

    /** Concatenates all distinct authors of the underlying annotations. */
    public String getAuthor() {
        Iterator<Annotation> ait=annots.iterator();
        List<String> authors=new LinkedList<String>();
        while(ait.hasNext()) {
            Annotation a=ait.next();
            if(a.getAuthor()!=null && !authors.contains(a.getAuthor())) {
                authors.add(a.getAuthor());
            }
        }
        StringBuffer str=new StringBuffer(32);
        for(String a: authors) {
            if(str.length()>0) {
                str.append(" ");
            }
            str.append(a);
        }
        return str.toString();
    }

    /** Sets the author of all underlying annotations. */
    public void setAuthor(String author) {
        Iterator<Annotation> ait=annots.iterator();
        while(ait.hasNext()) {
            Annotation a=ait.next();
            a.setAuthor(author);
        }
    }

    /** @see getAttributeName() */
    public String getName() {
        return getAttributeName();
    }
    
    /** Short-hand for getAttribute().getName() */
    public String getAttributeName() {
        return attDef.getName();
    }

    /** Changes the AttributeDef of this AttributeValue. 
     *  Creates the AttributeDef if it does not exist in model. */
    public void setAttributeName(String newName, DataModel model) {
        AttributeDef newAtt=model.getAttribute(newName);
        if(newAtt==null) {
            newAtt=new AttributeDefImpl(newName, "string");
            model.addAttribute(newAtt);
        }
        attDef=newAtt;
    }

    public String getType() {
        return "av";
    }
}
