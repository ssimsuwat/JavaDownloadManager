package com.suriya.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SFTPDownload extends SimpleDownload {

	final static Logger log = Logger.getLogger(SFTPDownload.class);

	private int port = 22;
	private Session session = null;
	private Channel channel = null;
	private ChannelSftp channelSftp = null;

	public SFTPDownload(URI uri) {
		super(uri);
	}

	private void connect() {
		try {
			JSch jsch = new JSch();
			session = jsch.getSession(getUser(), getServer(), port);
			session.setPassword(getPass());
			java.util.Properties config = new java.util.Properties();
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			session.connect();
			channel = session.openChannel("sftp");
			channel.connect();
			channelSftp = (ChannelSftp) channel;
			/// channelSftp.cd(SFTPWORKINGDIR);
		} catch (JSchException e) {
			log.error("Not able to make the connection SFTP with server:" + getServer() + ", port:" + port + ", user:"
					+ getUser());
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
			
			connect();
			long contentLength = 0L;

			try {
				contentLength = channelSftp.lstat(fileName).getSize();
				log.info("File:" + fileName + ", size:" + contentLength);
			} catch (Exception e) {
				errorMsg = "Not able to get the size of remote file (" + fileName + ") with the error: "+ e.getMessage();
				log.error(errorMsg);
				DownloadManager.showErrorMessage(errorMsg);
				error();
				disconnect(session, channelSftp);
				return;
			}	

			File currentLocalFile = new File(directoryName, fileName);
			
			//The code to get the MD5 from server server and local
			InputStream remoteFileIs = channelSftp.get(fileName);
			String remoteFileMD5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(remoteFileIs);
			remoteFileIs.close();
			disconnect(session, channelSftp);

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

			// Set the size for this download if it hasn't been already set.
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
					
			file = new RandomAccessFile(directoryName+File.separator+fileName, "rw");
			file.seek(downloaded);

			connect();
			InputStream inputStream = channelSftp.get(fileName);
			log.info("Downloading the file:" + fileName + " from server:" + getServer() + ", with user:" + getUser());
			while (status == DOWNLOADING) {				
			
				byte buffer[];
				if (size - downloaded > MAX_BUFFER_SIZE) {
					buffer = new byte[MAX_BUFFER_SIZE];
				} else {
					buffer = new byte[(int) (size - downloaded)];
				}

				// Read from server into buffer.
				int read = inputStream.read(buffer);			
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
		} catch (Exception ex) {
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
			disconnect(session, channelSftp);
		}
	}

	private void disconnect(Session session, ChannelSftp channelSftp) {
		if (channelSftp.isConnected()) {
			channelSftp.disconnect();
		}
		if (session.isConnected()) {
			session.disconnect();
		}
	}

}
