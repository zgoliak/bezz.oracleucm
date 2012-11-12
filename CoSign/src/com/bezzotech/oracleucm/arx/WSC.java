package com.bezzotech.oracleucm.arx;

import intradoc.common.CommonDataConversion;
import intradoc.common.Errors;
import intradoc.common.ExecutionContext;
import intradoc.common.LocaleUtils;
import intradoc.common.Report;
import intradoc.common.ServiceException;
import intradoc.data.DataBinder;
import intradoc.data.DataException;
import intradoc.data.IdcCounterUtils;
import intradoc.data.Workspace;
import intradoc.server.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.bezzotech.oracleucm.arx.common.CMUtils;
import com.bezzotech.oracleucm.arx.common.XMLUtils;
import com.bezzotech.oracleucm.arx.service.FileStoreUtils;
import com.bezzotech.oracleucm.arx.shared.SharedObjects;

public class WSC {

	/** SharedObjects pointer to use for this request. */
	public SharedObjects m_shared;

	/** <span class="code">FileStoreUtils</span> object for this request. */
	protected FileStoreUtils m_fsutil;

	final String m_appName = "CoSign";
	private Document m_doc;
	private Service m_service;
	private Workspace m_workspace;
	private DataBinder m_binder;
	private Element m_doc_root;
	protected CMUtils m_cmutil;
	protected XMLUtils m_xmlutil;

	protected WSC( ExecutionContext context ) throws ServiceException {
		m_shared = SharedObjects.getSharedObjects( context );
		m_fsutil = FileStoreUtils.getFileStoreUtils( context );
		m_cmutil = CMUtils.getCMUtils( context );
		m_xmlutil = XMLUtils.getXMLUtils( context );
		if ( context instanceof Service ) {
			Service m_service = ( Service )context;
			m_workspace = m_service.getWorkspace();
			m_binder = m_service.getBinder();
		}
	}

	static WSC getWSC( ExecutionContext context ) throws ServiceException {
		return new WSC( context );
	}

	public CMUtils getCM() {
		return m_cmutil;
	}

	/**
	 *
	 */
	public String buildSignRequest() throws ServiceException {
		parseSigProfile();
		return buildSigProfile( true );
	}

	/**
	 *
	 */
	public String buildSigProfile( boolean signRequestFlag ) throws ServiceException {
		m_doc = m_xmlutil.getNewDocument();
		m_doc_root = m_doc.createElement( "request" );
		m_doc.appendChild( m_doc_root );
		if ( Boolean.parseBoolean( m_binder.getLocal( m_appName + ".Logic.allowAdHoc" ) ) )
			m_doc_root.appendChild( buildSigProfilesElement() );
		if ( signRequestFlag )
			m_doc_root.appendChild( m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "Document" ) );
		m_doc_root.appendChild( m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "SignReasons" ) );
		m_doc_root.appendChild( m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "Logic" ) );
		if ( !signRequestFlag )
			m_doc_root.appendChild(
					m_xmlutil.appendChildrenFromEnvironmental( m_appName, m_doc, "Logic", true ) );
		m_doc_root.appendChild(
				m_xmlutil.appendChildrenFromEnvironmental( m_appName, m_doc, "Url", false ) );
		m_doc_root.appendChild(
				m_xmlutil.appendChildrenFromEnvironmental( m_appName, m_doc, "Auth", false ) );
		return m_xmlutil.getStringFromDocument( m_doc );
	}

	/**
	 *
		*/
	public String buildVerifyRequest() throws ServiceException {
		m_doc = m_xmlutil.getNewDocument();
		m_doc_root = m_doc.createElement( "request" );
		m_doc.appendChild( m_doc_root );
		m_doc_root.appendChild( m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "Document" ) );
		return m_xmlutil.getStringFromDocument( m_doc );
	}

	/**
	 *
	 */
	public void parseSigProfile() throws ServiceException {
		m_doc = m_xmlutil.getExistingDocument( m_cmutil.retrieveSigProfilesFilePath() );
		m_doc_root = m_doc.getDocumentElement();
		parseSigProfileEx();
	}

	/**
	 *
	 */
	public void parseSigProfileEx() {
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "SignReasons" );
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Logic" );
		if ( Boolean.parseBoolean( m_binder.getLocal( m_appName + ".Logic.allowAdHoc" ) ) )
			parseSigProfiles();
	}

	/**
	 *
	 */
	private void parseWSCResponse( String response ) throws ServiceException {
		m_doc = m_xmlutil.getNewDocument( response );
		m_doc_root = m_doc.getDocumentElement();
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Error" );
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Session" );
		parseDocument();
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Result" );
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "SigDetails" );
		parseVerify();
	}

	/**
	 *
	 */
	public void processSignRequest() throws ServiceException {
		String message = uploadRequestToWSC();
		Report.debug( "bezzotechcosign", "WSC response: " + message, null );
		Pattern rcPattern = Pattern.compile( "<returnCode>([^<>]*)</returnCode>" );
		Matcher m = rcPattern.matcher( message );
		if ( m.find() ) {
			if ( Integer.parseInt( m.group( 1 ) ) == Errors.SUCCESS ) {
				Pattern siPattern = Pattern.compile( "<sessionId>([^<>]*)</sessionId>" );
				m = siPattern.matcher( message );
				if ( m.find() ) {
					Report.debug( "bezzotechcosign", "Session found: " + m.group( 1 ), null );
					m_binder.putLocal( "WSC_Session", m.group( 1 ) ); 
				} else {
					throw new ServiceException( "csInvalidSessionId" );
				}
			} else {
				throw new ServiceException( "csWSCFailed" );
			}
		} else {
			throw new ServiceException( "csWSCResponseInvalid" );
		}
	}

	/**
	 *
	 */
	public void processDownloadRequest() throws ServiceException {
		String message = downloadSignedFile();
		Report.debug( "bezzotechcosign", "WSC response: " + message, null );
		Pattern rcPattern = Pattern.compile( "<returnCode>([^<>]*)</returnCode>" );
		Matcher m = rcPattern.matcher( message );
		if ( m.find() ) {
			if ( Integer.parseInt( m.group( 1 ) ) == Errors.SUCCESS ) {
				parseWSCResponse( message );
				Report.debug( "bezzotechcosign", "Resulting binder: " + m_binder.toString(), null );
				byte[] buffer =
						CommonDataConversion.uudecode( m_binder.getLocal( "CoSign.Document.content" ), null );
				int bytesRead = buffer.length;
				String file =
						m_fsutil.getTemporaryFileName( "." + m_binder.getLocal( "CoSign.Document.contentType" ), 0x0 );
				try {
					BufferedOutputStream _bos = new BufferedOutputStream( new FileOutputStream( file ) );
					_bos.write( buffer, 0, bytesRead );
					_bos.close();
				} catch ( FileNotFoundException e ) {
					e.printStackTrace();
					throw new ServiceException( e.getMessage() );
				} catch ( IOException e ) {
					e.printStackTrace();
					throw new ServiceException( e.getMessage() );
				}
				m_binder.putLocal( "dDocName", m_binder.getLocal( "CoSign.Session.docId" ) );
				m_binder.mergeResultSetRowIntoLocalData(
						m_cmutil.getDocInfoByName( m_binder.getLocal( "CoSign.Session.docId" ) ) );
				m_binder.putLocal( "dRevLabel",
						( Integer.parseInt( m_binder.getLocal( "dRevLabel" ) ) + 1 ) + "" );
				m_binder.putLocal( "primaryFile:path", file );
				m_binder.putLocal( "dExtension", m_binder.getLocal( "CoSign.Document.contentType" ) );
				m_binder.putLocal( "dWebExtension", m_binder.getLocal( "CoSign.Document.contentType" ) );
				int statusCode = Integer.parseInt( m_binder.getLocal( "CoSign.Field.status" ) );
				String status;
				if ( statusCode == 0 ) status = "Valid";
				else if (statusCode == 1 ) status = "Invalid";
				else status = "Not Signed";
				m_binder.putLocal( "xSignatureStatus", status );
				m_binder.putLocal( "xSigner", m_binder.getLocal( "CoSign.Field.signerName" ) );
				m_binder.putLocal( "xSignTime", m_binder.getLocal( "CoSign.Field.signingTime" ) );
				int count = m_cmutil.getSignedCounter( m_binder.getLocal( "CoSign.Session.docId" ) );
				m_binder.putLocal( "xSignatureCount", ++count + "" );
				log();
				Report.debug( "bezzotechcosign", "Resulting binder: " + m_binder.toString(), null );
			} else {
				throw new ServiceException( "csWSCResponseInvalid" );
			}
		} else {
			throw new ServiceException( "csWSCResponseInvalid" );
		}
	}

	/**
		*
		*/
	public void processVerifyRequest() throws ServiceException {
		String message = postRequestToWSC( "VerifyService.aspx", m_binder.getLocal( "VerifyRequest" ),
				"application/x-www-form-urlencoded" );
		Report.debug( "bezzotechcosign", "WSC response: " + message, null );
		Pattern rcPattern = Pattern.compile( "<returnCode>([^<>]*)</returnCode>" );
		Matcher m = rcPattern.matcher( message );
		if ( m.find() ) {
			if ( Integer.parseInt( m.group( 1 ) ) == Errors.SUCCESS ) {
				parseWSCResponse( message );
				m_binder.mergeResultSetRowIntoLocalData(
						m_cmutil.getDocInfoByName( m_binder.getLocal( "CoSign.Session.docId" ) ) );
				int statusCode = Integer.parseInt( m_binder.getLocal( "CoSign.Field.status" ) );
				String status;
				if ( statusCode == 0 ) status = "Valid";
				else if (statusCode == 1 ) status = "Invalid";
				else status = "Not Signed";
				m_binder.putLocal( "xSignatureStatus", status );
				m_binder.putLocal( "xSigner", m_binder.getLocal( "CoSign.Field.signerName" ) );
				m_binder.putLocal( "xSignTime", m_binder.getLocal( "CoSign.Field.signingTime" ) );
				int count = m_cmutil.getSignedCounter( m_binder.getLocal( "CoSign.Session.docId" ) );
				m_binder.putLocal( "xSignatureCount", ++count + "" );
				m_cmutil.update();
				log();
			} else {
				throw new ServiceException( "csWSCResponseInvalid" );
			}
		} else {
			throw new ServiceException( "csWSCResponseInvalid" );
		}
	}

	/**
	 *
	 */
	private String uploadRequestToWSC() throws ServiceException {
		String WSC_URL = m_shared.getConfig( "wscServerAddress" );
		String WSC_PORT = m_shared.getConfig( "wscServerPort" );
		if ( WSC_PORT == null || WSC_PORT.equals( "" ) ) WSC_PORT = "80";
		String response = postRequest( WSC_URL + ":" + WSC_PORT + "/wsc/UploadDoc.aspx?docId=" +
				m_binder.getLocal( "CoSign.Document.fileID" ), m_binder.getLocal( "SignRequest" ),
				"application/x-www-form-urlencoded" );
		return response;
	}

	/**
	 *
	 */
	private String downloadSignedFile() throws ServiceException {
		String WSC_URL = m_shared.getConfig( "wscServerAddress" );
		String WSC_PORT = m_shared.getConfig( "wscServerPort" );
		if ( WSC_PORT == null || WSC_PORT.equals( "" ) ) WSC_PORT = "80";
		String response = postRequest( WSC_URL + ":" + WSC_PORT + "/wsc/pullSignedDoc.ashx?sessionID=" +
				m_binder.getLocal( "sessionId" ), null, null );
		return response;
	}

	/**
	 *
		*/
	private String postRequestToWSC( String urlQueryString, String content, String contentType )
			throws ServiceException {
		String WSC_URL = m_shared.getConfig( "wscServerAddress" );
		String WSC_PORT = m_shared.getConfig( "wscServerPort" );
		if ( WSC_PORT == null || WSC_PORT.equals( "" ) ) WSC_PORT = "80";
		String response = postRequest( WSC_URL + ":" + WSC_PORT + "/wsc/" + urlQueryString, content,
				contentType );
		return response;
	}

	/**
	 *
	 */
	private String postRequest( String iUrl, String iContent, String iContentType )
			throws ServiceException {
		StringBuffer response = new StringBuffer();
		try {
			URL url = new URL( iUrl );
			HttpURLConnection httpCon = ( HttpURLConnection ) url.openConnection();
			if ( iContentType != null ) httpCon.setRequestProperty( "Content-Type", iContentType );

			httpCon.setDoOutput( true );
			if ( iContent != null && iContentType != null ) {
				httpCon.setDoInput( true );
				httpCon.setRequestMethod( "POST" );
				httpCon.setChunkedStreamingMode(1);
			}

			if ( iContent != null ) {
				BufferedOutputStream _out = new BufferedOutputStream( httpCon.getOutputStream() );
				BufferedInputStream _in =
						new BufferedInputStream( new ByteArrayInputStream( iContent.getBytes( "UTF-8" ) ) );
				byte[] buffer = new byte[8 * 1024];int len = 0;
				while ( ( len = _in.read( buffer, 0, buffer.length ) ) > 0 ) {
					_out.write( buffer, 0, len );
				}
				_in.close();
				_out.flush();
				_out.close();
			}

			BufferedReader _read = null;
			InputStream _is = null;
			String strBuffer = null;
			if ( httpCon.getResponseCode() <= 400 ) {
				_is = httpCon.getInputStream();
			} else {
				/* error from server */
				_is = httpCon.getErrorStream();
			}
			_read = new BufferedReader( new InputStreamReader( _is ) );
			while ( ( strBuffer = _read.readLine() ) != null ) {
				response.append( strBuffer );
			}
			_is.close();
			_read.close();
			httpCon.disconnect();
		} catch ( MalformedURLException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		} catch ( ProtocolException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		} catch ( UnsupportedEncodingException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		} catch ( IOException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
		return response.toString();
	}

	/**
	 *
	 */
	private Element buildSigProfilesElement() throws ServiceException {
		Element root = m_doc.createElement( "SigProfiles" );
		Element row = m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "SigProfile" );
		root.appendChild( row );
		return root;
	}

	/**
	 *
	 */
	private void parseSigProfiles() {
		Element root = ( Element )m_doc_root.getElementsByTagName( "SigProfiles" ).item( 0 );
		m_xmlutil.parseChildrenToLocal( m_appName, root, "SigProfile" );
	}

	/**
	 *
	 */
	private void parseDocument() {
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Document" );
	}

	/**
	 *
	 */
	private void parseVerify() {
		Element root = ( Element )m_doc_root.getElementsByTagName( "Verify" ).item( 0 );
		m_xmlutil.parseChildrenToLocal( m_appName, root, "Status" );
		Element subroot = ( Element )root.getElementsByTagName( "Fields" ).item( 0 );
		m_xmlutil.parseChildrenToLocal( m_appName, subroot, "Field" );
	}

	/**
	 *
		*/
	private void log() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering log, passed in binder:" + m_binder.toString() , null );
		String title = m_binder.getAllowMissing( "CoSign.SigDetails.title" );
		if ( title == null )
			m_binder.putLocal( "CoSign.SigDetails.title", "null" );
		Report.debug( "bezzotechcosign", "Built binder:" + m_binder.toString() , null );
		try {
			m_workspace.execute( "IcosignSignatureDetails", m_binder );
		} catch ( DataException e ) {
			StringBuilder sb = new StringBuilder();
			for (StackTraceElement element : e.getStackTrace()) {
				sb.append(element.toString());
				sb.append("\n");
			}
			Report.debug( "bezzotechcosign", e.getMessage() + "\n" + sb.toString(), null );
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
	}
}