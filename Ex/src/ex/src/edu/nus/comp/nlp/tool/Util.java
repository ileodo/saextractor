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
import java.util.*;
import java.io.*;


/**
 * <p>Title: NLP Tools</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: NUS</p>
 * @author Qiu Long qiul@comp.nus.edu.sg
 * @version 1.0
 */
public class Util {

  //older version of readFile.
  public static StringBuffer readFile(String fileName) {
    return readFile(fileName, null);
  }

  /**
   * @param fileName
   * @param commentFlag The leading character indicating a line of comment.
   * @return The content, preferably plain text, of the file "fileName", with the comments ignored.
   */
  public static StringBuffer readFile(String fileName, String commentFlag) {
    StringBuffer sb = new StringBuffer();
    try {
      BufferedReader in =
          new BufferedReader(new FileReader(fileName));
      String s;
      while ( (s = in.readLine()) != null) {
        if (commentFlag != null
            && s.trim().startsWith(commentFlag)) {
          //ignore this line
          continue;
        }
        sb.append(s);
        //sb.append("/n"); to make it more platform independent (Log July 12, 2004)
        sb.append(System.getProperty("line.separator"));
      }
      in.close();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
    return sb;

  }

  public static void write(String fileName, String content, boolean append) {
    try {
      PrintWriter out = new PrintWriter(
          new BufferedWriter(new FileWriter(fileName, append)));
      out.println(content);
      out.close();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }
  /**
   *
   * @param fileName the name of the file to write to
   * @param content
   */
  public static void write(String fileName, String content) {
    write(fileName, content, false);
  }

  public static String removeTag(String str) {
    String rst = new String();
    Pattern p = Pattern.compile("<[.[^<>]]+>");
    Matcher m = p.matcher(str);
    rst = m.replaceAll(" ");
    return rst;
  }


  public static String removeBlankLine(String str) {
    String rst = new String();
    Pattern p = Pattern.compile("\n([\\s^\n]*\n[\\s^\n]*)+");
    Matcher m = p.matcher(str);
    rst = m.replaceAll("\n");
    return rst;
  }

  public static String removeSpace(String str) {
    return str.replaceAll("\\s", "");
  }

  public static String mergeDoubleSingleQuots(String str) {
    return str.replaceAll("''|``", "\"");
  }


  public static int numberOfTokens(String content) {
    String delim = " |\n";
    String[] tks = content.split(delim);
    HashSet tkSet = new HashSet();
    for (int i = 0; i < tks.length; i++) {
      tkSet.add(tks[i]);
    }
    return tkSet.size();
  }

  public static void UnixSystemCall(String command, String outputFileName) {
    try {
      String[] cmd = {
          "/bin/sh",
          "-c",
          "ulimit -s unlimited;" + command + "  > " + outputFileName};
      Process proc = Runtime.getRuntime().exec(cmd);
      proc.waitFor();

    }
    catch (Exception e) {
      System.err.println("Wrong while running "+command);
    }
  }

  public static String UnixSystemCall(String command) {
    String output = new String();
    try {
      String[] cmd = {
          "/bin/sh",
          "-c",
          "ulimit -s unlimited;" + command};
      Process proc = Runtime.getRuntime().exec(cmd);
      BufferedReader in = new BufferedReader(new InputStreamReader(proc.
          getInputStream()));
      String s;
      while ( (s = in.readLine()) != null) {
        output += s + "\n";
      }
      proc.waitFor();

      //System.err.println("UnixSystemCall exits as: "+proc.exitValue());

    }
    catch (Exception e) {
      System.err.println("Wrong while running "+command);
    }
    return output;
  }



}
