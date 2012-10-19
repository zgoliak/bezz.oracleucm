package com.bezzotech.oracleucm.arx.common;

import intradoc.common.ExecutionContext;
import intradoc.common.ServiceException;
import intradoc.common.StringUtils;
import intradoc.data.DataBinder;
import intradoc.server.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Map;
import java.util.Iterator;
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

import com.bezzotech.oracleucm.arx.shared.SharedObjects;

public class XMLUtils {
	/** The context for this request. */
	public ExecutionContext m_context;

	/** DataBinder The DataBinder for this request. */
	public DataBinder m_binder;

	/** SharedObjects pointer to use for this request. */
	public SharedObjects m_shared;

	protected XMLUtils( ExecutionContext context ) {
		m_context = context;
		m_shared = SharedObjects.getSharedObjects( m_context );
		if ( m_context instanceof Service ) {
			Service s = ( Service )m_context;
			m_binder = s.getBinder();
		}
	}

	/** Return a working FileStoreUtils object for a service.
		* @param context ExecutionContext to find a FileStoreProvider in.
		* @throws ServiceException if a FileStoreProvider cannot be found.
		* @return a ready-to-use FileStoreUtils object.
		*/
	static public XMLUtils getXMLUtils( ExecutionContext context ) {
		return new XMLUtils( context );
	}
	
	/**
		*
		*/
	public Document getNewDocument() throws ServiceException {
		Document dom = null;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			dom = db.newDocument();
		} catch ( ParserConfigurationException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		}
		return dom;
	}

	/**
		*
		*/
	public Document getExistingDocument( String path ) throws ServiceException {
		Document dom = null;
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			dom = documentBuilder.parse( path );
		} catch ( ParserConfigurationException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		} catch ( IOException e ) {
			e.printStackTrace();
			throw new ServiceException( e.getMessage() );
		} catch ( SAXException e ) {
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
	public Element appendChildrenFromEnvironmental( String appName, Document doc, String rootName )
			throws ServiceException {
		Map fields = m_shared.getConfigSet( appName + "." + rootName );
		Element root = doc.createElement( rootName );
		for ( Iterator < String > fieldIter = fields.keySet().iterator(); fieldIter.hasNext(); ) {
			String fieldName = ( String )fieldIter.next();
			Element child = doc.createElement( fieldName );
			Text text = doc.createTextNode(
					m_shared.getConfig( appName + "." + rootName + "." + fieldName ) );
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
	public Element appendChildrenFromLocal( String appName, Document doc, String rootName )
			throws ServiceException {
		String locStr = m_binder.getLocal( appName + "." + rootName + ".fields" );
		if ( locStr == null )
			throw new ServiceException( appName + " has not been installed properly" );

		Vector fields = ( Vector )StringUtils.parseArray( locStr, ';', '\\' );
		if ( fields.isEmpty() )
			throw new ServiceException( appName + " has not been installed properly" );

		Element root = doc.createElement( rootName );
		for ( Enumeration fieldsEnum = fields.elements(); fieldsEnum.hasMoreElements(); ) {
			String fieldName = ( String )fieldsEnum.nextElement();
			String fieldValue = m_binder.getLocal( appName + "." + rootName + "." + fieldName );
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
	public void parseChildrenToLocal( String appName, Element root, String baseName ) {
		Element base = ( Element )root.getElementsByTagName( baseName ).item( 0 );
		NodeList children = base.getChildNodes();
		String fields = "";
		for ( int i = 0; i < children.getLength(); i++ ) {
			Node node = children.item( i );
			if ( node.getNodeType() == Node.ELEMENT_NODE ) {
				Element child = ( Element )node;
				String fieldName = child.getTagName();
				String localValue = m_binder.getLocal( appName + "." + baseName + "." + fieldName );
				String fieldValue = child.getTextContent();
				if ( localValue != null ) { fieldValue += "," + localValue; }
				m_binder.putLocal( appName + "." + baseName + "." + fieldName, fieldValue );
				fields = fields + (fields == "" ? "" : ";") + fieldName;
			}
		}
		m_binder.putLocal( appName + "." + baseName + ".fields", fields );
	}

	/**
		*
		*/
	public String getStringFromDocument( Document doc ) {
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
	public getStringFromDocument( Document doc ) throws ServiceException {
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
	public String getStringFromNode( Node root ) throws IOException {
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
*/
}