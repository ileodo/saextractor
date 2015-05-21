// $Id: Attribute.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import ex.util.Const;
import ex.model.*;

/** Attribute of a training Instance */
public class Attribute {
    public Instance instance;
    public AttributeDef attDef;
    public PhraseInfo[] phrase; // arrays card elements, e.g. color is <black>, <white>, and <green>
    public Object[] value;      // normalized value (e.g. Integer or Double type)
    public int card;

    public Attribute(AttributeDef ad, Instance ins) {
        attDef=ad;
        instance=ins;
        card=0;
    }

    public String toString() {
        switch(card) {
        case 0:
            return Const.NULL;
        case 1:
            return attDef.name+" = "+phrase[0]+"("+value[0]+")";
        }
        StringBuffer buff=new StringBuffer(2048);
        buff.append(attDef.name);
        buff.append(" = [");
        for(int i=0;i<card;i++) {
            if(i>0)
                buff.append(",");
            buff.append(phrase[i]);
            buff.append("(");
            buff.append(value[0]);
            buff.append(")");
        }
        buff.append("]");
        return buff.toString();
    }

    public int addValue(PhraseInfo pi) {
        if(card>=attDef.maxCard)
            return Model.MAXCARD;
        if(card==0) {
            phrase=new PhraseInfo[1];
            value=new Object[1];
        }else {
            Object[] old=phrase;
            phrase=new PhraseInfo[old.length+1];
            System.arraycopy(old,0,phrase,0,old.length);
            old=value;
            value=new Object[old.length+1];
            System.arraycopy(old,0,value,0,old.length);
        }
        phrase[card]=pi;
        Object val=normalize(pi);
        value[card]=val;
        card++;
        return Model.OK;
    }

    // returns null when conversion impossible, otherwise the type associated with attDef.dataType
    public Object normalize(PhraseInfo pi) {
        return attDef.normalize(pi.tokens,0,pi.tokens.length);
    }
}
