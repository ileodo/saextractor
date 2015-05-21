// $Id: HtmlSaxParser.java 1700 2008-10-19 23:08:29Z labsky $
package ex.reader;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.*;

//using the NekoHTML parser
//import org.w3c.dom.Document;
//import org.cyberneko.html.parsers.DOMParser;
//import org.cyberneko.html.parsers.SAXParser;
//import javax.xml.parsers.SAXParser;

//HTML element short constants, info containing augmentations:
import org.cyberneko.html.HTMLElements;
import org.cyberneko.html.HTMLEventInfo;

//import org.w3c.dom.Node;
//import org.apache.xerces.parsers.AbstractSAXParser;
import org.xml.sax.*;
import org.apache.xerces.xni.*;
import org.apache.xerces.xni.parser.XMLParseException;

import uep.util.*;
import ex.util.*;
import ex.features.TagNameF;

public class HtmlSaxParser extends org.cyberneko.html.parsers.SAXParser {
    public static final String AUGMENTATIONS = "http://cyberneko.org/html/features/augmentations";
    public static final String BALANCE_TAGS  = "http://cyberneko.org/html/features/balance-tags";
    public static final String REPORT_ERRORS = "http://cyberneko.org/html/features/report-errors";
    public static final String ERROR_REPORTER = "http://cyberneko.org/html/properties/error-reporter";

    public static int SKIP_NONE = 0;
    public static int SKIP_CHARACTERS = 1;
    public static int SKIP_ALL = 2;

    public boolean forceUnixLineEngings=true;
    public boolean addInlineTagPointers=true;
    public int createTextNodes=1;
    public Document doc;
    protected Logger log;
    // html/xml parsers are unable to tell us byte/char offset, only line and column; this enables mapping
    private gnu.trove.TIntArrayList lineStartOffsets;
    // current tag path, children, text
    private Stack<TagAnnot> path;
    private ArrayList<ArrayList<Annot>> pathChildren;
    private int depth;
    private int maxDepth;
    private ArrayList<StringBuffer> pathText;
    // token array for Document
    private ArrayList<TokenAnnot> allTokens;
    int lastTextOffset;
    int lastTextEnd;
    boolean inCDATA;
    // maps indices from xmlprocessed text in pcdata and attributes to raw document.data 
    // enables us to insert labels into raw html, populated by mapProcessedText(String processed)
    int[] proc2raw=null;
    int skip=SKIP_NONE;
    // a tokenizer used to split pcdata and attribute values into TokenAnnots
    protected Tokenizer tokenizer;
    private TagNameF tagNameF=null;
    private ObjectRef<Integer> indexOfParam;
    private TagAnnot lastInline;
    
    private Map<String,Integer> skippedElements;
    private ArrayList<String> skippedElementStack;
    
    class HtmlErrorHandler implements org.cyberneko.html.HTMLErrorReporter {
        StringBuffer msg;
        Locale locale;
        ResourceBundle errorMessages;
        
        HtmlErrorHandler() {
            msg = new StringBuffer(128);
            locale = Locale.getDefault();
            errorMessages = ResourceBundle.getBundle("org/cyberneko/html/res/ErrorMessages", locale);
        }

        public String formatMessage(String key, Object[] args) {
            if(errorMessages==null)
                return formatSimpleMessage(key,args);
            try {
                String value = errorMessages.getString(key);
                String message = MessageFormat.format(value, args);
                return message;
            }
            catch (MissingResourceException e) {
                // ignore and return a simple format
            }
            return formatSimpleMessage(key,args);
        }

        public String formatSimpleMessage(String key, Object[] args) {
            msg.append(key);
            msg.append(':');
            for(Object o: args) {
                msg.append(' ');
                msg.append((o!=null)? o.toString(): "null");
            }
            return msg.toString();
        }

        public void reportError(String key, Object[] args) throws XMLParseException {
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"html error: "+formatMessage(key,args));
        }

        public void reportWarning(String key, Object[] args) throws XMLParseException {
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"html warning: "+formatMessage(key,args));
        }
    }
    
    public HtmlSaxParser(Tokenizer tok) {
        log=Logger.getLogger("HtmlSaxParser");
        String features[]={AUGMENTATIONS, BALANCE_TAGS, REPORT_ERRORS};
        Exception e=null;
        lineStartOffsets=new gnu.trove.TIntArrayList(200);
        path=new Stack<TagAnnot>();
        pathChildren=new ArrayList<ArrayList<Annot>>(20);
        pathText=new ArrayList<StringBuffer>(20);
        
        Options o=Options.getOptionsInstance();
        createTextNodes=o.getIntDefault("html_create_text_nodes", createTextNodes);
        lastInline=null;
        
        skippedElements=new HashMap<String,Integer>(8);
        String[] tags=o.getProperty("html_skip_tags", "script style input option label textarea").trim().toLowerCase().split("[ \t,;]+");
        for(String tag: tags) {
            skippedElements.put(tag, SKIP_CHARACTERS);
        }
        skippedElementStack=new ArrayList<String>(8);
        
        for(int i=0;i<features.length;i++) {
            try {
                setFeature(features[i], true);
            }catch(org.xml.sax.SAXNotRecognizedException ex) {
                e=ex;
            }catch(org.xml.sax.SAXNotSupportedException ex) {
                e=ex;
            }
            if(e!=null) {
                log.LG(Logger.WRN,"Error setting HTML parser feature "+features[i]+": "+e.getMessage());
                e=null;
            }
        }
        try {
            setProperty("http://cyberneko.org/html/properties/names/elems", "lower");
            setProperty("http://cyberneko.org/html/properties/names/attrs", "lower");
            //setProperty("http://cyberneko.org/html/properties/default-encoding", "windows-1250");
            setProperty("http://cyberneko.org/html/properties/default-encoding", "utf-8");
            setProperty(ERROR_REPORTER, new HtmlErrorHandler());
            // this.setErrorHandler(new HtmlErrorHandler()); <-- this has no effect
            
            // respect encoding in <meta> - however Neko seems to ignore this feature
            setFeature("http://cyberneko.org/html/features/scanner/ignore-specified-charset",false);
            // report CDATA as comments not as character data - we ignore both 
            // we tried getting CDATA as character data but Neko appears not to call startCDATA() and endCDATA()
            // which makes it less easy to tell CDATA content from normal content, which we need for index mapping
            // + Neko seems to sometime return wrong beginCol (+1) after it encounters <![CDATA[ ]]>
            setFeature("http://cyberneko.org/html/features/scanner/cdata-sections",false);
            setFeature("http://apache.org/xml/features/scanner/notify-char-refs",false); // include &#20; in character data
            // include HTML entities &nobr; &copy; and XML entities in character data
            setFeature("http://apache.org/xml/features/scanner/notify-builtin-refs",false);
            // include 5 XML entitities &amp; etc. in character data
            setFeature("http://cyberneko.org/html/features/scanner/notify-builtin-refs",false);
            setFeature("http://cyberneko.org/html/features/scanner/fix-mswindows-refs",true); // convert (tm) etc symbols to unicode
            // effective for comments and cdata in script context 
            // the cdata-sections feature is not effective for cdata in scripts, instead this data is always reported as characters
            setFeature("http://cyberneko.org/html/features/scanner/script/strip-comment-delims",false);
            setFeature("http://cyberneko.org/html/features/scanner/script/strip-cdata-delims",false);
        }catch(org.xml.sax.SAXNotRecognizedException ex) {
            e=ex;
        }catch(org.xml.sax.SAXNotSupportedException ex) {
            e=ex;
        }
        if(e!=null) {
            log.LG(Logger.ERR,"Error setting HTML parser property/feature: "+e.getMessage());
            e=null;
        }
        tokenizer=tok;
        tagNameF=TagNameF.getSingleton();
        indexOfParam=new ObjectRef<Integer>(null);
    }
    
    /** Returns the tokenizer used to segment document text into tokens. */
    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    private static Pattern charsetPat=Pattern.compile("charset\\s*=\\s*[\"']?\\s*([a-zA-Z0-9_\\-]+)");
    public void parseDocument(ex.reader.Document d) throws SAXException, IOException {
        doc=d;
        InputSource src=null;
        CacheItem ci=doc.cacheItem;
        skippedElementStack.clear();
        int maxTextLength;
        if(ci.data!=null) {
            if(forceUnixLineEngings) {
                // get rid of all \r\n so that annotation byte offsets are produced
                // based on newlines treated as 1 character - important for certain other tools
                ci.data=ci.data.replaceAll("\r", "");
            }
            maxTextLength=ci.data.length();
            BufferedReader reader = new BufferedReader(new StringReader(ci.data));
            src=new InputSource(reader);
        }else if(ci.rawData!=null) {
            if(forceUnixLineEngings) {
                // get rid of all \r\n so that annotation byte offsets are produced
                // based on newlines treated as 1 character - important for certain other tools
                int j=0;
                byte c;
                for(int i=0;i<ci.rawData.length;i++) {
                    c=ci.rawData[i];
                    switch(c) {
                    case '\r':
                        break;
                    default:
                        ci.rawData[j++]=c;
                        break;
                    }
                }
                if(j!=ci.rawData.length) {
                    byte[] rawData2=new byte[j];
                    System.arraycopy(ci.rawData, 0, rawData2, 0, j);
                    ci.rawData=rawData2;
                }
            }
            // try to determine encoding ourselves - find in HTTP Content-Type or in HTML meta tag
            String enc=CacheItem.detectEncoding(ci.contentType, ci.rawData);
            if(enc!=null) {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Using encoding '"+enc+"' found in Content-Type/meta tag of '"+ci.absUrl+"'");
                try {
                    ci.data=new String(ci.rawData, 0, ci.rawData.length-1, enc);
                    ci.encoding=enc;
                }catch(UnsupportedEncodingException ex) {
                    log.LG(Logger.ERR,"Error decoding data in '"+ci.absUrl+"' from '"+enc+"': "+ex.getMessage());
                }
            }
            if(ci.data!=null) {
                maxTextLength=ci.data.length();
                BufferedReader reader = new BufferedReader(new StringReader(ci.data));
                src=new InputSource(reader);
            }else {
                // give bytes to xmlparser, hope it will determine correct encoding which we will get in startDocument()
                // (however, Neko always gives its default encoding set by property)
                maxTextLength=ci.rawData.length;
                BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(ci.rawData));
                src=new InputSource(stream);
            }
        }else {
            throw new IOException("No data found in document to be parsed");
        }

        // FIXME: remove this when we no longer need to process data fetched before 3/2006
        if(true && ci.data!=null) {
            // comment any javascript redirects from the page, e.g. if(self==top) location.href="/";
            // this is now done by Fetcher but kept here so that all previously downloaded documents display correctly with JS enabled
            ci.data=ci.data.replaceAll("(location.href\\s*=\\s*\"[^\"]+\")","/*$1*/");
        }

        path.clear();
        pathText.clear();
        pathChildren.clear();
        depth=0;
        maxDepth=0;
        allTokens=new ArrayList<TokenAnnot>(512);
        lastTextOffset=-1;
        lastTextEnd=-1;
        proc2raw=new int[maxTextLength+1];
        inCDATA=false;
        fixColAfterCDATA=false;
        // start SAX parsing
        parse(src);

        // fill Document
        int tc=allTokens.size();
        doc.tokens=new TokenAnnot[tc];
        for(int i=0;i<tc;i++) {
            doc.tokens[i]=(TokenAnnot) allTokens.get(i);
        }
        doc.maxDomDepth=maxDepth;

        // check if ok
        doc.validate();
    }

    int getCharOffset(int lineNumber, int colNumber) {
        return lineStartOffsets.getQuick(lineNumber-1)+colNumber-1;
    }

    int readLineOffsets(String s) {
        Pattern pat=Pattern.compile("(\\r\\n|\\n|\\r)");
        Matcher mat=pat.matcher(s);
        lineStartOffsets.reset();
        lineStartOffsets.add(0); // idx of the 1st line
        int lno=1;
        while(mat.find()) {
            lineStartOffsets.add(mat.end());
            lno++;
        }
        return lno;
    }

    public void startDocument(org.apache.xerces.xni.XMLLocator locator, 
            String encoding, 
            org.apache.xerces.xni.NamespaceContext namespaceContext, 
            org.apache.xerces.xni.Augmentations augs) {
        CacheItem ci=doc.cacheItem;
        if(encoding==null) {
            log.LG(Logger.ERR,"Xerces didn't specify encoding it uses");
        }else {
            if(ci.encoding==null || ci.encoding.length()==0) {
                // set CacheItem's encoding and decode bytes to string
                ci.encoding=encoding;
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"CacheItem absUrl='"+ci.absUrl+"': setting guessed encoding='"+encoding+"'");
                if(ci.data!=null) {
                    log.LG(Logger.WRN,"CacheItem absUrl='"+ci.absUrl+"': had no encoding but has decoded data - will keep the data.");
                }
            }else if(!encoding.equalsIgnoreCase(ci.encoding)) {
                log.LG(Logger.WRN,"CacheItem absUrl='"+ci.absUrl+"': has encoding '"+ci.encoding+"' (Xerces default '"+encoding+"')");
                if(ci.rawData!=null) {
                    ci.data=null; // re-create if we have the raw data
                }
            }else {
                if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"CacheItem absUrl='"+ci.absUrl+"': encodings agree on '"+ci.encoding+"'");
            }
        }
        if(ci.data==null) {
            try {
                ci.data=new String(ci.rawData, 0, ci.rawData.length, ci.encoding);
            }catch(UnsupportedEncodingException ex) {
                log.LG(Logger.ERR,"Error decoding data in '"+ci.absUrl+"' from '"+ci.encoding+"': "+ex.getMessage());
                ci.data=null;
            }
        }
        int lno=-1;
        if(ci.data!=null) {
            lno=readLineOffsets(ci.data);
            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Html-parsing CacheItem absUrl='"+ci.absUrl+"': "+lno+" lines.");
        }else {
            log.LG(Logger.ERR,"CacheItem absUrl='"+ci.absUrl+"': has no decoded data. Token offsets will not work.");
        }
        doc.data=doc.cacheItem.data;
        doc.metaInfo=new DocumentMetaInfo(null,-1,-1,-1,null,-1);
    }

    /* callbacks for building Document */
    private int startChar;
    private int endChar;
    public void startElement(org.apache.xerces.xni.QName element, 
            org.apache.xerces.xni.XMLAttributes attrs, 
            org.apache.xerces.xni.Augmentations augs) throws XNIException {
        String elem=element.localpart;
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"<"+elem+">");
        HTMLEventInfo info=(HTMLEventInfo)augs.getItem(AUGMENTATIONS);
        setCharOffsets(info, elem, 1); // sets startChar, endChar
        TagAnnot parent=(depth>0)? (TagAnnot) path.peek(): null;
        int tagId=tagNameF.valueOf(elem);
        TagAnnot ta=new TagAnnot(tagId, startChar, endChar, -1, -1, parent, -1); // int tag, Annot par, int parIdx
        if(doc.rootElement==null)
            doc.rootElement=ta;
        // remember head start position
        if(!info.isSynthesized()) {
            switch(tagId) {
            case HTMLElements.HTML:
                doc.metaInfo.afterHtmlStartIdx=endChar+1;
                break;
            case HTMLElements.HEAD:
                doc.metaInfo.afterHeadStartIdx=endChar+1;
                break;
            case HTMLElements.BODY:
                doc.metaInfo.onloadStartIdx=endChar-1;
                break;
            }
        }
        // attributes
        int attCnt=attrs.getLength();
        ta.attributes=new HtmlAttribute[attCnt];
        for(int i=0;i<attCnt;i++) {
            String attName=attrs.getLocalName(i);
            String attValue=attrs.getValue(i);
            ta.attributes[i]=new HtmlAttribute(-1,attValue,null);
            // meta tag's content attribute?
            if(tagId==HTMLElements.META && (attName.equalsIgnoreCase("content") || attName.equalsIgnoreCase("charset"))) {
                setMetaEncoding(attName,attValue);
            }
            // body's onload attribute?
            if(tagId==HTMLElements.BODY && attName.equalsIgnoreCase("onload")) {
                setOnload(attName,attValue);
            }
        }
        if(depth>0) {
            // get tokens collected just before this element started
            StringBuffer buff=(StringBuffer) pathText.get(depth-1);
            if(buff.length()!=0) {
                insertTokens(buff.toString());
                buff.setLength(0);
            }
            // add this annot as parent's child
            ArrayList<Annot> parentsChildren=(ArrayList<Annot>) pathChildren.get(depth-1);
            ta.parentIdx=parentsChildren.size();
            parentsChildren.add(ta);
        }
        // prepare buffer to store this element's text
        if(pathText.size()==depth)
            pathText.add(new StringBuffer(64));
        // ensure place to store children of this element
        if(pathChildren.size()==depth)
            pathChildren.add(new ArrayList<Annot>(10));
        // add this annot to path
        path.push(ta);
        depth++;
        if(depth>maxDepth)
            maxDepth=depth;
        // skip content if this is e.g. a script element
        String lcElem=elem.toLowerCase();
        if(skippedElements.containsKey(lcElem)) {
            skippedElementStack.add(lcElem);
            skip=SKIP_CHARACTERS;
        }
        super.startElement(element, attrs, augs); // perform default processing	
    }

    public void endElement(org.apache.xerces.xni.QName element, 
            org.apache.xerces.xni.Augmentations augs) throws XNIException {
        String elem=element.localpart;
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"</"+elem+">");
        HTMLEventInfo info=(HTMLEventInfo) augs.getItem(AUGMENTATIONS);
        // get tokens collected just before this element ended
        StringBuffer buff=(StringBuffer) pathText.get(depth-1);
        if(buff.length()!=0) {
            insertTokens(buff.toString());
            buff.setLength(0);
        }
        setCharOffsets(info, elem, 0); // sets startChar, endChar
        TagAnnot tag=(TagAnnot) path.pop();
        depth--;
        tag.endIdxInner=startChar;
        tag.endIdx=endChar;
        // add end-of-tag to the last seen token, but only if the token is still under this element
        if(allTokens.size()>0) {
            TokenAnnot tok=allTokens.get(allTokens.size()-1);
            if(tok.hasAncestor(tag)) {
                tok.addTagEnd(tag);
            }
        }
        // If this tag is inline, then add it to the last token seen as endToken 
        // then add it to the very next token as startToken.
        // TODO: redesign the document model so that we do not need this
        if(addInlineTagPointers && tag.isInline()) {
            if(allTokens.size()>0) {
                TokenAnnot tok=allTokens.get(allTokens.size()-1);
                tok.addTagEnd(tag);
                lastInline=tag;
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Inline "+tag+" added as endToken to "+tok);
            }
        }
        
        // get child nodes collected for this element
        ArrayList<Annot> children=pathChildren.get(depth);
        int cnt=children.size();
        if(cnt>0) {
            tag.childNodes=new Annot[cnt];
            for(int i=0;i<cnt;i++)
                tag.childNodes[i]=(Annot) children.get(i);
            children.clear();
        }
        String lcElem=elem.toLowerCase();
        if(skip==SKIP_CHARACTERS && skippedElementStack.get(skippedElementStack.size()-1).equals(lcElem)) {
            skippedElementStack.remove(skippedElementStack.size()-1);
            if(skippedElementStack.size()==0) {
                skip=SKIP_NONE;
            }
        }
        super.endElement(element, augs);
    }

    public void characters(org.apache.xerces.xni.XMLString text, 
            org.apache.xerces.xni.Augmentations augs) throws XNIException {
        if(skip!=SKIP_NONE) {
            super.characters(text, augs);
            return;
        }
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"characters: "+text);
        // get text collected for this element
        StringBuffer buff=(StringBuffer) pathText.get(depth-1);
        // othwerise, only a single block of text is extended and we already have its offset
        String s=null;
        int beginRow=-1, beginCol=-1;
        int endRow=-1, endCol=-1;

        // save old characters if these do not immediately follow
        if(augs!=null) {
            HTMLEventInfo info=(HTMLEventInfo) augs.getItem(AUGMENTATIONS);
            beginRow = info.getBeginLineNumber();
            beginCol = info.getBeginColumnNumber();
            endRow = info.getEndLineNumber();
            endCol = info.getEndColumnNumber();
            startChar=getCharOffset(beginRow, beginCol);
            endChar=getCharOffset(endRow, endCol);
        }else {
            startChar=-1;
            endChar=-1;
        }
        // next text is separate tokens if anything was in between 
        // (FIXME: inline tags don't split tokens)
        if(startChar>lastTextEnd+1) {
            if(buff.length()!=0) {
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"start="+startChar+"lastEnd="+lastTextEnd);
                insertTokens(buff.toString());
                buff.setLength(0);
            }
            lastTextOffset=-1;
            if(augs==null)
                return;
        }
        if(lastTextOffset==-1) {
            // seems as a bug in Neko - beginCol of the text immediately following cdata, e.g.
            // <![CDATA[data]]> text
            // is _sometimes_ +1 more than it should be
            if(fixColAfterCDATA) {
                fixColAfterCDATA=false;
                int cdataEndIdx=doc.data.lastIndexOf("]]>",startChar);
                if(cdataEndIdx+3!=startChar) { // should equal
                    log.LG(Logger.WRN,"Fixing wrong begin column in text after CDATA: wrong="+beginCol+
                            ", correction="+(-startChar+(cdataEndIdx+3)));
                    beginCol-= startChar-(cdataEndIdx+3);
                    startChar=getCharOffset(beginRow, beginCol);
                }
            }
            // startChar is sometimes shifted due to Neko's inexact info given in augmentations
            // e.g. when doctype is in one line together with some text, the column information
            // is shifted by 2. We attempt to recover in mapProcessedText().
            lastTextOffset=startChar;
            if(log.IFLG(Logger.MML)) 
                s="["+startChar+"] ["+beginRow+"."+beginCol+"]";
        }
        String txt=text.toString();
        // lastTextEnd=startChar+txt.length(); // wrong
        lastTextEnd=endChar;

        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"characters '"+txt+"' "+s);
        buff.append(txt);
        super.characters(text, augs);
    }

    public void startGeneralEntity(String name, 
            org.apache.xerces.xni.XMLResourceIdentifier identifier,
            String encoding,
            org.apache.xerces.xni.Augmentations augs) throws XNIException {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"COOL GENERAL ENTITY: name='"+name+"', identifier='"+identifier.toString()+"',augs="+augs.toString());
        super.startGeneralEntity(name, identifier, encoding, augs);
    }

    public void ignorableWhitespace(org.apache.xerces.xni.XMLString text, 
            org.apache.xerces.xni.Augmentations augs) throws XNIException {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"COOL IGNORABLE WHITESPACE: '"+text.toString()+"'");
        super.ignorableWhitespace(text, augs);
    }

    public void startCDATA(org.apache.xerces.xni.Augmentations augs) throws XNIException {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"CDATA start");
        inCDATA=true;
        super.startCDATA(augs);
    }

    public void endCDATA(org.apache.xerces.xni.Augmentations augs) throws XNIException {
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"CDATA end");
        inCDATA=false;
        super.endCDATA(augs);
    }

    boolean fixColAfterCDATA;
    public void comment(org.apache.xerces.xni.XMLString text, 
            org.apache.xerces.xni.Augmentations augs) throws XNIException {
        HTMLEventInfo info=(HTMLEventInfo) augs.getItem(AUGMENTATIONS);
        int beginRow = info.getBeginLineNumber();
        int beginCol = info.getBeginColumnNumber();
        String s=text.toString();
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"COMMENT (ignored) '"+s+"' ["+beginRow+"."+beginCol+"]");
        // get tokens collected just before this comment/cdata
        if(depth>0) {
            StringBuffer buff=(StringBuffer) pathText.get(depth-1);
            if(buff.length()!=0) {
                insertTokens(buff.toString());
                buff.setLength(0);
            }
        }
        if(s.startsWith("[CDATA[")) {
            fixColAfterCDATA=true;
        }
        super.comment(text,augs);
    }

    protected static Pattern nonWhitePat=Pattern.compile("[^\\s]+");
    public void insertTokens(String s) {
        Matcher mat=nonWhitePat.matcher(s);
        if(!mat.find())
            return;
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Tokenizing document: '"+s+"'");
        mat.reset();
        // we substitute all \t with space, since JavaCC seems to treat \t as multiple chars (!)
        // we also convert all line breaks to spaces since since GrmTokenizer can only give us line+column information,
        // but we need character offsets
        s=s.replaceAll("[\t\r\n]"," ");
        // map xml-processed line of text to positions in raw document 
        // current raw position given by lastTextOffset
        int rc=mapProcessedText(s);
        if(rc==Const.EX_ERR) {
            log.LG(Logger.ERR,"Error mapping xmlprocessed text '"+s+"' to raw document data");
            return;
        }
        ArrayList<Annot> children=pathChildren.get(depth-1);
        // determine the containing node
        TagAnnot tokenParent=(TagAnnot)path.peek();
        int prevChildrenSize=0;
        if(createTextNodes>0) {
            // continue with an immediately preceding text node or create a new one
            Annot lastSibling=null;
            if(children.size()>0) {
                lastSibling=children.get(children.size()-1);
                if(lastSibling.annotType==Annot.ANNOT_TAG && 
                   ((TagAnnot)lastSibling).type==TagNameF.TEXTNODE) {
                    // we will add more tokens to tokenParent.childNodes
                    prevChildrenSize=0;
                    if(((TagAnnot)lastSibling).childNodes!=null)
                        prevChildrenSize=((TagAnnot)lastSibling).childNodes.length;
                    children=new ArrayList<Annot>(prevChildrenSize+4);
                }else {
                    lastSibling=null;
                }
            }
            if(lastSibling==null) {
                lastSibling=new TagAnnot(TagNameF.TEXTNODE, -1, -1, -1, -1, tokenParent, children.size());
                children.add(lastSibling); // lastSibling will be added to tokenParent as child
                children=new ArrayList<Annot>(8); // to store new tokens
            }
            tokenParent=(TagAnnot) lastSibling;
        }
        tokenizer.setInput(s);
        TokenAnnot token;
        int tc=0;
        while((token=tokenizer.next())!=null) {
            tc++;
            // update token position with respect to the full document
            token.startIdx=proc2raw[token.startIdx];
            token.endIdx=proc2raw[token.endIdx];
            // set parent tag
            token.parent=tokenParent;
            // set index within the parent tag
            token.parentIdx=prevChildrenSize+children.size();
            children.add(token);
            // set index within the full document
            token.idx=allTokens.size();
            // add token to document's collection
            allTokens.add(token);
        }
        lastTextOffset=-1;
        
        if(tc>0) {
            TokenAnnot first=allTokens.get(allTokens.size()-tc);
            TokenAnnot last=allTokens.get(allTokens.size()-1);
            // update containing textnode's position 
            // (or we could keep this -1 as for other artificial tags)
            if(createTextNodes>0) {
                if(tokenParent.startIdx==-1) {
                    tokenParent.startIdx=first.startIdx;
                    tokenParent.startIdxInner=first.startIdx;
                }
                tokenParent.endIdx=last.endIdx;
                tokenParent.endIdxInner=last.endIdx;
            }
            // set first and last token for each ancestor tag, 
            // set all tags starting just before the first token and all tags ending after the last token
            // (to enable quick DOM-token operations)
            // climb up and set needed stuff:
            TagAnnot anc=tokenParent;
            while(anc!=null) {
                // set the ancestor's first token if not set already to some previous token 
                if(anc.firstToken==null) {
                    anc.firstToken=first;
                    first.addTagStart(anc);
                }
                // overwrite any previous tokens that were not really the last ones
                anc.lastToken=last;
                // continue to the root
                anc=(TagAnnot) anc.parent;
            }
            // special case: add inline tag
            if(addInlineTagPointers && lastInline!=null) {
                first.addTagStart(lastInline);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC, "Inline "+lastInline+" added as startToken to "+first);
                lastInline=null;
            }
            
            if(createTextNodes>0) {
                // tag end only added set for the parent TextNode; for further ancestors this is called
                // from the end tag callback
                last.addTagEnd(tokenParent);
                
                // set up text node's children
                if(tokenParent.childNodes==null) {
                    tokenParent.childNodes=new Annot[children.size()];
                }else {
                    Annot[] tmp=tokenParent.childNodes;
                    ((TokenAnnot)tmp[tmp.length-1]).tagEnds=null; // clear end of TextNode which was set too early 
                    tokenParent.childNodes=new Annot[tmp.length+children.size()];
                    System.arraycopy(tmp, 0, tokenParent.childNodes, 0, tmp.length);
                }
                for(int k=0;k<children.size();k++) {
                    tokenParent.childNodes[prevChildrenSize+k]=children.get(k);
                }
            }
        }
    }

    public void setCharOffsets(HTMLEventInfo info, String elemName, int start) throws XNIException {
        startChar=-1;
        endChar=-1;
        boolean synthesized = info.isSynthesized();
        int beginRow = info.getBeginLineNumber();
        int beginCol = info.getBeginColumnNumber();
        int endRow = info.getEndLineNumber();
        int endCol = info.getEndColumnNumber();
        if(!synthesized) {
            startChar=getCharOffset(beginRow, beginCol);
            endChar=getCharOffset(endRow, endCol);
        }
        String lnocol="["+beginRow+"."+beginCol+","+ endRow+"."+endCol+"]";
        if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"<"+((start==0)?"/":"")+elemName+"> ["+startChar+","+endChar+"] "+lnocol);
    }

    Pattern patEnt=Pattern.compile("&[#a-zA-Z0-9_]+;");
    // match xml-processed String proc with the corresponding segment in unprocessed document.data
    // this is needed since each token has indices into the raw document (to insert labels)
    private int mapProcessedText(String proc) {
        String raw=doc.data;
        int procLen=proc.length();
        // int processed2raw[] maps indices from xmlprocessed proc to raw document.data, 
        // has extra end index (1 beyond proc's length) which points 1 beyond raw segment's length
        // this map is used to map any token (or any char subsequence) in proc to the token's serialization in raw
        // e.g. token given by proc.substring(0,4) is mapped to raw.substring(map[0],map[4+1]) 
        // which takes everything until the next proc character starts
        if(proc2raw.length<procLen+1)
            return Const.EX_ERR;
        if(lastTextOffset<0) // FIX: coredump prevention
            lastTextOffset=0;
        int pos=lastTextOffset;
        int recoveryCnt=0;
        for(int i=0;i<procLen;i++) {
            proc2raw[i]=pos;
            char c=proc.charAt(i);
            char d=raw.charAt(pos);
            if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"'"+c+"'?='"+d+"'(raw)");
            if(c!=d) {
                // skip comments - only when problems
                if(d=='<' && raw.substring(pos,pos+4).equals("<!--")) {
                    int commentEndIdx=raw.indexOf("-->",pos+4)+3;
                    if(commentEndIdx==-1) {
                        log.LG(Logger.ERR,"Mismatched HTML comments, ignoring");
                        pos+=4;
                    }else {
                        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"comment, skipped "+(commentEndIdx-pos));
                        pos=commentEndIdx;
                    }
                    d=raw.charAt(pos);
                    if(log.IFLG(Logger.MML)) log.LG(Logger.MML,"'"+c+"'?='"+d+"'(raw)");
                }
                // we did replaceAll("[\t\r\n]"," ") before tokenizing so ignore the difference now
                if(c==' ') {
                    switch(d) {
                    case '\r':
                        if(raw.length()>pos+1 && raw.charAt(pos+1)=='\n') // xmlparser does \r\n -> \n
                            pos++;
                    case '\n':
                    case '\t':
                        pos++;
                        continue;
                    }
                }
            }
            // try to match segments
            if(c==d) {
                if(c=='&' && raw.substring(pos,pos+5).equals("&amp;")) // special case of xml entity
                    pos+=5;
                else
                    pos++;
            }else if(c=='\n' && raw.substring(pos,pos+2).equals("\r\n")) { // xmlparser does \r\n -> \n
                pos+=2;
            }else if(i==0 && raw.charAt(pos)=='>' && (raw.charAt(pos+1)==c || raw.charAt(pos+1)=='&')) { // on attribute values without quotes, xmlparser inserts "
                pos+=1;
                i--;
            }else { // xml entity &karel; or &#x00d7; that was replaced by xmlparser with 1 character 
                String s=raw.substring(pos,pos+10);
                Matcher matEnt=patEnt.matcher(s);
                if(matEnt.find() && matEnt.start()==0) {
                    pos+=matEnt.end();
                }else {
                    log.LG(Logger.INF,"Error matching processed '"+proc+"' to '"+s+"'");
                    if(++recoveryCnt>=10) {
                        log.LG(Logger.ERR,"Cannot match processed '"+proc+"' to '"+s+"': skipping");
                        return Const.EX_ERR;
                    }
                    // try to recover by finding the corresponding segment in vicinity...
                    // fix modified newlines so we can match (we did replaceAll("[\t\r\n]"," ") before tokenizing)
                    // String needle=proc.replaceAll("[\t\r\n]"," ");
                    
                    // 1. look forward
                    int idx1=indexOfPrefixOfIgnWhite(raw, proc, pos, indexOfParam);
                    int len1=indexOfParam.data;
                    
//                    String needle1=needle;
//                    int idx1=-1;
//                    while(needle1.length()>0) {
//                        idx1=raw.indexOf(needle1, pos);
//                        if(idx1!=-1) {
//                            log.LG(Logger.TRC,"Can recover by mapping to '"+needle1+"' (+"+(idx1-pos)+")");
//                            break;
//                        }
//                        needle1=needle1.substring(0,needle1.length()-1);
//                    }

                    // 2. look backward
                    int idx2=lastIndexOfPrefixOfIgnWhite(raw, proc, pos-1, indexOfParam);
                    int len2=indexOfParam.data;
//                    String needle2=needle;
//                    int idx2=-1;
//                    while(needle2.length()>0) {
//                        idx2=raw.lastIndexOf(needle2, pos);
//                        if(idx2!=-1) {
//                            log.LG(Logger.TRC,"Can recover by mapping to '"+needle2+"' (-"+(idx2-pos)+")");
//                            break;
//                        }
//                        needle2=needle2.substring(0,needle2.length()-1);
//                    }

                    // choose the better match - the longer one and, if they are of the same length, the closer one
                    int idx=-1;
                    String needle=null;
                    if(idx1==-1 && idx2==-1) {
                        ;
                    }else if(idx1==-1) {
                        idx=idx2; needle=raw.substring(idx2, idx2+len2);
                    }else if(idx2==-1) {
                        idx=idx1; needle=raw.substring(idx1, idx1+len1);
                    }else if(len1>len2) {
                        idx=idx1; needle=raw.substring(idx1, idx1+len1);
                    }else if(len1<len2) {
                        idx=idx2; needle=raw.substring(idx2, idx2+len2);
                    }else if(idx1-pos <= pos-idx2) {
                        idx=idx1; needle=raw.substring(idx1, idx1+len1);
                    }else {
                        idx=idx2; needle=raw.substring(idx2, idx2+len2);
                    }
                    if(idx!=-1) {
                        //pos=idx+proc.length();
                        //i=proc.length()-1;
                        log.LG(Logger.INF,"Recovered by mapping to '"+needle+"' ("+(idx-pos)+")");
                        pos=idx;
                        //i--;
                        i=-1; // re-map the whole segment
                    }else {
                        // report error
                        String errSegment=raw.substring(lastTextOffset,lastTextOffset+proc.length());
                        log.LG(Logger.ERR,"Raw segment: '"+errSegment+"'("+s+") at '"+proc.substring(i)+"'");// +errSegment.substring(pos)+"'");
                        // return Const.EX_ERR;
                        break;
                    }
                }
            }
        }
        proc2raw[procLen]=pos;
        return Const.EX_OK;
    }

    void setMetaEncoding(String attName, String attValue) {
        String enc=null;
        int idx=-1;
        if(attName.equalsIgnoreCase("content")) {
            Matcher mat=charsetPat.matcher(attValue);
            if(mat.find()) {
                enc=mat.group(1);
            }
        }else { // attName.equalsIgnoreCase("charset")
            enc=attValue;
        }
        if(enc==null)
            return;
        idx=doc.data.indexOf(enc, startChar);
        if(idx>=endChar) {
            log.LG(Logger.ERR,"Error setting meta encoding: cannot find '"+enc+"'");
            return;
        }
        doc.metaInfo.encoding=enc;
        doc.metaInfo.encStartIdx=idx;
    }

    void setOnload(String attName, String attValue) {
        int idx=doc.data.indexOf(attValue, startChar);
        if(idx>=endChar) {
            log.LG(Logger.ERR,"Error setting onload: cannot find '"+attValue+"'");
            return;
        }
        doc.metaInfo.onload=attValue;
        doc.metaInfo.onloadStartIdx=idx;
        log.LG(Logger.WRN,"Found onload='"+attValue+"' at "+idx);
    }
    
    /** Returns the index of the longest matched prefix of needle found in haystack starting at from.
     * The length of matched needle's prefix is returned  */
    int indexOfPrefixOfIgnWhite(String haystack, String needle, int from, ObjectRef<Integer> pLen) {
        int matchedLen=-1;
        int matchedPos=-1;
        for(int i=from; i<haystack.length(); i++) {
            int j=0;
            int maxLen=Math.min(needle.length(), haystack.length()-i);
            for( ;j<maxLen;j++) {
                char h=haystack.charAt(i+j);
                char n=needle.charAt(j);
                if(h!=n && !(Character.isWhitespace(h) && Character.isWhitespace(n))) {
                    break;
                }
            }
            if(j>matchedLen && j>0) {
                matchedLen=j;
                matchedPos=i;
                if(matchedLen==needle.length()) {
                    break;
                }
            }
        }
        pLen.data=matchedLen;
        return matchedPos;
    }
    
    /** Returns the index of the longest matched prefix of needle found in haystack starting at from,
     * searching backward. The length of matched needle's prefix is returned  */
    int lastIndexOfPrefixOfIgnWhite(String haystack, String needle, int from, ObjectRef<Integer> pLen) {
        int matchedLen=-1;
        int matchedPos=-1;
        for(int i=from; i>=0; i--) {
            int j=0;
            int maxLen=Math.min(needle.length(), haystack.length()-i);
            for( ;j<maxLen;j++) {
                char h=haystack.charAt(i+j);
                char n=needle.charAt(j);
                if(h!=n && !(Character.isWhitespace(h) && Character.isWhitespace(n))) {
                    break;
                }
            }
            if(j>matchedLen && j>0) {
                matchedLen=j;
                matchedPos=i;
                if(matchedLen==needle.length()) {
                    break;
                }
            }
        }
        pLen.data=matchedLen;
        return matchedPos;
    }
}
