// $Id: PrecEstimator.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.util.*;
import java.io.*;
import uep.util.*;
import ex.features.*;
import ex.reader.*;

/** Tool to estimate P(attribute|phrase_i) for all phrases from supplied dictionary
    by searching corpus for occurences of each phrase. A user-defined function 
    tells which occurence represents attribute A and which not.
 */
public class PrecEstimator {
    ArrayList<String> urls;
    Logger log;
    FM fm;
    KB kb;
    Options o;

    public PrecEstimator() {
        log=Logger.getLogger("PrecEstimator");
        o=Options.getOptionsInstance();
        fm=FM.getFMInstance();
    }

    public static void main(String[] argv) throws Exception {
        // load options from file
        String cfg="config.cfg";
        Options o=Options.getOptionsInstance();
        try {
            o.load(new FileInputStream(cfg));
        }catch(Exception ex) {
            Logger.LOG(Logger.WRN,"Cannot find "+cfg+": "+ex.getMessage());
        }
        Logger.init("pe.log", -1, -1, null);
        if(argv.length<2) {
            System.err.println("Usage: PrecEstimator phraseBookFile corpusDocumentList");
            return;
        }
        String bookFile=argv[0];
        String docList=argv[1];
        PrecEstimator pe=new PrecEstimator();
        pe.estimate(bookFile, docList);
        pe.fm.deinit();
    }

    public void estimate(String bookFile, String docListFile) {
        Tokenizer tokenizer=createTokenizer();
        try {
            if(bookFile.endsWith(".bin")) {
                log.LG(log.INF,"Loading binary KB from "+bookFile);
                kb=KB.load(bookFile);
                if(kb==null)
                    throw new IOException("Could not restore KB from "+bookFile);
                // tries to add UNK word and phrase (already there) and registers vocab and phrasebook with related features
                fm.registerKB(kb);
            }else {
                // init empty KB
                Vocab voc=new VocabImpl(1000,false);
                PhraseBook book=new PhraseBookImpl("main", 1000, PhraseBook.DYNAMIC_PHRASEINFO, PhraseBook.MATCH_LEMMA, voc);
                kb=new KB("myKB",voc,book);
                // register KB: adds UNK word and phrase, registers vocab and phrasebook with related features
                fm.registerKB(kb);
                kb.phraseBook=PhraseBookImpl.readFrom(kb.phraseBook, bookFile, "iso-8859-1", tokenizer, kb.vocab);
                // cache binary KB
                String binFile=bookFile+".bin";
                log.LG(log.INF,"Saving KB as "+binFile);
                kb.save(binFile);
            }
        }catch(IOException ex) {
            log.LG(log.ERR,"Cannot read phrase book: "+ex);
            return;
        }
        //log.LG(log.INF,"Vocab: "+kb.vocab);

        urls=new ArrayList<String>(16);
        int cnt=0;
        try {
            cnt=populateList(docListFile, "iso-8859-1", urls);
        }catch(IOException ex) {
            log.LG(log.ERR,"Cannot read urls from "+docListFile);
            return;
        }
        DocumentReader dr=null;
        try {
            dr=new DocumentReader(tokenizer);
        }catch(ConfigException ex) {
            log.LG(log.ERR,"Cannot create document reader: "+ex);
            return;
        }
        for(int i=0;i<urls.size();i++) {
            String url=urls.get(i);
            log.LG(log.WRN,"Reading url="+url);
            CacheItem citem=dr.cacheItemFromFile(url);
            if(citem==null) {
                log.LG(log.ERR,"Cannot open "+url);
                return;
            }
            Document d=dr.parseHtml(citem);
            d.setTokenFeatures(kb.vocab);

            // find occurences of phrases from book
            NBestResult res=new NBestResult(1);
            for(int j=0;j<d.tokens.length;j++) {
                int rc=kb.phraseBook.get(d.tokens, j, -1, true, res);
                switch(rc) {
                case PhraseBook.MATCH_EXACT:
                case PhraseBook.MATCH_PREFIX:
                case PhraseBook.MATCH_IGNORECASE:
                case PhraseBook.MATCH_LEMMA:
                    log.LG(log.WRN,"Found "+res.toString());
                    for(int k=0;k<res.length;k++)
                        log.LG(log.WRN,k+". "+Document.toString(d.tokens, j, ((PhraseInfo)res.items[k]).tokens.length, " "));
                    break;
                }
                res.clear();
            }
        }
    }

    public Tokenizer createTokenizer() {
        Tokenizer tok=null;
        String tokCls=o.getProperty("tokenizer");
        if(tokCls!=null) {
            try {
                tok=(Tokenizer) Class.forName(tokCls).newInstance();
            }catch(Exception ex) {
                log.LG(log.ERR,"Tokenizer class "+tokCls+" not instantiated, using GrammarTokenizer: "+ex.toString());
            }
        }
        if(tok==null)
            tok=new GrammarTokenizer(); // new SimpleTokenizer();
        return tok;
    }

    public int populateList(String file, String enc, List<String> urls) throws IOException {
        int cnt=0;
        File f=new File(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f),enc), 512);
        String line;
        int lno=0;
        while((line=br.readLine())!=null) {
            lno++;
            line=line.trim();
            if(line.length()==0)
                continue;
            urls.add(line);
        }
        return cnt;
    }
}
