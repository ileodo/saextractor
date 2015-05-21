// $Id: InstanceImpl.java 1759 2008-11-28 01:08:36Z labsky $
package medieq.iet.generic;

import java.util.*;

import uep.util.Logger;

import medieq.iet.model.*;

public class InstanceImpl implements medieq.iet.model.Instance {
	protected ClassDef clsDef;
	protected String id;
	protected List<AttributeValue> atts;
	protected static int lastId=0;
    private static final List<AttributeValue> emptyAVList=new LinkedList<AttributeValue>();
    protected double score;
    protected String author;
	
	public InstanceImpl(String id, ClassDef clsDef) {
		this.id=id;
		this.clsDef=clsDef;
		atts=new ArrayList<AttributeValue>(8);
        score=-1;
	}

	public InstanceImpl(ClassDef clsDef) {
		this.id=String.valueOf(++lastId);
		this.clsDef=clsDef;
		atts=new ArrayList<AttributeValue>();
        score=-1;
	}

	/* Instance impl */
    
    /* Returns all populated attribute values in this instance. */
	public List<AttributeValue> getAttributes() {
		return atts;
	}

    /* Returns all attribute values of the given type in this instance. */
    public List<AttributeValue> getAttributes(AttributeDef attDef) {
        List<AttributeValue> list=null;
        Iterator<AttributeValue> avit=atts.iterator();
        while(avit.hasNext()) {
            AttributeValue av=avit.next();
            if(av.getAttributeDef()==attDef) {
                if(list==null)
                    list=new LinkedList<AttributeValue>();
                list.add(av);
            }
        }
        return (list!=null)? list: emptyAVList;
    }
    
	public ClassDef getClassDef() {
		return clsDef;
	}
	
	public String getId() {
		return id;
	}

	public String toXML() {
		StringBuffer buff=new StringBuffer(atts.size()*32);
		Iterator<AttributeValue> it=atts.iterator();
		buff.append("<instance id=\""+id+"\" class=\""+clsDef.getName()+"\"");
        if(score!=-1) {
            buff.append(" p=\""+String.format("%.4f",score)+"\"");
        }
        if(author!=null) {
            buff.append(" a=\""+author+"\"");
        }
        buff.append(">\n");
		while(it.hasNext()) {
			AttributeValue av=it.next();
			buff.append("  ");
			buff.append(av.toXML());
			buff.append("\n");
		}
		buff.append("</instance>\n");
		return buff.toString();
	}
	
	public String toString() {
	    return toXML();
	}

	/* write access methods */
	public void resetIds() {
		lastId=0;
	}
	
	public void setId(String id) {
		this.id=id;
	}
	
	public void setClassDef(ClassDef clsDef) {
		this.clsDef=clsDef;
	}
    
    public void setScore(double score) {
        this.score=score;
    }
    
    public double getScore() {
        return score;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author=author;
    }

    public double getConfidence() {
        double sum=0;
        for(AttributeValue av: atts) {
            sum+=av.getScore();
        }
        return sum/(double) atts.size();
    }

    public String getText() {
        StringBuffer s=new StringBuffer(128);
        for(AttributeValue av: atts) {
            if(s.length()>0) {
                s.append(", ");
            }
            s.append(av.getAttributeDef().getName());
            s.append("=");
            s.append(av.getText());
        }
        return s.toString();
    }

    /** @see getClassName() */
    public String getName() {
        return getClassName();
    }
    
    /** Short-hand for getClassDef().getName() */
    public String getClassName() {
        return clsDef.getName();
    }

    /** Changes the AttributeDef of this AttributeValue. 
     *  Creates the AttributeDef if it does not exist in model. */
    public void setClassName(String className, DataModel model) {
        ClassDef newCls=model.getClass(className);
        if(newCls==null) {
            newCls=new ClassDefImpl(className, null);
            model.addClass(newCls);
        }
        clsDef=newCls;
    }

    public List<AttributeValue> getAttributesByName(String attName, DataModel model) {
        AttributeDef attDef=model.getAttribute(attName);
        if(attDef==null) {
            Logger.LOG(Logger.INF,"Attribute "+attName+" does not exist in "+model); 
        }
        return getAttributes(attDef);
    }

    public String getType() {
        return "instance";
    }
}
