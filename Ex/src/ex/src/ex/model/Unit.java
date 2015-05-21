// $Id: Unit.java 1641 2008-09-12 21:53:08Z labsky $
package ex.model;

import java.util.regex.*;
import ex.util.Const;
import uep.util.Logger;

public class Unit {
    public String name;
    public double convertRatio;
    public Unit baseUnit;
    String[] phrases; // replace by Phrase[]

    public Unit(String n, Unit bu, double cr) {
        name=n;
        baseUnit=bu;
        convertRatio=cr;
    }

    public int setPhrases(String phrStr) {
        try {
            phrases=phrStr.split("|");
        }catch(PatternSyntaxException ex) {
            Logger log=Logger.getLogger("Model");
            log.LG(Logger.ERR,"Syntax error setting phrases '"+phrStr+"' for unit "+name);
            return Const.EX_ERR;
        }
        return Const.EX_OK;
    }
}
