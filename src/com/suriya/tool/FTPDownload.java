package com.suriya.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.net.URI;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;


public class FTPDownload extends SimpleDownload {

	final static Logger log = Logger.getLogger(FTPDownload.class);
	private FTPClient ftpClient;
	private int port = 21;

	public FTPDownload(URI uri) {
		super(uri);

	}

	private void connect() {
		try {
			ftpClient = new FTPClient();
			ftpClient.connect(getServer(), port);
			ftpClient.login(getUser(), getPass());
			ftpClient.enterLocalPassiveMode();
			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			int reply = ftpClient.getReplyCode();
			if (FTPReply.isPositiveCompletion(reply)) {
				log.info("Connected to FTP server successfully.");
			} else {
				log.info("Connection to FTP server failed.");
				disconnect(ftpClient);
				error();
			}
		} catch (SocketException ex) {
			log.error("Not able to make the connection SFTP to server:" + getServer() + ", port:" + port + ", user:"
					+ getUser() + ", with error:" + ex.getMessage());
			error();
		} catch (IOException ex) {
			log.error("Not able to make the connection SFTP to server:" + getServer() + ", port:" + port + ", user:"
					+ getUser() + ", with error:" + ex.getMessage());
			error();
		}
	}

	public void run() {

		RandomAccessFile file = null;
		InputStream stream = null;	
		String errorMsg = "";
		
		try {
			String fileName = getFileName(uri);
			String directoryName = getDownloadPath();			
			createDownloadPath(directoryName);
		    log.info("Downloading the file:"+fileName+", URL:"+getUrl()+", dest directoryName"+directoryName); 
			
			connect();
			long contentLength = 0;
			try {
				contentLength = getFileSize(ftpClient, fileName);
			} catch (Exception e) {
				errorMsg = "Not able to get the size of remote file (" + fileName + ") with the error: "
						+ e.getMessage();
				log.error(errorMsg);
				DownloadManager.showErrorMessage(errorMsg);
				error();
				disconnect(ftpClient);
				return;
			}

			// Set the size for this download if it hasn't been already set.
			if (size == -1) {
				size = contentLength;
				stateChanged();
			}
			
			File currentLocalFile = new File(directoryName, fileName);          
			
			//Logic for retrieving the MD5 takes time to process when the file size is very big. Speed to download will be fast, if disable this feature.
			InputStream remoteFileIs = ftpClient.retrieveFileStream(fileName);
			String remoteFileMD5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(remoteFileIs);
			remoteFileIs.close();
			disconnect(ftpClient);

			String localFileMD5 = "";
			if (currentLocalFile.exists()) {
				InputStream currentLocalFileIs = new FileInputStream(currentLocalFile);
				localFileMD5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(currentLocalFileIs);
				currentLocalFileIs.close();
			}

			log.info("Remote file MD5:" + remoteFileMD5 + ", Local file MD5:" + localFileMD5);

			if (remoteFileMD5.length() > 0 && localFileMD5.length() > 0
					&& localFileMD5.equalsIgnoreCase(remoteFileMD5)) {
				errorMsg = "The remote file (" + fileName + ") already exists in the directory:" + directoryName;
				log.error(errorMsg);
				DownloadManager.showErrorMessage(errorMsg);
				status = COMPLETE;
				stateChanged();
				return;
			}            
         
			// Set the local file length
			long offset = currentLocalFile.exists() ? currentLocalFile.length() : 0;			

			// long assumeDiskSpaceSize = 100000L;

			boolean isDiskSpaceEnough = checkDiskFreeSpaceForFile(getFreeDiskSpace(), contentLength);
			if (!isDiskSpaceEnough) {
				errorMsg = "The free disk space size(" + FileUtils.byteCountToDisplaySize(getFreeDiskSpace())
						+ ") is less than download " + "file size(" + FileUtils.byteCountToDisplaySize(contentLength)
						+ "). Download can not be done.";
				log.error(errorMsg);
				DownloadManager.showErrorMessage(errorMsg);
				error();
				return;
			}		

			if(offset>0) {
				downloaded = offset;
				stateChanged();
			}
			
			// Open file and seek to the end of it.
			file = new RandomAccessFile(directoryName+File.separator+fileName, "rw");
			file.seek(downloaded);						
		
			connect();			
			stream = retrieveFileStream(ftpClient, fileName, offset);
			log.info("Downloading the file:" + fileName + " from server:" + getServer() + ", with user:" + getUser()+", current status:"+status);
			while (status == DOWNLOADING) {			
			
				byte buffer[];
				if (size - downloaded > MAX_BUFFER_SIZE) {
					buffer = new byte[MAX_BUFFER_SIZE];
				} else {
					buffer = new byte[(int) (size - downloaded)];
				}

				// Read from server into buffer.
				int read = stream.read(buffer);
				if (read == 0) 
					break;
				
				// Write buffer to file.
				file.write(buffer, 0, read);				
				downloaded += read;
				stateChanged();			
			}		
					
			/*
			 * Change status to complete if this point was reached because
			 * downloading has finished.
			 */
			if (status == DOWNLOADING) {
				status = COMPLETE;
				stateChanged();
			}	
			log.info("Finish downloading the file: " + directoryName + File.separator + fileName);
		} catch (IOException ex) {
			log.error("Error: " + ex.getMessage());
			ex.printStackTrace();
			error();
		} finally {
			// Close file.
			if (file != null) {
				try {
					file.close();
				} catch (Exception e) {
				}
			}			
			disconnect(ftpClient);
		}
	}

	private void disconnect(FTPClient ftpClient) {
		try {
			if (ftpClient.isConnected()) {
				ftpClient.logout();
				ftpClient.disconnect();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private long getFileSize(FTPClient ftp, String filePath) throws Exception {
		long fileSize = 0;
		FTPFile[] files = ftp.listFiles(filePath);
		if (files.length == 1 && files[0].isFile()) {
			fileSize = files[0].getSize();
		}
		return fileSize;
	}

	public InputStream retrieveFileStream(FTPClient ftp, String remote, long offset) throws IOException {
		ftp.setRestartOffset(offset);
		return ftp.retrieveFileStream(remote);
	}
}
