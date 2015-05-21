// $Id: TaskWindow.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.ui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Vector;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.FileDialog;
import javax.swing.BorderFactory;
import javax.swing.border.TitledBorder;
import java.awt.Font;
import java.awt.Color;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.event.KeyEvent;
import javax.swing.JTextArea;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import java.awt.ComponentOrientation;
import javax.swing.SwingConstants;
import javax.swing.JFileChooser;
import javax.swing.table.AbstractTableModel;
import javax.swing.JSplitPane;

import uep.util.Logger;

import medieq.iet.model.*;
import medieq.iet.api.IETException;
import medieq.iet.generic.*;

public class TaskWindow extends JFrame implements TaskListener {

	private static final long serialVersionUID = 1L;

	private JPanel jContentPane = null;  //  @jve:decl-index=0:visual-constraint="13,40"
	private JPanel panelProps = null;
	private JPanel panelDocs = null;
	private JPanel panelButts = null;
	private JButton buttSave = null;
	private JButton buttCancel = null;
	private JButton buttRun = null;
	private JButton buttDelete = null;
	private JPanel panelName = null;
	private JTextField fldName = null;
	private JScrollPane scrollPane = null;
	private JTable tableFiles = null;
	private JPanel panelDocButts = null;
	private JButton buttAdd = null;
	private JButton buttDocDel = null;
	private JPanel panelDesc = null;
	private JTextArea areaDesc = null;
	private JLabel jLabel = null;
	private JLabel jLabel1 = null;
	private JPanel panelModel = null;
	private JPanel panelLastRun = null;
	private JLabel jLabel2 = null;
	private JLabel labProps = null;
	private JComboBox comboModel = null;
	private JButton buttModel = null;
	protected TaskWindow instance = null;
	private JPanel panelFile = null;
	private JTextField fldFile = null;
	private JLabel jLabel3 = null;
    private JButton buttOk = null;
    private JSplitPane splitPane = null;
	protected ExtFileFilter docFilter = null; 
	protected JFileChooser fileChooser = null;
    protected FileDialog fileDialog = null;

    protected String fileName = null;
	protected Task task;  //  @jve:decl-index=0:
	protected MainWindow mainWin = null;
	protected Vector<Document> docList = null;
    protected Logger log;
	
	public int rc = 0;
	public static final int RC_DELETE=1;
	public static final int RC_SAVE=2;
	public static final int RC_OK=3;
	public static final int RC_CANCEL=4;
	
	/**
	 * This is the default constructor
	 */
	public TaskWindow(MainWindow parent) {
		super();
		instance=this;
		mainWin=parent;
		docList=new Vector<Document>(16);
		rc=0;
        log=Logger.getLogger("TWin");
		initialize();
	}

	/**
	 * This method initializes this
	 * 
	 * @return void
	 */
	private void initialize() {
		this.setSize(809, 335);
		this.setName("taskFrame");
		this.setTitle("Extraction task");
		this.setContentPane(getJContentPane());
		this.addWindowListener(new java.awt.event.WindowAdapter() {   
			public void windowClosing(java.awt.event.WindowEvent e) {
				log.LG(Logger.TRC,"twin closed");
			}
			public void windowOpened(java.awt.event.WindowEvent e) {
                log.LG(Logger.TRC,"twin opened");
			}
		});
		fileDialog=new FileDialog(this, "Open extraction model", FileDialog.LOAD);
		fileChooser=new JFileChooser();
		docFilter = new ExtFileFilter("HTML and text documents");
		docFilter.add(".html");
		docFilter.add(".htm");
		docFilter.add(".xml");
		docFilter.add(".xhtml");
		docFilter.add(".txt");
		fileChooser.setFileFilter(docFilter);
	}

	/**
	 * This method initializes jContentPane
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getJContentPane() {
		if (jContentPane == null) {
			jContentPane = new JPanel();
			jContentPane.setLayout(new BorderLayout());
			jContentPane.setSize(new Dimension(800, 300));
			jContentPane.setName("contentPane");
			jContentPane.setPreferredSize(new Dimension(800, 300));
			jContentPane.add(getSplitPane(), BorderLayout.CENTER);
			jContentPane.add(getPanelButts(), BorderLayout.SOUTH);
		}
		return jContentPane;
	}

	/**
	 * This method initializes panelProps	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelProps() {
		if (panelProps == null) {
			panelProps = new JPanel();
			panelProps.setLayout(new BoxLayout(getPanelProps(), BoxLayout.Y_AXIS));
			panelProps.setBorder(BorderFactory.createTitledBorder(null, "Properties", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Dialog", Font.BOLD, 12), new Color(51, 51, 51)));
			//panelProps.setPreferredSize(new Dimension(400, 300));
			panelProps.add(getPanelName(), null);
			panelProps.add(getPanelFile(), null);
			panelProps.add(getPanelModel(), null);
			panelProps.add(getPanelDesc(), null);
			panelProps.add(getPanelLastRun(), null);
		}
		return panelProps;
	}

	/**
	 * This method initializes panelProps	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelDocs() {
		if (panelDocs == null) {
			panelDocs = new JPanel();
			panelDocs.setLayout(new BorderLayout());
			panelDocs.setBorder(BorderFactory.createTitledBorder(null, "Documents", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Dialog", Font.BOLD, 12), new Color(51, 51, 51)));
			//panelDocs.setPreferredSize(new Dimension(400, 300));
			panelDocs.add(getScrollPane(), BorderLayout.CENTER);
			panelDocs.add(getPanelDocButts(), BorderLayout.SOUTH);
		}
		return panelDocs;
	}

	/**
	 * This method initializes panelProps	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelButts() {
		if (panelButts == null) {
			panelButts = new JPanel();
			panelButts.setName("panelButts");
			panelButts.setLayout(new BoxLayout(getPanelButts(), BoxLayout.X_AXIS));
			panelButts.setPreferredSize(new Dimension(800, 30));
			panelButts.add(getButtOk(), null);
			panelButts.add(getButtSave(), null);
			panelButts.add(getButtCancel(), null);
			panelButts.add(getButtDelete(), null);
			panelButts.add(getButtRun(), null);
		}
		return panelButts;
	}

	protected void updateTask() {
		task.setName(fldName.getText());
		task.setDesc(areaDesc.getText());
		task.setFile(fldFile.getText());
		String modelUrl=null;
		if(comboModel.getSelectedItem()!=null) {
			modelUrl=comboModel.getSelectedItem().toString();
		}
		task.setModelUrl(modelUrl);
		task.setDocuments(docList);
	}
	
	protected boolean saveTask() {
		try {
		    BufferedWriter bw=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(task.getFile())), "utf-8"));
			mainWin.taskFactory.writeTask(task, bw);
            log.LG(Logger.TRC,"Ok wrote "+task.getFile());
		}catch(IOException ex) {
            log.LG(Logger.ERR,"Error writing task file: "+ex);
			return false;
		}
		return true;
	}
	
	/**
	 * This method initializes buttSave	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButtSave() {
		if (buttSave == null) {
			buttSave = new JButton();
			buttSave.setName("buttSave");
			buttSave.setText("Save");
			buttSave.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					rc=RC_SAVE;
					updateTask();
					boolean askForFile=true;
					if(task.getFile()!=null && task.getFile().trim().length()>0) {
						File f=new File(task.getFile());
						if(f.exists())
							askForFile=false;
					}
					if(askForFile) {
						FileDialog fileDialog=mainWin.fileDialog;
						fileDialog.setMode(FileDialog.SAVE);
						fileDialog.setVisible(true);
						String dir=fileDialog.getDirectory();
						String file=fileDialog.getFile();
						task.setFile(dir+file);
					}
					if(task.getFile()!=null) {
						saveTask();
					}
					mainWin.handleTaskChanged();
				}
			});
		}
		return buttSave;
	}

	/**
	 * This method initializes buttCancel	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButtCancel() {
		if (buttCancel == null) {
			buttCancel = new JButton();
			buttCancel.setName("buttCancel");
			buttCancel.setText("Cancel");
			buttCancel.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					rc=RC_CANCEL;
					mainWin.handleTaskChanged();
					instance.setVisible(false);
				}
			});
		}
		return buttCancel;
	}

	/**
	 * This method initializes buttRun	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButtRun() {
		if (buttRun == null) {
			buttRun = new JButton();
			buttRun.setName("buttRun");
			buttRun.setText("Run");
			buttRun.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					if(buttRun.getText().equals("Run")) {
						updateTask();
                        log.LG(Logger.INF,"Running task "+task.getName()+" ...");
//						task.setEngine(mainWin.getSelectedEngine());
						task.addListener(instance);
                        try {
                            task.start();
                        }catch(IETException ex) {
                            log.LG(Logger.ERR,"Could not start task");
                        }
					}else {
                        log.LG(Logger.INF,"Stopping task "+task.getName()+" ...");
						task.stop();
					}
				}
			});
		}
		return buttRun;
	}

	/**
	 * This method initializes buttDelete	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButtDelete() {
		if (buttDelete == null) {
			buttDelete = new JButton();
			buttDelete.setText("Delete");
			buttDelete.setName("buttDelete");
			buttDelete.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					rc=RC_DELETE;
					mainWin.handleTaskChanged();
					instance.setVisible(false);
				}
			});
		}
		return buttDelete;
	}

	/**
	 * This method initializes panelName	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelName() {
		if (panelName == null) {
			jLabel = new JLabel();
			jLabel.setText("Task name");
			jLabel.setPreferredSize(new Dimension(63, 2));
			panelName = new JPanel();
			panelName.setLayout(new BorderLayout());
			panelName.setPreferredSize(new Dimension(0, 18));
			panelName.add(getFldName(), BorderLayout.EAST);
			panelName.add(jLabel, BorderLayout.WEST);
		}
		return panelName;
	}

	/**
	 * This method initializes fldName	
	 * 	
	 * @return javax.swing.JTextField	
	 */
	private JTextField getFldName() {
		if (fldName == null) {
			fldName = new JTextField();
			fldName.setName("fldName");
			fldName.setToolTipText("Task name");
			fldName.setPreferredSize(new Dimension(295, 20));
			fldName.setMaximumSize(new Dimension(20000, 20));
			fldName.setText("Task name");
		}
		return fldName;
	}

	/**
	 * This method initializes scrollPane	
	 * 	
	 * @return javax.swing.JScrollPane	
	 */
	private JScrollPane getScrollPane() {
		if (scrollPane == null) {
			scrollPane = new JScrollPane();
			scrollPane.setViewportView(getTableFiles());
		}
		return scrollPane;
	}

	/**
	 * This method initializes tableFiles	
	 * 	
	 * @return javax.swing.JTable	
	 */
	private JTable getTableFiles() {
		if (tableFiles == null) {
			DocumentTableModel dtm=new DocumentTableModel(docList);
			tableFiles = new JTable(dtm);
			tableFiles.addMouseListener(new java.awt.event.MouseAdapter() {
				public void mouseClicked(java.awt.event.MouseEvent e) {
					if (e.getClickCount() == 2) {
						int i=tableFiles.getSelectedRow();
						if(i<0 || i>=docList.size())
							return;
						Document doc=docList.get(i);
						String docFile=doc.getFile();
						boolean showLabeled=(tableFiles.getSelectedColumn()>3);
						//log.LG(Logger.TRC,"selcol="+tableFiles.getSelectedColumn());
						String fileToShow=getDocumentUrl(docFile, showLabeled);

						Runtime r=Runtime.getRuntime();
						try {
							String exec=mainWin.getBrowserExecutable()+" "+fileToShow;
                            log.LG(Logger.TRC,"Running browser "+exec);
							r.exec(exec);
						}catch(IOException ex) {
                            log.LG(Logger.ERR,"Cannot launch browser: "+ex);
						}
					}
				}
			});
		}
		return tableFiles;
	}

	private String getDocumentUrl(String fname, boolean labeled) {
		if(fname.startsWith("file:///")) {
			fname=fname.substring(8);
		}
		File f=new File(fname);
		String abs=f.getAbsolutePath();
		// check if labeled version exists
		if(labeled) {
			String lfname=abs+".lab.html";
			File check=new File(lfname);
			if(check.exists()) {
				abs+=".lab.html";
			}else {
                log.LG(Logger.ERR,"Labeled doc does not exist: "+lfname);
			}
		}
		abs="file:///"+abs;
		return abs;
	}

	/**
	 * This method initializes panelDocButts	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelDocButts() {
		if (panelDocButts == null) {
			panelDocButts = new JPanel();
			panelDocButts.setLayout(new BoxLayout(getPanelDocButts(), BoxLayout.X_AXIS));
			panelDocButts.add(getButtAdd(), null);
			panelDocButts.add(getButtDocDel(), null);
		}
		return panelDocButts;
	}

	/**
	 * This method initializes buttAdd	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButtAdd() {
		if (buttAdd == null) {
			buttAdd = new JButton();
			buttAdd.setName("buttAdd");
			buttAdd.setText("Add...");
			buttAdd.setMnemonic(KeyEvent.VK_UNDEFINED);
			buttAdd.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					fileChooser.setDialogTitle("Select documents to extract from");
					fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
					fileChooser.setCurrentDirectory(new File("."));
					fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
					fileChooser.setMultiSelectionEnabled(true);
					fileChooser.setApproveButtonText("Add selected documents");
					int returnVal = fileChooser.showDialog(instance, null);

					if(returnVal != JFileChooser.APPROVE_OPTION)
						return;

					File[] selList=fileChooser.getSelectedFiles();
                    log.LG(Logger.TRC,"Selected document/directory count: "+selList.length);
					for(int i=0;i<selList.length;i++) {
						File f=selList[i];
                        log.LG(Logger.TRC,"D"+(i+1)+": "+f.getAbsolutePath());
						Document added=new DocumentImpl(null, f.getAbsolutePath());
						if(!docList.contains(added)) {
							docList.add(added);
						}
					}
                    log.LG(Logger.TRC,"calling updateUI");
					tableFiles.updateUI();
                    log.LG(Logger.TRC,"called updateUI");
					/*
					fileDialog.setTitle("Add files to extract from");
					fileDialog.setDirectory(".");
					fileDialog.setMode(FileDialog.LOAD);
					fileDialog.setVisible(true);
					String dir=fileDialog.getDirectory();
					String file=fileDialog.getFile();
					System.err.println("You selected "+dir+file);
					if(file!=null) {
						File f=new File(dir+file);
						if(f.exists()) {
							Document d=new DocumentImpl(dir+file);
							task.addDocument(d);
						}else {
							System.err.println("Document does not exist: "+dir+file);
						}
					}
					*/
				}
			});
		}
		return buttAdd;
	}

	/**
	 * This method initializes buttDocDel	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButtDocDel() {
		if (buttDocDel == null) {
			buttDocDel = new JButton();
			buttDocDel.setText("Delete selection");
			buttDocDel.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					int[] idxs=tableFiles.getSelectedRows();
					java.util.Arrays.sort(idxs);
					int rem=0;
					for(int i=0;i<idxs.length;i++) {
						docList.remove(idxs[i]-rem);
						rem++;
					}
					tableFiles.updateUI();
				}
			});
		}
		return buttDocDel;
	}

	/**
	 * This method initializes panelDesc	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelDesc() {
		if (panelDesc == null) {
			jLabel1 = new JLabel();
			jLabel1.setText("Task description");
			panelDesc = new JPanel();
			panelDesc.setLayout(new BoxLayout(getPanelDesc(), BoxLayout.X_AXIS));
			panelDesc.setPreferredSize(new Dimension(0, 150));
			panelDesc.add(jLabel1, null);
			panelDesc.add(getAreaDesc(), null);
		}
		return panelDesc;
	}

	/**
	 * This method initializes areaDesc	
	 * 	
	 * @return javax.swing.JTextArea	
	 */
	private JTextArea getAreaDesc() {
		if (areaDesc == null) {
			areaDesc = new JTextArea();
			areaDesc.setText("Task description");
			areaDesc.setPreferredSize(new Dimension(290, 200));
			areaDesc.setTabSize(4);
		}
		return areaDesc;
	}

	/**
	 * This method initializes panelModel	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelModel() {
		if (panelModel == null) {
			jLabel2 = new JLabel();
			jLabel2.setText("Extraction model");
			panelModel = new JPanel();
			panelModel.setLayout(new BoxLayout(getPanelModel(), BoxLayout.X_AXIS));
			panelModel.setPreferredSize(new Dimension(0, 22));
			panelModel.add(jLabel2, null);
			panelModel.add(getComboModel(), null);
			panelModel.add(getButtModel(), null);
		}
		return panelModel;
	}

	/**
	 * This method initializes panelLastRun	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelLastRun() {
		if (panelLastRun == null) {
			labProps = new JLabel();
			labProps.setText("Properties");
			labProps.setDisplayedMnemonic(KeyEvent.VK_UNDEFINED);
			labProps.setHorizontalAlignment(SwingConstants.LEADING);
			labProps.setHorizontalTextPosition(SwingConstants.LEFT);
			labProps.setName("labProps");
			labProps.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
			panelLastRun = new JPanel();
			panelLastRun.setLayout(new BoxLayout(getPanelLastRun(), BoxLayout.X_AXIS));
			panelLastRun.setPreferredSize(new Dimension(0, 16));
			panelLastRun.add(labProps, null);
		}
		return panelLastRun;
	}

	/**
	 * This method initializes comboModel	
	 * 	
	 * @return javax.swing.JComboBox	
	 */
	private JComboBox getComboModel() {
		if (comboModel == null) {
			comboModel = new JComboBox();
		}
		return comboModel;
	}

	/**
	 * This method initializes buttModel	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButtModel() {
		if (buttModel == null) {
			buttModel = new JButton();
			buttModel.setText("...");
			buttModel.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					fileDialog.setTitle("Choose extraction model");
					//fileDialog.setDirectory(".");
					fileDialog.setMode(FileDialog.LOAD);
					fileDialog.setVisible(true);
					String dir=fileDialog.getDirectory();
					String file=fileDialog.getFile();
                    log.LG(Logger.TRC,"You selected "+dir+file);
					if(file!=null) {
						File f=new File(dir+file);
						task.setModelUrl(f.getAbsolutePath());
						selectComboItem(f.getAbsolutePath());
					}
				}
			});
		}
		return buttModel;
	}
	
	public void setTask(Task task) {
		this.task=task;
		fldName.setText(task.getName());
		areaDesc.setText(task.getDesc());
		fldFile.setText(task.getFile());
		labProps.setText("last run: "+task.getLastRun().toString());
		selectComboItem(task.getModelUrl());
		docList.clear();
		task.populateDocuments(docList);
		tableFiles.updateUI();
	}

	/**
	 * This method initializes panelFile	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelFile() {
		if (panelFile == null) {
			jLabel3 = new JLabel();
			jLabel3.setText("File");
			jLabel3.setToolTipText("File");
			panelFile = new JPanel();
			panelFile.setLayout(new BorderLayout());
			panelFile.setName("panelFile");
			panelFile.setPreferredSize(new Dimension(42, 18));
			panelFile.add(jLabel3, BorderLayout.WEST);
			panelFile.add(getFldFile(), BorderLayout.EAST);
		}
		return panelFile;
	}

	/**
	 * This method initializes fldFile	
	 * 	
	 * @return javax.swing.JTextField	
	 */
	private JTextField getFldFile() {
		if (fldFile == null) {
			fldFile = new JTextField();
			fldFile.setName("fldFile");
			fldFile.setPreferredSize(new Dimension(295, 20));
			fldName.setMaximumSize(new Dimension(20000, 20));
		}
		return fldFile;
	}

	/**
	 * This method initializes buttOk	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButtOk() {
		if (buttOk == null) {
			buttOk = new JButton();
			buttOk.setText("OK");
			buttOk.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					rc=RC_OK;
					updateTask();
					mainWin.handleTaskChanged();
					instance.setVisible(false);
				}
			});
		}
		return buttOk;
	}
	
	protected int selectComboItem(String model) {
		if(model==null) {
			comboModel.setSelectedItem(null);
			return -1;
		}
		int cnt=comboModel.getItemCount();
		int i;
		for(i=0;i<cnt;i++) {
			if(model.equals(comboModel.getItemAt(i)))
				break;
		}
		if(i==cnt) {
			comboModel.insertItemAt(model, 0);
			i=0;
		}
		comboModel.setSelectedIndex(i);
		return i;
	}

	protected class DocumentTableModel extends AbstractTableModel {
		static final long serialVersionUID=123456;
		String[] columnNames = {"Id", "File", "Size", "State", "Attributes", "Instances"};
		Object[] sampleValues = {"0", "file", new Integer(0), new Boolean(false), new Integer(0), new Integer(0)};
		List<Document> docList;
		
		public DocumentTableModel(List<Document> docList) {
			this.docList=docList;
		}
		public String getColumnName(int col) {
	        return columnNames[col].toString();
	    }
		public Class getColumnClass(int c) {
			return sampleValues[c].getClass();
	    }
	    public int getRowCount() {
	    	return docList.size();
	    }
	    public int getColumnCount() { 
	    	return columnNames.length; 
	    }
	    public Object getValueAt(int row, int col) {
	    	Document doc=docList.get(row);
	    	Object rc="ERR";
	    	switch(col) {
	    	case 0: rc=String.valueOf(doc.getId()); break;
	    	case 1: rc=(doc.getFile()!=null)? doc.getFile(): ""; break;
	    	case 2:	rc=String.valueOf(doc.getSize()); break;
	    	case 3: rc=new Boolean(doc.getProcessedModels().contains(task.getModelUrl())); break;
	    	case 4: rc=new Integer((doc.getAttributeValues()!=null)? doc.getAttributeValues().size(): 0); break;
	    	case 5: rc=new Integer((doc.getInstances()!=null)? doc.getInstances().size(): 0); break;
	    	}
	    	//System.err.println("["+row+","+col+"]="+rc);
	        return rc;
	    }
	    public boolean isCellEditable(int row, int col) { 
	    	return false;
	    }
	    public void setValueAt(Object value, int row, int col) {
	    	// ignore
	        fireTableCellUpdated(row, col);
	    }
	}

	public void onDocumentProcessed(Task task, int idx, Document doc) {
		this.setTitle("Processed "+(idx+1)+" of "+task.getDocumentCount());
		if(!doc.getProcessedModels().contains(task.getModelUrl()))
			doc.getProcessedModels().add(task.getModelUrl());
		tableFiles.updateUI();
		if(docList.size()>(idx+1)) {
			tableFiles.changeSelection(idx+1, 0, false, false);
		}
	}
	
	public void onStateChange(Task task, int state) {
		switch(state) {
		case Task.STATE_IDLE:
            log.LG(Logger.TRC,"removing listener");
			task.removeListener(this);
			buttRun.setText("Run");
			buttRun.setEnabled(true);
			break;
		case Task.STATE_STOPPING:
			buttRun.setEnabled(false);
			buttRun.setText("Stopping");
			break;
		case Task.STATE_EXECUTING:
			buttRun.setText("Stop");
			buttRun.setEnabled(true);
			for(int i=0;i<docList.size();i++) {
				docList.get(i).getProcessedModels().remove(task.getModelUrl());
			}
			tableFiles.updateUI();
			if(docList.size()>0) {
				tableFiles.changeSelection(0, 0, false, false);
			}
			break;
		}
	}

	/**
	 * This method initializes splitPane	
	 * 	
	 * @return javax.swing.JSplitPane	
	 */
	private JSplitPane getSplitPane() {
		if (splitPane == null) {
			splitPane = new JSplitPane();
			splitPane.setDividerLocation(400);
			splitPane.setLeftComponent(getPanelProps());
			splitPane.setRightComponent(getPanelDocs());
		}
		return splitPane;
	}
	
}  //  @jve:decl-index=0:visual-constraint="6,9"
