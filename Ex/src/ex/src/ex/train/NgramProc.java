// $Id: NgramProc.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import ex.features.FM;
import ex.model.Model;

import uep.util.Logger;

/** Ngram processor contains algorithms to perform transformations within a given ngram book. */
public class NgramProc {
    protected static Logger log=Logger.getLogger("ngram");
    Model model;
    
    public NgramProc(Model model) {
        this.model=model;
    }
    
    public PhraseBook uniq(PhraseBook orig) {
        // generate unique map
        HashMap<String,List<AbstractPhrase>> umap=new HashMap<String,List<AbstractPhrase>>();
        Iterator<AbstractPhrase> it=orig.iterator();
        while(it.hasNext()) {
            AbstractPhrase phr=it.next();
            String key=phr.getData().toString();
            List<AbstractPhrase> phrases=umap.get(key);
            if(phrases==null) {
                phrases=new LinkedList<AbstractPhrase>();
                umap.put(key, phrases);
            }
            AbstractPhrase copy=new GenericPhrase(phr);
            phrases.add(copy);
        }
        // create new book
        NgramBook ngrams=new NgramBook("uniq");
        Iterator<Entry<String,List<AbstractPhrase>>> pairIt=umap.entrySet().iterator();
        while(pairIt.hasNext()) {
            Entry<String,List<AbstractPhrase>> pair=pairIt.next();
            String key=pair.getKey();
            System.out.println(key);
            NgramInfo ni=NgramInfo.fromString(key, 0, key.length(), model, true);
            Iterator<AbstractPhrase> pit=pair.getValue().iterator();
            while(pit.hasNext()) {
                GenericPhrase phr=(GenericPhrase) pit.next();
                System.out.print("\t"); System.out.println(phr);
                ngrams.put(phr.tokens, 0, phr.getLength(), ni, true);
            }
        }
        return ngrams;
    }
    
    public static void main(String[] args) throws IOException {
        if(args.length==0) {
            System.err.println("Usage: java NgramProc <samples.ngram>");
            return;
        }
        Logger.init("NgramProc.log", -1, -1, null);
        
        // initialize feature manager 
        FM fm=FM.getFMInstance(); 
        Vocab mainVocab=new HashVocabImpl(200000, true, true); // new VocabImpl(200, true);
        PhraseBook mainBook=new NgramBook("test");
        mainBook.setLookupMode(PhraseBook.MATCH_IGNORECASE);
        KB modelKb=new KB("modelKB", mainVocab, mainBook);
        fm.registerKB(modelKb);
        fm.model=new Model("testModel", modelKb);
        
        // read
        String ngrFile=args[0];
        System.err.println("Reading "+ngrFile);
        PhraseBookReader r=new PhraseBookReaderImpl();
        // uses fm.model to register missing attributes:
        PhraseBook<NgramInfo,NgramInfo> ngrams = (PhraseBook<NgramInfo,NgramInfo>) r.read(ngrFile, PhraseBook.TYPE_NGRAM);
        // write
        String copyFile=ngrFile+".copy";
        System.err.println("Writing "+copyFile);
        PhraseBookWriter w=new PhraseBookWriterImpl();
        w.write(ngrams, copyFile);
        
        // uniq
        System.err.println("Uniqing "+ngrFile);
        NgramProc proc=new NgramProc(fm.model);
        PhraseBook uniq=proc.uniq(ngrams);
        
        // dump
        String fn="_uniq.ngram";
        System.err.println("Writing "+fn);
        w.write(uniq, fn);

        if(true) {
            // generate features
            NgramFeatureGen gen=new NgramFeatureGen();
            gen.setMaxFcnt(50000);
            gen.setMinMi(5);
            gen.setMinNgramOccCnt(2);
            
            System.err.println("Generating full features from "+ngrFile);
            NgramFeatureBook featBook=gen.createFeaturesFull(ngrams, fm.model);
            String fn2="_fgenfull.ngram";
            System.err.println("Writing "+fn2);            
            w.write(featBook, fn2);
            
            PhraseBook<List<FeatCand>,FeatCand> featBookCand=gen.createFeaturesFullCand(ngrams, fm.model);
            String fn2c="_fgenfullCand.ngram";
            System.err.println("Writing "+fn2c);            
            w.write(featBookCand, fn2c);
            
            System.err.println("Generating classed features from "+ngrFile);
            NgramFeatureBook featBookClassed=gen.createFeaturesClassed(ngrams, fm.model);
            String fn3="_fgenclassed.ngram";
            System.err.println("Writing "+fn3);
            w.write(featBookClassed, fn3);
            
            PhraseBook<List<FeatCand>,FeatCand> featBookClassedCand=gen.createFeaturesClassedCand(ngrams, fm.model);
            String fn3c="_fgenclassedCand.ngram";
            System.err.println("Writing "+fn3c);
            w.write(featBookClassedCand, fn3c);
            
            System.err.println("Reading NgramFeatureBook "+fn3);
            NgramFeatureBook featBookClassed2 = (NgramFeatureBook) r.read(fn3, PhraseBook.TYPE_NGRAM_FEATURE);
            String fn3copy="_fgenclassedCopy.ngram";
            System.err.println("Writing "+fn3copy);
            w.write(featBookClassed2, fn3copy);
        }
        
        fm.deinit();
    }
}
