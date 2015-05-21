// $Id: ExtractionModelImpl.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.generic;

import medieq.iet.model.ExtractionModel;

/* (non-Javadoc)
 * @see medieq.iet.model.ExtractionModel
 */
public class ExtractionModelImpl implements ExtractionModel {
    protected String engineName;
    protected String modelFile;
    
    public ExtractionModelImpl(String engineName, String modelFile) {
        this.engineName=engineName;
        this.modelFile=modelFile;
    }
    
    /* (non-Javadoc)
     * @see medieq.iet.model.ExtractionModel#getEngineName()
     */
    public String getEngineName() {
        return engineName;
    }

    /* (non-Javadoc)
     * @see medieq.iet.model.ExtractionModel#getModelFile()
     */
    public String getModelFile() {
        return modelFile;
    }
}
