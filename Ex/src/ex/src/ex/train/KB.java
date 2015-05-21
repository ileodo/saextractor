// $Id: KB.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.*;
import java.util.HashMap;
import uep.util.Logger;
import ex.util.Const;

public class KB implements Serializable {
    private static final long serialVersionUID = -832024484698530494L;
    private static final String anonymous="";
    public String name;
    public Vocab vocab;
    public PhraseBook phraseBook;
    public HashMap<String,PhraseBook> namedBooks;
    //public TagBook tagBook;
    //public InstanceBook instanceBook;
    public KB(String n, int vocCapacity, int phrCapacity) {
        name=(n==null)? anonymous: n;
        vocab=new HashVocabImpl(vocCapacity, false, true); // not ignoreCase, is master vocab
        phraseBook=new PhraseBookImpl("main", phrCapacity, PhraseBook.STATIC_PHRASEINFO, PhraseBook.MATCH_LEMMA, vocab); // forgiving matching
        namedBooks=new HashMap<String,PhraseBook>();
    }

    public KB(String n, Vocab v, PhraseBook p) {
        name=n;
        vocab=v;
        phraseBook=p;
        namedBooks=new HashMap<String,PhraseBook>();
    }

    public int save(String file) {
        try {
            ObjectOutputStream oos=new ObjectOutputStream(new FileOutputStream(new File(file)));
            oos.writeObject(this);

            oos=new ObjectOutputStream(new FileOutputStream(new File(file+".vocab")));
            oos.writeObject(this.vocab);
        }catch(IOException ex) {
            Logger.LOGERR("Error saving kb "+name+": "+ex.toString());
            return Const.EX_ERR;
        }
        return Const.EX_OK;
    }

    public static KB load(String file) {
        KB kb=null;
        try {
            ObjectInputStream ois=new ObjectInputStream(new FileInputStream(new File(file)));
            kb=(KB) ois.readObject();
        }catch(IOException ex) {
            Logger.LOGERR("Error loading kb from "+file+": "+ex.toString());
        }catch(ClassNotFoundException ex) {
            Logger.LOGERR("Error loading kb from "+file+": "+ex.toString());
        }
        return kb;
    }

    public void copyFrom(KB src) {
        name=src.name;
        vocab=src.vocab;
        phraseBook=src.phraseBook;
        namedBooks=src.namedBooks;
    }

    public String toString() {
        return "KB "+name+": V"+vocab.hashCode()+", P"+phraseBook.hashCode();
    }
}
