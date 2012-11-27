package com.bezzotech.oracleucm.arx;

import intradoc.common.CommonDataConversion;
import intradoc.common.Errors;
import intradoc.common.ExecutionContext;
import intradoc.common.Report;
import intradoc.common.ServiceException;
import intradoc.common.StringUtils;
import intradoc.data.DataBinder;
import intradoc.data.DataException;
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
import java.text.SimpleDateFormat;
import java.text.ParseException;
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
	protected Document m_doc;
	protected Service m_service;
	protected Workspace m_workspace;
	protected DataBinder m_binder;
	protected Element m_doc_root;
	protected CMUtils m_cmutil;
	protected XMLUtils m_xmlutil;

	protected WSC( ExecutionContext context ) throws ServiceException {
		m_shared = SharedObjects.getSharedObjects( context );
		m_fsutil = FileStoreUtils.getFileStoreUtils( context );
		m_cmutil = CMUtils.getCMUtils( context );
		m_xmlutil = XMLUtils.getXMLUtils( context );
		if( context instanceof Service ) {
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
		Report.trace( "bezzotechcosign", "Entering buildSignRequest", null );
		parseSigProfile();
		return buildSigProfile( true );
	}

	/**
	 *
	 */
	public String buildSigProfile( boolean isSignRequest ) throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering buildSigProfile, passed in parameters:" +
				"\n\tisSignRequest: " + isSignRequest, null );
		m_doc = m_xmlutil.getNewDocument();
		m_doc_root = m_doc.createElement( "request" );
		m_doc.appendChild( m_doc_root );
		if( Boolean.parseBoolean( m_binder.getLocal( m_appName + ".Logic.allowAdHoc" ) ) )
			m_doc_root.appendChild( buildSigProfilesElement() );
		else
			m_doc_root.appendChild( m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "SigField" ) );
		if( isSignRequest )
			m_doc_root.appendChild( m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "Document" ) );
		m_doc_root.appendChild( m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "SignReasons" ) );
		m_doc_root.appendChild( m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "Logic" ) );
		if( !isSignRequest )
			m_doc_root.appendChild(
					m_xmlutil.appendChildrenFromEnvironmental( m_appName, m_doc, "Logic", true ) );
		m_doc_root.appendChild(
				m_xmlutil.appendChildrenFromEnvironmental( m_appName, m_doc, "Url", false ) );
		m_doc_root.appendChild( buildAuthElement() );
		return m_xmlutil.getStringFromDocument( m_doc );
	}

	/**
	 *
		*/
	public void parseVerifyResponse( String response ) throws ServiceException {
		m_doc = m_xmlutil.getNewDocument( response );
		m_doc_root = m_doc.getDocumentElement();
		parseVerify();
	}

	/**
	 *
	 */
	public void parseSigProfile() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering parseSigProfile", null );
		m_doc = m_xmlutil.getExistingDocument( m_cmutil.retrieveSigProfilesFilePath() );
		m_doc_root = m_doc.getDocumentElement();
		parseSigProfileEx();
	}

	/**
	 *
	 */
	public void parseSigProfileEx() {
		Report.trace( "bezzotechcosign", "Entering parseSigProfileEx", null );
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "SignReasons" );
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Logic" );
		if( Boolean.parseBoolean( m_binder.getLocal( m_appName + ".Logic.allowAdHoc" ) ) )
			parseSigProfiles();
		else
			m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "SigField" );
	}

	/**
	 *
	 */
	protected void parseWSCResponse( String response, boolean isFullResponse ) throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering parseWSCResponse, passed in parameters:\n\tresponse: " +
				response, null );
		m_doc = m_xmlutil.getNewDocument( response );
		m_doc_root = m_doc.getDocumentElement();
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Error" );
		if( Integer.parseInt( m_binder.getLocal( "CoSign.Error.returnCode" ) ) == Errors.SUCCESS )
			m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Session" );
		if( isFullResponse ) {
			parseDocument();
			m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Result" );
			m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "SigDetails" );
			parseVerify();
		}
	}

	/**
	 *
	 */
	public void processSignRequest() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processSignRequest", null );
		String message = postRequestToWSC( "UploadDoc.aspx?docId=" +
				m_binder.getLocal( "CoSign.Document.fileID" ), m_binder.getLocal( "SignRequest" ),
				"application/x-www-form-urlencoded" );
		Report.trace( "bezzotechcosign", "WSC response: " + message, null );
		parseWSCResponse( message, false );
		if( Integer.parseInt( m_binder.getLocal( "CoSign.Error.returnCode" ) ) == Errors.SUCCESS ) {
			m_binder.putLocal( "WSC_Session", m_binder.getLocal( "CoSign.Session.sessionId" ) );
		} else {
			throw new ServiceException( m_binder.getLocal( "CoSign.Error.errorMessage" ) );
		}
	}

	/**
	 *  Note: Concern over possible syncing issues
	 */
	public void processDownloadRequest() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processDownloadRequest", null );
		String message = postRequestToWSC( "pullSignedDoc.ashx?sessionID=" +
				m_binder.getLocal( "sessionId" ), null, null );
		Report.trace( "bezzotechcosign", "WSC response: " + message, null );
		parseWSCResponse( message, true );
		Report.trace( "bezzotechcosign", "Resulting binder: " + m_binder.toString(), null );
		if( Integer.parseInt( m_binder.getLocal( "CoSign.Error.returnCode" ) ) == Errors.SUCCESS ) {
			byte[] buffer =
					CommonDataConversion.uudecode( m_binder.getLocal( "CoSign.Document.content" ), null );
			int bytesRead = buffer.length;
			String file =
					m_fsutil.getTemporaryFileName( "." + m_binder.getLocal( "CoSign.Document.contentType" ), 0x0 );
			BufferedOutputStream _bos = null;
			try {
				_bos = new BufferedOutputStream( new FileOutputStream( file ) );
				_bos.write( buffer, 0, bytesRead );
			} catch ( FileNotFoundException e ) {
				throwFullError( e );
			} catch ( IOException e ) {
				throwFullError( e );
			} finally {
				try {
					_bos.close();
				} catch ( IOException e ) {
					throwFullError( e );
				}
			}
			m_binder.putLocal( "dDocName", m_binder.getLocal( "CoSign.Session.docId" ) );
			m_binder.mergeResultSetRowIntoLocalData(
					m_cmutil.getDocInfoByName( m_binder.getLocal( "CoSign.Session.docId" ) ) );
			m_binder.putLocalDate( "dInDate", new java.util.Date() );
			m_binder.putLocal( "primaryFile:path", file );
			m_binder.putLocal( "dExtension", m_binder.getLocal( "CoSign.Document.contentType" ) );
			m_binder.putLocal( "dWebExtension", m_binder.getLocal( "CoSign.Document.contentType" ) );
			int statusCode = Integer.parseInt( m_binder.getLocal( "CoSign.Field.status" ) );
			String status;
			if( statusCode == 0 ) status = "Valid";
			else if(statusCode == 1 ) status = "Invalid";
			else status = "Not Signed";
			m_binder.putLocal( "xSignatureStatus", status );
			m_binder.putLocal( "xSigner", m_binder.getLocal( "CoSign.Field.signerName" ) );
			try {
				SimpleDateFormat sdf = new SimpleDateFormat( m_shared.getConfig( "CoSignDateFormat" ) );
				Date date = sdf.parse( m_binder.getLocal( "CoSign.Field.signingTime" ) );
				m_binder.putLocalDate( "xSignTime", date );
				m_binder.putLocalDate( "CoSign.Field.signingTime", date );
			} catch ( ParseException e ) {
				throwFullError( e );
			}
			int count = m_cmutil.getSignedCounter( m_binder.getLocal( "CoSign.Session.docId" ) );
			m_binder.putLocal( "xSignatureCount", ++count + "" );
			prepareLog();
		} else {
			throw new ServiceException( m_binder.getLocal( "CoSign.Error.errorMessage" ) );
		}
	}

	/**
		*
		*/
	public void processVerifyRequest() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processVerifyRequest", null );
		String message = postStreamToWSC( "VerifyService.aspx", m_binder,
				m_binder.getResultSetValue( m_binder.getResultSet( "DOC_INFO" ), "dFormat" ) );
		Report.trace( "bezzotechcosign", "WSC response: " + message, null );
		parseVerifyResponse( message );
		if( m_binder.getLocal( "CoSign.Error.returnCode" ) != null ) {
			throw new ServiceException( m_binder.getLocal( "CoSign.Error.errorMessage" ) );
		}
		if( m_binder.getLocal( "CoSign.Field.fieldName" ) == null )	return;
		m_binder.mergeResultSetRowIntoLocalData( m_binder.getResultSet( "DOC_INFO" ) );
		int statusCode = Integer.parseInt( m_binder.getLocal( "CoSign.Field.status" ) );
		String status;
		if( statusCode == 0 ) status = "Valid";
		else if(statusCode == 1 ) status = "Invalid";
		else status = "Not Signed";
		m_binder.putLocal( "xSignatureStatus", status );
		m_binder.putLocal( "xSigner", m_binder.getLocal( "CoSign.Field.signerName" ) );
		try {
			SimpleDateFormat sdf = new SimpleDateFormat( m_shared.getConfig( "CoSignDateFormat" ) );
			Date date = sdf.parse( m_binder.getLocal( "CoSign.Field.signingTime" ) );
			m_binder.putLocalDate( "xSignTime", date );
			m_binder.putLocalDate( "CoSign.Field.signingTime", date );
		} catch ( ParseException e ) {
			throwFullError( e );
		}
		int count = m_cmutil.getSignedCounter( m_binder.getLocal( "dID" ) );
		m_binder.putLocal( "xSignatureCount", ++count + "" );
		m_cmutil.update();
		log();
	}

	/**
	 *
		*/
	protected String postStreamToWSC( String urlQueryString, DataBinder content, String contentType )
			throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering postStreamToWSC, passed in parameters:" +
				"\n\turlQueryString: " + urlQueryString + "\n\tcontent:\n\tcontentType: " + contentType, null );
		String WSC_URL = m_shared.getConfig( "wscServerAddress" );
		String WSC_PORT = m_shared.getConfig( "wscServerPort" );
		String WSC_ROOT = m_shared.getConfig( "CoSignVerifyServerPath" );
		if( WSC_PORT == null || WSC_PORT.equals( "" ) ) WSC_PORT = "80";
		String response = postRequestBinder( WSC_URL + ":" + WSC_PORT + WSC_ROOT + urlQueryString, content,
				contentType );
		return response;
	}

	/**
	 *
		*/
	protected String postRequestToWSC( String urlQueryString, String content, String contentType )
			throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processSignRequest, passed in parameters:" +
				"\n\turlQueryString: " + urlQueryString + "\n\tcontent: " + content + "\n\tcontentType: " +
				contentType, null );
		String WSC_URL = m_shared.getConfig( "wscServerAddress" );
		String WSC_PORT = m_shared.getConfig( "wscServerPort" );
		String WSC_ROOT = m_shared.getConfig( "CoSignServerPath" );
		if( WSC_PORT == null || WSC_PORT.equals( "" ) ) WSC_PORT = "80";
		String response = postRequestString( WSC_URL + ":" + WSC_PORT + WSC_ROOT + urlQueryString, content,
				contentType );
		return response;
	}

	/**
	 *  Note: CoSign running over SSL
		*  Note: Handling read in case of Socket lag
	 */
	protected String postRequestBinder( String iUrl, DataBinder content, String iContentType )
			throws ServiceException {
		StringBuffer response = new StringBuffer();
		BufferedOutputStream _out = null;
		BufferedReader _read = null;
		InputStream _is = null;
		HttpURLConnection httpCon = null;
		try {
			URL url = new URL( iUrl );
			httpCon = ( HttpURLConnection ) url.openConnection();
			httpCon.setRequestProperty( "Content-Type", iContentType );
			httpCon.setDoOutput( true );
			httpCon.setRequestMethod( "POST" );

			Report.trace( "bezzotechcosign", "Avialable file size: " + content.m_inStream.available(), null );
			_out = new BufferedOutputStream( httpCon.getOutputStream() );
			byte[] baBuffer = new byte[64 * 1024];
			int len = 0;
			while ( ( len = content.m_inStream.read( baBuffer, 0, baBuffer.length ) ) > 0 ) {
				_out.write( baBuffer, 0, len );
			}
			_out.flush();
			if( httpCon.getResponseCode() <= 400 ) {
				_is = httpCon.getInputStream();
			} else {
				/* error from server */
				_is = httpCon.getErrorStream();
			}
			_read = new BufferedReader( new InputStreamReader( _is ) );
			String strBuffer = null;
			while ( ( strBuffer = _read.readLine() ) != null ) {
				response.append( strBuffer );
			}
		} catch ( MalformedURLException e ) {
		 throwFullError( e );
		} catch ( ProtocolException e ) {
		 throwFullError( e );
		} catch ( IOException e ) {
		 throwFullError( e );
		} finally {
		 try {
				content.m_inStream.close();
				_out.close();
				_is.close();
				_read.close();
				httpCon.disconnect();
			} catch ( IOException e ) {
				throwFullError( e );
			}
		}
		return response.toString();
	}

	/**
	 *
	 */
	protected String postRequestString( String iUrl, String iContent, String iContentType )
			throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering processSignRequest, passed in parameters:" +
				"\n\tiUrl: " + iUrl + "\n\tiContent: " + iContent + "\n\tiContentType: " +	iContentType, null );
		StringBuffer response = new StringBuffer();
		BufferedOutputStream _out = null;
		BufferedInputStream _in = null;
		BufferedReader _read = null;
		InputStream _is = null;
		HttpURLConnection httpCon = null;
		try {
			URL url = new URL( iUrl );
			httpCon = ( HttpURLConnection ) url.openConnection();
			if( iContentType != null ) httpCon.setRequestProperty( "Content-Type", iContentType );

			httpCon.setDoOutput( true );
			if( iContent != null && iContentType != null ) {
				httpCon.setDoInput( true );
				httpCon.setRequestMethod( "POST" );
				httpCon.setChunkedStreamingMode(1);
			}

			if( iContent != null ) {
				_out = new BufferedOutputStream( httpCon.getOutputStream() );
				_in = new BufferedInputStream( new ByteArrayInputStream( iContent.getBytes( "UTF-8" ) ) );
				byte[] buffer = new byte[8 * 1024];
				int len = 0;
				while ( ( len = _in.read( buffer, 0, buffer.length ) ) > 0 ) {
					_out.write( buffer, 0, len );
				}
				_out.flush();
			}

			if( httpCon.getResponseCode() <= 400 ) {
				_is = httpCon.getInputStream();
			} else {
				/* error from server */
				_is = httpCon.getErrorStream();
			}
			_read = new BufferedReader( new InputStreamReader( _is ) );
			String strBuffer = null;
			while( ( strBuffer = _read.readLine() ) != null ) {
				response.append( strBuffer );
			}
		} catch ( MalformedURLException e ) {
		 throwFullError( e );
		} catch ( ProtocolException e ) {
		 throwFullError( e );
		} catch ( UnsupportedEncodingException e ) {
		 throwFullError( e );
		} catch ( IOException e ) {
		 throwFullError( e );
		} finally {
		 try {
				_in.close();
				_out.close();
				_is.close();
				_read.close();
				httpCon.disconnect();
			} catch ( IOException e ) {
				throwFullError( e );
			}
		}
		return response.toString();
	}

	/**
	 *
	 */
	protected Element buildSigProfilesElement() throws ServiceException {
		Element root = m_doc.createElement( "SigProfiles" );
		Element row = m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "SigProfile" );
		root.appendChild( row );
		return root;
	}

	/** Generates XML Element Auth from binder local properties
	 *  <Auth>...</Auth>
		*
		*  If environmental variable useWCCUserName is set to TRUE, we inject dUser value into username
	 *  text node
		*
		*  Throws ServiceException - dUser value is missing from session
		*  Throws ServiceException - Auth Element was not built properly for injection
		*/
	protected Element buildAuthElement() throws ServiceException {
		Element ele = m_xmlutil.appendChildrenFromEnvironmental( m_appName, m_doc, "Auth", false );
		if( StringUtils.convertToBool( m_shared.getConfig( "useWCCUserName" ), false ) ) {
			String username = m_binder.getLocal( "dUser" );
			if( username == null || username.equals( "" ) ) throw new ServiceException( "csInvalidWCCSession" );
			m_xmlutil.appendTextNodeToChild( m_doc, ele, "username", username );
		}
		return ele;
	}

	/** Parse SigProfile XML response to binder local properties
	 *  <SigProfiles><SigProfil>...</SigProfile></SigProfiles>
	 */
	protected void parseSigProfiles() {
		Element root = ( Element )m_doc_root.getElementsByTagName( "SigProfiles" ).item( 0 );
		m_xmlutil.parseChildrenToLocal( m_appName, root, "SigProfile" );
	}

	/** Parse Document XML response to binder local properties
	 *  <Document>...</Document>
	 */
	protected void parseDocument() {
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Document" );
	}

	/** Parse Verify XML response to binder local properties
	 *  <Verify><Status>...</Status><Fields><Field>...</Field></Fields></Verify>
	 */
	protected void parseVerify() {
		Element root = ( Element )m_doc_root.getElementsByTagName( "Verify" ).item( 0 );
		m_xmlutil.parseChildrenToLocal( m_appName, root, "Status" );
		Element subroot = ( Element )root.getElementsByTagName( "Fields" ).item( 0 );
		if( subroot.getChildNodes().getLength() > 0 )
			m_xmlutil.parseChildrenToLocal( m_appName, subroot, "Field" );
	}

	/** Prepares binder for inserting Verify response.  We get much more data on pull than we do on
	 *  Verify request
		*/
	protected void prepareLog() {
		Report.trace( "bezzotechcosign", "Entering prepareLog, passed in binder:", null );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.x" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.x", "0" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.y" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.y", "0" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.width" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.width", "0" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.height" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.height", "0" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.pageNumber" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.pageNumber", "0" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.dateFormat" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.dateFormat", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.timeformat" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.timeformat", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.graphicalImage" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.graphicalImage", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.signer" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.signer", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.date" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.date", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.initials" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.initials", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.logo" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.logo", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.showTitle" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.showTitle", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.showReason" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.showReason", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.title" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.title", "null" );
		if( m_binder.getAllowMissing( "CoSign.SigDetails.reason" ) == null )
			m_binder.putLocal( "CoSign.SigDetails.reason", "null" );
		Report.trace( "bezzotechcosign", "Built binder:" + m_binder.toString() , null );
	}

	/** Inserts Verify response into DB
	 *
		*  Throws ServiceException - error occurred during query action
		*/
	protected void log() throws ServiceException {
		Report.trace( "bezzotechcosign", "Entering log, passed in binder:", null );
		prepareLog();
		try {
			m_workspace.execute( "IcosignSignatureDetails", m_binder );
		} catch ( DataException e ) {
		 throwFullError( e );
		}
	}

	/** Prints out error message and stack trace from caught exceptions and throws them as message in
	 *  ServiceException
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