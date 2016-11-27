package com.suriya.tool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.net.ftp.FTPClient;
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

		String fileName = getFileName(uri);
		String directoryName = getDownloadPath();
		createDownloadPath(directoryName);
		String errorMsg = "";
		System.out.println("Server:" + server + ", user:" + user + ", pass:" + pass);
		try {

			connect();
			long contentLength = 0L;

			try {
				contentLength = channelSftp.lstat(fileName).getSize();
				log.info("File:" + fileName + ", size:" + contentLength);
			} catch (Exception e) {
				errorMsg = "Not able to get the size of remote file (" + fileName + ") with the error: "
						+ e.getMessage();
				log.error(errorMsg);
				DownloadManager.showErrorMessage(errorMsg);
				error();
				disconnect(session, channelSftp);
			}

			// Check for valid content length.
			if (contentLength == 0) {
				errorMsg = "The size of remote file (" + fileName+ ") is 0. Please check the remote source file again.";
				log.error(errorMsg);
				DownloadManager.showErrorMessage(errorMsg);
				error();
				disconnect(session, channelSftp);
			}

			File currentLocalFile = new File(directoryName, fileName);
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
			}

			int mode = 0;
			boolean append = false;
			if (offset == 0) {
				mode = ChannelSftp.OVERWRITE;
			} else if (offset < size) {
				mode = ChannelSftp.RESUME;
				append = true;
			}

			while (status == DOWNLOADING) {
				log.info("Downloading the file:" + fileName + " from server:" + getServer() + ", with user:" + getUser()
						+ ".");
				connect();
				InputStream inputStream = channelSftp.get(fileName, mode);
				writeFile(inputStream, directoryName, fileName, append);

				if (status == DOWNLOADING) {
					status = COMPLETE;
					stateChanged();
				}
				log.info("Finish downloading the file: " + directoryName + File.separator + fileName);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			error();
		} finally {
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
