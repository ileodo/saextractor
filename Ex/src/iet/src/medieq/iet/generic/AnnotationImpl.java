// $Id: AnnotationImpl.java 1695 2008-10-19 23:03:57Z labsky $
package medieq.iet.generic;

import java.util.LinkedList;
import java.util.List;

public class AnnotationImpl implements medieq.iet.model.Annotation {
	protected int offset; // offset in characters from doc start
	protected int len; // length in characters
	protected String text; // exact unnormalized value found in doc
	protected String author; // author's id; human or IE Engine
	protected Object data; // user-defined data associated with this annot
	protected List<Object> debugInfo;
    
	public AnnotationImpl(int offset, int len, String text, String author) {
		this.offset=offset;
		this.len=len;
		this.text=text;
		this.author=author;
		if(offset==-1 || (text!=null && len!=text.length())) {
		    throw new IllegalArgumentException("Annotation text='"+text+"' offset="+offset+" len="+len+" real len="+text.length());
		}
	}
	
	/* Annotation impl */
	public String getAuthor() {
		return author;
	}

	public int getLength() {
		return len;
	}

	public int getStartOffset() {
		return offset;
	}

    public String getText() {
        return text;
    }

	/* write access methods */
	public void setAuthor(String author) {
		this.author=author;
	}

	public void setLength(int len) {
		this.len=len;
	}

	public void setStartOffset(int offset) {
		this.offset=offset;
	}
    
    public void setUserData(Object data) {
        this.data=data;
    }
    
    public Object getUserData() {
        return data;
    }

    public void setText(String text) {
        this.text=text;
    }
    
    public String toString() {
        return "an="+text+"["+offset+","+(offset+len-1)+"] a="+author;
    }

    public void addDebugInfo(Object obj) {
        if(debugInfo==null) {
            debugInfo=new LinkedList<Object>();
        }
        debugInfo.add(obj);
    }
    
    public List<Object> getDebugInfo() {
        return debugInfo;
    }

    /** Confidences not supported for Annotations. Only supported for AttributeValues and Instances. */
    public double getConfidence() {
        return -1;
    }

    /** Names not supported for Annotations. Only supported for AttributeValues and Instances. */
    public String getName() {
        return null;
    }

    /** @return "an" */
    public String getType() {
        return "an";
    }
}
