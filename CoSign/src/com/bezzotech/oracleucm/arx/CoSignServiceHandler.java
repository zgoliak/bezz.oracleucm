package com.bezzotech.oracleucm.arx;

import intradoc.common.Errors;
import intradoc.common.Report;
import intradoc.common.ServiceException;
import intradoc.data.DataBinder;
import intradoc.data.DataException;
import intradoc.data.DataResultSet;
import intradoc.data.IdcCounterUtils;
import intradoc.data.ResultSet;
import intradoc.data.ResultSetUtils;
import intradoc.data.Workspace;
import intradoc.server.Service;
import intradoc.server.ServiceHandler;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.bezzotech.oracleucm.arx.common.CMUtils;
import com.bezzotech.oracleucm.arx.common.XMLUtils;
import com.bezzotech.oracleucm.arx.service.FileStoreUtils;
import com.bezzotech.oracleucm.arx.shared.SharedObjects;

public class CoSignServiceHandler extends ServiceHandler {
	private static boolean m_undo = false;
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
		Report.debug( "bezzotechcosign", "Required metadata for Signing Ceremony have been gathered from" +
				" content item.", null );
		Report.debug( "bezzotechcosign", super.m_binder.toString(), null );

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
			e.printStackTrace();
			msg += e.getMessage();
			m_undo = true;
		} catch ( Exception e ) {
			e.printStackTrace();
			msg += e.getMessage();
			m_undo = true;
		} finally {
			if ( super.m_binder.getLocal( "WSC_Session" ) == null ) {
				msg += "\n\tNo Session found";
				m_undo = true;
			}
		}

		log ( msg );

		if ( m_undo ) {
			m_cmutils.rollback( msg );
		}
	}

	/**
	 *
	 */
	public void processSignedDocument() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering processSignedDocument, passed in binder:", null );
		if ( super.m_binder.getLocal( "sessionId" ) == null )
			super.m_service.createServiceException( null, "csInvalidSessionId" );
		if ( super.m_binder.getLocal( "docId" ) == null )
			super.m_service.createServiceException( null, "csInvalidDocId" );

		String msg = "";
		try { m_WSC.processDownloadRequest(); }
		catch ( Exception e ) {
			msg += e.getMessage();
			m_undo = true;
		}

		log ( msg );

		if ( m_undo ) {
			m_cmutils.rollback( msg );
		}
	}

	/**
	 *
		*/
	public void processReviewRequest() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering processSignedDocument, passed in binder:", null );
		ResultSet rset = m_cmutils.getSignatureReview( super.m_binder.getLocal( "dID" ) );
		if( rset.isEmpty() ) {
			super.m_binder.putLocal( "CoSign.Document.fields", "fileID;contentType;content" );
			super.m_binder.putLocal( "docId", super.m_binder.getLocal( "dID" ) );
			rset = m_cmutils.getDocInfo( super.m_binder.getLocal( "docId" ) );
			super.m_binder.putLocal( "CoSign.Document.fileID", ResultSetUtils.getValue( rset, "dDocName" ) );
			super.m_binder.putLocal( "CoSign.Document.contentType",
					ResultSetUtils.getValue( rset, "dExtension" ) );
			super.m_binder.putLocal( "CoSignProfile",
					ResultSetUtils.getValue( rset, m_shared.getConfig( "coSignSignatureProfileMetaField" ) ) );
			DataResultSet drset = new DataResultSet();
			drset.copy( rset );
			super.m_binder.addResultSet( "DOC_INFO", drset );
			super.m_binder.putLocal( "CoSign.Document.content", PLACEHOLDER_CONTENT );
			String VerifyRequest = m_WSC.buildVerifyRequest();

			String msg = "";
			try {
				String file = m_cmutils.getFileAsString();
				String input = VerifyRequest.replaceAll( PLACEHOLDER_CONTENT, file );
				String output = URLEncoder.encode( input, "UTF-8" );
				VerifyRequest = "inputXML=" + output;
				super.m_binder.putLocal( "VerifyRequest", VerifyRequest );
				m_WSC.processVerifyRequest();
				rset = m_cmutils.getSignatureReview( super.m_binder.getLocal( "dID" ) );
			} catch ( UnsupportedEncodingException e ) {
				e.printStackTrace();
				msg += e.getMessage();
				m_undo = true;
			} catch ( Exception e ) {
				e.printStackTrace();
				msg += e.getMessage();
				m_undo = true;
			}
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
		Report.debug( "bezzotechcosign", "Entering log, passed in binder:", null );
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
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
	}
}