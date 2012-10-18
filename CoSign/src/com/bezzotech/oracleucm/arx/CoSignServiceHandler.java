package com.bezzotech.oracleucm.arx;

import intradoc.common.CommonDataConversion;
import intradoc.common.Errors;
import intradoc.common.ExecutionContext;
import intradoc.common.FileUtils;
import intradoc.common.Report;
import intradoc.common.ServiceException;
import intradoc.common.StringUtils;

import intradoc.data.DataBinder;
import intradoc.data.DataException;
import intradoc.data.DataResultSet;
import intradoc.data.IdcProperties;
import intradoc.data.ResultSet;
import intradoc.data.ResultSetUtils;
import intradoc.data.Workspace;

import intradoc.filestore.FileStoreProvider;
import intradoc.filestore.FileStoreUtils;
import intradoc.filestore.IdcFileDescriptor;

import intradoc.server.Service;
import intradoc.server.ServiceData;
import intradoc.server.ServiceHandler;
import intradoc.server.ServiceManager;

import intradoc.shared.SecurityUtils;
import intradoc.shared.SharedObjects;
import intradoc.shared.UserData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import org.xml.sax.SAXException;

public class CoSignServiceHandler extends ServiceHandler {
	private static boolean m_undo = false;
	/**
	 *  TODO: Need to implement better Cookie handling
	 */
	// private static Map < String, String > cookie = new HashMap < String, String > ();
	private static String m_cookie = "";
	private final String PLACEHOLDER_CONTENT = "uploadedFileContent";

	public void generateCoSignProfile() throws ServiceException {
		String s = WSC.buildSigProfile( super.m_binder, false );
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
		DataBinder requestBinder = new DataBinder();
		requestBinder.putLocal( "CoSign.Document.fields", "fileID;contentType;content" );
		requestBinder.putLocal( "docName", super.m_binder.getLocal( "dDocName" ) );
		requestBinder.putLocal( "CoSign.Document.fileID", super.m_binder.getLocal( "dID" ) );
		ResultSet rset =
				CMUtils.getDocInfo( super.m_workspace, requestBinder.getLocal( "CoSign.Document.fileID" ) );
		requestBinder.putLocal( "CoSign.Document.contentType", ResultSetUtils.getValue( rset, "dExtension" ) );
		requestBinder.putLocal( "CoSignProfile", ResultSetUtils.getValue( rset, "xCoSignContentProfile" ) );
		DataResultSet drset = new DataResultSet();
		drset.copy( rset );
		requestBinder.addResultSet( "DOC_INFO", drset );
		Report.debug( "bezzotechcosign", "Required metadata for Signing Ceremony have been gathered from" +
				" content item.", null );
		Report.debug( "bezzotechcosign", requestBinder.toString(), null );

		requestBinder.putLocal( "CoSign.Document.content", PLACEHOLDER_CONTENT );
		String SignRequest = WSC.buildSignRequest( super.m_service, super.m_workspace, requestBinder );

		String msg = "";
		try {
			String file = CMUtils.getFileAsString( super.m_service, requestBinder );
			Report.debug( "bezzotechcosign", "Base 64 encoded file: " + file, null );
			String input = SignRequest.replaceAll( PLACEHOLDER_CONTENT, file );
			Report.debug( "bezzotechcosign", "Sign Request updated: " + input, null );
			String output = URLEncoder.encode( input, "UTF-8" );
			Report.debug( "bezzotechcosign", "sign Request url encoded: " + output, null );
			SignRequest = "inputXML=" + output;
			requestBinder.putLocal( "SignRequest", SignRequest );
			WSC.processSignRequest( requestBinder );
		} catch ( UnsupportedEncodingException e ) {
			e.printStackTrace();
			msg = e.getMessage();
			m_undo = true;
		} catch ( ServiceException e ) {
			e.printStackTrace();
			msg = e.getMessage();
			m_undo = true;
		} finally {
			String session = requestBinder.getLocal( "WSC_Session" );
			if ( session != null )
				super.m_binder.putLocal( "WSC_Session", session );
		}
		if ( m_undo ) {
			CMUtils.rollback( super.m_service, super.m_workspace, requestBinder, msg );
		}
	}

	public void processSignedDocument() throws ServiceException {
		Report.debug( "bezzotechcosign", "Entering processSignedDocument, passed in binder: " +
				super.m_binder.toString(), null );
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
			CMUtils.rollback( super.m_service, super.m_workspace, requestBinder, msg );
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
			WSC.parseSigProfile( super.m_service, super.m_workspace, super.m_binder );
//			parseDocument( dom );
		} catch ( Exception e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
	}

	public static class WSC {
		final static String m_appName = "CoSign";
		private static Document m_doc;
		private static Service m_service;
		private static Workspace m_workspace;
		private static DataBinder m_binder;
		private static Element m_doc_root;

		/**
		 *
			*/
		public static String buildSigProfile( DataBinder binder, boolean documentFlag )
				throws ServiceException {
			m_doc = XMLUtils.getNewDocument();
			m_binder = binder;
			m_doc_root = m_doc.createElement( "request" );
			m_doc.appendChild( m_doc_root );
			m_doc_root.appendChild( buildSigProfilesElement() );
			if ( documentFlag ) m_doc_root.appendChild( buildDocumentElement () );
			m_doc_root.appendChild( buildSignReasonsElement() );
			m_doc_root.appendChild( buildRejectReasonsElement() );
			m_doc_root.appendChild( buildLogicElement() );
			m_doc_root.appendChild( buildUrlElement() );
			m_doc_root.appendChild( buildAuthElement() );
			return XMLUtils.getStringFromDocument( m_doc );
		}

		/**
		 *
			*/
		public static String buildSignRequest( Service service, Workspace workspace, DataBinder binder )
				throws ServiceException {
			parseSigProfile( service, workspace, binder );
			return buildSigProfile( m_binder, true );
		}

		/**
		 *
			*/
		private static void parseSigProfile( Service service, Workspace workspace, DataBinder binder )
				throws ServiceException {
			m_service = service;
			m_workspace = workspace;
			m_binder = binder;
			m_doc = XMLUtils.getExistingDocument(
					CMUtils.retrieveSigProfilesFilePath( m_service, m_workspace, m_binder ) );
			m_doc_root = m_doc.getDocumentElement();
			parseSigProfile();
		}

		/**
		 *
			*/
		public static void parseSigProfile() {
			parseSigProfiles();
			parseSignReasons();
			parseRejectReasons();
			parseAuth();
		}

		/**
		 *
			*/
		private static void processSignRequest( DataBinder inBinder ) throws ServiceException {
			Report.debug( "bezzotechcosign", "Entering processSignRequest," +
					"passed in parameters:\ninBinder: " + inBinder.toString(), null );
			String message = uploadRequestToWSC( inBinder );
			Report.debug( "bezzotechcosign", "WSC Response: " + message, null );
			Pattern rcPattern = Pattern.compile( "<returnCode>([^<>]*)</returnCode>" );
			Matcher m = rcPattern.matcher( message );
			if ( m.find() ) {
				if ( Integer.parseInt( m.group( 1 ) ) == Errors.SUCCESS ) {
					Pattern siPattern = Pattern.compile( "<sessionId>([^<>]*)</sessionId>" );
					m = siPattern.matcher( message );
					if ( m.find() ) {
						inBinder.putLocal( "WSC_Session", m.group( 1 ) ); 
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
		private static String uploadRequestToWSC( DataBinder inBinder ) throws ServiceException {
			String WSC_URL = SharedObjects.getEnvironmentValue("CoSign_WSC_URL");
			String response = "";
			try {
				response = PostRequest( WSC_URL + "?docId=" + inBinder.getLocal( "docName" ),
				inBinder.getLocal( "SignRequest" ), "application/x-www-form-urlencoded" );
			} catch ( Exception e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			}
			return response;
		}

		/**
		 *
			*/
		private static String PostRequest( String i_Url, String i_Content, String i_ContentType )
				throws Exception {
			StringBuffer response = new StringBuffer();
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
			return response.toString();
		}

		/**
		 *
			*/
		private static Element buildSigProfilesElement() throws ServiceException {
			Element root = m_doc.createElement( "SigProfiles" );
			Element row = XMLUtils.appendChildrenFromLocal( m_appName, m_doc, m_binder, "SigProfile" );
			root.appendChild( row );
			return root;
		}

		/**
		 *
			*/
		private static void parseSigProfiles() {
			Element root = ( Element )m_doc_root.getElementsByTagName( "SigProfiles" ).item( 0 );
			XMLUtils.parseChildrenToLocal( m_appName, root, m_binder, "SigProfile" );
		}

		/**
		 *
			*/
		private static Element buildSignReasonsElement() throws ServiceException {
			return XMLUtils.appendChildrenFromLocal( m_appName, m_doc, m_binder, "SignReasons" );
		}

		/**
		 *
			*/
		private static void parseSignReasons() {
			XMLUtils.parseChildrenToLocal( m_appName, m_doc_root, m_binder, "SignReasons" );
		}

		/**
		 *
			*/
		private static Element buildRejectReasonsElement() throws ServiceException {
			return XMLUtils.appendChildrenFromLocal( m_appName, m_doc, m_binder, "RejectReasons" );
		}

		/**
		 *
			*/
		private static void parseRejectReasons() {
			XMLUtils.parseChildrenToLocal( m_appName, m_doc_root, m_binder, "RejectReasons" );
		}

		/**
		 *
			*/
		private static Element buildDocumentElement() throws ServiceException {
			return XMLUtils.appendChildrenFromLocal( m_appName, m_doc, m_binder, "Document" );
		}

		/**
		 *
			*/
		private static void parseDocument() {
			XMLUtils.parseChildrenToLocal( m_appName, m_doc_root, m_binder, "Document" );
		}

		/**
		 *
			*/
		private static Element buildLogicElement() throws ServiceException {
			return XMLUtils.appendChildrenFromEnvironmental( m_appName, m_doc, "Logic" );
		}

		/**
		 *
			*/
		private static Element buildUrlElement() throws ServiceException {
			return XMLUtils.appendChildrenFromEnvironmental( m_appName, m_doc, "Url" );
		}

		/**
		 *
			*/
		private static Element buildAuthElement() throws ServiceException {
			return XMLUtils.appendChildrenFromLocal( m_appName, m_doc, m_binder, "Auth" );
		}

		/**
		 *
			*/
		private static void parseAuth() {
			XMLUtils.parseChildrenToLocal( m_appName, m_doc_root, m_binder, "Auth" );
		}
	}

	public static class CMUtils {
		/* Flag to storeTempFile to disable automatic cleanup of the temp file. */
		public static final int F_NO_CLEANUP = 0x10000000;

		/**
		 *
			*/
		public static Vector getEnvironmentalsAsList( String appName, String rootName )
				throws ServiceException {
			Report.debug( "bezzotechcosign", "Entering getEnvironmentalsAsList, passed in attributes:" +
					"\n\tappName: " + appName + "\n\trootName: " + rootName, null );
			String envStr = SharedObjects.getEnvironmentValue( appName + "." + rootName + ".fields" );
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
		public static String getFileAsString( Service service, DataBinder inBinder )
				throws ServiceException {
			Report.debug( "bezzotechcosign", "Entering getFileAsString, passed in parameters:\n\tservice:" +
					"\n\tinBinder", null );
			byte [] file = getFileAsByteArray( service, inBinder );
			return MiscUtils.getBase64( file );
		}

		/**
		 *
			*/
		public static byte [] getFileAsByteArray( Service service, DataBinder inBinder )
				throws ServiceException {
			Report.debug( "bezzotechcosign", "Entering getFileAsByteArray, passed in parameters:\n\tservice:" +
					"\n\tinBinder", null );
			DataBinder binder = new DataBinder();
			binder.mergeResultSetRowIntoLocalData( inBinder.getResultSet( "DOC_INFO" ) );
			binder.putLocal( FileStoreProvider.SP_RENDITION_ID, FileStoreProvider.R_PRIMARY );
			byte[] b = null;
			try {
				String primaryFilePath = getFilePath( service, inBinder );
				RandomAccessFile f = new RandomAccessFile( primaryFilePath, "r" );
				b = new byte[ ( int )f.length() ];
 			f.read( b );
			} catch ( FileNotFoundException e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			} catch ( IOException e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			}
			return b;
		}

		/**
		 *
			*/
		private static ResultSet getDocInfo( Workspace workspace, String dID ) throws ServiceException {
			DataBinder binder = new DataBinder();
			binder.putLocal( "dID", dID );
			ResultSet rset = null;
			try {
				rset = workspace.createResultSet( "QdocInfo", binder );
			} catch ( Exception e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			}
			if ( rset.isEmpty() ) {	/*This should never happen*/	}
			return rset;
		}

// ******** This method also exists in the ConvertToPDFServiceHandler component. ********
// ******** This method is gratuitously taken from BezzotechLib's FileStoreUtils class. ********
		/** Return a path to a file based on fileData.  This file may need to be copied from a storage
		 *  provider onto the local filesystem.  In this case, the file will be one of the DataBinder's
			*  temporary files, meaning it will be automatically cleaned up when the service request
			*  terminates.  Under no circumstances should the caller of this API manipulate or delete the
			*  file.
			*  @return The path to the file.
			*  @param fileData DataBinder containing file metadata.
			*  @binder.in FileStoreProvider.SP_RENDITION_ID One of the FileStoreProvider R_ constants.
			*  @binder.in all other docmeta for the file.
			*  @throws DataException if the file cannot be found using the provided metadata.
			*  @throws ServiceException if an error occurs while reading/transferring the file.
			*/
		private static String getFilePath( Service service, DataBinder fileData ) throws ServiceException {
		 Report.debug( "bezzotechcosign", "Entering getFilePath, passed in parameters:\n\tservice" +
					"\n\tfileData", null );
			String path;
			ExecutionContext context = service;
			FileStoreProvider fileStore = service.m_fileStore;
			try {
				IdcFileDescriptor d = fileStore.createDescriptor( fileData, new HashMap(), context );
				FileStoreUtils utils = new intradoc.filestore.FileStoreUtils();
				if ( utils.isStoredOnFileSystem( d, fileStore ) ) {
					path = d.getProperty( FileStoreProvider.SP_PATH );
				} else {
					long counter = fileData.getNextFileCounter();
					path = fileData.getTemporaryDirectory();
					String extension = fileData.getLocal( "dExtension" );
					fileData.addTempFile( path );
					if ( extension == null ) extension = "";
					else extension = "." + extension;
					path = FileUtils.getAbsolutePath( path, "" + counter + extension );
					fileStore.copyToLocalFile( d, new File( path ), null );
				}
			} catch ( DataException e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			} catch ( IOException e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			}
			return path;
		}

		/**
		 *  Query for the particular Sign Request Protocol (SRP) to send, currently we are using a
			*  one-to-one architecture where each SRP only contains a single Signature Profile, so locating
			*  the correct profile is as simple as finding its SRP.
			*/
		private static String retrieveSigProfilesFilePath( Service service, Workspace workspace,
				DataBinder inBinder ) throws ServiceException {
			String sql = "SELECT dID FROM Revisions WHERE dDocType = 'CoSignRequestProtocol' AND " +
					"dDocTitle = '" + inBinder.getLocal( "CoSignProfile" ) + "'";
			ResultSet rset = null;
			try {
				rset = workspace.createResultSetSQL( sql );
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
			binder.mergeResultSetRowIntoLocalData(
					getDocInfo( workspace, ResultSetUtils.getValue( drset, "dID" ) ) );
			binder.putLocal( FileStoreProvider.SP_RENDITION_ID, FileStoreProvider.R_PRIMARY );
			String returnStr = "";
			returnStr = getFilePath( service, binder );
			return returnStr;
		}

// ******** This method also exists in the ConvertToPDFServiceHandler component. ********
// ******** This method is gratuitously taken from BezzotechLib's FileStoreUtils class. ********
		/** Return a temporary file path.
		 * @param extension Extension to put on the file name.
			* @param nameFlags If this contains F_NO_CLEANUP do not register the file for automatic cleanup.
			* @throws ServiceException if a DataBinder is not configured with this request.
			* @return a temporary file path.
			*/
		private static String getTemporaryFileName( DataBinder binder, String extension, int nameFlags )
				throws ServiceException {
			if ( binder == null )
				throw new ServiceException( null, "csUnableToCreateTempFile" );

			long counter = binder.getNextFileCounter();
			String nodeId = SharedObjects.getEnvironmentValue( "ClusterNodeName" );
			String fileName;
			if ( nodeId != null ) {
				fileName = "trans-" + nodeId + "-" + counter + extension;
			} else {
				fileName = "trans-" + counter + extension;
			}
			String tempDir = binder.getTemporaryDirectory();
			fileName = FileUtils.getAbsolutePath( tempDir, fileName );
			if ( ( nameFlags & F_NO_CLEANUP ) == 0 )
				binder.addTempFile( fileName );
			return fileName;
		}

		/** Execute a service as the current user.
		 * @param binder The service request binder
			* @throws ServiceException if the service fails.
			*/
		private static void executeServiceSimple( Service service, Workspace workspace, DataBinder binder )
				throws ServiceException {
			try {
				ServiceManager sm = new ServiceManager();
				String serviceName = binder.getLocal( "IdcService" );
				if ( serviceName == null )
					service.createServiceExceptionEx( null, "!csIDCServiceMissing", Errors.RESOURCE_MISCONFIGURED );
				
				ServiceData sd = sm.getService( serviceName );
				Service s = sm.getInitializedService( serviceName, binder, workspace );
				UserData user = ( UserData )service.getCachedObject( "UserData" );
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
		private static void rollback( Service service, Workspace workspace, DataBinder inBinder,
				String error ) throws ServiceException {
			Report.debug( "bezzotechcosign", "Entering rollback, passed in parameters:\n\tinBinder:" +
					"\n\terror: " + error, null );
			DataBinder undoBinder = new DataBinder();
			undoBinder.putLocal( "IdcService", "UNDO_CHECKOUT_BY_NAME" );
			undoBinder.putLocal( "dDocName", inBinder.getLocal( "docName" ) );
			executeServiceSimple( service, workspace, undoBinder );
			throw new ServiceException( error );
		}
	}

	public static class XMLUtils {
		/**
		 *
			*/
		public static Document getNewDocument() throws ServiceException {
			Document dom = null;
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			try {
				DocumentBuilder db = dbf.newDocumentBuilder();
				dom = db.newDocument();
			} catch ( Exception e ) {
				throw new ServiceException( e.getMessage() );
			}
			return dom;
		}

		/**
		 *
			*/
		public static Document getExistingDocument( String path ) throws ServiceException {
			Document dom = null;
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			try {
				DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
				dom = documentBuilder.parse( path );
			} catch ( Exception e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			}
			return dom;
		}

		/**
		 *  rootName - String - Name of Environmental base to retrieve from binder (this should also be
			*    the name of the expect XML Node we will be passing back
			*  Note: Environmental variable "fields" will be expected to return the field names available to
			*    retrieve
			*  Note: Environmental variables will be expected to be presented in the following format:
			*  {Application Name}.{XML Node name}.{Field Name}
			*  Where: "Application Name" will be stored within the application code
			*    "XML Node Name" will be determined by the expected XML output
			*    "Field Name" will be determined by the expected XML output
			*/
		public static Element appendChildrenFromEnvironmental( String appName, Document doc,
				String rootName ) throws ServiceException {
			Report.debug( "bezzotechcosign", "Entering appendChildrenFromEnvironmental," +
					" passed in attributes:\n\tappName: " + appName + "\n\tdoc:\n\trootName: " + rootName, null );
			Vector fields = CMUtils.getEnvironmentalsAsList( appName, rootName );
			Element root = doc.createElement( rootName );
			for ( Enumeration fieldsEnum = fields.elements(); fieldsEnum.hasMoreElements(); ) {
				String fieldName = ( String )fieldsEnum.nextElement();
				Element child = doc.createElement( fieldName );
				Text text = doc.createTextNode(
						SharedObjects.getEnvironmentValue( appName + "." + rootName + "." + fieldName ) );
				child.appendChild( text );
				root.appendChild( child );
			}
			return root;
		}

		/**
		 *  rootName - String - Name of Local base to retrieve from binder (this should also be
			*    the name of the expect XML Node we will be passing back
			*  Note: Local variable "fields" will be expected to return the field names available to
			*    retrieve
			*  Note: Local variables will be expected to be presented in the following format:
			*  {Application Name}.{XML Node name}.{Field Name}
			*  Where: "Application Name" will be stored within the application code
			*    "XML Node Name" will be determined by the expected XML output
			*    "Field Name" will be determined by the expected XML output
			*/
		public static Element appendChildrenFromLocal( String appName, Document doc, DataBinder binder,
				String rootName ) throws ServiceException {
			Report.debug( "bezzotechcosign", "Entering appendChildrenFromLocal, passed in attributes:" +
					"\n\tappName: " + appName + "\n\tdoc:\n\tbinder:\n\trootName: " + rootName, null );
			String locStr = binder.getLocal( appName + "." + rootName + ".fields" );
			if ( locStr == null )
				throw new ServiceException( appName + " has not been installed properly" );

			Vector fields = ( Vector )StringUtils.parseArray( locStr, ';', '\\' );
			if ( fields.isEmpty() )
				throw new ServiceException( appName + " has not been installed properly" );

			Element root = doc.createElement( rootName );
			for ( Enumeration fieldsEnum = fields.elements(); fieldsEnum.hasMoreElements(); ) {
				String fieldName = ( String )fieldsEnum.nextElement();
				String fieldValue = binder.getLocal( appName + "." + rootName + "." + fieldName );
				if ( fieldValue != null || fieldValue != "" ) {
					Vector fieldValues = StringUtils.parseArray( fieldValue, ',', '\\' );
					for ( Enumeration fieldValuesEnum = fieldValues.elements(); fieldValuesEnum.hasMoreElements(); ) {
						fieldValue = ( String )fieldValuesEnum.nextElement();
						Element child = doc.createElement( fieldName );
						Text text = doc.createTextNode( fieldValue );
						child.appendChild( text );
						root.appendChild( child );
					}
				} else {
					Element child = doc.createElement( fieldName );
					Text text = doc.createTextNode( fieldValue );
					child.appendChild( text );
					root.appendChild( child );
				}
			}
			return root;
		}

		/**
		 *
			*/
		public static void parseChildrenToLocal( String appName, Element root, DataBinder binder,
				String baseName ) {
			Report.debug( "bezzotechcosign", "Entering parseChildrenToLocal, passed in attributes:" +
					"\n\tappName: " + appName + "\n\troot: " + root.getTagName() + "\n\tbinder:\n\tbaseName: " +
					baseName, null );
			Element base = ( Element )root.getElementsByTagName( baseName ).item( 0 );
			NodeList children = base.getChildNodes();
			String fields = "";
			for ( int i = 0; i < children.getLength(); i++ ) {
				Node node = children.item( i );
				if ( node.getNodeType() == Node.ELEMENT_NODE ) {
					Element child = ( Element )node;
					String fieldName = child.getTagName();
					String localValue = binder.getLocal( appName + "." + baseName + "." + fieldName );
					String fieldValue = child.getTextContent();
					if ( localValue != null ) { fieldValue += "," + localValue; }
					binder.putLocal( appName + "." + baseName + "." + fieldName, fieldValue );
					fields = fields + (fields == "" ? "" : ";") + fieldName;
				}
			}
			binder.putLocal( appName + "." + baseName + ".fields", fields );
		}

		/**
		 *
			*/
		public static String getStringFromDoc( Document doc ) throws ServiceException {
			DOMImplementation domImpl = doc.getImplementation();
			DOMImplementationLS domImplLS = (DOMImplementationLS)domImpl.getFeature("LS", "3.0");
			LSSerializer serializer = domImplLS.createLSSerializer();
			serializer.getDomConfig().setParameter("xml-declaration", Boolean.valueOf(false));
			LSOutput lsOutput = domImplLS.createLSOutput();
			lsOutput.setEncoding("UTF-8");
			StringWriter output = new StringWriter();
			lsOutput.setCharacterStream(output);
			serializer.write(doc, lsOutput);
			return output.toString();
		}

		/**
		 *
			*/
		public static String getStringFromDocument( Document doc ) throws ServiceException {
			Node root = doc.getDocumentElement();
			String returnStr = null;
			try {
				returnStr = getStringFromNode( root );
			} catch ( IOException e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			}
			return returnStr;
		}

		/**
		 *
			*/
		public static String getStringFromNode( Node root ) throws IOException {
			StringBuilder result = new StringBuilder();
			if ( root.getNodeType() == 3 ) {
				result.append( root.getNodeValue() );
			} else {
				if ( root.getNodeType() != 9 ) {
					StringBuffer attrs = new StringBuffer();
					for ( int k = 0; k < root.getAttributes().getLength(); ++k ) {
						attrs.append( " " )
								.append( root.getAttributes().item( k ).getNodeName() )
								.append( "=\"" )
								.append( root.getAttributes().item( k ).getNodeValue() )
								.append( "\" " );
					}
					result.append( "<" ).append( root.getNodeName() ).append( " " ).append( attrs ).append( ">" );
				} else {
					result.append( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" );
				}

				NodeList nodes = root.getChildNodes();
				for ( int i = 0, j = nodes.getLength(); i < j; i++ ) {
					Node node = nodes.item( i );
					result.append( getStringFromNode( node ) );
				}

				if ( root.getNodeType() != 9 ) {
					result.append( "</" ).append( root.getNodeName() ).append( ">" );
				}
			}
			return result.toString();
		}
	}

	public static class MiscUtils {
		public static String getBase64( byte [] file ) throws ServiceException {
			Report.debug( "bezzotechcosign", "Entering getBase64, passed in parameters:\n\tfile", null );
			return CommonDataConversion.uuencode( file, 0, file.length );
		}

		public static byte [] getByteArrayFromBase64( String content ) {
			return CommonDataConversion.uudecode( content, null );
		}

		public static void writeFileToTemp( DataBinder binder, String file ) throws ServiceException {
			String filePath = CMUtils.getTemporaryFileName( binder, ".pdf", 0x0 );
			int readBytes = file.length();
			try {
				BufferedWriter _writer = new BufferedWriter( new FileWriter( filePath ) );
				_writer.write( file, 0, readBytes );
				_writer.close();
			} catch ( FileNotFoundException e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			} catch ( IOException e ) {
				e.printStackTrace();
				throw new ServiceException( e.getMessage() );
			}
		}
	}

private String downloadSignedFile( DataBinder inBinder ) throws ServiceException {
StringBuffer response = null;
InputStream _is = null;
String strBuffer = null;
try {
String WSC_URL_PULL = SharedObjects.getEnvironmentValue("CoSign_WSC_URL_PULL");
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
//     BASE64Decoder decoder = new BASE64Decoder();
byte[] buffer = MiscUtils.getByteArrayFromBase64( content ); //decoder.decodeBuffer( content );
int bytesRead = buffer.length;
String file = CMUtils.getTemporaryFileName( super.m_binder, ".pdf", 0x0 );
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