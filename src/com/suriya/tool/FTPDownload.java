package com.suriya.tool;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.log4j.Logger;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

public class FTPDownload extends SimpleDownload {

	final static Logger log = Logger.getLogger(FTPDownload.class);
	private FTPClient ftpClient;
	private int port = 21;

	public FTPDownload(URL url) {
		super(url);
		
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
			log.error("Not able to make the connection SFTP to server:"+getServer()
			+", port:"+port+", user:"+getUser()+", with error:"+ex.getMessage());
            error();
        } catch (IOException ex) {
        	log.error("Not able to make the connection SFTP to server:"+getServer()
			+", port:"+port+", user:"+getUser()+", with error:"+ex.getMessage());
            error();
        } 		
	}
	
	
	public void run() {		
	
		String fileName = getFileName(url);		
		String directoryName = getDownloadPath();
		createDownloadPath(directoryName);
				
		try {			
			connect();				
			long contentLength = 0;
			try {
				contentLength = getFileSize(ftpClient, fileName);
			} catch (Exception e) {				
				log.error("Not able to get the remote file size with the error:"+e.getMessage());				
				error();
			}		      
		    	    
		    if (contentLength < 1) {		      
			   error();
			}
		    
		    
		    //Check file exist on the local machine and then set the length		     
		    File currentLocalFile = new File(directoryName, fileName);
		    long offset = currentLocalFile.exists()? currentLocalFile.length() : 0;		    
		  	    
			//Set the size for this download if it hasn't been already set.			
			if (size == -1) {
				size = contentLength;
				stateChanged();
			}
		
		   log.info("File:"+fileName+", size:"+contentLength);	
			
		   InputStream remoteFileIs = ftpClient.retrieveFileStream(fileName);
		    String remoteFileMD5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(remoteFileIs);	  		    
		    remoteFileIs.close();
		    disconnect(ftpClient);
		    
		    String localFileMD5 = "";
		    if(currentLocalFile.exists()) {
		    	InputStream currentLocalFileIs = new FileInputStream(currentLocalFile);
		    	localFileMD5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(currentLocalFileIs);	  		    
		    	currentLocalFileIs.close();
		    } 
		    
		    log.info("Remote file MD5:"+remoteFileMD5+", Local file MD5:"+localFileMD5);
		   
		   
		   //long assumeDiskSpaceSize = 100000L;
		   
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
			      	
		  
     	   boolean downloadFileSuccess = false;      	       	
     	       	   
     	   while(status == DOWNLOADING) {       		      		  
     		   log.info("Downloading the file:"+getFileName(url)+" from server:"+getServer() +", with user:"+getUser()+".");
     		   connect();		
   	     	   InputStream inputStream = retrieveFileStream(ftpClient, fileName, offset);
     		   writeFile(inputStream, directoryName, fileName, append);
     		   
     		   downloadFileSuccess = ftpClient.completePendingCommand();
     		   if (downloadFileSuccess) {
     			   log.info("The file "+fileName+" has been downloaded successfully.");
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
