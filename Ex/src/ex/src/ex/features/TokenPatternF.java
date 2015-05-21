// $Id: TokenPatternF.java 1641 2008-09-12 21:53:08Z labsky $
package ex.features;

import ex.ac.TokenPattern;

public class TokenPatternF extends PhraseF implements IntFeature {
    public TokenPattern pat;

    public TokenPatternF(int featureId, String featureName, TokenPattern pat) {
        super(featureId, featureName, VAL_BOOLEAN);
        this.pat=pat;
    }
    
    public static String genFeatureName(TokenPattern pat) {
        String s;
        if(pat.modelElement!=null) {
            s=pat.modelElement.name+".";
        }else {
            s="";
        }
        switch(pat.type) {
        case TokenPattern.PAT_VAL:
            s+="value.";
            break;
        case TokenPattern.PAT_CTX_L:
        case TokenPattern.PAT_CTX_R:
        case TokenPattern.PAT_CTX_LR:
            s+="context.";
            break;
        default:
            s+="gen.";
        }
        s+=pat.id;
        return s;
    }
    
    public TokenPatternF(TokenPattern pat) {
        super(FM.getFMInstance().getNextFeatureId(), genFeatureName(pat), VAL_BOOLEAN);
        this.pat=pat;
    }
    
    public String toString(int val) {
        return boolValue2string(val);
    }
}
