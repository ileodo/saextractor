// $Id: ScriptPatternF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import ex.model.ScriptPattern;

public class ScriptPatternF extends PhraseF implements IntFeature {
    public ScriptPattern pat;

    public ScriptPatternF(int featureId, String featureName, ScriptPattern pat) {
        super(featureId, featureName, VAL_BOOLEAN);
        this.pat=pat;
    }
    
    public ScriptPatternF(ScriptPattern pat) {
        super(FM.getFMInstance().getNextFeatureId(), pat.toString(), VAL_BOOLEAN);
        this.pat=pat;
    }
    
    public String toString(int val) {
        return boolValue2string(val);
    }
}
