package com.suriya.tool;

import java.io.*;
import java.net.*;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

// This class downloads a file from a URL.
class HTTPDownload extends SimpleDownload {

	final static Logger log = Logger.getLogger(HTTPDownload.class);
	private HttpURLConnection connection;
	
	public HTTPDownload(URI uri) {
		super(uri);		
	}

	private void connect(long currerntRange) {
		// Open connection to URL.
		URL url = null;
		try {
			url = new URL(getUrl());
			log.info("url:"+getUrl());
			connection = (HttpURLConnection) url.openConnection();		
			// Specify what portion of file to download.
			connection.setRequestProperty("Range", "bytes=" + currerntRange + "-");
			// Connect to server.
			connection.connect();
		} catch (MalformedURLException e) {
			log.error("Error when trying to make HTTP connection: "+e.getMessage());
			error();
		} catch (IOException e) {
			log.error("Error when trying to make HTTP connection: "+e.getMessage());
			error();
		}
	}
	
	// Download file.
	public void run() {
		RandomAccessFile file = null;
		InputStream stream = null;
		String errorMsg = "";

		try {
			String fileName = getFileName(uri);
			String directoryName = getDownloadPath();			
			createDownloadPath(directoryName);
			
			connect(downloaded);				
			
			// Make sure response code is in the 200 range.
			if (connection.getResponseCode() / 100 != 2) {
				error();
				return;
			}

			// Check for valid content length.
			int contentLength = connection.getContentLength();
			log.info("File:" + fileName + ", size:" + contentLength);
			if (contentLength < 1) {
				error();
				return;
			}

			File currentLocalFile = new File(directoryName, fileName);
			InputStream remoteFileIs = connection.getInputStream();	
			String remoteFileMD5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(remoteFileIs);
			remoteFileIs.close();
			disconnect(connection);

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
			
			/*
			 * Set the size for this download if it hasn't been already set.
			 */
			if (size == -1) {
				size = contentLength;
				stateChanged();
			}
			
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

			connect(downloaded);
			
			// Make sure response code is in the 200 range.
			if (connection.getResponseCode() / 100 != 2) {
				error();
				return;
			}
			
			stream = connection.getInputStream();
			while (status == DOWNLOADING) {
				/*
				 * Size buffer according to how much of the file is left to
				 * download.
				 */
				byte buffer[];
				if (size - downloaded > MAX_BUFFER_SIZE) {
					buffer = new byte[MAX_BUFFER_SIZE];
				} else {
					buffer = new byte[(int) (size - downloaded)];
				}

				// Read from server into buffer.
				int read = stream.read(buffer);
				//log.info("read:"+read);
				if (read == -1)
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
		} catch (Exception e) {
			error();
		} finally {
			// Close file.
			if (file != null) {
				try {
					file.close();
				} catch (Exception e) {
				}
			}
			// Close connection to server.
			if (stream != null) {
				try {
					stream.close();
				} catch (Exception e) {
				}
			}
			disconnect(connection);
		}
	}
	
	private void disconnect(HttpURLConnection connection) {
		if (connection != null) 
			connection.disconnect();		
	}

}