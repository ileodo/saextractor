package ex.reader;

import java.io.Reader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.*;
import ex.features.TokenTypeF;

/** A SimpleTokenizer splits input on whitespaces.
  <p>
  This is not meant as a serious tokenizer ;-)
 */

public class SimpleTokenizer extends Tokenizer {
    Pattern pat;
    Matcher mat;

    private static int ALPHA     =0;
    private static int NUM       =1;
    private static int INT       =2;
    private static int FLOAT     =3;
    private static int ALPHANUM  =4;
    private static int ALPHAPUNCT=5;
    private static int PUNCT     =6;
    private static int ANP       =7;
    private static int OTHER     =8;
    private static String[] tokenImage={
        "ALPHA", "NUM", "INT", "FLOAT", "ALPHANUM", "ALPHAPUNCT", "PUNCT", "ANP", "OTHER"
    };

    public SimpleTokenizer() {
        pat=Pattern.compile("[^\\s]+");
        // fills static TokenFeature with token types recognized by SimpleTokenizer
        copyConstants();
    }

    public void copyConstants() {
        TokenTypeF.getSingleton().setTokenTypes(tokenImage);
    }

    public void setInput(String input) {
        super.setInput(input);
        mat=pat.matcher(inputString);
    }

    public void setInput(Reader input) throws IOException {
        super.setInput(input);
        BufferedReader br=new BufferedReader(input);
        int buffSize=2048;
        char[] buff=new char[buffSize];
        StringBuffer sb=new StringBuffer(buffSize);
        while(br.read(buff,0,buffSize)!=-1)
            sb.append(buff);
        inputString=sb.toString();
        mat=pat.matcher(inputString);
    }

    private static Pattern hasLetter=Pattern.compile("["+
            "\u0041-\u005a"+
            "\u0061-\u007a"+
            "\u00c0-\u00d6"+
            "\u00d8-\u00f6"+
            "\u00f8-\u00ff"+
            "\u0100-\u1fff"+
    "]");
    private static Pattern hasDigit=Pattern.compile("["+
            "\\u0030-\\u0039"+
            "\\u0660-\\u0669"+
            "\\u06f0-\\u06f9"+
            "\\u0966-\\u096f"+
            "\\u09e6-\\u09ef"+
            "\\u0a66-\\u0a6f"+
            "\\u0ae6-\\u0aef"+
            "\\u0b66-\\u0b6f"+
            "\\u0be7-\\u0bef"+
            "\\u0c66-\\u0c6f"+
            "\\u0ce6-\\u0cef"+
            "\\u0d66-\\u0d6f"+
            "\\u0e50-\\u0e59"+
            "\\u0ed0-\\u0ed9"+
            "\\u1040-\\u1049"+
    "]");
    private static Pattern hasPunct=Pattern.compile("[_|\\-\\/\\.,]");
    private static Pattern hasDotComma=Pattern.compile("[\\.,]");

    public TokenAnnot next() {
        if(!mat.find())
            return null;
        String tok=mat.group();
        boolean letter=hasLetter.matcher(tok).find();
        boolean digit=hasDigit.matcher(tok).find();
        boolean punct=hasPunct.matcher(tok).find();
        Matcher dcm=hasDotComma.matcher(tok);
        boolean singleDotOrComma = (dcm.find() && !dcm.find());
        //Logger log=Logger.getLogger("SimpleTokenizer");
        //log.LG(log.ERR,"'"+tok+"' "+letter+","+digit+","+punct+","+singleDotOrComma);
        int type=-1;
        if(letter) {
            if(digit) {
                type=punct? ANP: ALPHANUM;
            }else {
                type=punct? ALPHAPUNCT: ALPHA;
            }
        }else {
            if(digit) {
                if(singleDotOrComma)
                    type=FLOAT;
                else {
                    type=punct? NUM: INT;
                }
            }else {
                type=punct? PUNCT: OTHER;
            }
        }
        return new TokenAnnot(type, mat.start(), mat.end(), null, -1, -1, tok);
    }
}
