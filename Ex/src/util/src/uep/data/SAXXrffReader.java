// $Id: $
package uep.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import uep.util.Logger;

class SAXXrffReader implements ContentHandler {
    SampleSet set;
    boolean uniq;
    boolean headerOnly;
    boolean inData;
    String curElem;
    SampleFeature feat;
    Sample smp;
    StringBuffer b;
    List<String> featVals;
    int valIdx;
    public SAXXrffReader(SampleSet set) {
        this.set=set;
        uniq=true;
        headerOnly=false;
        init();
    }
    protected void init() {
        inData=false;
        curElem=null;
        feat=null;
        b=new StringBuffer(128);
        featVals=new ArrayList<String>(32);
        valIdx=-1;
    }
    public SampleSet getSampleSet() {
        return set;
    }
    public void startDocument() throws SAXException {
        init();
    }
    public void endDocument() throws SAXException {
        ;
    }
    public void startElement(String uri, String localName, String name, Attributes atts) throws SAXException {
        curElem=name;
        if(!inData) {
            if(curElem.equalsIgnoreCase("dataset")) {
                ;
            }else if(curElem.equalsIgnoreCase("header")) {
                ;
            }else if(curElem.equalsIgnoreCase("attributes")) {
                ;
            }else if(curElem.equalsIgnoreCase("attribute")) {
                SampleFeature sf=new SampleFeature(null,SampleFeature.DT_ENUM,null);
                sf.setName(atts.getValue("name"));
                if(sf.getName().equals("class"))
                    set.classIdx=set.features.size();
                String type=atts.getValue("type");
                if(type.equalsIgnoreCase("real") || type.equalsIgnoreCase("numeric")) {
                    sf.setType(SampleFeature.DT_INT);
                }else if(type.equalsIgnoreCase("string")) {
                    sf.setType(SampleFeature.DT_STRING);
                }else if(type.equals("nominal")) {
                    sf.setType(SampleFeature.DT_ENUM);
                }else {
                    Logger.LOGERR(set.name+": Unknown feature datatype "+type);
                }
                set.features.add(sf);
                featVals.clear();
            }else if(curElem.equalsIgnoreCase("labels")) {
                ;
            }else if(curElem.equalsIgnoreCase("label")) {
                feat=set.features.get(set.features.size()-1);
            }else if(curElem.equalsIgnoreCase("instances")) {
                inData=true;
            }
        }else if(!headerOnly) {
            if(curElem.equalsIgnoreCase("instance")) {
                String ws=atts.getValue("weight");
                smp=new SampleImpl();
                if(ws!=null) {
                    smp.setWeight(Integer.parseInt(ws));
                }
            }else if(curElem.equalsIgnoreCase("value")) {
                valIdx=Integer.parseInt(atts.getValue("index"));
            }
        }
        b.setLength(0);
    }
    public void endElement(String uri, String localName, String name) throws SAXException {
       if(!inData) {
           if(name.equalsIgnoreCase("label")) {
               featVals.add(b.toString().trim());
           }else if(name.equalsIgnoreCase("attribute")) {
               if(featVals.size()>0) {
                   String[] vals=new String[featVals.size()];
                   for(int i=0;i<vals.length;i++)
                       vals[i]=featVals.get(i);
                   feat.setValues(vals);
                   featVals.clear();
               }
           }
       }else {
           if(name.equalsIgnoreCase("instance")) {
               if(uniq) {
                   set.addSample(smp);
               }else {
                   // no checks for duplicates
                   set.samples.add(smp);
               }
               smp=null;
               if(set.samples.size()%100==0) {
                   System.err.print("\r"+set.samples.size()+" instances ");
               }
               boolean debug=false;
               if(debug) {
                   if(set.samples.size()%2000==0) {
                       try {
                           System.err.print("\nWaiting for profiler...\n");
                           (new BufferedReader(new InputStreamReader(System.in))).readLine();
                       }catch(IOException ex) {
                           System.err.print("Error waiting for profiler\n");
                       }
                   }
               }
           }else if(name.equalsIgnoreCase("value")) {
               smp.setFeatureValue(valIdx-1,b.toString().trim());
           }
       }
       b.setLength(0);
    }
    public void characters(char[] ch, int start, int length) throws SAXException {
        b.append(ch, start, length);
        // System.err.println("-------->"+String.valueOf(ch, start, length));
    }
    public void endPrefixMapping(String prefix) throws SAXException {
        ;
    }
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        ;
    }
    public void processingInstruction(String target, String data) throws SAXException {
        ;
    }
    public void setDocumentLocator(Locator locator) {
        ;
    }
    public void skippedEntity(String name) throws SAXException {
        ;
    }
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        ;
    }
}
