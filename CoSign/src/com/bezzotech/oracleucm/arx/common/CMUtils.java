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
import intradoc.jdbc.*;
import intradoc.server.Service;
import intradoc.server.ServiceData;
import intradoc.server.ServiceManager;
import intradoc.shared.SecurityUtils;
import intradoc.shared.UserAttribInfo;
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
	protected UserData m_userData;

	protected CMUtils( ExecutionContext context ) throws ServiceException {
		m_fsutil = FileStoreUtils.getFileStoreUtils( context );
		m_shared = SharedObjects.getSharedObjects( context );
		if ( context instanceof Service ) {
			m_service = ( Service )context;
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
	public ResultSet getDocInfoByName( String dDocName ) throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getDocInfoByName, passed in parameter(s):\n\tdDocName: " +
				dDocName, null );
		DataBinder binder = new DataBinder();
		binder.putLocal( "dDocName", dDocName );
		return createResultSet( "QlatestDocInfoByName", binder );
	}

	/**
	 *
	 */
	public ResultSet getDocInfo( String dID ) throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getDocInfo, passed in parameter(s):\n\tdID: " +
				dID, null );
		DataBinder binder = new DataBinder();
		binder.putLocal( "dID", dID );
		return createResultSet( "QdocInfo", binder );
	}

	/**
	 *
		*/
	public ResultSet getSignatureReview( String dID ) throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getSignatureReview, passed in parameter(s):\n\tdID: " +
				dID, null );
		DataBinder binder = new DataBinder();
		binder.putLocal( "dID", dID );
		return createResultSet( "QcosignSignatureDetails", binder );
	}

	/**
	 *
	 */
	public int getSignedCounter( String dDocName ) throws ServiceException {
		DataBinder binder = new DataBinder();
		binder.putLocal( "dDocName", dDocName );
		String result =
				ResultSetUtils.getValue( createResultSet( "QsignatureCountByName", binder ), "xSignatureCount" );
		return Integer.parseInt( result );
	}

	/**
	 *
		*/
	private ResultSet createResultSet( String query, DataBinder binder ) throws ServiceException {
		ResultSet rset = null;
		try {
			rset = m_workspace.createResultSet( query, binder );
		} catch ( DataException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
		if ( rset.isEmpty() ) {	Report.trace( "bezzotechcosign", "Query returned no results", null );	}
		return rset;
	}

	/**
	 *  Query for the particular Sign Request Protocol (SRP) to send, currently we are using a
	 *  one-to-one architecture where each SRP only contains a single Signature Profile, so locating
	 *  the correct profile is as simple as finding its SRP.
	 */
	public String retrieveSigProfilesFilePath() throws ServiceException {
		DataBinder binder = new DataBinder();
		if ( m_binder.getLocal( "CoSignProfile" ) != null ) {
			Report.debug( "bezzotechcosign", "CoSignProfile in binder, need to search", null );
			binder.putLocal( m_shared.getConfig( "coSignSignatureProfileMetaField" ),
					m_binder.getLocal( "CoSignProfile" ) );
			UserData ud = getUserData();
			Vector userRoles = SecurityUtils.getRoleList( ud );
			Report.debug( "bezzotechcosign", "User Roles Found: " + userRoles.toString() + "\n\tUserData: " +
					ud.toString(), null );

			String packageStr = "";
			for( int i = 0; i < userRoles.size(); i++ ){
				UserAttribInfo uai = ( UserAttribInfo )userRoles.elementAt( i );
				if( packageStr.length() > 0 )
					packageStr = ( new StringBuilder() ).append( packageStr ).append( "," ).toString();
				packageStr = ( new StringBuilder() ).append( packageStr ).append( uai.m_attribName ).toString();
			}

			binder.putLocal( "xCoSignRequiredSignatures", packageStr );
			Report.debug( "bezzotechcosign", "Query binder: " + binder.toString(), null );
			ResultSet rset = null;
			try {
				rset = m_workspace.createResultSet( "QsignatureProfileID", binder );
			} catch ( DataException e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			} finally {
				if (rset == null ) throw new ServiceException( "Who the heck did you piss off" );
				if ( rset.isEmpty() ) throw new ServiceException( "csNoProtocolsFound" );
			}
			DataResultSet drset = new DataResultSet();
			drset.copy( rset );
			if ( drset.getNumRows() <= 0 ) {
				throw new ServiceException( "Unable to locate the Sign Request Profile: " +
						m_binder.getLocal( "CoSignProfile" ) );
			} else if ( drset.getNumRows() > 1 ) {
				throw new ServiceException( "We found more than 1 Sign Request Protocol, please contact your " +
						"system administrator." );
			}
			Report.debug( "bezzotechcosign", "CoSign Profile found: " + drset.toString(), null );
			binder.mergeResultSetRowIntoLocalData( getDocInfo( ResultSetUtils.getValue( drset, "dID" ) ) );
		} else {
			binder.mergeResultSetRowIntoLocalData( getDocInfo( m_binder.getLocal( "dID" ) ) );
		}
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

	private UserData getUserData() throws ServiceException {
		UserData ud = ( UserData )m_service.getCachedObject( "UserData" );
		if ( ud == null ) {
			if ( m_binder.getLocal( "dUser" ) != null ) {
				if ( m_service.fillUserData( m_binder.getLocal( "dUser" ) ) )
		Report.debug( "bezzotechcosign", "Using dUser", null );
					ud = m_service.getUserData();
			} else
		Report.debug( "bezzotechcosign", "Creating Admin", null );
				ud = SecurityUtils.createDefaultAdminUserData();
		}
		Report.debug( "bezzotechcosign", "UserData: " + ud.toString(), null );
		return ud;
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
			UserData user = getUserData();//( UserData )m_service.getCachedObject( "UserData" );
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
		Report.debug( "bezzotechcosign", "Entering rollback, passed in parameters:\n\terror: " + error +
				"\n\tbinder: " , null );
		DataBinder undoBinder = new DataBinder();
		undoBinder.putLocal( "IdcService", "UNDO_CHECKOUT_BY_NAME" );
		undoBinder.putLocal( "dDocName", m_binder.getLocal( "dDocName" ) );
		executeServiceSimple( undoBinder );
		throw new ServiceException( error );
	}

	/**
	 *
		*/
	public void update() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering update, passed in binder: " + m_binder.toString() , null );
		DataBinder undoBinder = new DataBinder();
		m_binder.putLocal( "IdcService", "UPDATE_DOCINFO_SUB" );
		executeServiceSimple( undoBinder );
	}
}