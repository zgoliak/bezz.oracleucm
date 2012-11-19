package com.bezzotech.oracleucm.arx;

import intradoc.common.Report;
import intradoc.common.ServiceException;
import intradoc.data.DataBinder;
import intradoc.data.DataException;
import intradoc.data.DataResultSet;
import intradoc.data.ResultSet;
import intradoc.data.ResultSetUtils;
import intradoc.server.Service;
import intradoc.server.ServiceHandler;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.bezzotech.oracleucm.arx.common.CMUtils;
import com.bezzotech.oracleucm.arx.common.XMLUtils;
import com.bezzotech.oracleucm.arx.service.FileStoreUtils;
import com.bezzotech.oracleucm.arx.shared.SharedObjects;

public class CoSignServiceHandler extends ServiceHandler {
	private static boolean m_undo;
	/**
	 *  TODO: Need to implement better Cookie handling
	 */
	// private static Map < String, String > cookie = new HashMap < String, String > ();
	private static String m_cookie = "";
	private final String PLACEHOLDER_CONTENT = "uploadedFileContent";

	/** <span class="code">FileStoreUtils</span> object for this request. */
	protected FileStoreUtils m_fsutil;

	/** SharedObjects pointer to use for this request. */
	public SharedObjects m_shared;

	protected CMUtils m_cmutils;
	protected XMLUtils m_xmlutils;
	protected WSC m_WSC;

	/** Initialize the handler and set up the <span class="code">m_fsutil</span> object.
	 * @param s the Service that we operate with.
	 * @throws ServiceException if super.init() or the FileStoreUtils construction does.
	 * @throws DataException if super.init() does.
	 */
	public void init( Service s ) throws ServiceException, DataException {
		super.init( s );
		m_undo = false;
		m_fsutil = FileStoreUtils.getFileStoreUtils( s );
		//m_cmutils = CMUtils.getCMUtils( s );
		m_xmlutils = XMLUtils.getXMLUtils( s );
		m_shared = SharedObjects.getSharedObjects( s );
		m_WSC = WSC.getWSC( s );
		m_cmutils = m_WSC.getCM();
	}

	/**
	 *
	 */
	public void generateCoSignProfile() throws ServiceException {
		String s = m_WSC.buildSigProfile( false );
		String s1 = super.m_binder.getNextFileCounter() + ".xml";
		String s2 = super.m_binder.getTemporaryDirectory() + s1;
		try {
			FileOutputStream fileoutputstream = new FileOutputStream( s2 );
			OutputStreamWriter outputstreamwriter = new OutputStreamWriter( fileoutputstream, "UTF8" );
			outputstreamwriter.write( s );
			outputstreamwriter.close();
		} catch( Exception exception ) {
			super.m_service.createServiceException( null, exception.getMessage() );
		}
		super.m_binder.addTempFile( s2 );
		super.m_binder.putLocal( "primaryFile", s1 );
		super.m_binder.putLocal( "primaryFile:path", s2 );
	}

	/**
	 *
	 */
	public void processSignRequest() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processSignRequest, passed in binder:" + super.m_binder.toString(), null );
		super.m_binder.putLocal( "CoSign.Document.fields", "fileID;contentType;content" );
		super.m_binder.putLocal( "CoSign.Document.fileID", super.m_binder.getLocal( "dDocName" ) );
		super.m_binder.putLocal( "docId", super.m_binder.getLocal( "dID" ) );
		ResultSet rset =
				m_cmutils.getDocInfo( super.m_binder.getLocal( "docId" ) );
		super.m_binder.putLocal( "CoSign.Document.contentType",
				ResultSetUtils.getValue( rset, "dExtension" ) );
		super.m_binder.putLocal( "CoSignProfile",
				ResultSetUtils.getValue( rset, m_shared.getConfig( "coSignSignatureProfileMetaField" ) ) );
		DataResultSet drset = new DataResultSet();
		drset.copy( rset );
		super.m_binder.addResultSet( "DOC_INFO", drset );
		Report.trace( "bezzotechcosign", "Required metadata for Signing Ceremony have been gathered from" +
				" content item, resulting binder: " + super.m_binder.toString(), null );

		super.m_binder.putLocal( "CoSign.Document.content", PLACEHOLDER_CONTENT );
		String SignRequest = m_WSC.buildSignRequest();

		String msg = "";
		try {
			String file = m_cmutils.getFileAsString();
			String input = SignRequest.replaceAll( PLACEHOLDER_CONTENT, file );
			String output = URLEncoder.encode( input, "UTF-8" );
			SignRequest = "inputXML=" + output;
			super.m_binder.putLocal( "SignRequest", SignRequest );
			m_WSC.processSignRequest();
		} catch ( UnsupportedEncodingException e ) {
		Report.debug( "bezzotechcosign", "Rollback trigger > m_undo: " + m_undo, null );
			e.printStackTrace();
			msg += e.getMessage();
			m_undo = true;
		} catch ( Exception e ) {
		Report.debug( "bezzotechcosign", "Rollback trigger > m_undo: " + m_undo, null );
			e.printStackTrace();
			msg += e.getMessage();
			m_undo = true;
		} finally {
		Report.debug( "bezzotechcosign", "Rollback trigger > m_undo: " + m_undo, null );
			if( super.m_binder.getLocal( "WSC_Session" ) == null ) {
				msg += "\n\tCosign returned without a valid session found";
				m_undo = true;
			}
		}

		log ( msg );

		Report.debug( "bezzotechcosign", "Rollback trigger > m_undo: " + m_undo, null );
		if( m_undo ) {
			m_cmutils.rollback( msg );
		}
	}

	/**
	 *
	 */
	public void processSignedDocument() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processSignedDocument, passed in binder:" + super.m_binder.toString(), null );
		String msg = "";
		if( super.m_binder.getLocal( "errorMessage" ) != null ) {
			msg = super.m_binder.getLocal( "errorMessage" );
			m_undo = true;
		}
		if( super.m_binder.getLocal( "sessionId" ) == null ) {
			msg = "csInvalidSessionId";
			m_undo = true;
		}
		if( super.m_binder.getLocal( "docId" ) == null ) {
			msg = "csInvalidDocId";
			m_undo = true;
		}
		super.m_binder.putLocal( "dDocName", super.m_binder.getLocal( "docId" ) );
		super.m_binder.putLocal( "dID", "" );

		try { if( !m_undo ) m_WSC.processDownloadRequest(); }
		catch ( Exception e ) {
			msg += e.getMessage();
			m_undo = true;
		}

		log ( msg );

		if( m_undo )
			m_cmutils.rollback( msg );
		else
		 m_cmutils.checkin();

		if( super.m_binder.getLocal( "dWorkflowState").length() > 0 )
			m_cmutils.approve();
		}

	/**
	 *
		*/
	public void processReviewRequest() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processReviewRequest, passed in binder:", null );
		ResultSet rset = m_cmutils.getSignatureReview( super.m_binder.getLocal( "dID" ) );
		if( rset.isEmpty() ) {
			ResultSet diRSet = m_cmutils.getDocInfo( super.m_binder.getLocal( "dID" ) );
			super.m_binder.putLocal( "dDocName", ResultSetUtils.getValue( diRSet, "dDocName" ) );
			DataResultSet drset = new DataResultSet();
			drset.copy( diRSet );
			super.m_binder.addResultSet( "DOC_INFO", drset );
			super.m_binder.m_inStream = m_cmutils.getFileAsStream();

			String msg = "";
			boolean term = false;
			try {
				m_WSC.processVerifyRequest();
			} catch( Exception e) {
			 msg = e.getMessage();
				term = true;
			}
			log( msg );
			if( term )
				return;
			rset = m_cmutils.getSignatureReview( super.m_binder.getLocal( "dID" ) );
		}
		DataResultSet drset = new DataResultSet();
		drset.copy( rset );
		Report.debug( "bezzotechcosign", "Rows found: " + drset.getNumRows(), null );
		m_binder.addResultSet( "SignatureReview", drset );
	}

	/**
	 *
	 */
	public void readXMLToBinder() throws ServiceException {
		try {
			m_WSC.parseSigProfile();
		} catch ( Exception e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
	}

	/**
	 *
		*/
	private void log( String msg ) throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering log, passed in binder:", null );
		DataBinder binder = new DataBinder();
		binder.putLocal( "User", super.m_binder.getLocal( "dUser" ) );
		binder.putLocal( "Operation", super.m_binder.getLocal( "IdcService" ) );
		binder.putLocal( "Error", msg );
		binder.putLocal( "dDocName", super.m_binder.getLocal( "dDocName" ) );
		binder.putLocal( "dID", super.m_binder.getLocal( "dID" ) );
		binder.putLocalDate( "date", new java.util.Date() );
		try {
			m_workspace.execute( "IcosignHistory", binder );
		} catch ( DataException e ) {
			throwFullError( e );
		}
	}

	/**
	 *
		*/
	private void throwFullError( Exception e ) throws ServiceException {
			StringBuilder sb = new StringBuilder();
			for(StackTraceElement element : e.getStackTrace()) {
				sb.append(element.toString());
				sb.append("\n");
			}
			throw new ServiceException( e.getMessage() + "\n" + sb.toString() );
	}
}