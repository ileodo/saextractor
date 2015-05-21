// $Id: MainWindow.java 1643 2008-09-12 21:56:20Z labsky $
package medieq.iet.ui;

import java.io.*;
import java.util.*;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JFrame;
import java.awt.Dimension;
import javax.swing.JFileChooser;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import java.awt.GridBagLayout;
import javax.swing.JButton;
import java.awt.GridBagConstraints;
import javax.swing.SwingConstants;
import javax.swing.JList;
import java.awt.Toolkit;
import javax.swing.BoxLayout;
import java.awt.Panel;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import java.awt.FileDialog;
import javax.swing.ComboBoxModel;

import uep.util.Logger;
import medieq.iet.model.*;
import medieq.iet.api.IETApi;
import medieq.iet.api.IETException;
import medieq.iet.components.*;

public class MainWindow extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel jContentPane = null;
	private JMenu menu = null;
	private JMenuItem menuItemExit = null;
	private JMenuItem menuItemOpen = null;
	private JTabbedPane tabPane = null;
	private JPanel panelDMM = null;
	private JPanel panelTM = null;
	private JButton butRun = null;
	private JList listTasks = null;
	private JButton butNew = null;
	private Panel panelButts = null;
	private Panel panelList = null;

	protected Task curTask = null;
	private Vector<Task> tasks = null;
	private TaskWindow twin = null;
	private JButton butLoad = null;
	private JButton butRemove = null;
	private JComboBox comboModels = null;
	private JButton butModels = null;
	private JEditorPane editModel = null;
	private JMenuBar mainMenu = null;
	private JPanel panNorth = null;
	private JLabel labModel = null;
	
	protected TaskFactory taskFactory = null;
	protected ExtFileFilter taskFileFilter = null;
	protected ExtFileFilter modelFileFilter = null;  //  @jve:decl-index=0:
	protected JFileChooser fileChooser = null;
	protected FileDialog fileDialog = null;
	protected String fileName = null;
	
	protected MainWindow instance = null;
	private JPanel panBrowseModels = null;
	private JPanel panBrowseEngines = null;
	private JLabel labEngine = null;
	private JComboBox comboEngines = null;
	private JButton butEngines = null;
	
	IETApi iet = null;
	// protected Vector<Engine> engines = null;
	protected EngineBoxModel engineBoxModel = null;  

    protected Logger log;
	protected Properties cfg;
	protected String browserExecutable="C:/Progra~1/Mozilla Firefox/firefox.exe";
    
	public String getBrowserExecutable() {
	    return browserExecutable;
	}

	/**
	 * This is the default constructor
	 * @param iet 
	 */
	public MainWindow(IETApi iet) {
		super();
        this.iet=iet;
		this.instance=this;
		this.tasks=new Vector<Task>(16);
		// this.engines=new Vector<Engine>(4);
		this.taskFactory=new TaskFactory(iet);
        this.log=Logger.getLogger("MWin");
		initialize();
	}

	/**
	 * This method initializes this
	 * 
	 * @return void
	 */
	private void initialize() {
		this.setSize(788, 452);
		this.setJMenuBar(getMainMenu());
		this.setIconImage(Toolkit.getDefaultToolkit().getImage("res/green.png"));
		this.setContentPane(getJContentPane());
		this.setTitle("IE Toolkit");
		this.addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(java.awt.event.WindowEvent e) {
				System.exit(0);
			}
		});
		twin=new TaskWindow(this);
		fileChooser=new JFileChooser();
		taskFileFilter=new ExtFileFilter("Task files (*.task)");
		taskFileFilter.add(".task");
		fileChooser.setFileFilter(taskFileFilter);
		
		modelFileFilter=new ExtFileFilter("Model files (*.xml)");
		fileDialog=new FileDialog(this, "Open extraction model", FileDialog.LOAD);
		fileDialog.setFilenameFilter(modelFileFilter);

		cfg=new Properties();
		try {
		    cfg.load(new FileInputStream(new File("iet.cfg")));
		    if(cfg.containsKey("browser")) {
		        browserExecutable=(String) cfg.get("browser");
		    }
		}catch(IOException ex) {
            log.LG(Logger.WRN,"Cannot load iet.cfg; using default settings: "+ex);
		}
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
			jContentPane.add(getTabPane(), BorderLayout.CENTER);
		}
		return jContentPane;
	}

	/**
	 * This method initializes menu	
	 * 	
	 * @return javax.swing.JMenu	
	 */
	private JMenu getMenu() {
		if (menu == null) {
			menu = new JMenu();
			menu.setName("fileMenu");
			menu.setText("File");
			menu.add(getMenuItemExit());
			menu.add(getMenuItemOpen());
		}
		return menu;
	}

	/**
	 * This method initializes menuItemExit	
	 * 	
	 * @return javax.swing.JMenuItem	
	 */
	private JMenuItem getMenuItemExit() {
		if (menuItemExit == null) {
			menuItemExit = new JMenuItem();
			menuItemExit.setText("Exit");
			menuItemExit.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					System.exit(0);
				}
			});
		}
		return menuItemExit;
	}

	/**
	 * This method initializes menuItemOpen	
	 * 	
	 * @return javax.swing.JMenuItem	
	 */
	private JMenuItem getMenuItemOpen() {
		if (menuItemOpen == null) {
			menuItemOpen = new JMenuItem();
			menuItemOpen.setText("Open...");
			menuItemOpen.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
				selectModel();	
				}
			});
		}
		return menuItemOpen;
	}

	/**
	 * This method initializes tabPane	
	 * 	
	 * @return javax.swing.JTabbedPane	
	 */
	private JTabbedPane getTabPane() {
		if (tabPane == null) {
			tabPane = new JTabbedPane();
			tabPane.setName("tabPane");
			tabPane.addTab("Data Model Manager", null, getPanelDMM(), null);
			tabPane.addTab("Task Manager", null, getPanelTM(), null);
		}
		return tabPane;
	}

	/**
	 * This method initializes panelDMM	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelDMM() {
		if (panelDMM == null) {
			panelDMM = new JPanel();
			panelDMM.setLayout(new BoxLayout(getPanelDMM(), BoxLayout.Y_AXIS));
			panelDMM.setName("panelDMM");
			panelDMM.add(getPanNorth(), null);
			panelDMM.add(getEditModel(), null);
		}
		return panelDMM;
	}

	/**
	 * This method initializes panelTM	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanelTM() {
		if (panelTM == null) {
			panelTM = new JPanel();
			panelTM.setLayout(new BoxLayout(getPanelTM(), BoxLayout.Y_AXIS));
			panelTM.setName("panelTM");
			panelTM.add(getPanelList(), null);
			panelTM.add(getPanelButts(), null);
			panelTM.addKeyListener(new java.awt.event.KeyListener() {
				public void keyTyped(java.awt.event.KeyEvent e) {
                    log.LG(Logger.WRN,"keyTyped()");
				}
				public void keyPressed(java.awt.event.KeyEvent e) {
				}
				public void keyReleased(java.awt.event.KeyEvent e) {
				}
			});
		}
		return panelTM;
	}

	/**
	 * This method initializes butRun	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButRun() {
		if (butRun == null) {
			butRun = new JButton();
			butRun.setName("butRun");
			butRun.setVerticalAlignment(SwingConstants.BOTTOM);
			butRun.setHorizontalAlignment(SwingConstants.LEFT);
			butRun.setText("Run selected");
			butRun.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
                    log.LG(Logger.WRN,"actionPerformed()"); // TODO Auto-generated Event stub actionPerformed()
				}
			});
		}
		return butRun;
	}

	/**
	 * This method initializes listTasks	
	 * 	
	 * @return javax.swing.JList	
	 */
	private JList getListTasks() {
		if (listTasks == null) {
			listTasks = new JList();
			listTasks.setPreferredSize(new Dimension(20, 20));
			listTasks.setName("listTasks");
			listTasks.addMouseListener(new java.awt.event.MouseAdapter() {
				public void mouseClicked(java.awt.event.MouseEvent e) {
					if (e.getClickCount() == 2) {
			             int index = listTasks.locationToIndex(e.getPoint());
                         log.LG(Logger.WRN,"Double clicked item " + index);
			             if(index>=0 && index<listTasks.getModel().getSize()) {
			            	 curTask=(Task) listTasks.getModel().getElementAt(index);
			            	 twin.setTask(curTask);
			            	 twin.setVisible(true);
			             }
			        }
				}
			});
		}
		return listTasks;
	}

	/**
	 * This method initializes butNew	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButNew() {
		if (butNew == null) {
			butNew = new JButton();
			butNew.setText("New Task...");
			butNew.setHorizontalAlignment(SwingConstants.LEFT);
			butNew.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					curTask=new Task("New task", iet);
					twin.setTask(curTask);
					twin.setVisible(true);
				}
			});
		}
		return butNew;
	}

	/**
	 * This method initializes panelButts	
	 * 	
	 * @return java.awt.Panel	
	 */
	private Panel getPanelButts() {
		if (panelButts == null) {
			panelButts = new Panel();
			panelButts.setLayout(new BoxLayout(getPanelButts(), BoxLayout.X_AXIS));
			panelButts.add(getButNew(), null);
			panelButts.add(getButLoad(), null);
			panelButts.add(getButRun(), null);
			panelButts.add(getButRemove(), null);
		}
		return panelButts;
	}

	/**
	 * This method initializes panelList	
	 * 	
	 * @return java.awt.Panel	
	 */
	private Panel getPanelList() {
		if (panelList == null) {
			GridBagConstraints gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.fill = GridBagConstraints.BOTH;
			gridBagConstraints.weighty = 1.0;
			gridBagConstraints.weightx = 1.0;
			panelList = new Panel();
			panelList.setLayout(new GridBagLayout());
			panelList.add(getListTasks(), gridBagConstraints);
		}
		return panelList;
	}

	/**
	 * This method initializes butLoad	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButLoad() {
		if (butLoad == null) {
			butLoad = new JButton();
			butLoad.setName("butLoad");
			butLoad.setText("Load...");
			butLoad.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					fileChooser.setDialogTitle("Select tasks to load");
					fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
					fileChooser.setCurrentDirectory(new File("."));
					fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
					fileChooser.setMultiSelectionEnabled(true);
					fileChooser.setApproveButtonText("Load selected tasks");
					int returnVal = fileChooser.showDialog(instance, null);

					if(returnVal != JFileChooser.APPROVE_OPTION)
						return;
					
					File[] selList=fileChooser.getSelectedFiles();
                    log.LG(Logger.WRN,"Selected document count: "+selList.length);
					for(int i=0;i<selList.length;i++) {
						File f=selList[i];
						String absFile=f.getAbsolutePath(); // +"/"+f.getName()
                        log.LG(Logger.WRN,"T"+(i+1)+": "+absFile);
						Task task=null;
                        try {
                            task=taskFactory.readTask(absFile);
                        }catch(IETException ex) {
                            log.LG(Logger.ERR,"Could not read task "+absFile);
                        }
						if(task!=null && !tasks.contains(task)) {
                            log.LG(Logger.TRC,"Setting list data length="+tasks.size());
							tasks.add(task);
							listTasks.setListData(tasks);
						}
					}

				}
			});
		}
		return butLoad;
	}

	/**
	 * This method initializes butRemove	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButRemove() {
		if (butRemove == null) {
			butRemove = new JButton();
			butRemove.setText("Remove all selected");
			butRemove.setName("butRemove");
			butRemove.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					Object[] items=listTasks.getSelectedValues();
					for(int i=0;i<items.length;i++) {
						tasks.remove(items[i]);
					}
					listTasks.setListData(tasks);
				}
			});
		}
		return butRemove;
	}

	/**
	 * This method initializes comboModels	
	 * 	
	 * @return javax.swing.JComboBox	
	 */
	private JComboBox getComboModels() {
		if (comboModels == null) {
			comboModels = new JComboBox();
		}
		return comboModels;
	}

	/**
	 * This method initializes butModels	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButModels() {
		if (butModels == null) {
			butModels = new JButton();
			butModels.setText("..");
			butModels.setActionCommand("");
			butModels.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					selectModel();
				}
			});
		}
		return butModels;
	}

	/**
	 * This method initializes editModel	
	 * 	
	 * @return javax.swing.JEditorPane	
	 */
	private JEditorPane getEditModel() {
		if (editModel == null) {
			editModel = new JEditorPane();
			editModel.setPreferredSize(new Dimension(775, 342));
			editModel.setText("");
			editModel.setName("editModel");
		}
		return editModel;
	}

	/**
	 * This method initializes mainMenu	
	 * 	
	 * @return javax.swing.JMenuBar	
	 */
	private JMenuBar getMainMenu() {
		if (mainMenu == null) {
			mainMenu = new JMenuBar();
			mainMenu.add(getMenu());
		}
		return mainMenu;
	}

	/**
	 * This method initializes panNorth	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanNorth() {
		if (panNorth == null) {
			panNorth = new JPanel();
			panNorth.setLayout(new BoxLayout(getPanNorth(), BoxLayout.Y_AXIS));
			panNorth.setName("panModel");
			panNorth.add(getPanBrowseEngines(), null);
			panNorth.add(getPanBrowseModels(), null);
		}
		return panNorth;
	}

	protected void handleTaskChanged() {
		switch(twin.rc) {
		case TaskWindow.RC_OK:
		case TaskWindow.RC_SAVE:
			if(!tasks.contains(curTask)) { // new task
				tasks.add(curTask);
                log.LG(Logger.WRN,"Adding new task size="+tasks.size());
			}
			listTasks.setListData(tasks);
			break;
		case TaskWindow.RC_DELETE:
			if(tasks.contains(curTask)) { // ! new task
				tasks.remove(curTask);
				listTasks.setListData(tasks);
			}
			break;
		case TaskWindow.RC_CANCEL:
			break;
		}
        log.LG(Logger.WRN,"Left new handler size="+tasks.size());
	}

	/**
	 * This method initializes panBrowseModels	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanBrowseModels() {
		if (panBrowseModels == null) {
			labModel = new JLabel();
			labModel.setText("Extraction Models: ");
			labModel.setPreferredSize(new Dimension(123, 23));
			labModel.setName("labModel");
			panBrowseModels = new JPanel();
			panBrowseModels.setLayout(new BoxLayout(getPanBrowseModels(), BoxLayout.X_AXIS));
			panBrowseModels.setPreferredSize(new Dimension(189, 26));
			panBrowseModels.add(labModel, null);
			panBrowseModels.add(getComboModels(), null);
			panBrowseModels.add(getButModels(), null);
		}
		return panBrowseModels;
	}
	
	protected void selectModel() {
		fileDialog.setTitle("Open extraction model");
		fileDialog.setDirectory(".");
		fileDialog.setMode(FileDialog.LOAD);
		fileDialog.setVisible(true);
		String dir=fileDialog.getDirectory();
		String file=fileDialog.getFile();
        log.LG(Logger.WRN,"You selected "+dir+file);
		if(file!=null) {
			File f=new File(dir+file);
			StringBuffer buff=new StringBuffer((int)f.length());
			try {
				BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf8"));
				String s;
				while((s=br.readLine())!=null) {
					buff.append(s);
					buff.append("\n");
				}
				br.close();
				editModel.setText(buff.toString());
			}catch(IOException ex) {
                log.LG(Logger.ERR,"Cannot open extraction model file: "+ex);
			}
		}
	}

	/**
	 * This method initializes panBrowseEngines	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private JPanel getPanBrowseEngines() {
		if (panBrowseEngines == null) {
			labEngine = new JLabel();
			labEngine.setText("Extraction engines: ");
			labEngine.setPreferredSize(new Dimension(123, 23));
			labEngine.setName("labEngines");
			panBrowseEngines = new JPanel();
			panBrowseEngines.setLayout(new BoxLayout(getPanBrowseEngines(), BoxLayout.X_AXIS));
			panBrowseEngines.add(labEngine, null);
			panBrowseEngines.add(getComboEngines(), null);
			panBrowseEngines.add(getButEngines(), null);
		}
		return panBrowseEngines;
	}

	/**
	 * This method initializes comboEngines	
	 * 	
	 * @return javax.swing.JComboBox	
	 */
	private JComboBox getComboEngines() {
		if (comboEngines == null) {
			engineBoxModel = new EngineBoxModel(iet.getEngines());
			comboEngines = new JComboBox(engineBoxModel);
		}
		return comboEngines;
	}

	/**
	 * This method initializes butEngines	
	 * 	
	 * @return javax.swing.JButton	
	 */
	private JButton getButEngines() {
		if (butEngines == null) {
			butEngines = new JButton();
			butEngines.setText("..");
		}
		return butEngines;
	}
	
	public void addEngine(Configurable e) {
		if(!iet.getEngines().contains(e)) {
		    iet.getEngines().add(e);
			//if(engineBoxModel.getSelectedItem()==null)
			//	engineBoxModel.setSelectedItem(e);
			comboEngines.updateUI();
			if(comboEngines.getSelectedIndex()==-1)
				comboEngines.setSelectedIndex(0);
		}
	}
	
	public Configurable getSelectedEngine() {
		int i=comboEngines.getSelectedIndex();
		if(i>=0 && i<iet.getEngines().size())
			return iet.getEngines().get(i);
		return null;
	}
	
	protected class EngineBoxModel implements ComboBoxModel {
		protected List<Configurable> engineList;
		protected Object selItem;
		public EngineBoxModel(List<Configurable> engineList) {
			this.engineList=engineList;
		}
		public Object getElementAt(int i) {
		    Configurable e=engineList.get(i);
			if(e==null)
				return "n/a";
			return e.getName();
		}
		public int getSize() {
			return engineList.size();
		}
		public void setSelectedItem(Object anItem) {
			selItem=anItem;
		}
		public Object getSelectedItem() {
			return selItem;
		}
		public void addListDataListener(javax.swing.event.ListDataListener l) {
			return;
		}
		public void removeListDataListener(javax.swing.event.ListDataListener l) {
			return;
		}
	}
	
}  //  @jve:decl-index=0:visual-constraint="10,10"
