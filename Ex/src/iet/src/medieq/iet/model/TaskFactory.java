// $Id: TaskFactory.java 1695 2008-10-19 23:03:57Z labsky $
package medieq.iet.model;

import java.io.*;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DateFormat;
import java.text.ParseException;
import org.xml.sax.*;
import javax.xml.parsers.*;

import uep.util.Logger;
import uep.util.Options;
import medieq.iet.api.IETApi;
import medieq.iet.api.IETException;
import medieq.iet.components.DataModelFactory;
import medieq.iet.components.DocumentReader;
import medieq.iet.generic.*;

class SAXTaskReader implements ContentHandler {
	protected Task task;
	protected String curElem=null;
	protected Task.Proc curProc=null;
	protected ProcParam curParam=null;
	protected StringBuffer buff=new StringBuffer(128);
	protected DocumentSet docSet=null;
	protected String base=null;
	protected String enc=null;
	protected boolean forceEnc=false;
	protected DocumentFormat docFmt=null;
	protected List<Classification> defClasses=null;
    private static Pattern urlPat=Pattern.compile("^(http|https|ftp)://");
    private static Map<String,Integer> modeNames=new HashMap<String,Integer>();
    private static final String MODE_CROSSVALIDATE_STR="cv";
    private static final String MODE_FEATURE_INDUCTION_STR="fi";
    static {
        modeNames.put("test", Task.MODE_TEST_INSTANCES);
        modeNames.put("instances", Task.MODE_TEST_INSTANCES);
        modeNames.put("attributes", Task.MODE_TEST_ATTRIBUTES);
        modeNames.put("train", Task.MODE_TRAIN);
        modeNames.put("dump", Task.MODE_DUMP);
        modeNames.put(MODE_CROSSVALIDATE_STR, Task.MODE_CROSSVALIDATE);
    }
    
    public SAXTaskReader(Task emptyTask) {
        this.task=emptyTask;
        defClasses=new ArrayList<Classification>(4);
    }
    
	public Task getTask() {
		return task;
	}
	
	public int readURLs(DocumentSet docSet, StringBuffer buff) {
		int cnt=0;
		String[] urls=buff.toString().split("\n");
        String baseUrlStr=null;
        if(base!=null && base.length()>0) {
            if(isUrl(base)) {
                baseUrlStr=fixUrl(base);
                if(!baseUrlStr.endsWith("/") && !baseUrlStr.endsWith("\\"))
                    baseUrlStr+='/';
            }else {
                File abs=new File(base);
                baseUrlStr=fixUrl(abs.getAbsolutePath()+"/"+abs.getName());
            }
        }
		for(int i=0;i<urls.length;i++) {
			String url=fixUrl(urls[i].trim());
			if(url.length()>0) {
				url=preprocessUrl(baseUrlStr, url);
			    
				String localFile=null;
				if(!urlPat.matcher(url).find()) {
				    localFile=url;
				    url=null;
				}
				
		        Document doc=new DocumentImpl(url, localFile);
                doc.setEncoding(enc);
                doc.setForceEncoding(forceEnc);
                if(docFmt==null)
                    docFmt=DocumentFormat.getDefaultFormat();
                doc.setContentType(docFmt.contentType);
                if(defClasses.size()>0 && doc.getClassifications().size()==0) {
                    doc.getClassifications().addAll(defClasses);
                }
				docSet.getDocuments().add(doc);
				cnt++;
			}
		}
		return cnt;
	}
	
    private String preprocessUrl(String baseUrlStr, String inUrl) {
        // some documents are specified using multiple files; these would be separated using | on a single line
        String[] urls=inUrl.trim().split("\\s*\\|\\s*");
        StringBuffer url=new StringBuffer(128);
        for(int i=0;i<urls.length;i++) {
            String u=urls[i];
            if(base!=null && base.length()>0) {
                try {
                    URL baseUrl=new URL(baseUrlStr);
                    URL abs=new URL(baseUrl,u);
                    u=abs.toString();
                    u=fixUrl(u);
                }catch(MalformedURLException mex) {
                    Logger.LOG(Logger.ERR,"Error resolving document url="+u+" ("+inUrl+") in context "+base+"("+baseUrlStr+"): "+mex);
                }
            }
            if(u.startsWith("file:///")) {
                u=u.substring(8);
            }
            if(i>0) {
                url.append(" | ");
            }
            url.append(u);
        }
        return url.toString();
    }

    public void startDocument() {
    	// task=new Task(null);
    }
    
	public void characters(char[] ch, int start, int length) {
		buff.append(ch,start,length);
	}
	
	public void endElement(String uri, String localName, String qName) {
		if(curElem==null)
			return;
		if(curElem.equals("set")) {
			readURLs(docSet, buff);
			if(docSet.getDocuments().size()>0) {
				task.getDocuments().add(docSet);
			}
			docSet=null;
			base=null;
		}else if(curElem.equals("desc")) {
			task.setDesc(buff.toString());
        }else if(curElem.equals("mode")) {
            String ms=buff.toString().trim().toLowerCase();
            Integer mi=modeNames.get(ms);
            if(mi==null) {
                if(ms.startsWith(MODE_CROSSVALIDATE_STR)) {
                    Matcher m=Pattern.compile("\\d+").matcher(ms);
                    int p=MODE_CROSSVALIDATE_STR.length();
                    int round=0;
                    while(m.find(p)) {
                        if(round==0) {
                            task.foldCount=Integer.parseInt(m.group());
                            int j=ms.indexOf(MODE_FEATURE_INDUCTION_STR, m.end());
                            if(j!=-1) {
                                task.induceFeatures=1;
                                p=j+MODE_FEATURE_INDUCTION_STR.length();
                            }else {
                                p=m.end();
                            }
                        }else if(round==1) {
                            task.heldoutFoldCount=Integer.parseInt(m.group());
                            p=m.end();
                        }
                        round++;
                    }
                    if(round<1 || round>2 || task.foldCount<2 || task.heldoutFoldCount<0 || task.heldoutFoldCount>task.foldCount-2) {
                        throw new IllegalArgumentException("Error reading cross-validation mode: "+ms+" Expected cv-folds-heldout, e.g. cv-10-1");
                    }
                    mi=Task.MODE_CROSSVALIDATE;
                }
                if(mi==null) {
                    String msg="Unknown task mode="+ms;
                    Logger.LOGERR(msg);
                    throw new IllegalArgumentException(msg);
                }
            }
            task.setMode(mi);
		}else if(curElem.equals("datamodel")) {
			String modelUrl=buff.toString().trim();
			if(modelUrl.length()>0) {
			    if(task.getModel()!=null) {
			        Logger.LOGERR("TaskFactory error reading "+task.getFile()+": datamodel "+modelUrl+" overwrites previous "+task.getModel());
			    }
			    try {
			        task.setModel(DataModelFactory.readDataModelFromFile(modelUrl));
			    }catch(IETException e) {
			        Logger.LOGERR("TaskFactory error reading datamodel "+modelUrl+" from "+task.getFile()+"; using empty model");
			        task.setModel(DataModelFactory.createEmptyDataModel(modelUrl));
			    }
				task.setModelUrl(modelUrl);
			}
		}else if(curElem.equals("lastRun")) {
			if(buff.length()>0) {
				try {
					Date d=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).parse(buff.toString().trim());
					task.setLastRun(d);
				}catch(ParseException ex) {
					System.err.println("Error reading date "+buff.toString()+": "+ex);
				}
			}
		}else if(curElem.equals("proc") || curElem.equals("procedure")) {
		    curProc=null;
		}else if(curElem.equals("param")){
		    curParam.value=buff.toString().trim();
		    if(curProc!=null) {
		        curProc.setParam(curParam.name, curParam.value);
		    }
            // store globally as well for other components that do not see the task
            Options.getOptionsInstance().setProperty(curParam.name, curParam.value);
		    curParam=null;
		}else if(curElem.equalsIgnoreCase("tempDir")) {
		    task.setTempDir(buff.toString().trim());
		}else if(curElem.equalsIgnoreCase("outDir")) {
            task.setOutDir(buff.toString().trim());
        }
		curElem=null;
		buff.setLength(0);
	}
	
    public void startElement(String uri, String localName, String qName, Attributes atts) {
    	curElem=qName;
    	if(curElem.equals("task")) {
    		task.setName(atts.getValue("name"));
    	}else if(curElem.equals("set")) {
    		String setName=atts.getValue("name");
    		docSet=new DocumentSetImpl(setName);
    		base=atts.getValue("basedir");
    		if(base!=null)
    		    base=base.trim();
            forceEnc=false;
    		enc=atts.getValue("forceEncoding");
    		if(enc==null)
    		    enc=atts.getValue("forceencoding");
    		if(enc==null)
                enc=atts.getValue("forceEnc");
    		if(enc==null)
                enc=atts.getValue("forceenc");
    		if(enc!=null) {
                enc=enc.trim();
                forceEnc=true;
    		}else if((enc=atts.getValue("encoding"))!=null) {
    		    enc=enc.trim();
    		}
    		String fmtName=atts.getValue("type");
    		docFmt=(fmtName!=null)? DocumentFormat.formatForName(fmtName) :DocumentFormat.getDefaultFormat();
    		readDocumentClassifications(defClasses, atts.getValue("class"));
    	}else if(curElem.equals("pipeline")) {
    	    try {
    	        task.setPipelineUnit(atts.getValue("mode"));
    	    }catch (IETException ex) {
    	        Logger.LOGERR(ex.toString());
    	    }
        }else if(curElem.equals("proc") || curElem.equals("procedure")) {
            curProc=task.addNewProc(atts.getValue("engine").trim());
        }else if(curElem.equals("param")) {
            curParam=new ProcParam(atts.getValue("name"), null);
        }
    	buff.setLength(0);
    }
    
    private int readDocumentClassifications(List<Classification> lst, String value) {
        lst.clear();
        if(value==null)
            return 0;
        String[] items=value.trim().split("\\s*[;,]\\s*");
        int cc=0;
        for(String it: items) {
            String[] pair=it.split("\\s*:\\s*");
            if(pair.length!=2)
                throw new IllegalArgumentException("Expected document class list like \"cls1:70,cls2:20\", got: "+value);
            double conf=Double.parseDouble(pair[1]);
            if(conf>1 && conf<=100)
                conf/=100;
            lst.add(new ClassificationImpl(pair[0], conf));
            cc++;
        }
        return cc;
    }

    Pattern protoPat=Pattern.compile("^\\s*\\w+://");
    protected boolean isUrl(String url) {
        return protoPat.matcher(url).find();
    }
    
	protected String fixUrl(String url) {
		url=url.trim();
		if(url.length()>=3 && Character.isLetter(url.charAt(0)) && 
		   url.charAt(1)==':' && (url.charAt(2)=='/'||url.charAt(2)=='\\')) {  
			url="file:///"+url;
		}
        else if(url.startsWith("file:/") && url.charAt(6)!='/') {
            url="file:///"+url.substring(6);
        }
		return url;
	}

    /* unused */
	public void endDocument() {
		;
	}
    public void endPrefixMapping(String prefix) {
		;
	}
	public void ignorableWhitespace(char[] ch, int start, int length) {
		;
	}
    public void processingInstruction(String target, String data) {
		;
	} 
    public void setDocumentLocator(Locator locator) {
		;
	} 
    public void skippedEntity(String name) {
    	;
    } 
    public void startPrefixMapping(String prefix, String uri) {
    	;
    }
}

public class TaskFactory {
    protected DocumentReader docReader;
    protected IETApi iet;
    
    public TaskFactory(IETApi iet) {
        this.iet=iet;
        docReader=null;
    }
    
	public Task readTask(String file) throws IETException {
		XMLReader reader=null;
		SAXParserFactory spf = SAXParserFactory.newInstance();
        Exception e=null;
        String msg=null;
        try {
			SAXParser sp = spf.newSAXParser();
			reader = sp.getXMLReader();
		}catch(SAXException sex) {
            e=sex;
            msg="Cannot instantiate SAX parser: "+sex;
		}catch(ParserConfigurationException cex) {
            e=cex;
            msg="Cannot read task "+file+": "+cex;
		}
        if(reader==null) {
            Logger.LOG(Logger.ERR,msg);
            throw new IETException(msg,e);
        }
		SAXTaskReader handler = new SAXTaskReader(new Task(null, iet));
		reader.setContentHandler(handler);
		reader.setErrorHandler(null);
		reader.setEntityResolver(null);
		reader.setDTDHandler(null);
		
		InputSource source = new InputSource(file);
		Task task=null;
		try {
			reader.parse(source);
			task=handler.getTask();
			Iterator<DocumentSet> dsit=task.documents.iterator();
			while(dsit.hasNext()) {
			    DocumentSet ds=dsit.next();
			    Iterator<Document> dit=ds.getDocuments().iterator();
			    while(dit.hasNext()) {
			        Document doc=dit.next();
			        if(doc.getFile()!=null)
			            doc=readDocument(doc, task.getModel());
			    }
			}
		}catch(SAXException sex) {
            e=sex;
			msg="Error parsing task "+file+": "+sex;
		}catch(IOException ex) {
            e=ex;
			msg="Cannot read task "+file+": "+ex;
		}
        if(task==null) {
            Logger.LOG(Logger.ERR,msg);
            throw new IETException(msg,e);
        }
		return task;
	}

	/** throws exception on failure */
	public void writeTask(Task task, BufferedWriter buffWriter) throws IOException {
	    PrintWriter writer=new PrintWriter(buffWriter);
		writer.println("<?xml version=\"1.0\"?>"); // no encoding specified -> utf8
		writer.println("<task name=\""+escape(task.getName(),true)+"\">");
		writer.print("<desc>");
		writer.print(escape(task.getDesc(),false));
		writer.println("</desc>");
		writer.print("<model>");
		writer.print(escape(task.getModelUrl(),false));
		writer.println("</model>");
		writer.print("<lastRun>");
		writer.print(escape(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(task.getLastRun()),false));
		writer.println("</lastRun>");
        Iterator<Task.Proc> pit=task.procedures.iterator();
        while(pit.hasNext()) {
            Task.Proc p=pit.next();
            writer.println(p.toXML());
		}
		for(int i=0;i<task.getDocuments().size();i++) {
			DocumentSet docSet=task.getDocuments().get(i);
			writer.println("<set name=\""+docSet.getName()+"\" tmpdir=\""+task.tmpDir+"\" outdir=\""+task.outDir+"\">");
			for(int j=0;j<docSet.getDocuments().size();j++) {
				String url=docSet.getDocuments().get(j).getFile();
				if(url.startsWith("file:///"))
					url=url.substring(8);
				else if(url.startsWith("file:/"))
					url=url.substring(6);
				writer.println(url);
			}
			writer.println("</set>");
			writer.println();
		}
		writer.println("</task>");
		writer.flush();
		writer.close();
	}
	
	protected String escape(String src, boolean isAttr) {
		if(src==null)
			return "";
		String s=src;
		s=s.replaceAll("&","&amp;");
		s=s.replaceAll("<","&lt;");
		s=s.replaceAll(">","&gt;");
		if(isAttr)
			s=s.replaceAll("\"","&quot;");
		return s; 
	}
    
    public Task createTask(String name, 
                           List<Map<String, Object>> inputDocumentList,
                           String dataModelUrl
                           ) throws IETException {
        Task tsk=new Task(name, iet);
        if(dataModelUrl!=null) {
            tsk.setModel(DataModelFactory.readDataModelFromFile(dataModelUrl));
            tsk.setModelUrl(dataModelUrl);
        }else {
            tsk.setModel(DataModelFactory.createEmptyDataModel("datamodel-"+name));
        }
        Iterator<Map<String, Object>> dit=inputDocumentList.iterator();
        while(dit.hasNext()) {
            Map<String, Object> obj=dit.next();
            Document doc=createDocument(obj, tsk.getModel());
            if(doc==null) {
                continue;
            }
            tsk.addDocument(doc);
        }
        return tsk;
    }
    
    public Document createDocument(Map<String, Object> obj, DataModel model) {
        if(!obj.containsKey("FileName") || !obj.containsKey("FileLocation")) {
            Logger.LOG(Logger.ERR, "FileName or FileLocation not specified for object");
            return null;
        }
        String dir=(String) obj.get("FileLocation");
        if(dir!=null) {
            dir=dir.trim();
            if(dir.length()==0)
                dir=null;
        }
        String absFileName=(dir!=null)? (dir+"/"+(String)obj.get("FileName")): (String)obj.get("FileName");
        String url=(String) obj.get("WebUri");
        String id=(String) obj.get("FileID");
        String cls=(String) obj.get("FileContentType");
        String enc=(String) obj.get("Encoding");
        if(enc==null || (enc=enc.trim()).length()==0)
            enc="utf-8";

        Document doc=new DocumentImpl(url, absFileName);
        doc.setEncoding(enc);
        doc=readDocument(doc, model);
        if(id!=null)
            doc.setId(id);
        if(cls!=null) {
            // assume 1 value document classification with 100% confidence
            Classification c=new ClassificationImpl(cls, 1.0);
            doc.getClassifications().add(c);
        }
        return doc;
    }
    
    protected Document readDocument(Document doc, DataModel model) {
        Document d=null;
        String cls;
        try {
            if(docReader==null || !docReader.getFormat().contentType.equals(doc.getContentType())) {
                cls=DocumentFormat.formatForContentType(doc.getContentType()).documentReaderClassName;
                docReader=(DocumentReader) Class.forName(cls).newInstance();
            }
            d=docReader.readDocument(doc, model, null, false);
        }catch(IOException e) {
            Logger.LOG(Logger.WRN,"IET could not read document "+doc.getFile()+" using "+docReader+": "+e);
        } catch (InstantiationException e) {
            Logger.LOG(Logger.ERR,"IET could not create reader for "+doc.getFile()+", content type="+doc.getContentType()+", class name="+docReader+": "+e);
        } catch (IllegalAccessException e) {
            Logger.LOG(Logger.ERR,"IET has no permission to create reader for "+doc.getFile()+", content type="+doc.getContentType()+", class name="+docReader+": "+e);
        } catch (ClassNotFoundException e) {
            Logger.LOG(Logger.ERR,"Reader not found for "+doc.getFile()+", content type="+doc.getContentType()+", class name="+docReader+": "+e);
        }
        if(d!=null)
            doc=d;
        return doc;
    }
}
