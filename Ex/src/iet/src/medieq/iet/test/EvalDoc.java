// $Id: EvalDoc.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.test;

import java.io.*;
import java.util.*;

import uep.util.Logger;
import medieq.iet.model.*;
import medieq.iet.components.DatDocumentReader;
import medieq.iet.components.Evaluator;
import medieq.iet.components.EvaluatorImpl;
import medieq.iet.generic.*;

public class EvalDoc {
    public static void main(String[] args) {
        if(args.length!=1) {
            System.err.println("Usage: java EvaluatorImpl evalFile");
            System.exit(-1);
        }
        Logger.init("evaldoc.log", Logger.TRC, 0, null);
        Properties props=new Properties();
        DocumentSet goldSet=null;
        DocumentSet autoSet=null;
        try {
            props.load(new FileInputStream(new File(args[0])));
            goldSet=DocumentSetImpl.read(props.getProperty("gold_set"));
            goldSet.setBaseDir(props.getProperty("gold_dir"));
            autoSet=DocumentSetImpl.read(props.getProperty("auto_set"));
            autoSet.setBaseDir(props.getProperty("auto_dir"));
        }catch (IOException ex) {
            System.err.println("Error reading "+args[0]+": "+ex);
            System.exit(-1);
        }
        Evaluator evr=new EvaluatorImpl();
        evr.setDocumentReader(new DatDocumentReader());
        evr.setDataModel(new DataModelImpl("", "anonymous"));
        
        EvalResult res=new EvalResult("anonymous");
        try {
            evr.eval(goldSet, autoSet, res);
        }catch(IOException ex) {
            System.err.println("Error evaluating documents from "+args[0]+": "+ex);
            ex.printStackTrace(System.err);
            System.exit(-1);
        }
        System.out.println(res);
    }
}
