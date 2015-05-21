// $Id: Transformer.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.components;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import medieq.iet.model.AnnotableObject;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.DataModel;
import medieq.iet.model.Document;
import medieq.iet.model.DocumentSet;
import medieq.iet.model.Instance;

class RenameFilter implements AnnotationFilter {
    String orig;
    String dest;
    
    public RenameFilter(String orig, String dest) {
        this.orig=orig;
        this.dest=dest;
    }
    
    public boolean matches(AnnotableObject obj) {
        String objName=obj.getName().toLowerCase();
        return objName.equals(orig);
    }
    
    public String toString() {
        return orig+":"+dest;
    }
}

/** Simple document processor that renames or removes attribute values as per configuration. */
public class Transformer implements Engine {
    Map<String,RenameFilter> nameMap;
    Properties params;
    public final static String parRename="rename";
    
    public Transformer() {
        nameMap=new TreeMap<String,RenameFilter>();
        params=new Properties();
    }
    
    /** @return serialized attribute name map. */
    public String getModel() {
        return getRenameMap();
    }

    /** Not supported. */
    public int loadModel(String modelFile) throws IOException {
        throw new UnsupportedOperationException();
    }

    /** {@inheritDoc} */
    public void configure(Properties newParams) {
        params.putAll(newParams);
    }

    /** {@inheritDoc} */
    public void configure(InputStream cfgFile) throws IOException {
        params.load(cfgFile);
    }

    /** {@inheritDoc} */
    public Object getParam(String name) {
        return params.get(name);
    }

    /** {@inheritDoc} */
    public void setParam(String name, Object value) {
        if(name.equals(parRename)) {
            setRenameMap((String)value);
        }else {
            throw new IllegalArgumentException(name+"="+value);
            // params.put(name, value);
        }
    }
    
    /** @return serialized attribute name map. */
    protected String getRenameMap() {
        StringBuffer s=new StringBuffer(128);
        for(Map.Entry<String, RenameFilter> en: nameMap.entrySet()) {
            s.append(en.getKey()+":"+en.getValue()+"\n");
        }
        return s.toString();
    }
    
    /** Parses and sets attribute renaming map in format " att1:att1renamed , att2:att2renamed, att3todelete:- " */
    protected void setRenameMap(String sereMap) {
        nameMap.clear();
        if(sereMap!=null) {
            sereMap=sereMap.trim();
            String[] pairs=sereMap.split("[\\s,;]+");
            for(int i=0;i<pairs.length;i++) {
                String pair=pairs[i];
                String[] ar=pair.split(":");
                if(ar.length!=2) {
                    throw new IllegalArgumentException(sereMap);
                }
                String key=ar[0].toLowerCase();
                String dest=(ar[1].length()>0 && !ar[1].equals("-"))? ar[1]: null; 
                nameMap.put(key, new RenameFilter(key, dest));
            }
        }
        params.put(parRename, getRenameMap());
    }

    /** {@inheritDoc} */
    public boolean initialize(String cfgFile) throws IOException {
        if(cfgFile!=null) {
            configure(new FileInputStream(new File(cfgFile)));
        }
        return true;
    }

    /** {@inheritDoc} */
    public void uninitialize() {
        ;
    }

    /** Fails. */
    public boolean cancel(int cancelType) {
        return false; // not supported
    }

    /** {@inheritDoc} */
    public String getName() {
        return "transformer";
    }

    public int extractAttributes(Document doc, DataModel model) {
        return transform(doc, model);
    }

    public int extractAttributes(DocumentSet docSet, DataModel model) {
        int rc=0;
        for(Document doc: docSet.getDocuments()) {
            rc+=transform(doc, model);
        }
        return rc;
    }

    /** Renames or deletes attribute values and/or instances. */
    protected int transform(Document doc, DataModel model) {
        int cnt=0;
        // instance members
        Iterator<Instance> iit=doc.getInstances().iterator();
        while(iit.hasNext()) {
            Instance inst=iit.next();
            RenameFilter rf=nameMap.get(inst.getName());
            if(rf!=null && rf.matches(inst)) {
                if(rf.dest==null) {
                    iit.remove();
                    cnt++;
                }else if(!rf.dest.equals(rf.orig)) {
                    inst.setClassName(rf.dest, model);
                    cnt++;
                }
            }
            Iterator<AttributeValue> ait=inst.getAttributes().iterator();
            while(ait.hasNext()) {
                cnt+=transformAV(ait, model);
            }
        }
        // standalone
        Iterator<AttributeValue> ait=doc.getAttributeValues().iterator();
        while(ait.hasNext()) {
            cnt+=transformAV(ait, model);
        }
        return cnt;
    }
    
    /** Transforms a single attribute value. */
    protected int transformAV(Iterator<AttributeValue> it, DataModel model) {
        int cnt=0;
        AttributeValue av=it.next();
        String attName=av.getName().toLowerCase();
        RenameFilter rf=nameMap.get(attName);
        if(rf==null) {
            int i=attName.indexOf('.');
            if(i!=-1) {
                attName=attName.substring(i+1);
                rf=nameMap.get(attName);
            }
        }
        if(rf!=null && rf.matches(av)) {
            if(rf.dest==null) {
                it.remove();
                cnt=1;
            }else if(!rf.dest.equals(rf.orig)) {
                av.setAttributeName(rf.dest, model);
                cnt=1;
            }
        }
        return cnt;
    }
}
