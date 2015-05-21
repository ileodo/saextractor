// $Id: DocumentFormat.java 1759 2008-11-28 01:08:36Z labsky $
package medieq.iet.model;

import java.util.ArrayList;
import java.util.List;

public class DocumentFormat {
    public static final int UNKNOWN=0;
    public static final int TEXT=1;
    public static final int HTML=2;
    public static final int ELLOGON=3;
    public static final int SEMINAR=4;
    public static final int BIKE=5;
    public static final int COOKED=6;
    
    protected static final DocumentFormat[] builtinFormats={
        new DocumentFormat(UNKNOWN, "unknown",    "",                  "unknown format",                      "medieq.iet.components.TextHtmlDocumentReader",
                new String[] {""}),
        new DocumentFormat(TEXT,    "text",       "text/plain",        "plain text, no annotations",          "medieq.iet.components.TextHtmlDocumentReader",
                new String[] {"txt","ascii","plain","plaintext","raw","text/plain"}),
        new DocumentFormat(HTML,    "html/xhtml", "text/html",         "plain html, no annotations",          "medieq.iet.components.TextHtmlDocumentReader",
                new String[] {"html","xhtml","htm","text/html","text/xhtml"}),
        new DocumentFormat(ELLOGON, "ellogon",    "text/html-ellogon", "html with ellogon annotation header", "medieq.iet.components.DatDocumentReader",
                new String[] {"atf"}),
        new DocumentFormat(SEMINAR, "seminar",    "text/html-seminar", "text with annotations as SGML tags",  "medieq.iet.components.SeminarDocumentReader",
                new String[] {"seminars"}),
        new DocumentFormat(BIKE,    "bike",       "text/html-bike",    "html with annotations as SGML tags",  "medieq.iet.components.BikeDocumentReader",
                new String[] {"bikes"}),
        new DocumentFormat(COOKED,  "cooked",     "text/cooked",       "words followed by word classes",      "medieq.iet.components.CookedDocumentReader",
                new String[] {"cooked"}),
    };
    protected static List<DocumentFormat> userFormats=new ArrayList<DocumentFormat>(8);
    
    public int id;
    public String name;
    public String contentType;
    public String desc;
    public String documentReaderClassName;
    public String[] altNames;
    public DocumentFormat(int id, String name, String contentType, String desc, String documentReaderClassName, String[] altNames) {
        this.id = id;
        this.name = name;
        this.altNames = altNames;
        this.contentType = contentType;
        this.desc = desc;
        this.documentReaderClassName=documentReaderClassName;
    }
    
    public static DocumentFormat getDefaultFormat() {
        return builtinFormats[ELLOGON]; // during document reading falls back to HTML if the file is not ELLOGON
    }
    
    public static DocumentFormat formatForName(String name) {
        name=name.trim();
        for(DocumentFormat f: builtinFormats) {
            if(f.matches(name)) {
                return f;
            }
        }
        for(DocumentFormat f: userFormats) {
            if(f.matches(name)) {
                return f;
            }
        }
        return builtinFormats[UNKNOWN];
    }

    public boolean matches(String wantedFormatName) {
        wantedFormatName=wantedFormatName.trim();
        if(name.equalsIgnoreCase(wantedFormatName))
            return true;
        if(altNames!=null) {
            for(String n: altNames) {
                if(n.equalsIgnoreCase(wantedFormatName))
                    return true;
            }
        }
        return false;
    }

    public static DocumentFormat formatForContentType(String contentType) {
        if(contentType!=null) {
            contentType=contentType.trim();
            for(DocumentFormat f: builtinFormats) {
                if(f.contentType.equalsIgnoreCase(contentType)) {
                    return f;
                }
            }
            for(DocumentFormat f: userFormats) {
                if(f.contentType.equalsIgnoreCase(contentType)) {
                    return f;
                }
            }
        }
        return builtinFormats[UNKNOWN];
    }

    public static DocumentFormat formatForId(int id) {
        if(id<0||id>=builtinFormats.length+userFormats.size())
            return null;
        if(id<builtinFormats.length)
            return builtinFormats[id];
        else
            return userFormats.get(id-builtinFormats.length);
    }
        
    public static void addFormat(DocumentFormat fmt) {
        if(formatForName(fmt.name).id!=UNKNOWN)
            throw new IllegalArgumentException("Format named "+fmt.name+" already exists.");
        userFormats.add(fmt);
        fmt.id=builtinFormats.length+userFormats.size()-1;
    }
}
