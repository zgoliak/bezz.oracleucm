package com.bezzotech.oracleucm.arx.service;

import intradoc.common.ExecutionContext;
import intradoc.common.FileUtils;
import intradoc.common.ServiceException;
import intradoc.data.DataBinder;
import intradoc.data.DataException;
import intradoc.filestore.FileStoreProvider;
import intradoc.filestore.IdcFileDescriptor;
import intradoc.server.Service;

import java.io.File;
import java.util.HashMap;

import com.bezzotech.oracleucm.arx.shared.SharedObjects;

public class FileStoreUtils {
	/** Flag to storeTempFile to disable automatic cleanup of the temp file. */
	public final int F_NO_CLEANUP = 0x10000000;

	/** The context for this request. */
	public ExecutionContext m_context;

	/** DataBinder The DataBinder for this request. */
	public DataBinder m_binder;

	/** SharedObjects pointer to use for this request. */
	public SharedObjects m_shared;

	/** The filestore. */
	public FileStoreProvider m_fileStore;

	/** Utilities for the filestore. */
	public intradoc.filestore.FileStoreUtils m_utils;

	protected FileStoreUtils( ExecutionContext context ) throws ServiceException {
		m_context = context;
		m_shared = SharedObjects.getSharedObjects( m_context );
		if ( m_context instanceof Service ) {
			Service s = ( Service )m_context;
			m_fileStore = s.m_fileStore;
			m_binder = s.getBinder();
		} else {
			m_fileStore = ( FileStoreProvider )context.getCachedObject( "FileStoreProvider" );
			if ( m_fileStore == null ) {
				throw new ServiceException( "!$AJK FileStoreProvider not found in context." );
			}
		}
		m_utils = new intradoc.filestore.FileStoreUtils();
	}

	/** Return a working FileStoreUtils object for a service.
		* @param context ExecutionContext to find a FileStoreProvider in.
		* @throws ServiceException if a FileStoreProvider cannot be found.
		* @return a ready-to-use FileStoreUtils object.
		*/
	static public FileStoreUtils getFileStoreUtils( ExecutionContext context ) 
			throws ServiceException {
		return new FileStoreUtils( context );
	}

	/** Return a temporary file path.  
		* @param extension Extension to put on the file name.  
		* @param nameFlags If this contains F_NO_CLEANUP do not register the file for automatic cleanup.
		* @throws ServiceException if a DataBinder is not configured with this request.
		* @return a temporary file path.
		*/
	// ******** This method also exists in the ConvertToPDFServiceHandler component. ******** 
	public String getTemporaryFileName( String extension, int nameFlags ) throws ServiceException {
		if ( m_binder == null ) throw new ServiceException(
			"!$Unable to create temp file name, FileStoreUtils not constructed using a Service object." );
		long counter = m_binder.getNextFileCounter();
		String nodeId = m_shared.checkConfig( "ClusterNodeName" );
		String fileName;
		if ( nodeId != null ) fileName = "trans-" + nodeId + "-" + counter + extension;
		else fileName = "trans-" + counter + extension;

		String tempDir = m_binder.getTemporaryDirectory();
		fileName = FileUtils.getAbsolutePath( tempDir, fileName );
		if ( ( nameFlags & F_NO_CLEANUP ) == 0 ) m_binder.addTempFile( fileName );
		return fileName;
	}

	/** Return a path to a file based on fileData.  This file
		* may need to be copied from a storage provider onto the local 
		* filesystem.  In this case, the file will be one of the 
		* DataBinder's temporary files, meaning it will be automatically cleaned up
		* when the service request terminates.  Under no circumstances should
		* the caller of this API manipulate or delete the file.
		* @return The path to the file.
		* @param fileData DataBinder containing file metadata.
		* @binder.in FileStoreProvider.SP_RENDITION_ID One of the FileStoreProvider R_ constants.
		* @binder.in all other docmeta for the file.
		* @throws DataException if the file cannot be found using the provided metadata.
		* @throws ServiceException if an error occurs while reading/transferring the file.
		*/
	public String getFilePath( DataBinder fileData ) throws DataException, ServiceException {
		String path;
		IdcFileDescriptor d = m_fileStore.createDescriptor( fileData, new HashMap(), m_context );
		if ( m_utils.isStoredOnFileSystem( d, m_fileStore ) ) {
			path = d.getProperty( FileStoreProvider.SP_PATH );
		} else {
			long counter = fileData.getNextFileCounter();
			path = fileData.getTemporaryDirectory();
			String extension = fileData.getLocal( "dExtension" );
			fileData.addTempFile( path );
			if ( extension == null ) extension = "";
			else extension = "." + extension;
			path = FileUtils.getAbsolutePath( path, "" + counter + extension );
			try {
				m_fileStore.copyToLocalFile( d, new File( path ), null );
			} catch ( Exception e ) {
				ServiceException se = new ServiceException( e, "csTranslationUnableToCopyVault", path );
				String msg = intradoc.common.LocaleUtils.createMessageStringFromThrowable( se );
				throw se;
			}
		}
		return path;
	}
}