/**
* A rule-based English sentence splitter.
* Copyright (C) 2003-2006  Long Qiu

* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.

* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.

* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package edu.nus.comp.nlp.tool;
import java.util.regex.*;


/**
 * <p>Title: NLP Tools</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: NUS</p>
 * @author Qiu Long qiul@comp.nus.edu.sg
 * @version 2.01
 * @history
 * Jul. 23, 2004 Change package from ...NLP.tool to ...nlp.tool
 * Dec. 01, 2003 <tag>'s can be optionally kept in the output
 */

public class SentenceSplitter {
  final static String versionInfo = "SentenceSplitter version 2.01 20040927";

  public SentenceSplitter() {
  }

  public static void showHelpMessage() {
    System.out.println("Usage: \tjava -classpath SentenceSplitter.jar edu.nus.comp.nlp.tool.SentenceSplitter [options] textfile");
    System.out.println("Or: \tjava -jar SentenceSplitter.jar [options] textfile");
    System.out.println("Splits the text file into sentences. XML style tags are removed.");
    System.out.println("Options:");
    System.out.println(" -a str\t\t Surrounds sentences with str.");
    System.out.println(" -help\t\t Displays this message.");
    System.out.println(" -i\t\t Display sentences with numeric index.");
    System.out.println(" -s \t\t Puts single sentence in each line.");
    System.out.println(" -tag \t\t Keeps tags.");
    System.out.println(" -v \t\t Version information.");
    System.exit( 0);
  }

  public static void main(String[] args) {


   boolean showIdx = false;
   System.setProperty("SingleLine","false");
   System.setProperty("RetainTag","false");
   String extraStr = new String();
   if(args.length<1){
     showHelpMessage();
   }

   for (int i = 0; i < args.length ; i++) {
     if (args[i].equals("-i")) {
       showIdx = true;
     }
     if (args[i].equals("-s")) {
       System.setProperty("SingleLine","true");
     }
     if (args[i].equals("-tag")) {
       System.setProperty("RetainTag","true");
     }
     if (args[i].equals("-a")) {
       extraStr = args[i+1].equals("\\n")? "\n":args[i+1];
     }
     if (args[i].endsWith("-help")) {
       showHelpMessage();
     }
     if (args[i].endsWith("-v")) {
       System.out.println(versionInfo);
       System.exit(0);
     }
   }

   PlainText plainText1 = new PlainText(args[args.length-1]);
   plainText1.setSingleLine(System.getProperty("SingleLine").equals("true"));
   plainText1.extraStr = extraStr;
   if(System.getProperty("RetainTag").equals("false")){
     plainText1.removeTag();
   }
   plainText1.run(showIdx);
 }

}
