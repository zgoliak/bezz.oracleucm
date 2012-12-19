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
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import com.bezzotech.oracleucm.arx.common.CMUtils;
import com.bezzotech.oracleucm.arx.common.XMLUtils;
import com.bezzotech.oracleucm.arx.service.FileStoreUtils;
import com.bezzotech.oracleucm.arx.shared.SharedObjects;

public class CoSignServiceHandler extends ServiceHandler {
	protected static boolean m_undo;
	protected static String m_cookie = "";
	protected final String PLACEHOLDER_CONTENT = "__uploaded__File__Content__";

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
		String s1 = m_binder.getNextFileCounter() + ".xml";
		String s2 = m_binder.getTemporaryDirectory() + s1;
		OutputStreamWriter _out = null;
		try {
			_out = new OutputStreamWriter( new FileOutputStream( s2 ), "UTF8" );
			_out.write( s );
		} catch( Exception exception ) {
			m_service.createServiceException( null, exception.getMessage() );
		} finally {
			try {
				_out.close();
			} catch ( IOException e ) {
				throwFullError( e );
			}
		}
		m_binder.addTempFile( s2 );
		m_binder.putLocal( "primaryFile", s1 );
		m_binder.putLocal( "primaryFile:path", s2 );
		m_binder.putLocal( "dRevLabel", ( Integer.parseInt( m_binder.getLocal( "dRevLabel" ) ) + 1 ) + "" );
	}

	/**
	 *
	 */
	public void processSignRequest() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processSignRequest, passed in binder:" + m_binder.toString(), null );
		m_binder.putLocal( "xSignatureStatus", "sent-to-cosign" );
		m_cmutils.update();
		m_cmutils.checkout();

		m_binder.putLocal( "CoSign.Document.fields", "fileID;contentType;content" );
		m_binder.putLocal( "CoSign.Document.fileID", m_binder.getLocal( "dDocName" ) );
		m_binder.putLocal( "docId", m_binder.getLocal( "dID" ) );
		ResultSet rset = m_cmutils.getDocInfo( m_binder.getLocal( "docId" ) );
		m_binder.putLocal( "CoSign.Document.contentType", ResultSetUtils.getValue( rset, "dExtension" ) );
		m_binder.putLocal( "CoSignProfile",
				ResultSetUtils.getValue( rset, m_shared.getConfig( "coSignSignatureProfileMetaField" ) ) );
		DataResultSet drset = new DataResultSet();
		drset.copy( rset );
		m_binder.addResultSet( "DOC_INFO", drset );
		Report.trace( "bezzotechcosign", "Required metadata for Signing Ceremony have been gathered from" +
				" content item, resulting binder: " + m_binder.toString(), null );

		m_binder.putLocal( "CoSign.Document.content", PLACEHOLDER_CONTENT );
		String SignRequest = m_WSC.buildSignRequest();

		String msg = "";
		try {
			String file = m_cmutils.getFileAsString();
			String input = SignRequest.replaceAll( PLACEHOLDER_CONTENT, file );
			String output = URLEncoder.encode( input, "UTF-8" );
			SignRequest = "inputXML=" + output;
			m_binder.putLocal( "SignRequest", SignRequest );
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
			if( m_binder.getLocal( "WSC_Session" ) == null ) {
				msg += "\n\tCosign returned without a valid session found";
				m_undo = true;
			}
		}

		logHistory( msg );

		if( m_undo ) {
			m_cmutils.rollback( msg );
		}
	}

	/**
	 *
	 */
	public void processSignedDocument() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processSignedDocument, passed in binder:" + m_binder.toString(), null );
		String msg = "";
		if( m_binder.getLocal( "errorMessage" ) != null ) {
			msg = m_binder.getLocal( "errorMessage" );
			m_undo = true;
		}
		if( !m_undo && m_binder.getLocal( "sessionId" ) == null ) {
			msg = "csInvalidSessionId";
			m_undo = true;
		}
		if( !m_undo && m_binder.getLocal( "docId" ) == null ) {
			msg = "csInvalidDocId";
			m_undo = true;
		}
		m_binder.putLocal( "dDocName", m_binder.getLocal( "docId" ) );
		m_binder.putLocal( "dID", "" );

		try { if( !m_undo ) m_WSC.processDownloadRequest(); }
		catch ( Exception e ) {
			msg += e.getMessage();
			m_undo = true;
		}

		logHistory( msg );

		DataBinder qApproveBinder = new DataBinder();
		qApproveBinder.putLocal( "dDocName", m_binder.getLocal( "dDocName" ) );
		ResultSet rset = m_cmutils.createResultSet( "QwfDocInformation", qApproveBinder );
		DataResultSet drset = new DataResultSet();
		drset.copy( rset );
		Report.debug( "bezzotechcosign", "Resulting Rset: " + drset.toString(), null );

		String stepType = null;
		if( !m_undo && !drset.isEmpty() ) {
			stepType = drset.getStringValueByName( "dWfStepType" );
			if( stepType.indexOf( ":CN:" ) >= 0 ) // allow New Revision
				m_binder.putLocal( "dRevLabel",
						( Integer.parseInt( m_binder.getLocal( "dRevLabel" ) ) + 1 ) + "" );
			else if( stepType.indexOf( ":CE:" ) >= 0 ) {} // allow Edit Revision ZKG: Maybe build in Major/Minor revisioning
		} else if( !m_undo )
			m_binder.putLocal( "dRevLabel",
					( Integer.parseInt( m_binder.getLocal( "dRevLabel" ) ) + 1 ) + "" );

 	if( m_undo )
			m_cmutils.rollback( msg );
		else
		 m_cmutils.checkin();

		m_WSC.log();

		if( !drset.isEmpty() )
			m_cmutils.approve();
	}

	/**
	 *
		*/
	public void processReviewRequest() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processReviewRequest, passed in binder:", null );
		ResultSet rset = m_cmutils.getSignatureReview( m_binder.getLocal( "dID" ) );
		if( rset.isEmpty() ) {
			ResultSet diRSet = m_cmutils.getDocInfo( m_binder.getLocal( "dID" ) );
			m_binder.putLocal( "dDocName", ResultSetUtils.getValue( diRSet, "dDocName" ) );
			DataResultSet drset = new DataResultSet();
			drset.copy( diRSet );
			m_binder.addResultSet( "DOC_INFO", drset );
			m_binder.m_inStream = m_cmutils.getFileAsStream();

			String msg = "";
			boolean term = false;
			try {
				m_WSC.processVerifyRequest();
			} catch( Exception e) {
			 msg = e.getMessage();
				term = true;
			}
			logHistory( msg );
			if( term )
				throw new ServiceException( msg );

			rset = m_cmutils.getSignatureReview( m_binder.getLocal( "dID" ) );
		}
		DataResultSet drset = new DataResultSet();
		drset.copy( rset );
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
	protected void logHistory( String msg ) throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering log, passed in binder:", null );
		DataBinder binder = new DataBinder();
		binder.putLocal( "User", m_binder.getLocal( "dUser" ) );
		binder.putLocal( "Operation", m_binder.getLocal( "IdcService" ) );
		if( msg == null ) msg = "";
		binder.putLocal( "Error", msg );
		binder.putLocal( "dDocName", m_binder.getLocal( "dDocName" ) );
		binder.putLocal( "dID", m_binder.getLocal( "dID" ) );
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
	protected void throwFullError( Exception e ) throws ServiceException {
			StringBuilder sb = new StringBuilder();
			for(StackTraceElement element : e.getStackTrace()) {
				sb.append(element.toString());
				sb.append("\n");
			}
			throw new ServiceException( e.getMessage() + "\n" + sb.toString() );
	}
}