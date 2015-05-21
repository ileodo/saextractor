// $Id: PatComp.java 1933 2009-04-12 09:14:52Z labsky $
package ex.ac;

/** 
 *  TokenPattern compiler
 *  @author Martin Labsky labsky@vse.cz
 */

import java.io.*;
import java.util.*;
import java.util.regex.*;
import uep.util.Logger;

import ex.train.KB;
import ex.reader.Tokenizer;
import ex.model.ModelElement;
import ex.model.AttributeDef;
import ex.model.ClassDef;
import ex.train.PhraseBook;
import ex.train.PhraseBookImpl;

public class PatComp {
    private Logger log;
    private StringBuffer logBuff;
    public static final short ST_NOTOK=1;
    public static final short ST_TOK=2;
    protected List<FAState> resolveList;
    
    protected static int faCnt=0;
    protected static int faSubCnt=0;
    
    public PatComp() {
        log=Logger.getLogger("PatComp");
        logBuff=new StringBuffer(1024);
        resolveList=new LinkedList<FAState>();
    }

    public int compile(TokenPattern pat, List<TokenPattern> subPatterns, ModelElement mElem, KB kb, Tokenizer tok) 
    throws TokenPatternSyntaxException {
        pat.source=trimWhite(pat.source);
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Parsing '"+pat.source+"'");
        trimRoofs(pat);
        
        PTreeNode root=source2tree(pat, subPatterns, mElem);
        if(log.IFLG(Logger.TRC)) {
            root.serialize(logBuff);
            log.LG(Logger.TRC,"Parsed pattern: '"+logBuff.toString()+"'");
            logBuff.setLength(0);
        }

        root.normalize();

        // if $ found at the very end or at the very beginning, 
        // remove it and turn pattern into PAT_CTX_L or R (or PAT_CLS_CTX_L or R)
        switch(pat.type) {
        case TokenPattern.PAT_CTX_LR:
        case TokenPattern.PAT_CLS:
        case TokenPattern.PAT_CLS_CTX_LR:
            setPatternContext(pat, root);
        }
        if(log.IFLG(Logger.TRC)) {
            root.serialize(logBuff);
            log.LG(Logger.TRC,"Normalized pattern: '"+logBuff.toString()+"'");
            logBuff.setLength(0);
        }

        // set features for each token in pattern
        if(kb!=null) {
            if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Setting features for '"+pat.source+"'");
//            int[] fids=null;
//            if(pat.ignoreCase && pat.ignoreLemma) {
//                fids=new int[] {FM.TOKEN_ID, FM.TOKEN_LC, FM.TOKEN_LEMMA};
//            }else if(pat.ignoreCase) {
//                fids=new int[] {FM.TOKEN_ID, FM.TOKEN_LC};
//            }else if(pat.ignoreLemma) {
//                fids=new int[] {FM.TOKEN_ID, FM.TOKEN_LEMMA};
//            }else {
//                fids=new int[] {FM.TOKEN_ID};
//            }
            root.setTokenFeatures(kb, pat.matchFlags, tok);
        }

        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Compiling '"+pat.source+"'");
        pat.fa=tree2fa(root, pat);
        if(pat.fa==null) {
            throw new TokenPatternSyntaxException("Error compiling pattern "+pat.source);
        }
        return 0;
    }
    
    /** Loads and compiles a list TokenPattern (populates pat.fa) from file. */
    public int compileListFromFile(TokenPattern pat, KB kb, Tokenizer tokenizer, String file, String enc) {
        log.LG(Logger.WRN,"Loading list from file '"+file+"'");
        PhraseBook book=null;
        if(kb.namedBooks.containsKey(file)) {
            log.LG(Logger.WRN,"Using cached version of '"+file+"'");
            book=kb.namedBooks.get(file);
        }else {
            /*
            Collection col=kb.namedBooks.keySet();
            Iterator it=col.iterator();
            while(it.hasNext())
                log.LG(Logger.ERR,"-->"+it.next()+"'");
            */
            try {
                book=PhraseBookImpl.readFrom(null, file, enc, tokenizer, kb.vocab); // book named by fileName
            }catch(IOException ex) {
                log.LG(Logger.ERR,"Error reading "+file+": "+ex.toString());
                return -1;
            }
        }
        FATrieState state=new FATrieState(book, (pat.matchFlags & TokenPattern.MATCH_IGNORE_CASE)!=0, 
                (pat.matchFlags & TokenPattern.MATCH_IGNORE_LEMMA)!=0, file);
        pat.fa=new FA(state, state);
        // cache it with KB
        kb.namedBooks.put(file, book);
        return 0;
    }

    /** Loads and compiles TokenPattern (populates pat.fa) from string. */
    public int compileListFromString(TokenPattern pat, KB kb, Tokenizer tokenizer, String listName, String source) {
        PhraseBook book=null;
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Reading list from content '"+source+"'");
        BufferedReader br=new BufferedReader(new StringReader(source));
        try {
            book=PhraseBookImpl.readFrom(null, listName, br, (source.length()+1)/32, tokenizer, kb.vocab);                
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Error reading "+source+": "+ex.toString());
            return -1;
        }
        FATrieState state=new FATrieState(book, (pat.matchFlags & TokenPattern.MATCH_IGNORE_CASE)!=0, 
                (pat.matchFlags & TokenPattern.MATCH_IGNORE_LEMMA)!=0, listName);
        pat.fa=new FA(state, state);
        // cache it with KB
        kb.namedBooks.put(listName, book);
        return 0;
    }

    private void throwUnexpected(char c,int pos,String src) throws TokenPatternSyntaxException {
        throw new TokenPatternSyntaxException("Unexpected '"+c+"' at "+pos+": pattern '"+src+"'");
    }

    private short str2short(String s) throws TokenPatternSyntaxException {
        short x;
        try {
            x=Short.parseShort(s);
        }catch(NumberFormatException ex) {
            throw(new TokenPatternSyntaxException("Expected a short number: "+ex.toString()));
        }
        return x;
    }

    private char char2special(char c) throws TokenPatternSyntaxException {
        switch(c) {
        case 'n': return '\n';
        case 'r': return '\r';
        case 't': return '\t';
        case '\\': return '\\';
        default: throw(new TokenPatternSyntaxException("Unknown escape sequence \\"+c));
        }
    }

    private char fromUnicode(String s, int i) throws TokenPatternSyntaxException {
        if(s.length()<i+4)
            throw new TokenPatternSyntaxException("Unterminated unicode character reference in '"+s+"', pos="+i);
        String n=s.substring(i,i+4);
        char value;
        try {
            value=(char) Integer.parseInt(n, 16);
        }catch(NumberFormatException ex) {
            throw new TokenPatternSyntaxException("Cannot parse unicode character reference '"+n+"', as hex int in "+s);
        }
        return value;
    }

    private static Pattern patCard2=Pattern.compile("\\{([0-9]+),([0-9]+)\\}");
    private static Pattern patCard1=Pattern.compile("\\{([0-9]+)\\}");

    private PTreeNode source2tree(TokenPattern pat, List<TokenPattern> subPatterns, ModelElement mElem) throws TokenPatternSyntaxException {
        String s=pat.source;
        if(s.length()==0) {
            throw new TokenPatternSyntaxException("Empty pattern");
        }
        PTreeNode root=new PTreeNode(null,PTreeNode.AND,(short)1,(short)1,null);
        PTreeNode parentNode=root;

        char c='\0';
        char last='\0';
        int len=s.length();
        short state=ST_NOTOK;
        StringBuffer tok=new StringBuffer(64);
        PTreeNode lastNode=null;
        int nextSubPatternIdx=0;
        boolean tokIsAttribute=false;

        for(int i=0;i<=len;i++) {
            c=(i<len)? s.charAt(i): '\0';
            switch(state) {
            case ST_NOTOK:
                switch(c) {
                /* whitespace */
                case ' ':
                case '\t':
                case '\r':
                    break;

                /* grouping */
                case '(':
                    PTreeNode child=new PTreeNode(parentNode,PTreeNode.AND,(short)1,(short)1,null);
                    parentNode.addChild(child);
                    parentNode=child;
                    break;
                case ')':
                    /* parent is an OR parent's child - return to the OR parent */
                    if(parentNode.isORChild) {
                        parentNode=parentNode.parent;
                        parentNode.collapseLastChild();
                    }
                    parentNode=parentNode.parent;
                    if(parentNode==null)
                        throwUnexpected(c,i,s);
                    break;
                case '\0':
                    /* parent is an OR parent's child - return to the OR parent */
                    if(parentNode.isORChild) {
                        parentNode=parentNode.parent;
                        parentNode.collapseLastChild();
                    }
                    break;

                /* OR */
                case '|':
                case '\n':
                    /* parent is an OR parent's child - return to the OR parent */
                    if(parentNode.isORChild) {
                        parentNode=parentNode.parent;
                        parentNode.collapseLastChild();

                        PTreeNode newChild=new PTreeNode(parentNode,PTreeNode.AND,(short)1,(short)1,null);
                        newChild.isORChild=true;
                        parentNode.addChild(newChild);
                        parentNode=newChild;
                        break;
                    }
                    /* AND parent turns out to be an OR node - take care of the first OR child and create an empty next OR child */
                    if(parentNode.type!=PTreeNode.OR) { // && (parentNode.children!=null && parentNode.children.size()>0)
                        parentNode.setOR(); // creates an extra single child node from the first OR child if its len>1
                        PTreeNode orChild=new PTreeNode(parentNode,PTreeNode.AND,(short)1,(short)1,null);
                        orChild.isORChild=true;
                        parentNode.addChild(orChild);
                        parentNode=orChild;
                    }
                    break;

                /* cardinality */
                case '*':
                    lastNode=parentNode.getChild(-1); if(lastNode==null) throwUnexpected(c,i,s);
                    lastNode.minCnt=0;
                    lastNode.maxCnt=-1;
                    break;
                case '+':
                    lastNode=parentNode.getChild(-1); if(lastNode==null) throwUnexpected(c,i,s);
                    lastNode.minCnt=1;
                    lastNode.maxCnt=-1;
                    break;
                case '?':
                    lastNode=parentNode.getChild(-1); if(lastNode==null) throwUnexpected(c,i,s);
                    lastNode.minCnt=0;
                    lastNode.maxCnt=1;
                    break;
                case '{': {
                    lastNode=parentNode.getChild(-1); if(lastNode==null) throwUnexpected(c,i,s);
                    int epos=s.indexOf('}',i+1);
                    if(epos==-1)
                        throw(new TokenPatternSyntaxException(i+": unterminated { in cardinality specification: "+s.substring(i)));
                    CharSequence seq=s.subSequence(i,Math.min(s.length(),epos+1));
                    Matcher m=patCard2.matcher(seq);
                    if(m.matches()) {
                        lastNode.minCnt=str2short(m.group(1));
                        lastNode.maxCnt=str2short(m.group(2));
                    }else {
                        m=patCard1.matcher(seq);
                        if(m.matches()) {
                            lastNode.minCnt=str2short(m.group(1));
                            lastNode.maxCnt=lastNode.minCnt;
                        }else {
                            throw(new TokenPatternSyntaxException(i+": error in cardinality specification syntax: "+seq));
                        }
                    }
                    i=epos;
                    break;
                }
                case '\\':
                    last='\\';
                    state=ST_TOK;
                    break;
                case '\1': // sub-pattern placeholder - process in ST_TOK
                    state=ST_TOK;
                    i--;
                    break;
                case '$': // attribute value placeholder - process in ST_TOK
                    state=ST_TOK;
                    i--;
                    break;
                default:
                    state=ST_TOK;
                last='\0';
                tok.append(c);
                }
                break; /* ST_NOTOK */

            case ST_TOK:
                switch(c) {
                case ' ':
                case '\t':
                case '(':
                case ')':
                case '*':
                case '+':
                case '?':
                case '{':
                case '}':
                case '|':
                case '\n':
                case '\r':
                    if(last=='\\') {
                        tok.append(c);
                        last=c;
                        break;
                    }
                case '\0':
                    if(tok.length()>0) {
                        String token=tok.toString();
                        if(!tokIsAttribute) { // normal token
                            parentNode.addChild(new PTreeNode(parentNode,PTreeNode.LEAF,(short)1,(short)1,token));
                        }else { // token = attribute name (alphabetic token starting with $)
                            tokIsAttribute=false;
                            ClassDef cls=(mElem instanceof ClassDef)? (ClassDef)mElem: ((AttributeDef)mElem).myClass;
                            AttributeDef neighbor=cls.attributes.get(token);
                            //if(neighbor==null)
                            //    throw new TokenPatternSyntaxException("$"+token+": reference to unknown attribute");
                            // add single sub-pattern state to accept attribute value
                            PTreeNode valNode=new PTreeNode(parentNode,PTreeNode.VALUE,(short)1,(short)1,"$"+token);
                            valNode.fa.startState=valNode.fa.finalState=new FAACState(neighbor,"$"+token);
                            parentNode.addChild(valNode);
                            pat.contentType |= TokenPattern.PATTERN_WITH_ACS;
                            if(neighbor==null) {
                                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"$"+token+": attribute reference will be resolved later");
                                resolveList.add(valNode.fa.startState);
                                pat.addUsedElement(token);
                            }else {
                                pat.addUsedElement(neighbor);
                            }
                        }
                        tok.setLength(0);
                    }
                    state=ST_NOTOK;
                    i--;
                    break;
                case '\1': // placeholder for sub-pattern's precompiled FA
                    TokenPattern subPat=subPatterns.get(nextSubPatternIdx++);
                    if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Subpattern "+subPat.id+"("+(nextSubPatternIdx-1)+","+"ctype="+subPat.contentType+") found in "+pat.id+"(ctype="+pat.contentType+")");
                    // if any sub pattern uses ACs or labels, then parent pattern uses them as well
                    pat.contentType |= subPat.contentType;
                    pat.addUsedElements(subPat);
                    // either create a subpattern state, or keep a single-token state
                    PTreeNode subNode=null;
                    if(subPat.fa.startState==subPat.fa.finalState) { // single token state
                        subNode=new PTreeNode(parentNode,PTreeNode.PATTERN,(short)1,(short)1,"(TOK)");
                        subNode.fa.startState=subPat.fa.startState;
                        subNode.fa.finalState=subPat.fa.finalState;
                    }else { // single sub-pattern state
                        subNode=new PTreeNode(parentNode,PTreeNode.PATTERN,(short)1,(short)1,"(PAT)");
                        subNode.fa.startState=subNode.fa.finalState=new FAPatternState(subPat,FAPatternState.PS_GEN,"(PAT)");
                    }
                    parentNode.addChild(subNode);
                    state=ST_NOTOK;
                    break;
                case '$': {
                    if(last=='\\') { // escaped $ character 
                        last='\0';
                        tok.append(c);
                        break;
                    }
                    int dim=0;
                    if(s.length()>i+1) {
                        char cdim=s.charAt(i+1);
                        if(Character.isDigit(cdim)) { // $, $1, .., $9
                            dim=(int) (cdim-'0');
                            i++;
                        }else if(Character.isLetter(cdim)) {
                            tokIsAttribute=true;
                            break; // stay in ST_TOK, create PTreeNode when we have the whole token
                        }
                    }
                    // add single sub-pattern state to accept the whole attribute value (dim=0) or its dim-th dimension, 
                    // or a whole valid instance candidate of this class
                    String nodeName="$"+mElem.getName()+dim;
                    PTreeNode valNode=new PTreeNode(parentNode,PTreeNode.VALUE,(short)1,(short)1,nodeName);
                    if(mElem instanceof AttributeDef) {
                        if(pat.type!=TokenPattern.PAT_CTX_LR) {
                            log.LG(Logger.ERR,"Found $: forcing pattern to CTX_LR: "+pat);
                        }
                        pat.type=TokenPattern.PAT_CTX_LR;
                        AttributeDef attDef=(AttributeDef) mElem;
                        // this way we also search for non-existing ACs:
                        valNode.fa.startState=valNode.fa.finalState=
                            new FAPatternState(attDef.getDataTypePattern(),(short)(FAPatternState.PS_VAL+dim),nodeName);
                        // this way we would only boost existing ACs:
                        // valNode.startState=valNode.finalState=new FAACState(attDef,nodeName);
                    }else {
                        pat.type=TokenPattern.PAT_CLS_CTX_LR;
                        ClassDef clsDef=(ClassDef) mElem;
                        valNode.fa.startState=valNode.fa.finalState=new FAICState(clsDef,nodeName);
                    }
                    parentNode.addChild(valNode);
                    state=ST_NOTOK;
                    break;
                }
                default:
                    if(last=='\\') {
                        if(c=='u') { // unicode escape seq \u00d7
                            c=fromUnicode(s,i+1);
                            i+=4;
                        }else {
                            c=char2special(c); // special chars \n \r \t ...
                        }
                    }
                    tok.append(c);
                    if(last=='\\' && c=='\\') {
                        last='\0'; // turn off the escape for next char
                    }else {
                        last=c;
                    }
                }
                break; /* ST_TOK */
            } /* switch(state) */
        } /* for i<len */
        if(parentNode!=root) {
            throw new TokenPatternSyntaxException("Unterminated ( in pattern "+s);
        }
        return root;
    }

    public FA tree2fa(PTreeNode root, TokenPattern compiledPattern) {
        try {
            LinkedList<TokenPattern> patLst=new LinkedList<TokenPattern>();
            patLst.add(compiledPattern);
            while(patLst.size()>0) {
                root.genStates(patLst.remove(0), patLst);
                root.fa.makeStartFinalNull();
            }
        }catch(TokenPatternSyntaxException ex) {
            log.LG(Logger.ERR,"FA compile error: "+ex.toString());
            return null;
        }
//        FA fa=new FA();
//        fa.startState=root.startState;
//        fa.finalState=root.finalState;
        if(log.IFLG(Logger.TRC)) {
            String data=root.fa.toString();
            log.LG(Logger.TRC,"Generated FA:\n"+data);
            if(true) {
                log.LGX(Logger.TRC, data, faCnt+"_"+compiledPattern.id+".dot");
                faCnt++;
                faSubCnt=0;
            }
        }
        // fa.finalState=new FAState(FAState.ST_FINAL);
        // Set openStates=new TreeSet();
        // openStates.add(fa.startState);
        // openStates
        // preorder walk the pattern parse tree, generating an FA with output on states
        //root.startState=fa.startState;
        //fa.finalState=
        // link all open states to final state
        /*
        Iterator it=openStates.iterator();
        while(it.hasNext()) {
            FAState st=(FAState) it.next();
            st.addArc(fa.finalState);
        }
        */
        return root.fa;
    }
    
    private static Pattern patWhiteStart=Pattern.compile("^[\\n\\r \\t]+");
    private static Pattern patWhiteEnd=Pattern.compile("[\\n\\r \\t]+$");
    public String trimWhite(String src) {
        int swl=0;
        int ewl=0;
        Matcher m=patWhiteStart.matcher(src);
        if(m.find()) {
            swl=m.group().length();
            if(swl==src.length())
                return "";
        }
        m=patWhiteEnd.matcher(src);
        if(m.find()) {
            ewl=m.group().length();
        }
        if(swl>0 || ewl>0) {
            src=src.substring(swl, src.length()-ewl);
        }
        return src;
    }
    
    protected void trimRoofs(TokenPattern pat) {
        if(! (pat.modelElement instanceof ClassDef)) {
            return;
        }
        if(pat.source.startsWith("^")) {
            if(pat.source.endsWith("$")) { // "^"
                pat.type=TokenPattern.PAT_CLS_VAL_LR;
                pat.source=pat.source.substring(1, pat.source.length()-1);
            }else {
                pat.type=TokenPattern.PAT_CLS_VAL_L;
                pat.source=pat.source.substring(1);
            }
        }else if(pat.source.endsWith("$")) { // "^"
            pat.type=TokenPattern.PAT_CLS_VAL_R;
            pat.source=pat.source.substring(0, pat.source.length()-1);
        }
    }
    
    private boolean isDollarNode(PTreeNode node) {
        return (node.type==PTreeNode.VALUE &&
                ((node.fa.startState.type==FAState.ST_PATTERN &&
                        ((FAPatternState)node.fa.startState).patType==FAPatternState.PS_VAL) || 
                        (node.fa.startState.type==FAState.ST_IC)));
    }
    
    protected void setPatternContext(TokenPattern pat, PTreeNode root) throws TokenPatternSyntaxException {
        boolean isClass=(pat.modelElement instanceof ClassDef);
        if(root.children!=null) {
            // $ placeholder (but not $1 or $att) at start or end
            if(root.children.size()>1 && isDollarNode(root.children.get(0))) {
                root.children.remove(0);
                pat.type=isClass? TokenPattern.PAT_CLS_CTX_R: TokenPattern.PAT_CTX_R;
            }
            if(root.children.size()>1 && isDollarNode(root.children.get(root.children.size()-1))) {
                root.children.remove(root.children.size()-1);
                if(isClass) {
                    pat.type=(pat.type==TokenPattern.PAT_CLS_CTX_R)? TokenPattern.PAT_CLS_CTX_LR: TokenPattern.PAT_CLS_CTX_L; 
                }else {
                    pat.type=(pat.type==TokenPattern.PAT_CTX_R)? TokenPattern.PAT_CTX_LR: TokenPattern.PAT_CTX_L;
                }
            }
        }
        //if(root.children.size()==0)
        //    throw new TokenPatternSyntaxException("Context pattern only contains $ placeholder: '"+pat.source+"'");
        // renormalize
        root.normalize();
        // if type for class pattern is still undecided, it is a simple value patterns without $ anywhere
        if(pat.type==TokenPattern.PAT_CLS) {
            pat.type=TokenPattern.PAT_CLS_VAL;
        }
    }
    
    public void resolveReferences(ClassDef cls) throws TokenPatternSyntaxException {
        for(FAState st: resolveList) {
            if(st instanceof FAACState) {
                String attName=((String)(st.data)).substring(1);
                AttributeDef ad=cls.attributes.get(attName);
                if(ad==null)
                    throw new TokenPatternSyntaxException("Can't resolve attribute reference "+st.data);
                ((FAACState)st).ad=ad;
            //}else if(st instanceof FAICCState) {{
            }else if(st instanceof FAPhraseState) {
                ((FAPhraseState)st).resolveReferences(cls);
            }else {
                throw new TokenPatternSyntaxException("Internal error: don't know how to resolve state "+st);
            }
        }
        resolveList.clear();
        for(TokenPattern clsPat: cls.ctxPatterns) {
            clsPat.resolveReferences(cls);
        }
    }

    public void addToResolveList(FAState st) {
        resolveList.add(st);
    }
}
