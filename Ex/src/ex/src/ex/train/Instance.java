// $Id: Instance.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.*;
import java.sql.*;
import ex.util.Const;
import uep.util.Logger;
import ex.reader.*;
import ex.model.*;
import ex.features.*;

/** Instance as found in training data */
public class Instance {
    public ClassDef myClass;
    public Attribute[] attributes; // attributes indexed by their AttributeDef.id

    public Instance(ClassDef clsDef) {
        myClass=clsDef;
        attributes=new Attribute[myClass.getAttCount()];
    }

    public int addAttribute(int attId, PhraseInfo pi) {
        if(attId<0 || attId>=attributes.length)
            return Const.EX_ERR;
        Attribute a=attributes[attId];
        if(a==null) {
            a=new Attribute((AttributeDef) myClass.attArray[attId], this);
            attributes[attId]=a;
        }
        int rc=a.addValue(pi);
        switch(rc) {
        case Model.MAXCARD:
            Logger log=Logger.getLogger("Instance");
            log.LG(Logger.WRN,"Cannot add another value '"+pi.toString()+
                    "' of attribute "+a.attDef.name+"(card="+a.card+")");
            return Const.EX_ERR;
        }
        return Const.EX_OK;
    }

    public static Instance fromDb(Model model, ResultSet row, Tokenizer tok, ArrayList<TokenAnnot> tokenBuffer, PhraseBook attBook) {
        Logger log=Logger.getLogger("Instance");
        ClassDef cls=model.classArray[0]; // just take the 1st and only one for now
        Instance ins=new Instance(model.classArray[0]);
        // get values for model attributes: model.classes, model.attributes
        int cnt=cls.attArray.length;
        NBestResult nbr=new NBestResult(1);
        for(int i=0;i<cnt;i++) {
            AttributeDef ad=(AttributeDef) cls.attArray[i];
            if(ad.dbName==null)
                continue;

            String val=null;
            try {
                val=row.getString(ad.dbName);
            }catch(SQLException ex) {
                log.LG(Logger.WRN,"Cannot get value for object attribute '"+ad.name+
                        "' with dbname='"+ad.dbName+"': "+ex.toString());
                return null;
            }
            if(val==null)
                continue;

            // for all alternative representations of the same attribute value
            String[] alts=ad.getAlts(val);
            for(int k=0;k<alts.length;k++) {
                tok.setInput(alts[k]);
                TokenAnnot ta;
                tokenBuffer.clear();
                while((ta=tok.next())!=null) {
                    // find each TokenInfo in vocab, or add it and compute its features
                    int rc=ta.setFeatures(attBook.getVocab());
                    if(rc!=Const.EX_OK)
                        log.LG(Logger.ERR,"Error computing features for token "+ta);
                    tokenBuffer.add(ta);
                }
                TokenInfo[] tis=new TokenInfo[tokenBuffer.size()];
                for(int j=0;j<tis.length;j++)
                    tis[j]=((TokenAnnot) tokenBuffer.get(j)).ti;

                // find exact phrase
                PhraseInfo pi=null;
                int rc=attBook.get(tis, nbr);
                if(rc==PhraseBook.MATCH_EXACT) {
                    pi=(PhraseInfo) nbr.items[0];
                }else {
                    // if not found, add new PhraseInfo
                    pi=new PhraseInfo(tis);
                    pi.initFeatures();
                    rc=pi.computeFeatures(attBook);
                    if(rc!=Const.EX_OK)
                        log.LG(Logger.ERR,"Error computing features for phrase "+pi);
                }
                log.LG(Logger.TRC,"Read attribute value "+pi);

                // increment count of this phrase and its lemmatized phrase (if it exists) for this attribute
                pi.intValues[ad.phraseCntF.id]++;
                PhraseInfo piLemma=attBook.get(pi.intValues[FM.PHRASE_LEMMA]);
                if(piLemma!=null)
                    piLemma.intValues[ad.phraseCntF.id]++;

                // add the original attribute value to instance
                if(alts[k].equals(val)) {
                    rc=ins.addAttribute(ad.id, pi);
                    if(rc!=Const.EX_OK)
                        log.LG(Logger.ERR,"Error adding the original attribute value "+pi+" to training instance");
                }

                // add reference to the attribute this phrase belongs to (for parsing the training document)
                pi.data=ins.attributes[ad.id];
            }
        }
        log.LG(Logger.INF,"Loaded instance:\n"+ins.toString());
        return ins;
    }

    public String toString() {
        StringBuffer buff=new StringBuffer(1024);
        buff.append(myClass.name);
        buff.append(" {\n");
        for(int i=0;i<attributes.length;i++) {
            //if(i>0)
            //buff.append("\n");
            if(attributes[i]!=null) {
                buff.append(attributes[i].toString());
                buff.append("\n");
            }
        }
        buff.append("}\n");
        return buff.toString();
    }
}
