// $Id: Postprocessor.java 1933 2009-04-12 09:14:52Z labsky $
package ex.dom;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import uep.util.Logger;
import uep.util.Options;
import ex.ac.AC;
import ex.api.Ex;
import ex.model.Model;
import ex.parser.ICBase;
import ex.parser.Parser;
import ex.reader.Document;
import ex.util.search.Path;
import medieq.iet.generic.AnnotationImpl;
import medieq.iet.generic.AttributeValueImpl;
import medieq.iet.model.AnnotableObject;
import medieq.iet.model.Annotation;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.DataModel;

public class Postprocessor {
    protected static Logger log;
    protected Options o;
    private ArrayList<AC> acs;
    private ArrayList<ICBase> ics;
    
    public Postprocessor() {
        if(log==null)
            log=Logger.getLogger("ppc");
        o=Options.getOptionsInstance();
        acs=new ArrayList<AC>(16);
        ics=new ArrayList<ICBase>(16);
    }
    
    /** Updates DOM of the ex document. Adds n-best paths of extracted objects to the 
     *  document. Does not write to ietDoc, extracted post-processed objects will be 
     *  copied to it by IETApi. 
     *  IETApi will have an easier job since it will only copy the references to
     *  already existing IET objects. Translation from ACs and ICs to IET objects is 
     *  now done here. */
    public void updateDocuments(Model exModel, DataModel ietModel, 
                   Document exDoc, medieq.iet.model.Document ietDoc,
                   List<Path> paths) {
        addExtractedValues(exDoc, paths, ietModel, ietDoc);
        postprocess(exModel);
        copyExtractedValues(exDoc, ietDoc);
        fillAnnotatedSource(ietDoc, exDoc, paths);
        dumpAnnotatedSource(ietDoc);
    }
    
    /** Copies post-processed extracted objects  from ex document to IET document. */
    private void copyExtractedValues(Document exDoc, medieq.iet.model.Document ietDoc) {
        if(exDoc.extractedPaths==null || exDoc.extractedPaths.size()==0)
            return;
        ExtractedObjects bestSeq=exDoc.extractedPaths.get(0);
        for(AnnotableObject o: bestSeq) {
            if(o instanceof medieq.iet.model.Instance) {
                ietDoc.getInstances().add((medieq.iet.model.Instance) o);
            }else if(o instanceof medieq.iet.model.AttributeValue) {
                ietDoc.getAttributeValues().add((medieq.iet.model.AttributeValue) o);
            }else {
                log.LG(Logger.WRN, "Ignored unknown AnnotableObject: "+o);
            }
        }
    }

    /** Adds all extracted items from each path in n-best as IET objects
     *  to the source Ex document. Uses IET DataModel and Document to 
     *  find existing IET AttributeDefs and ClassDefs, and to register newly 
     *  created ones. */
    private void addExtractedValues(Document exDoc, 
                                    List<Path> paths,
                                    medieq.iet.model.DataModel ietModel,
                                    medieq.iet.model.Document ietDoc) {
        exDoc.extractedPaths.clear();
        for(int i=0;i<paths.size();i++) {
            Path p=paths.get(i);
            ExtractedObjects objs=new ExtractedObjects(ietModel);
            addExtractedValues(objs, p, ietModel, ietDoc);
            exDoc.extractedPaths.add(objs);
        }
    }

    private void addExtractedValues(ExtractedObjects objs, 
                                    Path path,
                                    DataModel ietModel, 
                                    medieq.iet.model.Document ietDoc) {
        Parser.addPathComponents(path, ics, acs);
        // add standalone ACs
        for(AC ac: acs) {
            medieq.iet.model.AttributeValue av=ac2av(ac, ietDoc, ietModel);
            objs.add(av);
        }
        acs.clear();
        // add instances
        for(ICBase ic: ics) {
            medieq.iet.model.ClassDef clsDef=getClassDef(ic.clsDef, ietDoc, ietModel);
            medieq.iet.model.Instance inst=ic2inst(ic, ietDoc, ietModel);
            objs.add(inst);
            if(Parser.MIXED_MODE==Parser.MIXED_ALLOW) {
                // also include standalone attributes embedded in instances (orphans)
                ic.getACs(acs, ICBase.ACS_ORPHANS);
                for(AC orphan: acs) {
                    medieq.iet.model.AttributeValue av=ac2av(orphan, ietDoc, ietModel);
                    objs.add(av);
                }
            }
        }
    }

    /** Applies post-processing rules defined by the used extraction ontology 
     *  to the ex document's DOM. Changes content of n-best paths of extracted 
     *  that have been added to the DOM by updateDOM. */
    public void postprocess(Model exModel) {
        exModel.postprocess();
    }

//    /** add extracted attributes and instances to IET document. */
//    protected void addExtractedValues(medieq.iet.model.Document ietDoc,
//                                      medieq.iet.model.DataModel ietModel,
//                                      List<Path> paths) {
//        if(paths.size()>0) {
//            Path bp=paths.get(0);
//            if(bp.length()>2) {
//                ArrayList<ICBase> ics=new ArrayList<ICBase>(bp.length()-2);
//                ArrayList<AC> acs=new ArrayList<AC>(bp.length()-2);
//                Parser.addPathComponents(bp, ics, acs);
//                for(int i=0;i<ics.size();i++) {
//                    ICBase ic=(ICBase) ics.get(i);
//                    medieq.iet.model.ClassDef clsDef=getClassDef(ic.clsDef, ietDoc, ietModel);
//                    medieq.iet.model.Instance inst=ic2inst(ic, ietDoc, ietModel);
//                    ietDoc.getInstances().add(inst);
//                    // also include orphans
//                    ArrayList<AC> orphans=new ArrayList<AC>(8);
//                    ic.getACs(orphans, ICBase.ACS_ORPHANS);
//                    for(AC orphan: orphans) {
//                        medieq.iet.model.AttributeValue av=ac2av(orphan, ietDoc, ietModel);
//                        ietDoc.getAttributeValues().add(av);
//                    }
//                }
//                for(int i=0;i<acs.size();i++) {
//                    medieq.iet.model.AttributeValue av=ac2av(acs.get(i), ietDoc, ietModel);
//                    ietDoc.getAttributeValues().add(av);
//                }
//            }
//        }
//    }
    
    protected medieq.iet.model.Instance ic2inst(ICBase ic, 
                                                medieq.iet.model.Document ietDoc,
                                                medieq.iet.model.DataModel ietModel) {
        medieq.iet.model.ClassDef clsDef=getClassDef(ic.clsDef, ietDoc, ietModel);
        medieq.iet.generic.InstanceImpl inst=new medieq.iet.generic.InstanceImpl(clsDef);
        inst.setAuthor(Ex.engineName);
        ArrayList<AC> acs=new ArrayList<AC>(ic.getACCount());
        ic.getACs(acs, ICBase.ACS_MEMBERS | ICBase.ACS_REF | ICBase.ACS_NONREF);
        ArrayList<AC> refs=new ArrayList<AC>(ic.getACCount());
        if(ic.getACCount()>1)
            ic.getACs(refs, ICBase.ACS_MEMBERS | ICBase.ACS_REF);
        for(int i=0;i<acs.size();i++) {
            AC ac=acs.get(i);
            AttributeValueImpl av=ac2av(ac, ietDoc, ietModel);
            if(refs.contains(ac))
                av.setText("ref: "+av.getText());
            av.setInstance(inst);
            inst.getAttributes().add(av);
        }
        // Math.exp(ic.score/ic.attCount())
        inst.setScore(ic.combinedProb);
        return inst;
    }

    protected AttributeValueImpl ac2av(AC ac, 
                                       medieq.iet.model.Document ietDoc,
                                       medieq.iet.model.DataModel ietModel) {
        medieq.iet.model.AttributeDef attDef=getAttDef(null, ac.getAttribute(), ietDoc, ietModel);
        int startCharPos=ac.getStartToken().startIdx;
        int endCharPos=ac.getEndToken().endIdx;
        int charLen=endCharPos-startCharPos; // endCharPos is the index of the char behind the last char of this annot
        String annText=(ietDoc.getSource()==null)? null:
            ietDoc.getSource().substring(startCharPos, endCharPos);
        Annotation ann=new AnnotationImpl(startCharPos, charLen, annText, Ex.engineName);
        if(Logger.getLogger("ACFinder").IFLG(Logger.INF)) {
            ann.addDebugInfo(ac.toStringIncEvidence(1));
        }
        ArrayList<Annotation> annList=new ArrayList<Annotation>();
        annList.add(ann);
        medieq.iet.model.Instance inst=null;
        AttributeValueImpl av=new AttributeValueImpl(attDef, ac.getText(), ac.getProb(), inst, annList);
        av.setScore(ac.getProb());
        if(log.IFLG(Logger.INF)) log.LG(Logger.INF, "ac2av av="+ac.getText()+" an="+annText+" p_av="+ac.getProb());
        return av;
    }
    
    protected medieq.iet.model.ClassDef getClassDef(ex.model.ClassDef exClsDef,
                                                    medieq.iet.model.Document ietDoc,
                                                    medieq.iet.model.DataModel ietModel) {
        medieq.iet.model.ClassDef clsDef=ietModel.getClass(exClsDef.name);
        if(clsDef!=null)
            return clsDef;
        // prepare involved AttributeDefs, make a list of AttributeClassLinks
        clsDef=new medieq.iet.generic.ClassDefImpl(exClsDef.name, null);
        List<medieq.iet.model.AttributeClassLink> attLinkList=new ArrayList<medieq.iet.model.AttributeClassLink>(16);
        for(int i=0;i<exClsDef.attArray.length;i++) {
            ex.model.AttributeDef exAttDef=exClsDef.attArray[i];
            medieq.iet.model.AttributeDef attDef=getAttDef(null, exAttDef, ietDoc, ietModel);
            // attList.add(exAttDef);
            medieq.iet.model.AttributeClassLink link=new medieq.iet.generic.AttributeClassLinkImpl(
                    attDef, clsDef, exAttDef.minCard, exAttDef.maxCard );
            attLinkList.add(link);
        }
        clsDef.getAttributeLinks().addAll(attLinkList);
        ietModel.addClass(clsDef);
        return clsDef;
    }

    protected medieq.iet.model.AttributeDef getAttDef(medieq.iet.model.ClassDef clsDef, 
                                                      ex.model.AttributeDef exAttDef,
                                                      medieq.iet.model.Document ietDoc,
                                                      medieq.iet.model.DataModel ietModel) {
        String fullAttName=exAttDef.name;
        if(clsDef!=null) {
            fullAttName=clsDef.getName()+"."+exAttDef.name;
        }
        // look in datamodel
        medieq.iet.model.AttributeDef def=ietModel.getAttribute(fullAttName);
        if(def==null)
            def=ietModel.getAttribute(exAttDef.name);
        // look in document
        if(def==null) {
            for(AttributeValue av: ietDoc.getAttributeValues()) {
                if(av.getAttributeDef().getName().equals(fullAttName) || av.getAttributeDef().getName().equals(exAttDef.name)) {
                    def=av.getAttributeDef();
                    break;
                }
            }
        }
        // have to create
        if(def==null) {
            def=new medieq.iet.generic.AttributeDefImpl(exAttDef.name, ex.model.AttributeDef.dataTypes[exAttDef.dataType]);
        }
        ietModel.addAttribute(def);
        return def;
    }

    private void fillAnnotatedSource(medieq.iet.model.Document doc, Document exDoc, List<Path> paths) {
        int nbestShow=o.getIntDefault("parser_nbest_show",1);
        nbestShow=Math.min(nbestShow, paths.size());
        String[] instanceTables=new String[nbestShow==0? 1: nbestShow];
        instanceTables[0]="<div>No instances found.</div>";
        for(int i=0;i<paths.size();i++)
            instanceTables[i]=Parser.pathToTable(paths.get(i));

        // annotate found patterns in document's source
        boolean live=true;
        // only annotate ACs found in the nbest paths:
        HashSet<AC> acsToAnnotate=new HashSet<AC>();
        // Collection<AC> coll=acsToAnnotate;
        HashSet<ICBase> icsToAnnotate=new HashSet<ICBase>();
        for(int i=0;i<paths.size();i++) {
            Path p=paths.get(i);
            Parser.addPathComponents(p, icsToAnnotate, acsToAnnotate);
            Iterator<ICBase> icit=icsToAnnotate.iterator();
            while(icit.hasNext()) {
                ICBase ic=(ICBase) icit.next();
                ic.getACs(acsToAnnotate, ICBase.ACS_ALL);
            }
        }
        // don't show patterns, show atts, use live labels
        String labeled=exDoc.getAnnotatedDocument(false,true,"utf-8",live,acsToAnnotate);
        log.LG(Logger.INF,"Annotated "+exDoc.labelCnt+" patterns/attributes.");
        if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Document:\n"+labeled);

        // add annotated doc source
        StringBuffer buff=new StringBuffer(doc.getSize()*2);
        String acTable=exDoc.getACTable();
        buff.append(acTable);
        buff.append("\n");
        for(int i=0;i<instanceTables.length && i<nbestShow;i++) {
            buff.append(instanceTables[i]);
            buff.append("\n");
        }
        buff.append(labeled);
        String annSource=buff.toString();
        doc.setAnnotatedSource(annSource);
    }

    private void dumpAnnotatedSource(medieq.iet.model.Document doc) {
        // output in utf-8 to file (for now put it next to the orig document)
        String fn=doc.getFile();
        String[] parts=fn.split("\\|");
        fn = parts[0].trim()+".lab.html";
        if(fn.startsWith("file:///"))
            fn=fn.substring(8);
        else if(fn.startsWith("file:/"))
            fn=fn.substring(6);
        File labFile=new File(fn);
        OutputStream os=System.out;
        try {
            os=new FileOutputStream(labFile);
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Cannot write to "+labFile);
        }
        PrintStream ps=null;
        try {
            ps = new PrintStream(os, true, "utf-8");
        }catch(IOException ex) {
            log.LG(Logger.ERR,"Cannot utf-8 encode");
            return;
        }
        ps.print(doc.getAnnotatedSource());
        ps.close();
    }
}
