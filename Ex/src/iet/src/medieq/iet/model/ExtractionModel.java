// $Id: ExtractionModel.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.model;

/** ExtractionModel links a generic DataModel to a list of IE-engine specific
 *  extraction models. E.g. a data model about contact information
 *  may link to 2 extraction models specified for 2 extraction engines,
 *  where one can extract names and e-mail addresses and the other one job titles.
 */
public interface ExtractionModel {
    /** classname of a specific extraction engine */
    public String getEngineName();
    /** filename of an extraction model for the specified extraction engine */
    public String getModelFile();
}
