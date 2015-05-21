// $Id: ExtFileFilter.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.ui;

import java.io.*;
import java.util.*;

public class ExtFileFilter extends javax.swing.filechooser.FileFilter implements java.io.FilenameFilter {
	protected Vector<String> extList=new Vector<String>(8);
	protected String desc;
	
	public ExtFileFilter(String description) {
		setDescription(description);
	}
	
	protected boolean accept(String name) {
		for(int i=0;i<extList.size();i++) {
			if(name.endsWith(extList.get(i)))
				return true;
		}
		return false;
	}
	
	public boolean accept(File f) {
		return f.isDirectory() || accept(f.getName());
	}
	
	public boolean accept(File dir, String name) {
		File f=new File(dir, name);
		return f.isDirectory() || accept(name);
	}
	
	public void add(String ext) {
		if(!ext.startsWith("."))
			ext='.'+ext;
		for(int i=0;i<extList.size();i++) {
			if(ext.equals(extList.get(i)))
				return;
		}
		extList.add(ext);
	}
	
	public void clear() {
		extList.clear();
	}
	
	public void setDescription(String description) {
		desc=(description!=null)? description: "Filter";
	}
	
	public String getDescription() {
		return desc;
	}
}
