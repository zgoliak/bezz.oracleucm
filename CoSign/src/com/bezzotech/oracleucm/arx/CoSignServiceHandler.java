package com.bezzotech.oracleucm.arx;

import intradoc.common.CommonDataConversion;
import intradoc.common.Errors;
import intradoc.common.Report;
import intradoc.common.ServiceException;

import intradoc.data.DataBinder;
import intradoc.data.DataException;
import intradoc.data.DataResultSet;
import intradoc.data.IdcProperties;
import intradoc.data.ResultSet;
import intradoc.data.ResultSetUtils;
import intradoc.data.Workspace;

import intradoc.server.Service;
import intradoc.server.ServiceHandler;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
//import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

//import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Scanner;
import java.util.StringTokenizer;

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
		m_cmutils = CMUtils.getCMUtils( m_service );
		m_xmlutils = XMLUtils.getXMLUtils( m_service );
		m_shared = SharedObjects.getSharedObjects( m_service );
		m_WSC = WSC.getWSC( m_service );
	}

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

	public void processSignRequest() throws ServiceException {
//		DataBinder requestBinder = new DataBinder();
		super.m_binder.putLocal( "CoSign.Document.fields", "fileID;contentType;content" );
		super.m_binder.putLocal( "docName", super.m_binder.getLocal( "dDocName" ) );
		super.m_binder.putLocal( "CoSign.Document.fileID", super.m_binder.getLocal( "dID" ) );
		ResultSet rset =
				m_cmutils.getDocInfo( super.m_binder.getLocal( "CoSign.Document.fileID" ) );
		super.m_binder.putLocal( "CoSign.Document.contentType", ResultSetUtils.getValue( rset, "dExtension" ) );
		super.m_binder.putLocal( "CoSignProfile", ResultSetUtils.getValue( rset, "xCoSignContentProfile" ) );
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
			msg = e.getMessage();
			m_undo = true;
		} catch ( Exception e ) {
			e.printStackTrace();
			msg = e.getMessage();
			m_undo = true;
		} finally {
			String session = super.m_binder.getLocal( "WSC_Session" );
			if ( session != null )
				super.m_binder.putLocal( "WSC_Session", session );
		}
		if ( m_undo ) {
			m_cmutils.rollback( msg );
		}
	}

	public void processSignedDocument() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering processSignedDocument, passed in binder:", null );
		DataBinder requestBinder = new DataBinder();
		requestBinder.putLocal( "sessionId", super.m_binder.getLocal( "sessionId" ) );
		if ( requestBinder.getLocal( "sessionId" ) == null )
			super.m_service.createServiceException( null, "csInvalidSessionId" );
		requestBinder.putLocal( "docID", super.m_binder.getLocal( "dID" ) );
		if ( requestBinder.getLocal( "docID" ) == null )
			super.m_service.createServiceException( null, "csInvalidDocId" );

		String msg = "";
		try { processDownloadRequest( requestBinder ); }
		catch ( Exception e ) {
			msg = e.getMessage();
			m_undo = true;
		}

		if ( m_undo ) {
			m_cmutils.rollback( msg );
		}
	}

	public void readXMLToBinder() throws ServiceException {
/*		DataBinder binder = new DataBinder();
		binder.mergeResultSetRowIntoLocalData(
				CMUtils.getDocInfo( super.m_workspace, super.m_binder.getLocal( "dID" ) ) );
		binder.putLocal( FileStoreProvider.SP_RENDITION_ID, FileStoreProvider.R_PRIMARY );
		StringBuffer content = new StringBuffer();
		try {
			String primaryFilePath = CMUtils.getFilePath( super.m_service, binder );
			Report.debug( "bezzotechcosign", "File path: " + primaryFilePath, null );
			Document dom = XMLUtils.getExistingDocument( primaryFilePath );
			Report.debug( "bezzotechcosign", "Document for parsing has been prepared", null );
*/
		try {
			m_WSC.parseSigProfile();
//			parseDocument( dom );
		} catch ( Exception e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
	}

private String downloadSignedFile( DataBinder inBinder ) throws ServiceException {
StringBuffer response = null;
InputStream _is = null;
String strBuffer = null;
try {
String WSC_URL_PULL = m_shared.getConfig( "CoSign_WSC_URL_PULL" );
URL url = new URL( WSC_URL_PULL + "?sessionID=" + inBinder.getLocal( "sessionID" ) );
HttpURLConnection httpCon = ( HttpURLConnection ) url.openConnection();
httpCon.setDoOutput( true );
if ( httpCon.getResponseCode() <= 400 ) {
_is = httpCon.getInputStream();
BufferedReader _in = new BufferedReader( new InputStreamReader( _is ) );
while ( ( strBuffer = _in.readLine() ) != null ) {
response.append( strBuffer );
}
} else {
/* error from server */
_is = httpCon.getErrorStream();
BufferedReader _in = new BufferedReader( new InputStreamReader( _is ) );
while ( ( strBuffer = _in.readLine() ) != null ) {
response.append( strBuffer );
}
_in.close();
super.m_service.createServiceException( null, "WSC Error response: " + response.toString() );
}
} catch ( Exception e ) {
super.m_service.createServiceException( null, e.getMessage() );
} finally {
try {
if( _is != null )
_is.close();
} catch ( Exception e ) {
super.m_service.createServiceException( null, e.getMessage() );
}
}
return response.toString();
}

private void processDownloadRequest( DataBinder inBinder ) throws Exception {
String message = downloadSignedFile( inBinder );
Report.debug( "bezzotechcosign", "WSC Pull response: " + message, null );

Pattern rcPattern = Pattern.compile( "<returnCode>([^<>]*)</returnCode>" );
Matcher m = rcPattern.matcher( message );
if ( m.find() ) {
if ( Integer.parseInt( m.group( 1 ) ) == Errors.SUCCESS ) {
Pattern cPattern = Pattern.compile( "<content>([^<>]*)</content>" );
m = cPattern.matcher( message );
if ( m.find() ) {
String match, content;
content = m.group( 1 );
/**
*  This cannot be permitted to stay
*/
byte[] buffer = CommonDataConversion.uudecode( content, null );
int bytesRead = buffer.length;
String file = m_fsutil.getTemporaryFileName( ".pdf", 0x0 );
BufferedOutputStream _bos = new BufferedOutputStream( new FileOutputStream( file ) );
_bos.write( buffer, 0, bytesRead );
_bos.close();
super.m_binder.putLocal( "primaryFile:path", file );
super.m_binder.putLocal( "dExtension", "pdf" );
super.m_binder.putLocal( "dWebExtension", "pdf" );
super.m_binder.putLocal( "dDocName", inBinder.getLocal( "docID" ) );
} else {
super.m_service.createServiceException( null, "csWSCResponseInvalid" );
}
} else {
super.m_service.createServiceException( null, "csWSCResponseInvalid" );
}
} else {
super.m_service.createServiceException( null, "csWSCResponseInvalid" );
}
}
}