package com.suriya.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;

import org.apache.commons.io.FileUtils;
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

		String fileName = getFileName(uri);
		String directoryName = getDownloadPath();
		createDownloadPath(directoryName);
		String errorMsg = "";
		
		try {
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
			}

			if (contentLength == 0) {
				errorMsg = "The size of remote file (" + fileName+ ") is 0. Please check the remote source file again.";
				log.error(errorMsg);
				DownloadManager.showErrorMessage(errorMsg);
				error();
				disconnect(ftpClient);
			}

			File currentLocalFile = new File(directoryName, fileName);

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
			}

			// Set the local file length
			long offset = currentLocalFile.exists() ? currentLocalFile.length() : 0;

			// Set the size for this download if it hasn't been already set.
			if (size == -1) {
				size = contentLength;
				stateChanged();
			}

			log.info("File:" + fileName + ", size:" + contentLength);

			// long assumeDiskSpaceSize = 100000L;

			boolean isDiskSpaceEnough = checkDiskFreeSpaceForFile(getFreeDiskSpace(), contentLength);
			if (!isDiskSpaceEnough) {
				errorMsg = "The free disk space size(" + FileUtils.byteCountToDisplaySize(getFreeDiskSpace())
						+ ") is less than download " + "file size(" + FileUtils.byteCountToDisplaySize(contentLength)
						+ "). Download can not be done.";
				log.error(errorMsg);
				DownloadManager.showErrorMessage(errorMsg);
				error();
			}
			
			boolean append = false;
			
			if (offset != 0 && offset != size) {				
				append = true;
			}

			boolean downloadFileSuccess = false;

			while (status == DOWNLOADING) {
				log.info("Downloading the file:" + fileName + " from server:" + getServer() + ", with user:" + getUser()+ ".");
				connect();
				InputStream inputStream = retrieveFileStream(ftpClient, fileName, offset);
				writeFile(inputStream, directoryName, fileName, append);

				downloadFileSuccess = ftpClient.completePendingCommand();
				if (downloadFileSuccess) {
					log.info("The file " + fileName + " has been downloaded successfully.");
					status = COMPLETE;
					stateChanged();
				} else {
					error();
				}
			}
		} catch (IOException ex) {
			log.error("Error: " + ex.getMessage());
			ex.printStackTrace();
			error();
		} finally {
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
