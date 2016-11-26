package com.suriya.tool;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
	private Channel channel     = null;
	private ChannelSftp channelSftp = null;
	  
	public SFTPDownload(URL url) {
		super(url);		
	}

	private void connect() {
		try {
			JSch jsch = new JSch();
			session = jsch.getSession(getUser(),getServer(),port);
			session.setPassword(getPass());
			java.util.Properties config = new java.util.Properties();
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			session.connect();
			channel = session.openChannel("sftp");
			channel.connect();
			channelSftp = (ChannelSftp)channel;
			///channelSftp.cd(SFTPWORKINGDIR);
		} catch (JSchException e) {
			log.error("Not able to make the connection SFTP with server:"+getServer()+", port:"+port+", user:"+getUser());
			 error();
		}
	}
	
	
	public void run() {
		
		    String fileName = getFileName(url);
		    String directoryName = getDownloadPath();
			createDownloadPath(directoryName);
		
			try{
		        	
		    	    connect();
		        	long contentLength = channelSftp.lstat(fileName).getSize();		
		        	log.info("File:"+getFileName(url)+", size:"+contentLength);
					// Check for valid content length.				
					if (contentLength < 1) {
					   error();
					}
					
					//Check file exist on the local machine and then set the length		
					File currentLocalFile = new File(directoryName, fileName);
					long offset = currentLocalFile.exists()? currentLocalFile.length() : 0;
					
					// Set the size for this download if it hasn't been already set.					 
					if (size == -1) {
						size =  contentLength;
						stateChanged();
					}
					
					log.info("File:"+fileName+", size:"+contentLength);	
					
					InputStream remoteFileIs = channelSftp.get(fileName);
					String remoteFileMD5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(remoteFileIs);	  		    
					remoteFileIs.close();
					disconnect(session, channelSftp);
					    
					String localFileMD5 = "";
					if(currentLocalFile.exists()) {
					   	InputStream currentLocalFileIs = new FileInputStream(currentLocalFile);
					   	localFileMD5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(currentLocalFileIs);	  		    
					   	currentLocalFileIs.close();
					} 
					    
					log.info("Remote file MD5:"+remoteFileMD5+", Local file MD5:"+localFileMD5);
					
					 boolean isDiskSpaceEnough = checkDiskFreeSpaceForFile(getFreeDiskSpace(),contentLength);
					   if(!isDiskSpaceEnough) {
						   String errorMsg = "The free disk space size("
					            +FileUtils.byteCountToDisplaySize(getFreeDiskSpace())+") is less than download "
					            + "file size("+FileUtils.byteCountToDisplaySize(contentLength)+"). Download can be be done.";
						   log.error(errorMsg);
						   DownloadManager.showErrorMessage(errorMsg);
						   error();
					 }   
					   
					 if (offset == size) {
						    DownloadManager.showErrorMessage("The file '"+fileName+"' is already existing in the path '"+currentLocalFile.getPath()+"'");  
							status = COMPLETE;
							stateChanged();
					 }				 	  
							
					 int mode = 0;
					 boolean append = false;
					 if (offset == 0) {
						 mode=ChannelSftp.OVERWRITE;
					 } else if (offset<size) {  
						 mode=ChannelSftp.RESUME;	
						 append = true;
					 }
					 
					 while(status == DOWNLOADING) {  
						 log.info("Downloading the file:"+getFileName(url)+" from server:"+getServer() +", with user:"+getUser()+".");
						 connect();	
						 InputStream inputStream = channelSftp.get(fileName, mode);
						 writeFile(inputStream, directoryName, fileName, append); 			     	

						 if (status == DOWNLOADING) {
							 status = COMPLETE;
							 stateChanged();
						 }
						 log.info("Finish downloading the file: "+directoryName + File.separator + fileName);
					 }
		        } catch(Exception ex){
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
		if(session.isConnected()) {
			session.disconnect();
		}		
	}
	
}
