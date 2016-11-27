package com.suriya.tool;

import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.commons.validator.routines.UrlValidator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

// The Download Manager.
public class DownloadManager extends JFrame implements Observer {

	final static Logger log = Logger.getLogger(DownloadManager.class);

	// Add download text field.
	private JTextField addTextField;

	// Download table's data model.
	private DownloadsTableModel tableModel;

	// Table listing downloads.
	private JTable table;

	// These are the buttons for managing the selected download.
	private JButton pauseButton, resumeButton;
	private JButton cancelButton, clearButton;

	// Currently selected download.
	// private HTTPDownload selectedDownload;
	private SimpleDownload selectedDownload;

	// Flag for whether or not table selection is being cleared.
	private boolean clearing;

	// Constructor for Download Manager.
	public DownloadManager() {
		// Set application title.
		setTitle("Java Download Manager");

		// Set window size.
		setSize(640, 480);
		this.setLocationRelativeTo(null);

		// Handle window closing events.
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				actionExit();
			}
		});

		// Set up file menu.
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		JMenuItem fileExitMenuItem = new JMenuItem("Exit", KeyEvent.VK_X);
		fileExitMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionExit();
			}
		});
		fileMenu.add(fileExitMenuItem);
		menuBar.add(fileMenu);
		setJMenuBar(menuBar);

		// Set up add panel.
		JPanel addPanel = new JPanel();
		addTextField = new JTextField(30);
		addPanel.add(addTextField);
		JButton addButton = new JButton("Add Download");
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionAdd();
			}
		});
		addPanel.add(addButton);

		// Set up Downloads table.
		tableModel = new DownloadsTableModel();
		table = new JTable(tableModel);

		//Set the size for each table column
		table.getColumnModel().getColumn(0).setPreferredWidth(220);
		table.getColumnModel().getColumn(1).setPreferredWidth(100);
		table.getColumnModel().getColumn(2).setPreferredWidth(100);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

		//Set the right and left margin for the SIZE column 
		DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer() {
			Border padding = BorderFactory.createEmptyBorder(0, 10, 0, 10);
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				setBorder(BorderFactory.createCompoundBorder(getBorder(), padding));
				return this;
			}
		};
		
		//Set the text alignment for the column no.1, and 3
		rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
		table.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);

		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(JLabel.CENTER);
		table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				tableSelectionChanged();
			}
		});
		// Allow only one row at a time to be selected.
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Set up ProgressBar as renderer for progress column.
		ProgressRenderer renderer = new ProgressRenderer(0, 100);
		renderer.setStringPainted(true); // show progress text
		table.setDefaultRenderer(JProgressBar.class, renderer);

		// Set table's row height large enough to fit JProgressBar.
		table.setRowHeight((int) renderer.getPreferredSize().getHeight());

		// Set up downloads panel.
		JPanel downloadsPanel = new JPanel();
		downloadsPanel.setBorder(BorderFactory.createTitledBorder("Downloads"));
		downloadsPanel.setLayout(new BorderLayout());
		downloadsPanel.add(new JScrollPane(table), BorderLayout.CENTER);

		// Set up buttons panel.
		JPanel buttonsPanel = new JPanel();
		pauseButton = new JButton("Pause");
		pauseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionPause();
			}
		});
		pauseButton.setEnabled(false);
		buttonsPanel.add(pauseButton);

		resumeButton = new JButton("Resume");
		resumeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionResume();
			}
		});
		resumeButton.setEnabled(false);
		buttonsPanel.add(resumeButton);

		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionCancel();
			}
		});
		cancelButton.setEnabled(false);
		buttonsPanel.add(cancelButton);

		clearButton = new JButton("Clear");
		clearButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				actionClear();
			}
		});
		clearButton.setEnabled(false);
		buttonsPanel.add(clearButton);

		// Add panels to display.
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(addPanel, BorderLayout.NORTH);
		getContentPane().add(downloadsPanel, BorderLayout.CENTER);
		getContentPane().add(buttonsPanel, BorderLayout.SOUTH);
	}

	// Exit this program.
	private void actionExit() {
		System.exit(0);
	}

	// Add a new download.
	private void actionAdd() {

		log.info("The input Download URL:" + addTextField.getText());
		URI verifiedUri = verifyUri(addTextField.getText());
		if (verifiedUri != null) {
			log.info("verifiedUri:" + verifiedUri.toString());
			String protocol = verifiedUri.toString().substring(0, verifiedUri.toString().indexOf("://"));
			if (protocol.equals("http"))
				tableModel.addDownload(new HTTPDownload(verifiedUri));
			else if (protocol.equals("ftp"))
				tableModel.addDownload(new FTPDownload(verifiedUri));
			else
				tableModel.addDownload(new SFTPDownload(verifiedUri));

			addTextField.setText(""); // reset add text field
		} else {
			String errorMsg = "Invalid Download URL:" + addTextField.getText();
			log.error(errorMsg);
			showErrorMessage(errorMsg);
		}
	}

	public static void showErrorMessage(String message) {
		JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	// Verify download URI.
	private URI verifyUri(String uri) {
		URI verifiedUri = null;
		String[] schemes = { "http", "ftp", "sftp" };

		UrlValidator urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS);
		if (!urlValidator.isValid(uri)) {
			log.info("uri: " + uri + ", isvalid: " + urlValidator.isValid(uri));
			return null;
		}

		try {
			verifiedUri = new URI(uri);
		} catch (URISyntaxException e) {
			log.error("Error when validating uri: " + e.getMessage());
			return null;
		}

		return verifiedUri;
	}

	public static int ordinalIndexOf(String str, String substr, int n) {
		int pos = str.indexOf(substr);
		while (--n > 0 && pos != -1)
			pos = str.indexOf(substr, pos + 1);
		return pos;
	}

	// Called when table row selection changes.
	private void tableSelectionChanged() {
		/*
		 * Unregister from receiving notifications from the last selected
		 * download.
		 */
		if (selectedDownload != null)
			selectedDownload.deleteObserver(DownloadManager.this);

		/*
		 * If not in the middle of clearing a download, set the selected
		 * download and register to receive notifications from it.
		 */
		if (!clearing) {
			int currentRow = table.getSelectedRow();
			log.debug("currentRow:"+currentRow);
			if(currentRow>=0) {
				selectedDownload = tableModel.getDownload(table.getSelectedRow());		
				selectedDownload.addObserver(DownloadManager.this);
				updateButtons();
			}	
		}
	}

	// Pause the selected download.
	private void actionPause() {
		selectedDownload.pause();
		updateButtons();
	}

	// Resume the selected download.
	private void actionResume() {
		selectedDownload.resume();
		updateButtons();
	}

	// Cancel the selected download.
	private void actionCancel() {
		selectedDownload.cancel();
		updateButtons();
	}

	// Clear the selected download.
	private void actionClear() {
		clearing = true;
		tableModel.clearDownload(table.getSelectedRow());
		clearing = false;
		selectedDownload = null;
		updateButtons();
	}

	/*
	 * Update each button's state based off of the currently selected download's
	 * status.
	 */
	private void updateButtons() {
		if (selectedDownload != null) {
			int status = selectedDownload.getStatus();
			switch (status) {
			case SimpleDownload.DOWNLOADING:
				pauseButton.setEnabled(true);
				resumeButton.setEnabled(false);
				cancelButton.setEnabled(true);
				clearButton.setEnabled(false);
				break;
			case SimpleDownload.PAUSED:
				pauseButton.setEnabled(false);
				resumeButton.setEnabled(true);
				cancelButton.setEnabled(true);
				clearButton.setEnabled(false);
				break;
			case SimpleDownload.ERROR:
				pauseButton.setEnabled(false);
				resumeButton.setEnabled(true);
				cancelButton.setEnabled(false);
				clearButton.setEnabled(true);
				break;
			default: // COMPLETE or CANCELLED
				pauseButton.setEnabled(false);
				resumeButton.setEnabled(false);
				cancelButton.setEnabled(false);
				clearButton.setEnabled(true);
			}
		} else {
			// No download is selected in table.
			pauseButton.setEnabled(false);
			resumeButton.setEnabled(false);
			cancelButton.setEnabled(false);
			clearButton.setEnabled(false);
		}
	}

	/*
	 * Update is called when a Download notifies its observers of any changes.
	 */
	public void update(Observable o, Object arg) {
		// Update buttons if the selected download has changed.
		if (selectedDownload != null && selectedDownload.equals(o))
			updateButtons();
	}

	// Run the Download Manager.
	public static void main(String[] args) {

		DownloadManager manager = new DownloadManager();
		// manager.show();
		manager.setVisible(true);

		PropertyConfigurator.configure("log4j.properties");
	}
}
