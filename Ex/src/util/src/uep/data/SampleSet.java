// $Id: $
package uep.data;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import uep.util.Logger;

public class SampleSet implements Iterable<Sample>, AddableDataSet {
    protected String name;
    protected ArrayList<SampleFeature> features;
    protected int classIdx;
    protected ArrayList<Sample> samples;
    protected boolean weighted; /** Whether instances are weighted. For ARFF IO, weight is always the first attribute. */
    protected Map<Sample,Sample> uniqSet;

    public SampleSet(String name, boolean weighted) {
        this.name=(name==null)? "noname": name;
        this.weighted=weighted;
        this.features=new ArrayList<SampleFeature>(16);
        this.classIdx=0; // by default, the first feature is the class feature
        this.samples=new ArrayList<Sample>(4);
        if(weighted) {
            uniqSet=new TreeMap<Sample,Sample>();
        }else {
            uniqSet=null;
        }
    }
    
    static Pattern patArffRel=Pattern.compile("^@relation\\s+([^\\s]+)$",Pattern.CASE_INSENSITIVE);
    static Pattern patArffAtt=Pattern.compile("^@attribute\\s+([^\\s]+)\\s+(.+)$",Pattern.CASE_INSENSITIVE);
    static Pattern patArffData=Pattern.compile("^@data$",Pattern.CASE_INSENSITIVE);

    public void clear() {
        if(uniqSet!=null) {
            uniqSet.clear();
        }
        samples.clear();
        features.clear();
        classIdx=0;
        // keep name and weighted
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name=name;
    }
    
    /** @return number of samples. */
    public int size() {
        return samples.size();
    }
    
    public int getClassIdx() {
        return classIdx;
    }
    
    public boolean getWeighted() {
        return weighted;
    }

    public void setWeighted(boolean weighted) {
        this.weighted=weighted;
    }

    /** Sets the class feature index (0-based). */
    public void setClassIdx(int classFeatureIdx) {
        classIdx=classFeatureIdx;
    }
    
    /** @return SampleFeature at the specified 0-based index. */
    public SampleFeature getFeature(int idx) {
        return features.get(idx);
    }

    /** Adds a SampleFeature at the end of the feature list. */
    public void addFeature(SampleFeature f) {
        features.add(f);
    }

    /** @return Constant list of SampleFeatures with 0-based index. */
    public List<SampleFeature> getFeatures() {
        return Collections.unmodifiableList(features);
    }
    
    /** Adds a sample. If the dataset is unique and the same sample already exists in it,
     * only its weight is increased by the weight of the added sample. In this case false
     * is returned, true otherwise. 
     * This method is not thread-safe. */
    public boolean addSample(Sample smp) {
        Sample old=(uniqSet!=null)? uniqSet.get(smp): null;
        if(old!=null) {
            old.incWeight(smp.getWeight());
        }else {
            Sample copy;
            try {
                copy=smp.clone();
            }catch(CloneNotSupportedException e) {
                throw new IllegalArgumentException("Can't clone sample: "+e.toString());
            }
            samples.add(copy);
            if(uniqSet!=null) {
                uniqSet.put(copy, copy);
            }
        }
        return old==null;
    }
    
    /** Adds all samples of set to this set by casting set to SampleSet and 
     *  by invoking addSample() repeatedly. */
    public void addAll(AddableDataSet dataSet) {
        SampleSet sset=(SampleSet) dataSet;
        for(Sample s: sset) {
            addSample(s);
        }
    }
    
    /** @return Constant list of Samples with 0-based index. */
    public List<Sample> getSamples() {
        return Collections.unmodifiableList(samples);
    }
    
    /** Sets the feature list of this dataset. */
    public void setFeatures(Collection<SampleFeature> feats) {
        features.clear();
        features.addAll(feats);
    }
    
    /** Reads a weka arff dataset from file. 
     * @return SampleSet */
    public static SampleSet readArff(String file, boolean headerOnly, boolean uniq) throws IOException {
        SampleSet set=new SampleSet(file, uniq);
        BufferedReader in=new BufferedReader(new InputStreamReader(
                ((file!=null)? new FileInputStream(new File(file)): System.in),
                "utf-8"));
        Map<String,Sample> counts=null;
        if(uniq) {
            counts=new HashMap<String,Sample>();
        }
        String s;
        int lno=0;
        boolean inData=false;
        int sc=0;
        while((s=in.readLine())!=null) {
            lno++;
            s=s.trim();
            if(s.length()==0 || s.startsWith("%"))
                continue;
            if(!inData) {
                Matcher m;
                if((m=patArffRel.matcher(s)).find()) {
                    set.name=m.group(1);
                }else if((m=patArffAtt.matcher(s)).find()) {
                    SampleFeature sf=new SampleFeature(null,SampleFeature.DT_ENUM,null);
                    sf.setName(m.group(1));
                    if(sf.getName().equals("class"))
                        set.classIdx=set.features.size();
                    String type=m.group(2);
                    if(type.equalsIgnoreCase("real")) {
                        sf.setType(SampleFeature.DT_INT);
                    }else if(type.equalsIgnoreCase("string")) {
                        sf.setType(SampleFeature.DT_STRING);
                    }else if(type.startsWith("{") && type.endsWith("}")) {
                        sf.setType(SampleFeature.DT_ENUM);
                        sf.setValues(type.substring(1,type.length()-1).trim().split("\\s*,\\s*"));
                    }else {
                        Logger.LOGERR(file+":"+lno+": Unknown feature datatype "+type+": "+s);
                    }
                    set.features.add(sf);
                }else if((m=patArffData.matcher(s)).find()) {
                    inData=true;
                    if(set.features.size()==0)
                        throw new IOException(file+":"+lno+": No @attributes found.");
                }else {
                    Logger.LOGERR(file+":"+lno+": Error reading line: "+s);
                }
            }else {
                if(headerOnly)
                    break;
                String[] vals=s.split("\\s*,\\s*");
                if(vals.length!=set.features.size())
                    throw new IOException(file+":"+lno+": Sample has "+vals.length+" features, expected "+set.features.size());
                sc++;
                Sample smp=new SampleImpl();
                for(int i=0;i<vals.length;i++) {
                    smp.setFeatureValue(i+1, vals[i]);
                }
                if(uniq) {
                    set.addSample(smp);
                }else {
                    set.samples.add(smp);
                }
            }
        }
        System.err.print("Read "+set.features.size()+" features, "+sc+" samples, "+(uniq?(set.samples.size()+" uniq"):"")+"\n");
        in.close();
        return set;
    }

    /** Writes all features of a dataset to out. */
    protected void writeArffFeatures(BufferedWriter out) throws IOException {
        if(weighted)
            out.write("@attribute instance_weight REAL\n");
        for(int i=0;i<features.size();i++) {
            SampleFeature f=features.get(i);
            out.write("@attribute "+f.getName()+" ");
            String type;
            switch(f.getType()) {
            case SampleFeature.DT_ENUM:
                StringBuffer b=new StringBuffer(32);
                b.append("{");
                for(int j=0;j<f.getValues().length;j++) {
                    if(j>0)
                        b.append(",");
                    b.append(f.getValues()[j]);
                }
                b.append("}");
                type=b.toString();
                break;
            case SampleFeature.DT_INT:
            case SampleFeature.DT_FLOAT:
                type="REAL";
                break;
            default:
                type=f.type2string();
            }
            out.write(type+"\n");
        }
    }

    /** Writes all samples of a dataset to out in arff format. */
    protected void writeArffData(BufferedWriter out) throws IOException {
        for(int i=0;i<samples.size();i++) {
            Sample smp=samples.get(i);
            if(weighted) {
                out.write(smp.getWeight());
                if(smp.getEnabledFeatureCount()>0)
                    out.write(",");
            }
            for(int j=0;j<features.size();j++) {
                if(j>0)
                    out.write(",");
                String val=smp.getFeatureValue(j);
                if(val==null)
                    val="0";
                out.write(val);
            }
            out.write("\n");
        }
    }
    
    /** Writes a dataset to an arff file. */
    public void writeArff(String file) throws IOException {
        BufferedWriter out=new BufferedWriter(new OutputStreamWriter(
                ((file!=null)? new FileOutputStream(new File(file)): System.out),
                "utf-8"));
        out.write("@relation "+((name.length()==0)?"noname":name)+"\n\n");
        writeArffFeatures(out);
        out.write("\n@data\n");
        writeArffData(out);
        out.close();
    }
    
    /** Concatenates multiple arff files given in fileList, one file per line. The resulting 
     * dataset, with a concatenated data section, is written to outFile. */
    public static int catArff(BufferedReader fileList, String outFile) throws IOException {
        BufferedWriter out=new BufferedWriter(new OutputStreamWriter(
                ((outFile!=null)? new FileOutputStream(new File(outFile)): System.out), 
                "utf-8"));
        SampleSet firstSet=null;
        String file;
        int lno=0;
        int rc=0;
        while((file=fileList.readLine())!=null) {
            lno++;
            file=file.trim();
            if(file.length()==0 || file.startsWith("%") || file.startsWith("#"))
                continue;
            SampleSet set=readArff(file, true, false);
            if(firstSet==null) {
                firstSet=set;
                out.write("@relation "+((set.name.length()==0)?"cat":set.name)+"\n\n");
                set.writeArffFeatures(out);
                out.write("\n@data\n");
            }else {
                if(!sameFeatures(firstSet, set))
                    throw new IOException(file+" (input line "+lno+") Features differ from previous sets.");
            }
            // just copy data section
            BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(new File(file)), "utf-8"));
            String s;
            int lno2=0;
            boolean inData=false;
            while((s=in.readLine())!=null) {
                lno2++;
                s=s.trim();
                if(inData) {
                    if(s.length()==0 || s.startsWith("%"))
                        continue;
                    out.write(s);
                    out.write("\n");
                    rc++;
                }else {
                    if(s.equalsIgnoreCase("@data"))
                        inData=true;
                }
            }
            in.close();
        }
        out.close();
        return rc;
    }

    /** Writes all features of a dataset to out. */
    protected void writeXrffFeatures(BufferedWriter out) throws IOException {
        out.write("<header>\n<attributes>\n");
        for(int i=0;i<features.size();i++) {
            SampleFeature f=features.get(i);
            out.write("<attribute name=\""+f.getName()+"\" type=\"");
            String type;
            switch(f.getType()) {
            case SampleFeature.DT_ENUM:
                type="nominal";
                break;
            case SampleFeature.DT_INT:
            case SampleFeature.DT_FLOAT:
                type="numeric";
                break;
            default:
                type=f.type2string();
            }
            out.write(type+"\"");
            if(classIdx==i || (f.getName().equals("class") && classIdx<0))
                out.write(" class=\"yes\"");
            if(true) // debug
                out.write(" debidx=\""+(i+1)+"\"");
            if(f.getType()==SampleFeature.DT_ENUM) {
                out.write(">\n<labels>\n");
                for(int j=0;j<f.getValues().length;j++) {
                    out.write("<label>");
                    out.write(f.getValues()[j]);
                    out.write("</label>\n");
                }
                out.write("</labels>\n</attribute>\n");
            }else {
                out.write("/>\n");
            }
        }
        out.write("</attributes>\n</header>\n");
    }
    
    /** Writes all samples of a dataset to out in xrff format. 
     * @param sparse */
    protected void writeXrffData(BufferedWriter out, boolean sparse) throws IOException {
        out.write("<body>\n<instances>\n");
        for(int i=0;i<samples.size();i++) {
            Sample smp=samples.get(i);
            if(smp.getDebugInfo()!=null) {
                out.write("<!--");
                int k=0;
                for(Map.Entry<String,Integer> en: smp.getDebugInfo().entrySet()) {
                    if(k!=0)
                        out.write(',');
                    out.write(en.getKey()+":"+en.getValue());
                    k++;
                }
                out.write("-->\n");
            }
            out.write("<instance");
            if(weighted)
                out.write(" weight=\""+smp.getWeight()+"\"");
            if(sparse)
                out.write(" type=\"sparse\"");
            out.write(">");
            for(Map.Entry<Integer,String> en: smp) {
                // Logger.LOGERR("f"+j+"="+smp.featValues[j]);
                if(sparse && (en.getValue()==null || en.getValue().equals("0")))
                    continue;
                out.write("<value");
                if(sparse) {
                    out.write(" index=\"");
                    out.write(String.valueOf(en.getKey()+1));
                    out.write("\"");
                }
                out.write(">");
                out.write(en.getValue());
                out.write("</value>");
            }
            out.write("</instance>\n");
        }
        out.write("</instances>\n</body>\n");
    }
    
    /** Writes a dataset to out in xrff syntax. 
     * @param sparse */
    public void writeXrff(BufferedWriter out, boolean sparse) throws IOException {
        out.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        out.write("<!DOCTYPE dataset SYSTEM \"xrff.dtd\">\n");        
        out.write("<dataset name=\""+((name.length()==0)?"noname":name)+"\" version=\"3.5.3\">\n");
        writeXrffFeatures(out);
        writeXrffData(out, sparse);
        out.write("</dataset>\n");
        out.close();
    }

    /** Writes a dataset to an xrff file using utf-8. 
     * @param sparse */
    public void writeXrff(String file, boolean sparse) throws IOException {
        BufferedWriter out=new BufferedWriter(new OutputStreamWriter(
                ((file!=null)? new FileOutputStream(new File(file)): System.out),
                "utf-8"));
        writeXrff(out, sparse);
    }
    
    /** Calls writeXrff(out, true) 
     * @param output writer */
    public void writeTo(BufferedWriter out) throws IOException {
        writeXrff(out, true);
    }
    
    /** Returns true if the two datasets have the same set of features. */
    public static boolean sameFeatures(SampleSet s1, SampleSet s2) {
        if(s1.features.size()!=s2.features.size())
            return false;
        for(int i=0;i<s1.features.size();i++) {
            SampleFeature f1=s1.features.get(i);
            SampleFeature f2=s2.features.get(i);
            if(!f1.getName().equals(f2.getName()) || f1.getType()!=f2.getType() || 
               (f1.getType()==SampleFeature.DT_ENUM && !f1.type2string().equals(f2.type2string())))
                return false;
        }
        return true;
    }
    
    /** Reads a weka xrff dataset from file. 
     * @return SampleSet */
    public static SampleSet readXrff(String file, boolean headerOnly, boolean uniq) throws IOException {
        SampleSet ss=null;
        XMLReader reader=null;
        String msg=null;
        try {
            SAXParser sp = SAXParserFactory.newInstance().newSAXParser();
            reader = sp.getXMLReader();
        }catch(SAXException sex) {
            msg="Cannot instantiate SAX parser: "+sex;
        }catch(ParserConfigurationException cex) {
            msg="Cannot read xrff data file "+file+": "+cex;
        }
        if(reader==null) {
            Logger.LOG(Logger.ERR,msg);
            throw new IOException(msg);
        }
        SAXXrffReader handler = new SAXXrffReader(new SampleSet(file,true));
        reader.setContentHandler(handler);
        reader.setErrorHandler(null);
        reader.setEntityResolver(null);
        reader.setDTDHandler(null);
        handler.headerOnly=headerOnly;
        handler.uniq=uniq;
        
        InputSource source = new InputSource(file);
        try {
            reader.parse(source);
            ss=handler.getSampleSet();
        }catch(SAXException sex) {
            msg="Error parsing xrff "+file+": "+sex;
        }catch(IOException ex) {
            msg="Cannot read xrff "+file+": "+ex;
        }
        if(ss==null) {
            Logger.LOG(Logger.ERR,msg);
            throw new IOException(msg);
        }
        return ss;
    }     
    
    static Pattern patNum=Pattern.compile("\\d+");
    
    /** Method for command line usage. */
    public static void main(String[] args) throws IOException {
        if(args.length==0) {
            System.err.println("Usage: SampleSet read [-fax] (arff_file|xrff_file|arff@stdin) | cat < arff_file_list | a2x [-us] (arff_file|stdin)");
            System.exit(1);
        }
        if(args[0].equalsIgnoreCase("cat")) {
            BufferedReader fileList=new BufferedReader(new InputStreamReader(System.in));
            catArff(fileList, null);
            fileList.close();
        }else if(args[0].equalsIgnoreCase("read")) {
            String file=null;
            int minNegCnt=1;
            int minPosCnt=1;
            boolean dumpArff=false;
            boolean dumpXrff=true;
            if(args.length>1) {
                file=args[1];
                if(file.startsWith("-")) {
                    int i=file.indexOf("f");
                    if(i!=-1) {
                        minNegCnt=2;
                        Matcher m=patNum.matcher(file);
                        if(m.find(i) && m.start()==i+1) {
                            minNegCnt=Integer.parseInt(m.group());
                        }
                    }
                    dumpArff=file.indexOf("a")!=-1;
                    dumpXrff=file.indexOf("x")!=-1;
                    file=(args.length>2)? args[2]: null;
                }
            }
            SampleSet set=null;
            if(file!=null && file.toLowerCase().contains("xrff")) {
                set=SampleSet.readXrff(file, false, false);
            }else {
                set=SampleSet.readArff(file, false, false);
            }
            if(minNegCnt>1 || minPosCnt>1) {
                set.filter(minNegCnt, minPosCnt);
            }
            if(dumpXrff)
                set.writeXrff(new BufferedWriter(new OutputStreamWriter(System.out)), true);
            if(dumpArff)
                set.writeArff(null);
        }else if(args[0].equalsIgnoreCase("a2x")) {
            String file=null;
            boolean sparse=false;
            boolean uniq=false;
            if(args.length>1) {
                file=args[1];
                if(file.startsWith("-")) {
                    sparse=file.indexOf("s")!=-1;
                    uniq=file.indexOf("u")!=-1;
                    file=(args.length>2)? args[2]: null;
                }
            }
            SampleSet set=SampleSet.readArff(file, false, uniq);
            set.writeXrff(new BufferedWriter(new OutputStreamWriter(System.out)), sparse);
        }else if(args[0].equalsIgnoreCase("x2a")) {
            String file=(args.length>1)? args[1]: null;
            SampleSet set=SampleSet.readXrff(file, false, false);
            set.writeArff(file);
        }
    }
    
    void filter(int minNegCount, int minPosCount) {
        ArrayList<Sample> s2=new ArrayList<Sample>(4096);
        for(Sample smp: samples) {
            String cls=smp.getFeatureValue(0);
            if(cls!=null && cls.equalsIgnoreCase("bg")) {
                if(smp.getWeight()<minNegCount) {
                    continue;
                }
            }else {
                if(smp.getWeight()<minPosCount) {
                    continue;
                }
            }
            s2.add(smp);
        }
        samples.clear();
        samples=s2;
    }

    public Iterator<Sample> iterator() {
        return samples.iterator();
    }

    /** @return sum of weights of all samples */
    public int getWeightedSampleCount() {
        int sum=0;
        for(Sample s: samples) {
            sum+=s.getWeight();
        }
        return sum;
    }
    
    /** @return information about this SampleSet */
    public String toString() {
        return "SampleSet "+name+", samples="+size()+", weightedsamples="+getWeightedSampleCount()+", features="+features.size()+" clsIdx="+classIdx;
    }
}
