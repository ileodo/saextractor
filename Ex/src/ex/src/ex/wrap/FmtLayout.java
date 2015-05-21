// $Id: FmtLayout.java 1641 2008-09-12 21:53:08Z labsky $
package ex.wrap;

import uep.util.Logger;
import ex.reader.*;
import ex.features.*;
import ex.model.AttributeDef;

/** 
    Represents a layout formatting pattern. Used to store regular formatting
    of instances present in document(s).
 */
public class FmtLayout {
    // layout's root tag, with other TagAnnot children, leafs are SemAnnot
    TagAnnot root;
    TagAnnot firstTag;

    public FmtLayout(TagAnnot layoutRootTag, TagAnnot firstTag) {
        root=layoutRootTag;
        this.firstTag=firstTag;
    }

    public boolean contains(AttributeDef attDef) {
        SemAnnot sa=root.getFirstLabel(attDef, true);
        return (sa!=null);
    }

    public String toString() {
        StringBuffer buff=new StringBuffer(128);
        toString(root, buff);
        return buff.toString();
    }

    public void toString(TagAnnot ta, StringBuffer buff) {
        // name
        buff.append(TagNameF.getSingleton().toString(ta.type));
        // allowed idxs in parent
        /*
        String ord=String.valueOf(ta.parentIdx);
        switch(ta.type) {
        case HTMLElements.TR:
            ord=(ta.parentIdx>0)? "1+": "0";
            break;
        }
         */
        // abusing doc idxs as min and max parentIdx
        String ord=String.valueOf(ta.startIdx);
        if(ta.startIdxInner > ta.startIdx) {
            ord=String.valueOf(ta.startIdx)+"-"+((ta.startIdxInner==Integer.MAX_VALUE)? "N":ta.startIdxInner);
        }
        buff.append("["+ord+"]");
        // start
        buff.append("{");

        // children
        for(int i=0;i<ta.childNodes.length;i++) {
            if(i>0)
                buff.append(",");
            Annot a=ta.childNodes[i];
            if(a instanceof TagAnnot) {
                toString((TagAnnot) a, buff);
            }else if(a instanceof SemAnnot) {
                toString((SemAnnot) a, buff);
            }else {
                Logger.LOG(Logger.ERR,"Unknown annot type found: "+a);
                buff.append("?");
                return;
            }
        }

        // end
        buff.append("}");
    }

    public void toString(SemAnnot a, StringBuffer buff) {
        SemAnnot sa=(SemAnnot) a;
        buff.append(((AttributeDef)sa.data).name);
        buff.append("#");
        buff.append(sa.startIdx);        
    }
}
