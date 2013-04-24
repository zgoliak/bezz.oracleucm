package com.bezzotech.oracleucm.arx.common;

import intradoc.common.CommonDataConversion;
import intradoc.common.Errors;
import intradoc.common.ExecutionContext;
import intradoc.common.LocaleUtils;
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
import intradoc.server.DocServiceHandler;
import intradoc.server.Service;
import intradoc.server.ServiceData;
import intradoc.server.ServiceManager;
import intradoc.shared.SecurityUtils;
import intradoc.shared.UserAttribInfo;
import intradoc.shared.UserData;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
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
	static protected Workspace m_workspace;
	protected UserData m_userData;

	protected CMUtils( ExecutionContext context ) throws ServiceException {
		m_fsutil = FileStoreUtils.getFileStoreUtils( context );
		m_shared = SharedObjects.getSharedObjects( context );
		if( context instanceof Service ) {
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
		if( envStr == null )
			throw new ServiceException( appName + " has not been properly configured" );

		Vector fields = ( Vector )StringUtils.parseArray( envStr, ';', '\\' );
		if( fields.isEmpty() )
			throw new ServiceException( appName + " has not been properly configured" );

		return fields;
	}

	/**
	 *
	 */
	public String getFileAsString() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getFileAsString, passed in parameters:", null );
		byte [] file = getFileAsByteArray();
		return CommonDataConversion.uuencode( file, 0, file.length );
	}

	/**
	 *
	 */
	public byte [] getFileAsByteArray() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getFileAsByteArray, passed in parameters:", null );
		DataBinder binder = new DataBinder();
		binder.mergeResultSetRowIntoLocalData( m_binder.getResultSet( "DOC_INFO" ) );
		binder.putLocal( FileStoreProvider.SP_RENDITION_ID, FileStoreProvider.R_PRIMARY );
		byte[] b = null;
		RandomAccessFile _raf = null;
		try {
			String primaryFilePath = m_fsutil.getFilePath( binder );
			_raf = new RandomAccessFile( primaryFilePath, "r" );
			b = new byte[ ( int )_raf.length() ];
			_raf.read( b );
		}
		catch ( FileNotFoundException e ) {
			throwFullError( e );
		}
		catch ( IOException e ) {
			throwFullError( e );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
		finally {
			try {
				_raf.close();
			}
			catch ( IOException e ) {
				throwFullError( e );
			}
		}
		return b;
	}

	/**
	 *
	 */
	public BufferedInputStream getFileAsStream() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering getFileAsStream, passed in parameters:", null );
		DataBinder binder = new DataBinder();
		binder.mergeResultSetRowIntoLocalData( m_binder.getResultSet( "DOC_INFO" ) );
		binder.putLocal( FileStoreProvider.SP_RENDITION_ID, FileStoreProvider.R_PRIMARY );
		BufferedInputStream _content = null;
		try {
			String primaryFilePath = m_fsutil.getFilePath( binder );
			_content = new BufferedInputStream( new FileInputStream( new File( primaryFilePath ) ) );
		}
		catch ( FileNotFoundException e ) {
			throwFullError( e );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
		return _content;
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
		if( result == null || result.equals( "" ) ) return 0;
		return Integer.parseInt( result );
	}

	/**
	 *
	 */
	static public ResultSet createResultSet( String query, DataBinder binder ) throws ServiceException {
		ResultSet rset = null;
		try {
			rset = m_workspace.createResultSet( query, binder );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
		if( rset.isEmpty() ) {	Report.trace( "bezzotechcosign", "Query returned no results", null );	}
		return rset;
	}

	/**
	 *  Query for the particular Sign Request Protocol (SRP) to send, currently we are using a
	 *  one-to-one architecture where each SRP only contains a single Signature Profile, so locating
	 *  the correct profile is as simple as finding its SRP.
	 */
	public String retrieveSigProfilesFilePath() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering retrieveSigProfilesFilePath", null );
		DataBinder binder = new DataBinder();
		if( m_binder.getLocal( "CoSignProfile" ) != null ) {
			DataResultSet drset = new DataResultSet();
			drset.copy( getProfileWithMatchingTagAndUserRoles( m_binder.getLocal( "CoSignProfile" ) ) );
			if( drset.getNumRows() <= 0 ) {
				throw new ServiceException( "Unable to locate the Sign Request Profile: " +
						m_binder.getLocal( "CoSignProfile" ) );
			}
			else if( drset.getNumRows() > 1 ) {
				throw new ServiceException( "This user is assigned to multiple signature profiles for this " +
						"document.  Please contact your administrator." );
			}
			drset.first();
			Report.debug( "bezzotechcosign", "Retrieved ResultSet: " + drset.toString(), null );
			binder.mergeResultSetRowIntoLocalData( getDocInfo( drset.getStringValueByName( "dID" ) ) );
		}
		else {
			binder.mergeResultSetRowIntoLocalData( getDocInfo( m_binder.getLocal( "dID" ) ) );
		}
		binder.putLocal( FileStoreProvider.SP_RENDITION_ID, FileStoreProvider.R_PRIMARY );
		binder.putLocal( FileStoreProvider.SP_RENDITION_PATH, FileStoreProvider.R_PRIMARY );
		String returnStr = null;
		try {
			returnStr = m_fsutil.getFilePath( binder );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
		return returnStr;
	}

	/**
	 *
	 */
	protected UserData getUserData() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering getUserData", null );
		UserData ud = ( UserData )m_service.getCachedObject( "UserData" );
		if( ud == null ) {
			if( m_binder.getLocal( "dUser" ) != null ) {
				if( m_service.fillUserData( m_binder.getLocal( "dUser" ) ) )
					ud = m_service.getUserData();
			}
			else
				ud = SecurityUtils.createDefaultAdminUserData();
		}
		return ud;
	}

	/**
	 *
		*/
	public ResultSet getProfileWithMatchingTagAndUserRoles( String profileTag ) throws ServiceException {
		DataBinder binder = new DataBinder();
		binder.putLocal( m_shared.getConfig( "coSignSignatureProfileMetaField" ), profileTag );
		ResultSet rset = createResultSet( "QsignatureProfileID", binder );
		DataResultSet drset = new DataResultSet();
		drset.copy( rset );
		Report.debug( "bezzotechcosign", "Rows: " + drset.getNumRows(), null );
		if( drset.getNumRows() <= 0 ) {
			throw new ServiceException( "Unable to locate the Sign Request Profile: " + profileTag );
		}

		Vector userRoles = SecurityUtils.getRoleList( getUserData() );
		String packageStr = "";
		boolean found = false;
		do {
			String requiredRole = drset.getStringValueByName( "xCoSignRequiredSignatures" );
			for( int i = 0; i < userRoles.size(); i++ ) {
				UserAttribInfo uai = ( UserAttribInfo )userRoles.elementAt( i );
				Report.debug( "bezzotechcosign", "Required Roles: " + requiredRole + "\n\tRole: " +
						uai.m_attribName + "\n\ttest: " + requiredRole.indexOf( uai.m_attribName ), null );
				if( requiredRole.indexOf( uai.m_attribName ) >= 0 ) {
					if( found )
						throw new ServiceException( "The content " + m_binder.getLocal( "dDocName" ) +
								" has multiple signature profiles.  Please contact your administrator." );
					else
						found = true;
				}
			}
			if( !found ) {
				drset.deleteCurrentRow();
				Report.debug( "bezzotechcosign", "Deleted ResultSet Row: " + drset.toString(), null );
			}
			else
				drset.next();
			found = false;
			Report.debug( "bezzotechcosign", "Current: " + drset.getCurrentRow() + "\n\tRows: " +
					drset.getNumRows(), null );
		} while( drset.isRowPresent() );
		return drset;
	}

	/** Execute a service as the current user.
	 * @param binder The service request binder
	 * @throws ServiceException if the service fails.
	 *  @deprecated
	 */
	@Deprecated protected void executeServiceSimple( DataBinder binder ) throws ServiceException {
		try {
			ServiceManager sm = new ServiceManager();
			String serviceName = binder.getLocal( "IdcService" );
			if( serviceName == null )
				m_service.createServiceExceptionEx( null, "!csIDCServiceMissing", Errors.RESOURCE_MISCONFIGURED );

			ServiceData sd = sm.getService( serviceName );
			Service s = sm.getInitializedService( serviceName, binder, m_workspace );
			UserData user = getUserData();
			if( user == null ) {
				user = SecurityUtils.createDefaultAdminUserData();
			}
			s.setUserData( user );
			s.doRequest();
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
	}

	/**
	 *
	 */
	public void rollback( String error, boolean isRedirect ) throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering rollback, passed in parameters:\n\terror: " + error +
				"\n\tservice: " + m_binder.getLocal( "IdcService" ) + "\n\tbinder: ", null );
		try {
			m_service.executeService( "UNDO_CHECKOUT_BY_NAME_IMPLEMENT" );
		}
		catch ( DataException e ) {
			throwFullError( e ); // Let's swallow this as that only means we do not need to undo
		}

		Report.debug( "bezzotechcosign", "Binder after undo: ", null );
		ResultSet rset = m_binder.getResultSet( "DOC_INFO" );
		String sigStatus = m_binder.getResultSetValue( rset, "xSignatureStatus" );
		if( sigStatus != null && sigStatus.equals( "sent-to-cosign" ) ) {
			m_binder.putLocal( "xSignatureStatus", "" );
			update();
			removeItemFromCoSignCacheTable( rset.getStringValueByName( "dDocName" ) );
		}
/*  Want to redirect user to Doc_info on Cancel */
		if( !error.equals( "" ) && !isRedirect ) {
			error = LocaleUtils.encodeMessage( error, null );
			throw new ServiceException( error );
		}
	}

	public void addItemToCoSignCacheTable( String contentId ) throws ServiceException {
		Report.trace( "bezzotechcosign", "Adding " + contentId + " to Global Table", null );
		Date dateNow = new Date ();
		SimpleDateFormat dateformatMMDDYYYY = new SimpleDateFormat( "MM/dd/yyyy HH:mm" );
		String now = dateformatMMDDYYYY.format( dateNow );
		DataResultSet drset = m_shared.getResultSet( "CoSignCheckedOutItems", false );
		if( drset == null ) {
			Report.debug( "bezzotechcosign", "Cache table not established yet, creating new", null );
			drset = new DataResultSet( new String[] { "dDocName", "tsDateTime" } );
		}
		if( !drset.isEmpty() ) {
			while( drset.isRowPresent() ) {
				if( drset.getStringValueByName( "dDocName" ).equals( contentId ) ) {
					drset.deleteCurrentRow();
					break;
				}
				drset.next();
			}
		}
		Vector row = drset.createEmptyRow();
		Report.debug( "bezzotechcosign", "RS Columns: " + drset.getNumFields() + " - Row size: " + row.size(), null );
		row.set( 0, contentId );
		row.set( 1, now );
		drset.addRow( row );
		m_shared.putResultSet( "CoSignCheckedOutItems", drset );
	}

	public void removeItemFromCoSignCacheTable( String contentId ) throws ServiceException {
		Report.trace( "bezzotechcosign", "Removing " + contentId + " from Global Table", null );
		removeItemFromCachedTable( "CoSignCheckedOutItems", contentId );
	}

	protected void removeItemFromCachedTable( String tableName, String contentId )
			throws ServiceException {
		removeRowFromCacheTable( tableName, contentId, "dDocName" );
	}

	protected void removeRowFromCacheTable( String tableName, String filterValue, String filterName )
			throws ServiceException {
		DataResultSet drset = m_shared.getResultSet( tableName, false );
		if( drset != null && !drset.isEmpty() ) {
			while( drset.isRowPresent() ) {
				if( drset.getStringValueByName( filterName ).equals( filterValue ) ) {
					drset.deleteCurrentRow();
					break;
				}
				drset.next();
			}
		}
		m_shared.putResultSet( "CoSignCheckedOutItems", drset );
	}

	public boolean itemExistsInCoSignCacheTable( String contentId ) throws ServiceException {
		return itemExistsInCacheTable( "CoSignCheckedOutItems", contentId );
	}

	protected boolean itemExistsInCacheTable( String tableName, String contentId )
			throws ServiceException {
		return rowExistsInCacheTable( tableName, contentId, "dDocName" );
	}

	protected boolean rowExistsInCacheTable( String tableName, String filterValue, String filterName )
			throws ServiceException {
		DataResultSet drset = m_shared.getResultSet( tableName, false );
		if( drset != null && !drset.isEmpty() ) {
			while( drset.isRowPresent() ) {
				if( drset.getStringValueByName( filterName ).equals( filterValue ) ) {
					return true;
				}
				drset.next();
			}
		}
		return false;
	}

	/**
	 *
	 */
	public void update() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering update, passed in binder: ", null );
		try {
			m_service.executeService( "UPDATE_DOCINFO_SUB" );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
	}

	/**
	 *
	 */
	public void checkout() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering checkout, passed in binder: ", null );
		try {
			m_service.executeService( "INTERNAL_CHECKOUT_SUB" );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
		finally {
			addItemToCoSignCacheTable( m_binder.getLocal( "dDocName" ) );
		}
	}

	/**
	 *
	 */
	public void checkinNew() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering checkinNew, passed in binder: ", null );
		try {
			m_service.executeService( "CHECKIN_NEW_SUB" );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
	}

	/**
	 *
	 */
	public void checkinSel() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering checkinSel, passed in binder: ", null );
		try {
			m_service.executeService( "CHECKIN_SEL_SUB" );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
	}

	/**
	 *
	 */
	public void approve() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering approve, passed in binder: ", null );
		DataResultSet drset = new DataResultSet();
		drset.copy( getDocInfoByName( m_binder.getLocal( "dDocName" ) ) );
		m_binder.mergeResultSetRowIntoLocalData( drset );
		m_binder.putLocal( "dWfName",
				ResultSetUtils.getValue( m_binder.getResultSet( "WF_INFO" ), "dWfName" ) );
		m_binder.putLocal( "curStepName",
				ResultSetUtils.getValue( m_binder.getResultSet( "WorkflowSteps" ), "dWfStepName" ) );
		try {
			m_service.executeService( "WORKFLOW_APPROVE_SUB" );
		}
		catch ( DataException e ) {
			throwFullError( e );
		}
	}

	static public void serviceDoRequest( String serviceName, DataBinder db, Workspace ws )
			throws DataException, ServiceException {
		ServiceManager sm = new ServiceManager();
		ServiceData sd = sm.getService( serviceName );
		Service service = sm.getInitializedService( serviceName, db, ws );
		UserData userData = SecurityUtils.createDefaultAdminUserData();
		service.setUserData( userData );
		db.putLocal( "IdcService", serviceName );
		service.doRequest();
	}

	/**
	 *
	 */
	static protected void throwFullError( Exception e ) throws ServiceException {
		StringBuilder sb = new StringBuilder();
		for(StackTraceElement element : e.getStackTrace()) {
			sb.append(element.toString());
			sb.append("\n");
		}
		Report.debug( "bezzotechcosign", e.getMessage() + "\n" + sb.toString(), null );
		throw new ServiceException( e.getMessage() + "\n" + sb.toString() );
	}
}