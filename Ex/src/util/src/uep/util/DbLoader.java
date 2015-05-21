// $Id: DbLoader.java 1642 2008-09-12 21:54:15Z labsky $
package uep.util;

/* 
   Loads a tab-separated (tsv) file into a database table. 
   The tsv file must contain a header specifying column names and data types.
*/

import java.sql.*;
import java.util.regex.*;
import java.io.*;
//Notice, do not import com.mysql.jdbc.* 
//or you will have problems! - as Alan L. Liu said ;-)

class Attrib {
    public Attrib(String n, String t) {
        name=n.toLowerCase().trim();
        type=t.toLowerCase().trim();
        if(type.equals("int") || type.equals("long") || type.equals("short"))
            dbType=java.sql.Types.INTEGER;
        else if(type.equals("real") || type.equals("float") || type.equals("double"))
            dbType=java.sql.Types.FLOAT;
        else
            dbType=java.sql.Types.VARCHAR;
    }
    public String name;
    public String type;
    public int dbType;
}

public class DbLoader {

    public static final String usage=
        "Usage: java DbLoader [-cfg CFG] -object_table tableName -f fileName";
    private Logger log;
    private String delim=" *\\t *";
    private static String cfgFile="./config.cfg";
    private static String dbDriver=null;
    private static String dbConnection=null;

    public static void main(String args[]) {

        // load global config + cmdline parameters
        Options o=Options.getOptionsInstance();
        if ((args.length >= 2) && args[1].toLowerCase().equals("-cfg")) {
            cfgFile=args[2];
        }
        try { o.load(new FileInputStream(cfgFile)); }
        catch(Exception ex) { System.err.println("Cannot find "+cfgFile+": "+ex.getMessage()); return; }
        o.add(0, args);
        String objTbl=null;
        String file=null;
        String dataEncoding=null;
        try {
            objTbl=o.getMandatoryProperty("object_table");
            file=o.getMandatoryProperty("f");
            dataEncoding=o.getMandatoryProperty("data_encoding");
        }catch(ConfigException ex) {
            System.err.println("Config error: "+ex.getMessage());
            System.err.println(usage);
            return;
        }
        // load file into objectTable
        DbLoader loader=new DbLoader();
        int rc=loader.file2table(file, dataEncoding, objTbl);
        if(rc!=UtilConst.OK) {
            System.err.println("Load failed");
        }
    }

    public DbLoader() {
        Logger.init("dbLoader.log", -1, -1, null);
        log=Logger.getLogger("DbLoader");
        Options o=Options.getOptionsInstance();
        try {
            dbDriver=o.getMandatoryProperty("db_driver");
            dbConnection=o.getMandatoryProperty("db_connection");
        }catch(ConfigException ex) {
            log.LG(Logger.ERR,"Config error: "+ex.getMessage());
        }
        try {
            Class.forName(dbDriver).newInstance(); 
        } catch (Exception ex) { 
            log.LG(Logger.ERR,dbDriver+" could not be instantiated");
            ex.printStackTrace();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbConnection);
    }

    private int file2table(String fileName, String dataEncoding, String table) {
        Connection conn=null;
        Statement st=null;
        ResultSet row=null;
        int rc=0;
        int attrCnt=0;
        Attrib[] attributes=null;
        int nextId=1; // start with 1 (if table is populated start with max ID + 1)
        boolean idsGiven=false; // one of the attributes is ID
        // get conn
        try {
            conn=getConnection();
            st=conn.createStatement();
            row=st.executeQuery("select max(ID) from "+table);
            if(row.first()!=false) {
                nextId=row.getInt(1)+1;
                if(nextId>1)
                    log.LG(Logger.WRN,"Table "+table+" is already populated, starting with ID="+nextId);
            }
        }catch(SQLException ex) {
            log.LG(Logger.ERR,"Insert SQLException: " + ex.getMessage());
            log.LG(Logger.ERR,"Insert SQLState: " + ex.getSQLState());
            log.LG(Logger.ERR,"Insert VendorError: " + ex.getErrorCode());
            rc=UtilConst.ERR;
        }finally {
            if(row!=null) {
                try { row.close(); } catch (SQLException ex) { }
                row=null;
            }
        }
        if(rc==UtilConst.ERR) {
            try { st.close(); } catch (SQLException ex) { }
            return rc;
        }
        // load it
        try {
            BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(fileName), dataEncoding));
            String line;
            int lno=0;
            int obj=0;
            Pattern patAttrCnt = Pattern.compile("^A\\s*=\\s*([0-9]+)\\s*");
            int attrsFound=0;

            // read attribute names and types (e.g. name varchar(255) \t age int \t ...  )
            while((line=in.readLine())!=null) {
                lno++;
                // log.LG(log.TRC,"'"+line+"'");
                line=line.trim();
                if(line.length()==0 || line.matches("^\\s*#.*") || line.matches("[^\\t \\n\\r]+")) { // comments or empty
                    // log.LG(log.TRC,fileName+"."+lno+": Comments");
                    continue;
                }
                if(line.matches("^\\s*%.*")) { // attribute definition(s)
                    line=line.replaceFirst("^\\s*%\\s*","");
                    // attr count
                    Matcher matAttrCnt=patAttrCnt.matcher(line);
                    if(matAttrCnt.matches()) {
                        try {
                            attrCnt=Integer.parseInt(matAttrCnt.group(1));
                            if(attrCnt<=0) {
                                attrCnt=0;
                                throw(new NumberFormatException(">=1"));
                            }
                            attributes=new Attrib[attrCnt];
                            if(log.IFLG(Logger.INF)) log.LG(Logger.INF,"Attribute count = "+attrCnt);
                        }catch(NumberFormatException ex) {
                            log.LG(Logger.ERR,"Number of attributes must be a positive integer: "+ex);
                            return -1;
                        }
                        continue;
                    }
                    // attributes
                    String[] attrs=line.split("\\s*[,;]\\s*");
                    int cnt=attrs.length;
                    if(attrs[attrs.length-1].matches("[^\\t \\n\\r]+")) {
                        cnt--;
                    }
                    if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,fileName+"."+lno+": "+cnt+" attributes");
                    if(attrsFound+cnt>attrCnt) {
                        log.LG(Logger.ERR,fileName+"."+lno+": "+attrsFound+" attributes found but A="+attrCnt);
                        return -1;
                    }
                    for(int i=0;i<cnt;i++) {
                        String[] pair=attrs[i].split("[ \t]+");
                        if(pair.length!=2) {
                            log.LG(Logger.ERR,fileName+"."+lno+": Error reading attribute "+attrs[i]);
                            return -1;
                        }
                        attributes[attrsFound+i]=new Attrib(pair[0],pair[1]);
                        if(pair[0].toLowerCase().equals("id"))
                            idsGiven=true;
                    }
                    attrsFound+=cnt;
                    continue;
                }
                lno--;
                break; // we are already reading the beef
            }
            if(attrCnt==0 || attrCnt!=attrsFound) {
                log.LG(Logger.ERR,fileName+"."+lno+": No attribute definitions found or wrong number of attributes");
                return -1;
            }

            // read and insert the beef
            while(line!=null || (line=in.readLine())!=null) {
                lno++;
                line=line.trim();
                if(line.length()==0 || line.matches("^\\s*#.*")) { // comments or empty
                    // log.LG(log.TRC,fileName+"."+lno+": Comments");
                    line=null;
                    continue;
                }
                if(log.IFLG(Logger.TRC)) log.LG(Logger.TRC,"Adding "+line);
                // String.split ignores any trailing delimiters - workaround by adding non-empty attribute "karel"
                String[] attrs=(line+"\t"+"karel").split(delim);
                if(attrs.length-1!=attrCnt) {
                    log.LG(Logger.ERR,fileName+"."+lno+": Found "+(attrs.length-1)+" attributes expected "+attrCnt);
                    StringBuffer x=new StringBuffer(line.length());
                    for(int i=0;i<(attrs.length-1);i++) {
                        x.append("\n"+i+". ");
                        x.append(attrs[i]);
                    }
                    log.LG(Logger.ERR,x.toString());
                    return -1;
                }
                String sql="insert into "+table+" ";
                if(!idsGiven)
                    sql+="set ID="+nextId;
                nextId++;
                for(int i=0; i<attrCnt; i++) {
                    Attrib a=attributes[i];
                    sql+=", "+a.name+"="+((a.dbType==java.sql.Types.VARCHAR)? "?": attrs[i]);
                }
                PreparedStatement ps=null;
                try {
                    ps=conn.prepareStatement(sql);
                    int j=1;
                    for(int i=0; i<attrCnt; i++) {
                        Attrib a=attributes[i];
                        if(a.dbType==java.sql.Types.VARCHAR) {
                            ps.setString(j,attrs[i]);
                            j++;
                        }
                    }
                    int cnt=ps.executeUpdate();
                    if(cnt!=1) {
                        log.LG(Logger.ERR,"Could not insert: " + sql);
                        return -1;
                    }else {
                        obj++;
                    }
                }catch(SQLException ex) {
                    log.LG(Logger.ERR,"Insert SQLException: " + ex.getMessage());
                    log.LG(Logger.ERR,"Insert SQLState: " + ex.getSQLState());
                    log.LG(Logger.ERR,"Insert VendorError: " + ex.getErrorCode());
                    log.LG(Logger.ERR,"SQL: " + sql);
                    return -1;
                }finally {
                    if(ps!=null) {
                        try{ ps.close(); } catch(SQLException ex) {}
                        ps=null;
                    }
                }
                line=null;
                if(obj%10==0) {
                    System.out.print("\rObjects "+obj);
                }
            }
            System.out.print("\rObjects "+obj+"\n");
            try { st.close(); st=null; } catch(SQLException ex) { ; }
            in.close();
        } catch(IOException e) {
            log.LG(Logger.ERR,"I/O Error"+e);
            return -1;
        }
        return rc;
    }

}
