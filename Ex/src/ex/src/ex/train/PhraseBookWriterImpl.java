// $Id: PhraseBookWriterImpl.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.*;
import java.util.Iterator;

import ex.features.TokenNgramF;

/** A generic (slow) phrase book writer to write any type of phrase book. */
public class PhraseBookWriterImpl implements PhraseBookWriter {

    public void write(PhraseBook book, String fileName) throws IOException {
        BufferedWriter wrt=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(fileName)), "utf-8"));
        write(book, wrt);
    }
    
    public void write(PhraseBook book, BufferedWriter wrt) throws IOException {
        if(book.getType()==PhraseBook.TYPE_NGRAM_FEATURE) {
            Iterator<TokenNgramF> fit=((NgramFeatureBook) book).features.iterator();
            while(fit.hasNext()) {
                TokenNgramF f=fit.next();
                wrt.write(f.toString());
                wrt.write("\n");
            }
            wrt.write("\n");
        }
        
        AbstractToken[] last=new AbstractToken[book.getMaxLen()];
        int lastLen=0;
        
        Iterator<AbstractPhrase> it=book.iterator();
        while(it.hasNext()) {
            AbstractPhrase p=it.next();
            // do not write same tokens again
            boolean flag=false;
            for(int i=0;i<p.getLength();i++) {
                if(i<lastLen && p.getToken(i).getToken().equals(last[i].getToken())) {
                    wrt.write(' ');
                }else {
                    if(flag)
                        wrt.write(' ');
                    flag=true;
                    wrt.write(p.getToken(i).getToken());
                    last[i]=p.getToken(i);
                }
            }
            lastLen=p.getLength();
            wrt.write("\t");
            wrt.write(p.getData().toString());
            wrt.write("\n");
        }
        wrt.close();
    }
}
