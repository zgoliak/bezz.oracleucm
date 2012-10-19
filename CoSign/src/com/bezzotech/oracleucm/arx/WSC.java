package com.bezzotech.oracleucm.arx;

import intradoc.common.Errors;
import intradoc.common.ExecutionContext;
import intradoc.common.ServiceException;
import intradoc.data.DataBinder;
import intradoc.data.Workspace;
import intradoc.server.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.bezzotech.oracleucm.arx.common.CMUtils;
import com.bezzotech.oracleucm.arx.common.XMLUtils;
import com.bezzotech.oracleucm.arx.shared.SharedObjects;

public class WSC {

	/** SharedObjects pointer to use for this request. */
	public SharedObjects m_shared;
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

	/**
		*
		*/
	public String buildSigProfile( boolean documentFlag ) throws ServiceException {
		m_doc = m_xmlutil.getNewDocument();
		m_doc_root = m_doc.createElement( "request" );
		m_doc.appendChild( m_doc_root );
		m_doc_root.appendChild( buildSigProfilesElement() );
		if ( documentFlag ) m_doc_root.appendChild( buildDocumentElement () );
		m_doc_root.appendChild( buildSignReasonsElement() );
		m_doc_root.appendChild( buildRejectReasonsElement() );
		m_doc_root.appendChild( buildLogicElement() );
		m_doc_root.appendChild( buildUrlElement() );
		m_doc_root.appendChild( buildAuthElement() );
		return m_xmlutil.getStringFromDocument( m_doc );
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
	public void parseSigProfile() throws ServiceException {
		m_doc = m_xmlutil.getExistingDocument( m_cmutil.retrieveSigProfilesFilePath() );
		m_doc_root = m_doc.getDocumentElement();
		parseSigProfileEx();
	}

	/**
		*
		*/
	public void parseSigProfileEx() {
		parseSigProfiles();
		parseSignReasons();
		parseRejectReasons();
		parseAuth();
	}

	/**
		*
		*/
	public void processSignRequest() throws ServiceException {
		String message = uploadRequestToWSC();
		Pattern rcPattern = Pattern.compile( "<returnCode>([^<>]*)</returnCode>" );
		Matcher m = rcPattern.matcher( message );
		if ( m.find() ) {
			if ( Integer.parseInt( m.group( 1 ) ) == Errors.SUCCESS ) {
				Pattern siPattern = Pattern.compile( "<sessionId>([^<>]*)</sessionId>" );
				m = siPattern.matcher( message );
				if ( m.find() ) {
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
	private String uploadRequestToWSC() throws ServiceException {
		String WSC_URL = m_shared.getConfig("CoSign_WSC_URL");
		String response = PostRequest( WSC_URL + "?docId=" + m_binder.getLocal( "docName" ),
				m_binder.getLocal( "SignRequest" ), "application/x-www-form-urlencoded" );
		return response;
	}

	/**
		*
		*/
	private String PostRequest( String i_Url, String i_Content, String i_ContentType )
			throws ServiceException {
		StringBuffer response = new StringBuffer();
		try {
			URL url = new URL( i_Url );
			HttpURLConnection httpCon = ( HttpURLConnection ) url.openConnection();
			httpCon.setRequestProperty( "Content-Type", i_ContentType );
			httpCon.setDoOutput( true );
			httpCon.setDoInput( true );
			httpCon.setRequestMethod( "POST" );
			httpCon.setChunkedStreamingMode(1);

			BufferedOutputStream _out = new BufferedOutputStream( httpCon.getOutputStream() );
			BufferedInputStream _in =
					new BufferedInputStream( new ByteArrayInputStream( i_Content.getBytes( "UTF-8" ) ) );
			byte[] buffer = new byte[8 * 1024];int len = 0;
			while ( ( len = _in.read( buffer, 0, buffer.length ) ) > 0 ) {
				_out.write( buffer, 0, len );
			}
			_in.close();
			_out.flush();
			_out.close();

			BufferedReader _read = null;
			InputStream _is = null;
			if ( httpCon.getResponseCode() <= 400 ) {
				_is = httpCon.getInputStream();
			} else {
				/* error from server */
				_is = httpCon.getErrorStream();
			}
			_read = new BufferedReader( new InputStreamReader( _is ) );String strBuffer = null;
			while ( ( strBuffer = _read.readLine() ) != null ) {
				response.append( strBuffer );
			}
			_is.close();
			_in.close();
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
	private Element buildSignReasonsElement() throws ServiceException {
		return m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "SignReasons" );
	}

	/**
		*
		*/
	private void parseSignReasons() {
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "SignReasons" );
	}

	/**
		*
		*/
	private Element buildRejectReasonsElement() throws ServiceException {
		return m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "RejectReasons" );
	}

	/**
		*
		*/
	private void parseRejectReasons() {
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "RejectReasons" );
	}

	/**
		*
		*/
	private Element buildDocumentElement() throws ServiceException {
		return m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "Document" );
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
	private Element buildLogicElement() throws ServiceException {
		return m_xmlutil.appendChildrenFromEnvironmental( m_appName, m_doc, "Logic" );
	}

	/**
		*
		*/
	private Element buildUrlElement() throws ServiceException {
		return m_xmlutil.appendChildrenFromEnvironmental( m_appName, m_doc, "Url" );
	}

	/**
		*
		*/
	private Element buildAuthElement() throws ServiceException {
		return m_xmlutil.appendChildrenFromLocal( m_appName, m_doc, "Auth" );
	}

	/**
		*
		*/
	private void parseAuth() {
		m_xmlutil.parseChildrenToLocal( m_appName, m_doc_root, "Auth" );
	}
}