// $Id: Annotable.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

import ex.model.ModelElement;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

public interface Annotable {
    public static int TYPE_AC=TokenPattern.PAT_LAST_TYPE+1;
    public static int TYPE_IC=TokenPattern.PAT_LAST_TYPE+2;
    public static int TYPE_LABEL=TokenPattern.PAT_LAST_TYPE+3;
    
    public int getStartIdx();
    public int getLength();
    public int getType();
    public double getProb();
    public ModelElement getModelElement();
}
