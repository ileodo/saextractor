// $Id: $
package medieq.iet.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uep.util.Logger;

import medieq.iet.generic.AnnotationImpl;
import medieq.iet.generic.AttributeValueImpl;
import medieq.iet.generic.ClassDefImpl;
import medieq.iet.generic.InstanceImpl;
import medieq.iet.model.Annotation;
import medieq.iet.model.AttributeValue;
import medieq.iet.model.ClassDef;
import medieq.iet.model.Document;
import medieq.iet.model.EvalAttRecord;
import medieq.iet.model.EvalInstRecord;
import medieq.iet.model.Instance;

public class Villain {
    EvaluatorImpl eval;
    Logger log;
    // we create temporary Annotations to compute Villain score, this is their author: 
    final static String VILLAIN_AUTHOR="Villain";
    // we create temporary Instances to compute Villain score, this counter is used to assign IDs to them:
    int artTinCntr;
    // all temporary instances will share this ClassDef:
    final static ClassDef artClass=new ClassDefImpl("VillainArtClass", null);
    
    public Villain(EvaluatorImpl eval) {
        this.eval=eval;
        log=Logger.getLogger("villain");
        artTinCntr=0;
    }
    
    protected int addArtInstancesForStandaloneAVs(List<Annotation> annots, List<Instance> instances) {
        int cnt=0;
        for(Annotation an: annots) {
            AttributeValue av = eval.an2av(an);
            if(av.getInstance()==null) {
                cnt++;
                Instance artIn=new InstanceImpl(String.valueOf(-(++artTinCntr)), artClass); // -1, -2, etc.
                artIn.setAuthor(VILLAIN_AUTHOR);
                // careful: we will remove all artificial Instances and also links to them from AVs
                artIn.getAttributes().add(av);
                av.setInstance(artIn);
                instances.add(artIn);
            }
        }
        return cnt;
    }
    
    protected int removeArtInstances(List<Instance> instances) {
        int cnt=0;
        for(Instance inst: instances) {
            if(inst.getAuthor()==VILLAIN_AUTHOR) {
                for(AttributeValue av: inst.getAttributes()) {
                    cnt++;
                    av.setInstance(null);
                }
                inst.getAttributes().clear();
            }
        }
        return cnt;
    }
    
    /** Adds to EvalResult appropriate link counts in order to compute Villain precision and recall. 
     * @param srcDoc document containing source Instances
     * @param tgtDoc document containing target Instances
     * @param mode evaluation flags: now ignored
     * @param eir where to store statistics
     * */
    public void eval(Document goldDoc, Document autoDoc, int mode, EvalInstRecord eir) {
        List<Instance> goldInstances = new ArrayList<Instance>(32);
        eval.getInstances(goldDoc, goldInstances, (goldDoc==autoDoc)? EvaluatorImpl.INCLUDE: 0, DocumentReader.goldStandardAuthor);
        List<Instance> autoInstances = new ArrayList<Instance>(32);
        eval.getInstances(autoDoc, autoInstances, (goldDoc==autoDoc)? EvaluatorImpl.EXCLUDE: 0, DocumentReader.goldStandardAuthor);
        // store instance counts
        eir.goldInstCnt+=goldInstances.size();
        eir.autoInstCnt+=autoInstances.size();
        
        evalInternal(goldDoc, autoDoc, mode, EvaluatorImpl.EVAL_RECALL, eir);
        evalInternal(autoDoc, goldDoc, mode, EvaluatorImpl.EVAL_PREC, eir);
    }
    
    public void evalInternal(Document srcDoc, Document tgtDoc, int mode, char evalType, EvalInstRecord eir) {
        // 0. create lists of src and dst annotations
        int fromSrc=0;
        int fromTgt=0;
        if(srcDoc==tgtDoc) {
            if(evalType==EvaluatorImpl.EVAL_RECALL) {
                fromSrc=EvaluatorImpl.INCLUDE;
                fromTgt=EvaluatorImpl.EXCLUDE;
            }else {
                fromSrc=EvaluatorImpl.EXCLUDE;
                fromTgt=EvaluatorImpl.INCLUDE;
            }
        }
        List<Annotation> srcAnnots=new ArrayList<Annotation>(32);
        eval.getAnnotations(srcDoc, srcAnnots, fromSrc, DocumentReader.goldStandardAuthor);
        List<Annotation> tgtAnnots=new ArrayList<Annotation>(32);
        eval.getAnnotations(tgtDoc, tgtAnnots, fromTgt, DocumentReader.goldStandardAuthor);
        
        // 0. create lists of src and dst instances 
        List<Instance> srcInstances = new ArrayList<Instance>(32);
        eval.getInstances(srcDoc, srcInstances, fromSrc, DocumentReader.goldStandardAuthor);
        List<Instance> tgtInstances = new ArrayList<Instance>(32);
        eval.getInstances(tgtDoc, tgtInstances, fromTgt, DocumentReader.goldStandardAuthor);
        // 0.5: create new temporary helper instances that encapsulate standalone AttributeValues (this is undone at the end):
        addArtInstancesForStandaloneAVs(srcAnnots, srcInstances);
        addArtInstancesForStandaloneAVs(tgtAnnots, tgtInstances);
        
        // 1. for each src annot, assign the matching tgt annot. 
        // If it does not exist, create an artificial standalone tgt Instance that contains the missed tgt annot.
        Map<Annotation,MatchedAnnotation> annMap=new HashMap<Annotation,MatchedAnnotation>(srcAnnots.size());
        List<Annotation> artTgtAnnots = new ArrayList<Annotation>(128);
        for(Annotation san: srcAnnots) {
            AttributeValue sav = eval.an2av(san);
            if(san==null || san.getText()==null) {
                log.LG(Logger.ERR, "Source annotation has no text: av="+sav);
                continue;
            }
            MatchedAnnotation best=eval.findClosestAnnotation(san, tgtAnnots, mode);
            if(best==null) {
                // create artificial exactly matching target Annotation, AV, Instance. 
                Instance artTin=new InstanceImpl(String.valueOf(-(++artTinCntr)), artClass); // -1, -2, etc.
                artTin.setAuthor(VILLAIN_AUTHOR);
                AttributeValue artTav=new AttributeValueImpl(sav.getAttributeDef(), san.getText(), sav.getConfidence(), 
                        artTin, san.getStartOffset(), san.getLength(), VILLAIN_AUTHOR);
                Annotation artTan=artTav.getAnnotations().get(0);
                ((AnnotationImpl)artTan).setUserData(artTav); // hack: storing AV in Annotation as its user data 
                artTgtAnnots.add(artTan);
                best=new MatchedAnnotation(artTan, 1.0, MatchedAnnotation.ARTIFICIAL);
            }
            annMap.put(san, best);
        }
                
        // 2. for each src instance, create a set of produced instances 
        // i.e. those tgt instances whose attributes contain at least 1 tgt annot 
        // that matched some src annot of the src instance
        Map<Instance,Map<Instance,Integer>> insMap=new HashMap<Instance,Map<Instance,Integer>>(128);
        for(Instance sin: srcInstances) {
            Map<Instance,Integer> tgtSet=new HashMap<Instance,Integer>(8);
            for(AttributeValue sav: sin.getAttributes()) {
                if(sav.getAnnotations().size()!=1) {
                    String err="Villain scorer does not support AttributeValues with "+sav.getAnnotations().size()+" annotations; skipping.";
                    // throw new IllegalArgumentException(err);
                    log.LG(Logger.ERR,err);
                    continue;
                }
                Annotation san=sav.getAnnotations().get(0);
                MatchedAnnotation mtan=annMap.get(san);
                if(mtan==null) {
                    throw new IllegalArgumentException("Internal error: no MatchedAnnotation for "+san);
                }
                AttributeValue tav=eval.an2av(mtan.annot); // also uses the hack 20 lines above for art annots
                Instance tin=tav.getInstance();
                Integer val=tgtSet.get(tin);
                if(val==null) {
                    val=0;
                }
                tgtSet.put(tin, val+1);
            }
            insMap.put(sin, tgtSet);
        }
        
        // 3. assemble components of Villain score
        int numerator=0;
        int denominator=0;
        StringBuffer debi=(EvalInstRecord.SHOW_PROBLEMS>1)? new StringBuffer(): null;
        String debis=null;
        for(Instance sin: srcInstances) {
            int avCnt = sin.getAttributes().size();
            numerator += avCnt - insMap.get(sin).size();
            log.LG(Logger.TRC,"numerator += "+avCnt+" - "+insMap.get(sin).size());
            denominator += avCnt - 1;
            if(debi!=null) {
                debi.append(sin.toString());
                debi.append("\n-->\n");
                Map<Instance,Integer> projected=insMap.get(sin);
                int i=0;
                for(Instance prj: projected.keySet()) {
                    i++;
                    debi.append(i+".\n"+prj);
                }
                debi.append("----------------\n");
                debis=debi.toString();
                debi.setLength(0);
            }
        }
        double villainScore = (denominator<=0)? 0: ((double)numerator/(double)denominator);
        log.LG(Logger.USR,"VillainScore(mode "+mode+", evaltype "+EvaluatorImpl.evalType2string(evalType)+")="+EvalAttRecord.fmtNum(villainScore));
        
        switch(evalType) {
        case EvaluatorImpl.EVAL_RECALL:
            eir.goldLinkCnt += denominator;
            eir.matchedAutoLinkCnt += numerator;
            // assemble stats
            eir.addErrorCountForDoc(srcDoc.getFile(), denominator-numerator, debis);
            break;
        case EvaluatorImpl.EVAL_PREC:
            eir.autoLinkCnt += denominator;
            eir.matchedGoldLinkCnt += numerator;
            // assemble stats
            eir.addErrorCountForDoc(srcDoc.getFile(), denominator-numerator, debis);
            break;
        default:
            throw new IllegalArgumentException("evalType="+evalType);
        }
        
        // 4. clear temporary instances (real standalone AttributeValues now point to them):
        removeArtInstances(srcInstances);
        removeArtInstances(tgtInstances);
    }
}
