// $Id: WekaShepherd.java 1641 2008-09-12 21:53:08Z labsky $
package ex.train.weka;

import uep.data.SampleSet;

/** Utility class to launch weka training and testing using custom IO layer. */
public class WekaShepherd {
    WekaConnector wc;
    String[] args;
    String classifierName; 
    String trainDataFile;
    String testDataFile;
    String outBinFile;
    String inpBinFile;
    boolean fillZeros;
    boolean evalTrain;
    SampleSet trainSet=null;
    SampleSet testSet=null;
    
    public String getOpt(String opt) throws Exception {
        String s=weka.core.Utils.getOption(opt, args).trim();
        if(s.length()==0)
            s=null;
        return s;
    }

    public boolean getFlag(String flag) throws Exception {
        return weka.core.Utils.getFlag(flag, args);
    }
    
    private void usage() {
        System.out.println("Usage: ");
        System.out.println("WekaShepherd -c ClassifierClassName -t train.xrff -T test.xrff -d modelBinFile -l modelBinFile -z -et -h");
        System.exit(-1);
    }
    
    public void work(String[] args) throws Exception {
        this.args=args;
        if(getFlag("h")||getFlag("?")||getFlag("help"))
            usage();
        classifierName = getOpt("c");
        trainDataFile = getOpt("t");
        testDataFile = getOpt("T");
        outBinFile = getOpt("d");
        inpBinFile = getOpt("l");
        fillZeros = getFlag("z");
        evalTrain = getFlag("et");
        wc=new WekaConnector();
        boolean didSomething=false;
        
        if(trainDataFile!=null && classifierName!=null) {
            didSomething=true;
            wc.loadSamples(trainDataFile, fillZeros);
            wc.setParam("algorithm", classifierName);
            wc.newClassifier();
            wc.trainClassifier();
            if(outBinFile==null)
                outBinFile=trainDataFile+".bin";
            wc.saveClassifier(outBinFile);
            if(evalTrain) {
                wc.classifyCurrentDataSet();
            }
        }
        
        if(testDataFile!=null) {
            didSomething=true;
            if(inpBinFile!=null) {
                wc.loadClassifier(inpBinFile);
            }
            testSet=SampleSet.readXrff(testDataFile, false, true);
            wc.clearDataSet();
            wc.addSamples(testSet, false);
            wc.classifyCurrentDataSet();
        }
        
        if(!didSomething) {
            usage();
        }
    }
    
    public static void main(String[] args) throws Exception {
        WekaShepherd ws=new WekaShepherd();
        ws.work(args);
    }
}
