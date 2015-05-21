// $Id: PatSubMatch.java 1641 2008-09-12 21:53:08Z labsky $
package ex.ac;

/** 
 *  @author Martin Labsky labsky@vse.cz
 */

public class PatSubMatch extends PatMatch {
    public int dim;
    public PatMatch parent;

    public PatSubMatch(TokenPattern pat, int startIdx, int len, int dimension) {
        super(pat,startIdx,len);
        dim=dimension;
    }
    
    public int getDim() { return dim; }

    public void setParent(PatMatch match) {
        parent=match;
    }
}
