package com.suriya.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Observable;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.log4j.Logger;

public abstract class SimpleDownload extends Observable implements Runnable {

	final static Logger log = Logger.getLogger(SimpleDownload.class);
	
	protected String user = "";
	protected String pass = "";
	protected String server = "";	

	// Max size of download buffer.
	public static final int MAX_BUFFER_SIZE = 1024;

	// These are the status names.
	public static final String STATUSES[] = { "Downloading", "Paused", "Complete", "Cancelled", "Error" };

	// These are the status codes.
	public static final int DOWNLOADING = 0;
	public static final int PAUSED = 1;
	public static final int COMPLETE = 2;
	public static final int CANCELLED = 3;
	public static final int ERROR = 4;

	protected URL url; // download URL
	protected long size; // size of download in bytes
	protected long downloaded; // number of bytes downloaded
	protected int status; // current status of download

	protected Properties prop = null;
	protected Properties systemProp = null;
			
	// Constructor for Download.
	public SimpleDownload(URL url) {
		this.url = url;
		size = -1;
		downloaded = 0L;
		status = DOWNLOADING;

		loadProperties();
		// Begin the download.
		download();
	}

	void loadProperties () {
		prop = new Properties();
		InputStream input = null;
		systemProp = System.getProperties();
		try {
			input = new FileInputStream("config.properties");
			// load a properties file
			prop.load(input);
			// get the property value and print it out
			System.out.println("BASE.DOWNLOAD.PATH:"+prop.getProperty("BASE.DOWNLOAD.PATH"));			

		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public long getFreeDiskSpace() {  	
		long freeSpace = 0L;
		String directoryName =  getDownloadPath();
		File currentDownloadPath = new File(directoryName);
		freeSpace = currentDownloadPath.getFreeSpace();
		return freeSpace;
	}
	
	public void createDownloadPath(String downloadPath) {
		File tempDir = new File(downloadPath);
		try {
			FileUtils.forceMkdir(tempDir);
		} catch (IOException e) {
			log.error("Error when trying to create sub download path"+e);
			e.printStackTrace();
		}
	}
	
	public String getDownloadPath() {  	
		String downloadPath = prop.getProperty("BASE.DOWNLOAD.PATH", systemProp.getProperty("user.dir"));
		String newDownloadPath= downloadPath.concat(File.separator).concat(getSubDownloadPath(server));
		log.info("newDownloadPath:"+newDownloadPath);
		return newDownloadPath;
	}
	
	public String getSubDownloadPath(String host) {
		String subDownloadPath = host.replace(".", "_");
		return subDownloadPath;
	}
	
	// Get this download's URL.
	public String getUrl() {
		String protocol = url.getProtocol();
		String currentUrl = url.toString();
		
		if ((protocol.equals("sftp") || protocol.equals("ftp")) && currentUrl.contains(",")) {
			String[] tempUrl = currentUrl.split(",");  
			currentUrl = tempUrl[0];
		}
		
		return currentUrl;
	}

	// Get this download's size.
	public long getSize() {
		return size;
	}

	public String getUser() {
		return user;
	}
	
	public String getPass() {
		return pass;
	}

	public String getServer() {
		return server;
	}	
	
	// Get this download's progress.
	public float getProgress() {
		return ((float) downloaded / size) * 100;
	}

	// Get this download's status.
	public int getStatus() {
		return status;
	}

	// Pause this download.
	public void pause() {
		status = PAUSED;
		stateChanged();
	}

	// Resume this download.
	public void resume() {
		status = DOWNLOADING;
		stateChanged();
		download();
	}

	// Cancel this download.
	public void cancel() {
		status = CANCELLED;
		stateChanged();
	}

	// Mark this download as having an error.
	protected void error() {
		status = ERROR;
		stateChanged();
	}

	// Start or resume downloading.
	protected void download() {
		Thread thread = new Thread(this);
		thread.start();
	}

	// Get file name portion of URL.
	protected String getFileName(URL url) {

		String protocol = url.getProtocol();
		String fileName = url.getFile().replace("/", "");		
		server = url.getHost();
		log.debug("protocol: " + protocol);
		log.debug("fileName: " + url.getFile());
		
		if ((protocol.equals("sftp") || protocol.equals("ftp")) && fileName.contains(",")) {
			String[] temp = fileName.split(",");
			if (temp != null) {
				fileName = temp[0];
				user = temp[1];
				pass = temp[2];
				
				log.debug("fileName: " + fileName);
				log.debug("user: " + user);
				log.debug("pass: " + pass);
			}
		}				
		return fileName;
	}

	@Override
	public void run() {
	}

	// Notify observers that this download's status has changed.
	protected void stateChanged() {
		setChanged();
		notifyObservers();
	}

	protected boolean checkDiskFreeSpaceForFile(long machineSpache, long fileSize) {
		boolean isDiskSpaceEnough =  (machineSpache>fileSize)? true: false;
		return isDiskSpaceEnough;		
	}
	
	protected void writeFile(InputStream inputStream, String destDirectory, String file, boolean append) {
		FileOutputStream fileOutputStream = null;
		CountingOutputStream cos = null;
		try {
			fileOutputStream = new FileOutputStream(destDirectory + "/" + file, append);

			cos = new CountingOutputStream(fileOutputStream) {
				protected void beforeWrite(int n) {
					super.beforeWrite(n);
					downloaded = getByteCount();
					stateChanged();
				}
			};

			IOUtils.copy(inputStream, cos);
			cos.flush();			
		} catch (FileNotFoundException e) {			
			e.printStackTrace();
			log.error(e.getMessage());
			error();
		} catch (IOException e) {			
			e.printStackTrace();
			log.error(e.getMessage());
			error();
		} finally {
			IOUtils.closeQuietly(cos);
			IOUtils.closeQuietly(fileOutputStream);
			IOUtils.closeQuietly(inputStream);
		}
	}
	
	
}
