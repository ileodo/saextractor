// $Id: BetterDOMParser.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Collection;
import java.util.Iterator;

import org.apache.xerces.parsers.DOMParser;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

public class BetterDOMParser extends DOMParser {
    protected Logger log;
    public boolean ignoreIgnorableWhitespace=false;
    public boolean useUserData=false;
    protected XMLLocator loc;
    protected HashMap<Node,XMLLocInfo> node2info;

    public BetterDOMParser() {
        log=Logger.getLogger("DOMP");
        try {
            this.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);
        }catch(org.xml.sax.SAXException ex) {
            log.LG(Logger.ERR,"Error creating DOMParser "+ex);
        }
        node2info=new HashMap<Node,XMLLocInfo>();
    }

    public XMLLocInfo getInfo(Node n) {
        return node2info.get(n);
    }

    public String getInfoString(Node n) {
        String s="unknown line";
        XMLLocInfo info=node2info.get(n);
        if(info!=null) {
            s=info.line+"."+info.col;
        }
        return s;
    }
    
    public HashMap<Node,XMLLocInfo> getInfoMap() {
        return node2info;
    }

    public HashMap<Node,XMLLocInfo> removeInfoMap() {
        HashMap<Node,XMLLocInfo> tmp=node2info;
        node2info=new HashMap<Node,XMLLocInfo>();
        return tmp;
    }
    
    // overriden from DocumentHandler
    public void startElement(QName elementQName, XMLAttributes attrList, Augmentations augs) throws XNIException {
        super.startElement(elementQName, attrList, augs);
        regLocInfo();
    }
    
    // overriden from DocumentHandler
    public void startDocument(XMLLocator locator, String encoding, 
                              NamespaceContext namespaceContext, Augmentations augs) throws XNIException {
        super.startDocument(locator, encoding, namespaceContext, augs);
        this.loc=locator;
        this.node2info.clear();
        regLocInfo();
    }

    protected void regLocInfo() {
        Node node=null;
        try {
            node=(Node) this.getProperty("http://apache.org/xml/properties/dom/current-element-node");
        }catch(org.xml.sax.SAXException ex) {
            log.LG(Logger.ERR,"Error getting current-element-node: "+ex);
        }
        if(node!=null) {
            XMLLocInfo info=new XMLLocInfo(loc.getLineNumber(), loc.getColumnNumber());
            // need correct Xerces version
            if(useUserData) {
                try {
                    node.setUserData("startLine", info, null);
                }catch(AbstractMethodError ex) {
                    log.LG(Logger.ERR,"Cannot store line number information with your Xerces: "+ex);
                }
            // or store on our own
            }else {
                node2info.put(node, info);
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,loc.getLineNumber()+"."+loc.getColumnNumber()+":"+((Element)node).getTagName());
            }
        }
    }
    
    public void ignorableWhitespace(XMLString text, Augmentations augs) throws XNIException {
        if(!ignoreIgnorableWhitespace)
            super.ignorableWhitespace( text, augs);
    }
    
    public static void main(String argv[]) {
        if(argv.length==0) {
            printUsage();
            System.exit(1);
        }
        BetterDOMParser prs=new BetterDOMParser();
        for (int i=0; i<argv.length; i++) {
            String arg=argv[i];
            boolean igwsp=false;
            boolean userData=false;
                        
            if(arg.startsWith("-")) {
                if(arg.equals("-h")) {
                    printUsage();
                    System.exit(1);
                }
                if(arg.equals("-i")) {
                    igwsp=true;
                    continue;
                }
                if(arg.equals("-u")) {
                    userData=true;
                    continue;
                }
            }
            
            prs.ignoreIgnorableWhitespace=igwsp;
            prs.useUserData=userData;
            try {
                prs.parse(arg);
            }catch(IOException ex) {
                Logger.LOG(Logger.ERR,"Error opening document "+arg+":"+ex);
            }catch(org.xml.sax.SAXException ex) {
                Logger.LOG(Logger.ERR,"Error parsing document "+arg+":"+ex);
            }
            if(Logger.IFLOG(Logger.TRC)) { 
                Collection<XMLLocInfo> col=prs.getInfoMap().values();
                Iterator<XMLLocInfo> it=col.iterator();
                while(it.hasNext()) {
                    XMLLocInfo info=it.next();
                    Logger.LOG(Logger.TRC,"["+info.line+","+info.col+"]");
                }
                // Document doc=prs.getDocument();
                // prs.print(doc);
            }
        }
    }

    protected static void printUsage() {
        System.err.println("usage: java BetterDOMParser uri\n");
        System.err.println("  -h       Display help screen.");
        System.err.println("  -i       Don't print ignorable white spaces.");
    }
}
