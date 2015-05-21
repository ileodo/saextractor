// $Id: EvalAttConfusion.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import uep.util.Util;

public class EvalAttConfusion {
    public AttributeDef ad;
    private int goldExactMatchCnt; // times gold annot of att1 was matched by auto annot of att2  
    private double goldPartialMatchCnt;
    private Map<String,Map<String,CountedString>> matches;
    
    public EvalAttConfusion(AttributeDef ad) {
        this.ad=ad;
        goldExactMatchCnt=0;
        goldPartialMatchCnt=0;
        matches=new TreeMap<String,Map<String,CountedString>>();
    }
    
    public void add(EvalAttConfusion other) {
        goldExactMatchCnt+=other.goldExactMatchCnt;
        goldPartialMatchCnt+=other.goldPartialMatchCnt;
        for(Map.Entry<String, Map<String,CountedString>> en: other.matches.entrySet()) {
            String src=en.getKey();
            Map<String,CountedString> myMap=matches.get(src);
            if(myMap==null) {
                myMap=new TreeMap<String,CountedString>();
                matches.put(src, myMap);
            }
            for(CountedString cs: en.getValue().values()) {
                CountedString myCs=myMap.get(cs.s);
                if(myCs==null) {
                    myCs=new CountedString(cs.s, 0);
                    myMap.put(cs.s, myCs);
                }
                myCs.add(cs);
            }
        }
    }
    
    public void addMatch(String src, String trg, double ratio, String docId, Collection<Object> debugInfo) {
        if(ratio==1) {
            goldExactMatchCnt++;
        }else {
            goldPartialMatchCnt+=ratio;
        }
        Map<String,CountedString> css=matches.get(src);
        if(css==null) {
            css=new TreeMap<String,CountedString>();
            matches.put(src, css);
        }
        CountedString cs=css.get(trg);
        if(cs==null) {
            cs=new CountedString(trg, 0);
            css.put(trg, cs);
        }
        cs.count++;
        if(debugInfo!=null && debugInfo.size()>0) {
            for(Object debi: debugInfo) {
                cs.addDebugInfo(debi);
            }
        }
        if(docId!=null) {
            if(cs.docs==null) {
                cs.docs=new TreeMap<String,Integer>();
            }
            Integer cnt=cs.docs.get(docId);
            if(cnt==null) {
                cnt=1;
            }
            cs.docs.put(docId,cnt);
        }
    }
    
    public String toString() {
        StringBuffer b=new StringBuffer(64);
        b.append(ad.getName()+"("+goldExactMatchCnt+"\\"+String.format("%.2f", goldPartialMatchCnt)+")\n");
        for(Map.Entry<String, Map<String,CountedString>> en: matches.entrySet()) {
            String s="  "+en.getKey();
            b.append(s);
            int i=0;
            for(CountedString cs: en.getValue().values()) {
                if(i++==0) {
                    b.append(" -> ");
                }else {
                    Util.rep(s.length(), ' ', b);
                }
                b.append("  "+cs.s+((cs.count>1)? ("("+cs.count+")"): ""));
                if(cs.docs!=null) {
                    int j=0;
                    for(Map.Entry<String, Integer> dc: cs.docs.entrySet()) {
                        if(j>0) {
                            b.append(", ");
                        }else {
                            b.append("\t");
                        }
                        b.append(dc.getKey()+":"+dc.getValue());
                    }
                }
                if(cs.debugInfo!=null && cs.debugInfo.length()>0) {
                    b.append("\n");
                    b.append(cs.debugInfo);
                }
                b.append("\n");
            }
        }
        return b.toString();
    }
}

class CountedString {
    public String s;
    public int count;
    Map<String,Integer> docs;
    public StringBuffer debugInfo;
    public CountedString(String s, int count) {
        this.s=s;
        this.count=0;
        docs=null;
        debugInfo=null;
    }
    public void add(CountedString cs) {
        count+=cs.count;
        if(cs.docs!=null) {
            if(docs==null) {
                docs=new TreeMap<String, Integer>();
            }
            for(Map.Entry<String, Integer> en: cs.docs.entrySet()) {
                Integer cnt=docs.get(en.getKey());
                if(cnt==null) {
                    cnt=1;
                }
                docs.put(en.getKey(), cnt);
            }
        }
        if(cs.debugInfo!=null && cs.debugInfo.length()>0) {
            addDebugInfo(cs.debugInfo);
        }
    }
    public void addDebugInfo(Object debi) {
        String debis=debi.toString();
        if(debugInfo==null) {
            debugInfo=new StringBuffer(s.length()+1);
            debugInfo.append("\t");
        }else {
            debugInfo.append("\n\t");
        }
        debugInfo.append(debis);
    }
}
