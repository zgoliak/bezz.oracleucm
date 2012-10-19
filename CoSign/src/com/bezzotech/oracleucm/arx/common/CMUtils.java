package com.bezzotech.oracleucm.arx.common;

import intradoc.common.CommonDataConversion;
import intradoc.common.Errors;
import intradoc.common.ExecutionContext;
import intradoc.common.Report;
import intradoc.common.ServiceException;
import intradoc.common.StringUtils;
import intradoc.data.DataBinder;
import intradoc.data.DataException;
import intradoc.data.DataResultSet;
import intradoc.data.ResultSet;
import intradoc.data.ResultSetUtils;
import intradoc.data.Workspace;
import intradoc.filestore.FileStoreProvider;
import intradoc.server.Service;
import intradoc.server.ServiceData;
import intradoc.server.ServiceManager;
import intradoc.shared.SecurityUtils;
import intradoc.shared.UserData;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Vector;

import com.bezzotech.oracleucm.arx.service.FileStoreUtils;
import com.bezzotech.oracleucm.arx.shared.SharedObjects;

public class CMUtils {
	/** <span class="code">FileStoreUtils</span> object for this request. */
	protected FileStoreUtils m_fsutil;

	/** SharedObjects pointer to use for this request. */
	public SharedObjects m_shared;

	/** DataBinder The DataBinder for this request. */
	public DataBinder m_binder;

	protected Service m_service;
	protected Workspace m_workspace;

	protected CMUtils( ExecutionContext context ) throws ServiceException {
		m_fsutil = FileStoreUtils.getFileStoreUtils( context );
		m_shared = SharedObjects.getSharedObjects( context );
		if ( context instanceof Service ) {
			Service m_service = ( Service )context;
			m_workspace = m_service.getWorkspace();
			m_binder = m_service.getBinder();
		}
	}

	/** Return a working FileStoreUtils object for a service.
		* @param context ExecutionContext to find a FileStoreProvider in.
		* @throws ServiceException if a FileStoreProvider cannot be found.
		* @return a ready-to-use FileStoreUtils object.
		*/
	static public CMUtils getCMUtils( ExecutionContext context ) throws ServiceException {
		return new CMUtils( context );
	}

	/**
		*
		*/
	public Vector getEnvironmentalsAsList( String appName, String rootName )
			throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getEnvironmentalsAsList, passed in attributes:" +
				"\n\tappName: " + appName + "\n\trootName: " + rootName, null );
		String envStr = m_shared.getConfig( appName + "." + rootName + ".fields" );
		if ( envStr == null )
			throw new ServiceException( appName + " has not been properly configured" );

		Vector fields = ( Vector )StringUtils.parseArray( envStr, ';', '\\' );
		if ( fields.isEmpty() )
			throw new ServiceException( appName + " has not been properly configured" );

		return fields;
	}

	/**
		*
		*/
	public String getFileAsString()
			throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getFileAsString, passed in parameters:", null );
		byte [] file = getFileAsByteArray();
		return CommonDataConversion.uuencode( file, 0, file.length );
	}

	/**
		*
		*/
	public byte [] getFileAsByteArray()
			throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getFileAsByteArray, passed in parameters:", null );
		DataBinder binder = new DataBinder();
		binder.mergeResultSetRowIntoLocalData( m_binder.getResultSet( "DOC_INFO" ) );
		binder.putLocal( FileStoreProvider.SP_RENDITION_ID, FileStoreProvider.R_PRIMARY );
		byte[] b = null;
		try {
			String primaryFilePath = m_fsutil.getFilePath( binder );
			RandomAccessFile f = new RandomAccessFile( primaryFilePath, "r" );
			b = new byte[ ( int )f.length() ];
			f.read( b );
		} catch ( FileNotFoundException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		} catch ( IOException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		} catch ( DataException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
		return b;
	}

	/**
		*
		*/
	public ResultSet getDocInfo( String dID ) throws ServiceException {
		DataBinder binder = new DataBinder();
		binder.putLocal( "dID", dID );
		ResultSet rset = null;
		try {
			rset = m_workspace.createResultSet( "QdocInfo", binder );
		} catch ( DataException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
		if ( rset.isEmpty() ) {	/*This should never happen*/	}
		return rset;
	}

	/**
		*  Query for the particular Sign Request Protocol (SRP) to send, currently we are using a
		*  one-to-one architecture where each SRP only contains a single Signature Profile, so locating
		*  the correct profile is as simple as finding its SRP.
		*/
	public String retrieveSigProfilesFilePath() throws ServiceException {
		String sql = "SELECT dID FROM Revisions WHERE dDocType = 'CoSignRequestProtocol' AND " +
				"dDocTitle = '" + m_binder.getLocal( "CoSignProfile" ) + "'";
		ResultSet rset = null;
		try {
			rset = m_workspace.createResultSetSQL( sql );
		} catch ( DataException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		} finally {
			if ( rset.isEmpty() )
				throw new ServiceException( "csNoProtocolsFound" );
		}
		DataResultSet drset = new DataResultSet();
		drset.copy( rset );
		if ( drset.getNumRows() > 1 ) {
			throw new ServiceException( "We found more than 1 Sign Request Protocol, please contact your " +
					"system administrator." );
		}

		DataBinder binder = new DataBinder();
		binder.mergeResultSetRowIntoLocalData( getDocInfo( ResultSetUtils.getValue( drset, "dID" ) ) );
		binder.putLocal( FileStoreProvider.SP_RENDITION_ID, FileStoreProvider.R_PRIMARY );
		binder.putLocal( FileStoreProvider.SP_RENDITION_PATH, FileStoreProvider.R_PRIMARY );
		String returnStr = null;
		try {
			returnStr = m_fsutil.getFilePath( binder );
		} catch ( DataException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
		return returnStr;
	}

	/** Execute a service as the current user.
		* @param binder The service request binder
		* @throws ServiceException if the service fails.
		*/
	private void executeServiceSimple( DataBinder binder )	throws ServiceException {
		try {
			ServiceManager sm = new ServiceManager();
			String serviceName = binder.getLocal( "IdcService" );
			if ( serviceName == null )
				m_service.createServiceExceptionEx( null, "!csIDCServiceMissing", Errors.RESOURCE_MISCONFIGURED );

			ServiceData sd = sm.getService( serviceName );
			Service s = sm.getInitializedService( serviceName, binder, m_workspace );
			UserData user = ( UserData )m_service.getCachedObject( "UserData" );
			if ( user == null ) {
				user = SecurityUtils.createDefaultAdminUserData();
			}
			s.setUserData( user );
			s.doRequest();
		} catch ( DataException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
	}

	/**
		*
		*/
	public void rollback( String error ) throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering rollback, passed in parameters:\n\terror: " + error,
				null );
		DataBinder undoBinder = new DataBinder();
		undoBinder.putLocal( "IdcService", "UNDO_CHECKOUT_BY_NAME" );
		undoBinder.putLocal( "dDocName", m_binder.getLocal( "docName" ) );
		executeServiceSimple( undoBinder );
		throw new ServiceException( error );
	}
}