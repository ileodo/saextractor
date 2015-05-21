package ex.reader.annot;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import uep.util.Logger;

import edu.nus.comp.nlp.tool.PlainText;
import ex.reader.*;
import ex.features.*;

public class SentSplitter implements DocumentAnnotator {
    Document doc;
    List<TagAnnot> todo=new LinkedList<TagAnnot>();
    StringBuffer buff=new StringBuffer(512);
    List<TokenAnnot> par=new ArrayList<TokenAnnot>(64);
    // TIntArrayList parMap=new TIntArrayList(64);
    Tokenizer tokenizer;
    Logger log;
    
    public SentSplitter(Tokenizer tokenizer) {
        log=Logger.getLogger("Sent");
        this.tokenizer=tokenizer;
    }
    
    public int annotate(Document doc) {
        // Descend through block elements.
        // Each time some text is found directly in the block element, 
        // disregarding inline elements between the block and the text,
        // sentence-split the text.
        this.doc=doc;
        par.clear();
        todo.add(doc.rootElement);
        int cnt=0;
        int dbg=0;
        while(todo.size()>0) {
            dbg++;
            cnt+=createSentForBlock(todo.remove(0));
        }
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Found "+cnt+" sentences.");
        return cnt;
    }
    
    public int createSentForBlock(TagAnnot block) {
        if(block.childNodes==null)
            return 0;
        int cnt=0;
        for(int i=0;i<block.childNodes.length;i++) {
            Annot a=block.childNodes[i];
            switch(a.annotType) {
            case Annot.ANNOT_TAG:
                TagAnnot tag=(TagAnnot) a;
                int tagType=TagTypeF.tag2type[tag.type];
                switch(tagType) {
                case TagTypeF.BLOCK:
                case TagTypeF.CONTAINER:
                case TagTypeF.HEADING:
                    todo.add(tag);
                    cnt+=endSection();
                    break;
                case TagTypeF.OBJECT:
                    // TODO: re-think if a single BR terminates a sentence as well, same for IMG
                    cnt+=endSection();
                    break;
                case TagTypeF.A:
                case TagTypeF.INLINE:
                case TagTypeF.STYLE:
                case TagTypeF.OTHER: // incl. TagNameF.TEXTNODE
                    cnt+=createSentForBlock(tag);
                    break;
                }
                break;
            case Annot.ANNOT_TOKEN:
                addTok((TokenAnnot)a);
                break;
            case Annot.ANNOT_TOKEN_TAG:
                ; // ignore images; can be inline
                break;
            }
        }
        int blockType=TagTypeF.tag2type[block.type];
        switch(blockType) {
        case TagTypeF.BLOCK:
        case TagTypeF.CONTAINER:
        case TagTypeF.HEADING:
            cnt+=endSection();
        }
        return cnt;
    }
    
    void addTok(TokenAnnot tok) {
        par.add(tok);
    }
    
//    Pattern patBOS=Pattern.compile("<s>");
//    Pattern patEOS=Pattern.compile("</s>");
//    Matcher bosMat=patBOS.matcher(input);
    
    int endSection() {
        if(par.size()==0)
            return 0;
        
        buff.append(par.get(0).token);
        for(int i=1;i<par.size();i++) {
            TokenAnnot tok=par.get(i);
            if(par.get(i-1).endIdx < tok.startIdx) {
                // TODO: add condition to also add space when there is such HTML tag
                // in between which would add a space in a browser (are there such non-block elems?)
                buff.append(' ');
            }
            // parMap.add(buff.length()); // remember for each token where it starts
            buff.append(tok.token);
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Splitting: "+buff);
        PlainText ss = new PlainText(buff);
        String[] sents = ss.splitSentences();
        if(log.IFLG(Logger.TRC)) {
            for(int m=0;m<sents.length;m++) {
                log.LG(Logger.TRC,(m+1)+". "+sents[m]);
            }
        }

        // create sentence labels
        int twc=0;
        StringBuffer wordPart=new StringBuffer(32);
sLoop:  for(int i=0;i<sents.length;i++) {
            // it is safest to tokenize again sentences that have been
            // split since the sentence splitter may have introduced extra 
            // chars in different places; tracking indices thus does not work.
            tokenizer.setInput(sents[i]);
            TokenAnnot ta;
            int wc=0; // word count in the original document's current sentence
            if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"Mapping "+(i+1)+". "+sents[i]+":");
            while((ta=tokenizer.next())!=null) {
                if(twc+wc>=par.size()) {
                    if(log.IFLG(Logger.WRN)) log.LG(Logger.WRN,"SentSplitter produced extra word at EOS: "+ta.token);
                }else if(ta.token.equals(par.get(twc+wc).token)) {
                    if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"E:"+ta.token);
                    // sentence splitter kept the token intact
                    wc++;
                    
                }else if(par.get(twc+wc).token.startsWith(ta.token)) {
                    // sentence splitter split 1 word to many
                    wordPart.append(ta.token);
                    int cnt=1;
                    while((ta=tokenizer.next())!=null) {
                        wordPart.append(ta.token);
                        cnt++;
                        if(par.get(twc+wc).token.equals(wordPart.toString())) {
                            wc++;
                            break;
                        }else if(par.get(twc+wc).token.startsWith(wordPart.toString())) {
                            ;
                        }else {
                            // TODO: still not 100% reliable; may occasionally cut sentences; but we do not care
                            if(log.IFLG(Logger.WRN)) log.LG(Logger.WRN,"SentSplitter split 1 word to multiple: cannot map ss:"+wordPart+"("+(cnt)+") to doc:"+par.get(twc+wc));
                            wc++;
                            break;
                        }
                    }
                    // We have hit the sentence splitter's EOS. However it may happen that the original word was split 
                    // between 2 sentences (e.g. suppose the word was www.host.com and the splitter is far from perfect).
                    if(ta==null && par.get(twc+wc).token.startsWith(wordPart.toString())) {
                        wordPart.setLength(0);
                        if(i+1<sents.length) {
                            // merge this sentence with the next one and re-map the merged sentence:
                            String[] old=sents;
                            sents=new String[old.length-1];
                            if(i>0)
                                System.arraycopy(old, 0, sents, 0, i);
                            sents[i]=old[i].trim()+old[i+1].trim();
                            if(i+2<old.length)
                                System.arraycopy(old, i+2, sents, i+1, old.length-(i+2));
                            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"SentSplitter: split single word between multiple sentences; merged sentence: "+sents[i]);
                            wc=0;
                            i--;
                            
                            continue sLoop;
                        }else {
                            if(log.IFLG(Logger.ERR)) log.LG(Logger.ERR,"SentSplitter: missing words");
                            break;
                        }
                    }
                    if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"S:ss:"+wordPart+"("+(cnt)+")->doc:"+par.get(twc+wc-1));
                    wordPart.setLength(0);
                    
                }else if(ta.token.startsWith(par.get(twc+wc).token)) {
                    // sentence splitter merged multiple words to 1
                    wordPart.append(par.get(twc+wc).token);
                    int ahead=1;
                    while(twc+wc+ahead<par.size()) {
                        wordPart.append(par.get(twc+wc+ahead).token);
                        if(wordPart.toString().equals(par.get(twc+wc+ahead).token)) {
                            wc+=ahead+1;
                            break;
                        }else if(wordPart.toString().startsWith(par.get(twc+wc+ahead).token)) {
                            ahead++;
                        }else {
                            if(log.IFLG(Logger.WRN)) log.LG(Logger.WRN,"SentSplitter merged multiple words to 1; cannot map ss:"+ta.token+" to doc:"+wordPart+"("+(ahead+1)+")");
                            wc+=ahead+1;
                            break;
                        }
                    }
                    if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"S:ss:"+ta.token+"->doc:"+wordPart+"("+(ahead+1)+")");
                    wordPart.setLength(0);
                    
                }else {
                    // TODO: improve the word split rate:
                    if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"SentSplitter modified word: cannot map ss:"+ta.token+" to doc:"+par.get(twc+wc).token);
                    wc++;
                    break;
                }
            }
            if(wc==0) {
                log.LG(Logger.WRN,"0 tokens in sentence: "+sents[i]);
                continue;
            }
            if(twc>=par.size()) {
                log.LG(Logger.WRN,"No tokens left for sentence "+(i+1)+"/"+sents.length+": found tokens="+twc+", total="+par.size()+": "+sents[i]);
                break;
            }
            if(twc+wc-1>=par.size()) {
                log.LG(Logger.WRN,"Extra tokens after sentence split: "+par.size()+"/"+(twc+wc)+": "+sents[i]);
                wc=par.size()-twc;
            }
            TokenAnnot bos=par.get(twc);
            TokenAnnot eos=par.get(twc+wc-1);
            SemAnnot sa=new SemAnnot(SemAnnot.TYPE_CHUNK, bos.idx, eos.idx, null, -1, "S", null);
            doc.addSemAnnot(sa);
            if(log.IFLG(Logger.TRC)) {
                String s1=sents[i];
                String s2=Document.toString(doc.tokens, bos.idx, eos.idx-bos.idx+1, " ");
                log.LG(Logger.TRC,"SentSplitter sentence/mapped doc sentence:\n\t"+s1+"\n\t"+s2);
            }
            twc+=wc;
        }
        if(twc!=par.size()) {
            log.LG(Logger.WRN,"Tokens before/after sentence split: "+par.size()+"/"+twc+"; sentences="+sents.length);
        }
/*
        int ti=0;
        TokenAnnot bos=par.get(0);
        for(int i=0;i<sents.length;i++) {
            String sent=sents[i];
            while(ti<parMap.size() && parMap.get(ti)<sent.length()) {
                ti++;
            }
            TokenAnnot eos=par.get(Math.min(ti, par.size()-1));
            if(log.IFLG(Logger.TRC)) {
                String s1=sents[i];
                String s2=Document.toString(doc.tokens, bos.idx, eos.idx-bos.idx+1, " ");
                log.LG(Logger.TRC,"\n1="+s1+"\n2="+s2);
            }
            SemAnnot sa=new SemAnnot(SemAnnot.TYPE_CHUNK, bos.idx, eos.idx, null, -1, "S");
            doc.addSemAnnot(sa);
            ti++;
            if(ti<parMap.size())
                bos=par.get(ti);
        }
*/
        
        // cleanup
        buff.setLength(0);
        par.clear();
        // parMap.clear();
        
        return sents.length;
    }
}
