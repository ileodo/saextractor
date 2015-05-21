// $Id: PhraseBookReaderImpl.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uep.util.Logger;

import ex.features.FM;
import ex.features.TokenNgramF;
import ex.features.TokenTypeF;
import ex.model.AttributeDef;
import ex.model.Model;
import ex.model.ModelReader;
import ex.reader.TokenAnnot;
import ex.reader.Tokenizer;

public class PhraseBookReaderImpl implements PhraseBookReader {
    
    private static Pattern intPat=Pattern.compile("\\d+"); 
    
    public PhraseBook read(String fileName, byte bookType) throws IOException {
        Vocab voc = FM.getFMInstance().getKB().vocab;
        Model model = FM.getFMInstance().model;
        boolean readingFeatures = false;
        PhraseBook book = null;
        Object data = null;
        switch(bookType) {
        case PhraseBook.TYPE_NGRAM:
            book=new NgramBook<NgramInfo,NgramInfo>("karel", new CountPhraseBookAdapter());
            data=new NgramInfo();
            break;
        case PhraseBook.TYPE_NGRAM_FEATURE:
            book=new NgramFeatureBook("franta");
            data=new ArrayList<TokenNgramF>(16);
            readingFeatures=true;
            break;
        case PhraseBook.TYPE_PHRASE:
            book=new PhraseBookImpl("franta", 1000, PhraseBook.DYNAMIC_PHRASEINFO, 
                    PhraseBook.MATCH_EXACT, voc);
            data=new NgramInfo();
            break;
        default:
            throw new IllegalArgumentException("Unknown book type "+bookType);
        }
        
        Tokenizer tokenizer=ModelReader.createTokenizer();
        MyPhrase myp=new MyPhrase(16, data);
        BufferedReader rdr=new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName)), "utf-8"));
        String line;
        int lno=0;
        System.err.println();
        while((line=rdr.readLine())!=null) {
            lno++;
            int ll=line.length();
            
            // 0. features
            if(readingFeatures) {
                if(ll==0) {
                    readingFeatures=false;
                }else {
                    TokenNgramF nf=TokenNgramF.fromString(line, model, true);
                    ((NgramFeatureBook) book).addFeature(nf);
                }
                continue;
            }
            
            // 1. read spaces 
            int pos=0;
            while(pos<ll && line.charAt(pos)==' ')
                pos++;
            
            // 2. read tokens
            int p=pos;
            int tabi=line.indexOf('\t');
            if(p>=ll || tabi==-1)
                throw new IOException(fileName+":"+lno+": syntax error");
            String tok=line.substring(pos, tabi);
            tokenizer.setInput(tok);
            TokenAnnot ta;
            // Logger.LOGERR("Tokenizing "+tok);
            int tokCnt=0;
            int tokType=0; // this must be set by the current tokenizer
            while((ta=tokenizer.next())!=null) {
                tokType=ta.type;
                TokenInfo ti=voc.get(ta.getToken());
                if(ti==null) {
                    ti=new TokenInfo(ta.getToken(), tokType);
                    ti.computeFeatures(voc, true); // also inserts token to voc
                }
                int ii=pos+tokCnt;
                if(ii>=myp.toks.length)
                    myp.ensureCapacity(ii+8);
                myp.toks[ii] = ti;
                tokCnt++;
            }
            if(tokCnt==0) {
                Logger.LOG(Logger.ERR, "Error reading phrase book token "+tok+": current tokenizer "+tokenizer.getClass().getName()+" splits it into "+tokCnt+" tokens; it will not be matched in documents; type="+TokenTypeF.getSingleton().toString(tokType));
            }else if(tokCnt>1) {
                Logger.LOG(Logger.WRN, tok+": "+tokCnt+" tokens per entry, type="+TokenTypeF.getSingleton().toString(tokType));
            }
            myp.len = pos + tokCnt;
            //Logger.LOGERR("P: len="+pos+"+"+tokCnt+":"+myp);

            // 3. read data
            p = tabi+1;
            try {
                switch(bookType) {
                case PhraseBook.TYPE_NGRAM:
                case PhraseBook.TYPE_PHRASE:
                    ((NgramInfo)myp.data).clear();
                    NgramInfo.fromString((NgramInfo)myp.data, line, p, ll, model, true);
                    break;
                case PhraseBook.TYPE_NGRAM_FEATURE:
                    ((List<TokenNgramF>)myp.data).clear();
                    Matcher m=intPat.matcher(line);
                    int intPos=p;
                    //System.err.print(line+"\n");
                    while(intPos<ll && m.find(intPos)) {
                        String intStr=line.substring(intPos, m.end());
                        //System.err.print("*"+intStr);
                        int fid=Integer.parseInt(intStr);
                        TokenNgramF f=((NgramFeatureBook) book).features.get(fid-1);
                        ((List<TokenNgramF>)myp.data).add(f);
                        intPos=m.end()+1;
                    }
                    //System.err.print("\n");
                    break;
                }
            }catch(IllegalArgumentException e) {
                throw new IOException(fileName+":"+lno+": "+e);
            }
                        
            // add to book
            book.put(myp.toks, 0, myp.len, myp.data, false);
            if(lno%1000==0) {
                System.err.print("\r"+lno);
            }
        }
        System.err.println();
        rdr.close();
        
        return book;
    }

    class MyPhrase implements AbstractPhrase {
        Object data;
        TokenInfo[] toks;
        int len;
        public MyPhrase(int initCap, Object data) {
            this.data=data;
            toks=new TokenInfo[initCap];
        }
        public void ensureCapacity(int cap) {
            if(toks.length<cap) {
                TokenInfo[] old=toks;
                toks=new TokenInfo[cap];
                System.arraycopy(old, 0, toks, 0, old.length);
            }
        }
        public Object getData() {
            return data;
        }
        public int getLength() {
            return len;
        }
        public AbstractToken getToken(int idx) {
            return toks[idx];
        }
        public String toString() {
            String s="";
            for(int i=0;i<len;i++) {
                s+=(toks[i]==null)? "(null)": toks[i].token;
                s+=" ";
            }
            return s;
        }
    }
}
