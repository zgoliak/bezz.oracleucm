package com.bezzotech.oracleucm.arx;

import com.bezzotech.oracleucm.arx.common.CMUtils;
import com.bezzotech.oracleucm.arx.shared.SharedObjects;

import intradoc.common.ExecutionContext;
import intradoc.common.Report;
import intradoc.common.ServiceException;
import intradoc.common.StringUtils;
import intradoc.data.DataBinder;
import intradoc.data.DataException;
import intradoc.data.DataResultSet;
import intradoc.data.ResultSet;
import intradoc.data.ResultSetUtils;
import intradoc.data.Workspace;
import intradoc.server.IdcExtendedLoader;
import intradoc.server.Service;
import intradoc.server.ServiceData;
import intradoc.server.ServiceManager;
import intradoc.shared.FilterImplementor;
import intradoc.shared.SecurityUtils;
import intradoc.shared.UserData;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

public class CoSignFilters implements FilterImplementor {
	protected static HashSet < String > m_oldContentIds = new HashSet < String > ();
	protected static String m_currentVersion = "3.5";
	protected static String m_currentUpdateValue = "1";
	protected Workspace m_workspace;
	protected SharedObjects m_shared;

	public CoSignFilters() {
		m_workspace = null;
		m_shared = null;
	}

	public int doFilter( Workspace ws, DataBinder db, ExecutionContext ec )
			throws DataException, ServiceException {
//		Report.trace( "bezzotechcosign", "Entering CoSignFilters.doFilter", null );
		m_workspace = ws;
		if( ec == null ) {
			System.out.println( "Plugin filter called without a context." );
			return FilterImplementor.CONTINUE;
		}
		Object obj = ec.getCachedObject( "filterParameter" );
		m_shared = SharedObjects.getSharedObjects( ec );
		if( obj == null || !( obj instanceof String ) ) {
			System.out.println( "Plugin filter called without filter parameter." );
			return FilterImplementor.CONTINUE;
		}
		String s = ( String )obj;
		// Clear CoSign fields if not a CoSign document and CoSign service
		if( s.equals( "validateCoSign" ) ) {
			if( db.getLocal( "IdcService" ) == null || 
					( db.getLocal( "IdcService" ).contains( "CHECKIN" ) && 
							!db.getLocal( "IdcService" ).equals( "COSIGN_CHECKIN_SIGNEDDOCUMENT" ) ) ||
					( db.getLocal( "xCoSignSignatureTag" ) == null ||
							db.getLocal( "xCoSignSignatureTag" ) == "" ||
							db.getLocal( "xCoSignSignatureTag" ) == "Externally Signed" ) ) {
				Report.trace( "bezzotechcosign", "Clearing CoSign values!", null );
				db.putLocal( "xSignTime", "" );
				db.putLocal( "xSigner", "" );
				db.putLocal( "xSignatureCount", "" );
				db.putLocal( "xSignatureStatus", "" );
			}
			return FINISHED;
		}
		// Grant enhanced permissions when checking in signed document
		else if( s.equals( "alterCoSignRole" ) ) {
			if(	db.getLocal( "IdcService" ) != null &&
					db.getLocal( "IdcService" ).equals( "COSIGN_CHECKIN_SIGNEDDOCUMENT" ) ) {
				UserData userData = ( UserData )ec.getCachedObject( "TargetUserData" );
				userData.addAttribute( "role", "admin", "3" );
				Report.trace(null, "Granting the 'admin' role.", null);
			}
			return FINISHED;
		}
		// CoSign clean-up if document signing not complete
		else if( s.equals( "CoSignFrequentEvent" ) ) {
			Report.trace( "bezzotechcosign", "Running Frequent Event now!", null );

		 // Locate and checkout content that is involved in CoSign for too long
			ResultSet rset = CMUtils.createResultSet( "QcheckedoutCoSignContent", db );
			if( rset != null && !rset.isEmpty() ) {
				Date dateNow = new Date ();
				SimpleDateFormat dateformatMMDDYYYY = new SimpleDateFormat("MMddyyyy");
				String now = dateformatMMDDYYYY.format( dateNow );
				DataResultSet drset = m_shared.getResultSet( "CoSignCheckedOutItems", false );
				if( drset == null ) {
					drset = new DataResultSet( new String[] { "dDocName", "tsDateTime" } );
				}
				if( drset.isEmpty() ) {
					drset.copy( rset );
				}
				else {
					DataResultSet tmpRS = new DataResultSet();
					tmpRS.copy( rset );
					do {
						String id = tmpRS.getStringValueByName( "dDocName" );
						if( drset.findRow( drset.getFieldInfoIndex( "dDocName" ), id ) != null ) {

							// item has been in-process for at least 5 minutes: force a timeout
							DataBinder undo = new DataBinder( db.getEnvironment() );
							undo.putLocal( "dDocName", id );
							CMUtils.serviceDoRequest( "UNDO_CHECKOUT_BY_NAME", undo, ws );

							DataBinder update = new DataBinder( db.getEnvironment() );
							update.putLocal( "dDocName", id );
							ResultSet diRSet = CMUtils.createResultSet( "QlatestDocInfoByName", update );
							update.mergeResultSetRowIntoLocalData( diRSet );
							update.putLocal( "xSignatureStatus", "" );
							CMUtils.serviceDoRequest( "UPDATE_DOCINFO", update, ws );
							tmpRS.deleteCurrentRow();
						}
					} while( tmpRS.next() );
					drset = tmpRS;
				}
				removeColumn( drset, "dDocName" );
				String values[] = new String [] { now };
				String colmns[] = new String [] { "tsDateTime" };
				ResultSetUtils.addColumnsWithDefaultValues( drset, null, values, colmns );
				m_shared.putResultSet( "CoSignCheckedOutItems", drset );
			}
			return FINISHED;
		}
/*		if( s.equals( "createCoSignTables" ) ) {
			createLogTable( ws, loader );
			createHistoryTable( ws, loader );
			loader.setDBConfigValue( "ComponentInstall", "CoSign", m_currentVersion, "1" );
		}
*/
		return CONTINUE;
	}

/*	protected void createMetaFields( Workspace ws ) {
		String iID = CompInstallUtils.getInstallID( "CoSign" );
		Properties cData = CompInstallUtils.loadComponentInstallOrConfigData( "CoSign", iID, false );
		Report.trace( "cosigninstall",
				( new StringBuilder() )
						.append( "createMetaFields():  " )
						.append( "CoSign" )
						.append( "-" )
						.append( "configData" )
						.append( "= " )
						.append( cData )
						.append( cData == null || !cData.isEmpty() ? "" : ( new StringBuilder() )
								.append( "   " ).append( "configData" )
								.append( ".isEmpty()=true" )
								.toString())
						.toString(),
				null);
		configureMetaField( ws, cData, "coSignSignatureProfileMetaField",
				"csCoSignSignatureProfileMetaField", "String", false, "", "", "1", "90000" );
		configureMetaField( ws, cData, "CoSignSignatureStatus", "csxCoSignSignatureStatus", "String",
				true, "?", "?", "1", "90010" );
		configureMetaField( ws, cData, "CoSignSigner", "csxCoSignSigner", "String", false, "", "", "1",
				"90020" );
		configureMetaField( ws, cData, "CoSignSignTime", "csxCoSignSignTime", "Date", false, "", "", "1",
				"90030" );
		configureMetaField( ws, cData, "CoSignSignatureCount", "csxCoSignSignatureCount", "Int", false,
				"", "", "1", "90040" );
		configureMetaField( ws, cData, "CoSignRequiredSignatures", "csxCoSignRequiredSignatures",
				"BigText", true, "?", "?", "0", "90050" );
		configureMetaField( ws, cData, "CoSignSignatureReasons", "csxCoSignSignatureReasons", "Memo",
				true, "?", "?", "0", "90060" );
	}

	protected void createLogTable( Workspace ws, IdcExtendedLoader iel ) throws ServiceException, DataException {
		String as[] = ws.getTableList();
		String s = "CoSignSignatureDetails";
		int i = StringUtils.findStringIndexEx( as, s, true );
		if( i < 0 ) {
			String columns[][] = {
					{ "sID", "int", "" }, 
					{ "dID", "varchar", "80" }, 
					{ "dDocName", "varchar", "80" }, 
					{ "fieldName", "varchar", "80" }, 
					{ "status", "varchar", "80" }, 
					{ "signingTime", "date", "" }, 
					{ "signerEmail", "varchar", "80" }, 
					{ "signerName", "varchar", "80" }, 
					{ "signReason", "varchar", "200" }, 
					{ "certErrorStatus", "varchar", "80" }, 
					{ "x", "int", "" }, 
					{ "y", "int", "" }, 
					{ "width", "int", "" }, 
					{ "height", "int", "" }, 
					{ "pageNumber", "int", "" }, 
					{ "dateFormat", "varchar", "40" }, 
					{ "timeformat", "varchar", "40" }, 
					{ "graphicalImage", "varchar", "80" }, 
					{ "signer", "varchar", "80" }, 
					{ "signdate", "date", "" }, 
					{ "initials", "varchar", "10" }, 
					{ "logo", "varchar", "80" }, 
					{ "showTitle", "varchar", "10" }, 
					{ "showReason", "varchar", "10" }, 
					{ "title", "varchar", "80" }, 
					{ "reason", "varchar", "200" }
			};
			iel.createTable( s, columns, new String[] { "sID", "dID" } );
			Report.trace( "cosigninstall", "createLogTable() create table: " + s, null );
//			IdcCounterUtils.registerCounter( ws, "CoSignSignatureDetailsID", -1L, 1 );
			CompInstallUtils.addIndex( ws, s, "sID" );
			m_indexGenerator.generateIndexes( s );
//			updateConfigTable( s );
		}
	}
	
	protected void createHistoryTable( Workspace ws, IdcExtendedLoader iel ) throws ServiceException, DataException {
		String as[] = ws.getTableList();
		String s = "CoSignHistory";
		int i = StringUtils.findStringIndexEx( as, s, true );
		if( i < 0 ) {
			String columns[][] = {
					{ "ID", "int", "" },
					{ "User", "varchar", "50" },
					{ "Date", "date", "" },
					{ "Operation", "varchar", "50" },
					{ "Error", "varchar", "250" },
					{ "dDocName", "varchar", "80" },
					{ "dID", "varchar", "80" }
			};
			iel.createTable( s, columns, new String[] { "ID" } );
			Report.trace( "cosigninstall", "createHistoryTable() create table: " + s, null );
//			IdcCounterUtils.registerCounter( ws, "CoSignHistoryID", -1L, 1 );
			CompInstallUtils.addIndex( ws, s, "ID" );
			m_indexGenerator.generateIndexes( s );
		}
	}

	private void configureMetaField( Workspace ws, Properties configData, String configuredMetaName,
			String caption, String type, boolean isOptionList, String optionListKey, String optListType,
			String indexed, String order ) throws ServiceException, DataException {
		String metaName = m_shared.getConfig( configuredMetaName );
		if( metaName == null || metaName.length() < 1 ) {
			metaName = configData.getProperty( configuredMetaName );
			if( metaName == null || metaName.length() < 1 ) {
				Report.trace( "cosigninstall",
						( new StringBuilder() )
								.append( "configureMetaField: " )
								.append( configuredMetaName )
								.append( ", can not find configured value. Metafield not created." )
								.toString(),
						null);
				return;
			}
		}
		Properties propsMetafieldInfo = new Properties();
		propsMetafieldInfo.put( "dCaption", caption );
//		propsMetafieldInfo.put( "dType", "Text" );
		propsMetafieldInfo.put( "dIsRequired", "0" );
		propsMetafieldInfo.put( "dIsEnabled", "1" );
		propsMetafieldInfo.put( "dIsSearchable", indexed );
//		propsMetafieldInfo.put( "dIsOptionList", "1" );
//		propsMetafieldInfo.put( "dDefaultValue", "FALSE" );
//		propsMetafieldInfo.put( "dOptionListKey", "view://Folders.TrueFalseView" );
//		propsMetafieldInfo.put( "dOptionListType", optListType );
		propsMetafieldInfo.put( "dOrder", order );
		propsMetafieldInfo.put( "dComponentName", "CoSign" );
		MetaFieldUtils.updateMetaDataFromProps( ws, null, propsMetafieldInfo, metaName,
				!MetaFieldUtils.hasDocMetaDef( metaName ) );
	}
*/
	protected void removeColumns( DataResultSet drset, String columns[] ) {
		for( int i = 0; i < columns.length; i++ ) {
			removeColumn( drset, columns[ i ] );
		}
	}

	protected void removeColumn( DataResultSet drset, String column ) {
		Vector <String> colsToRemove =
				new Vector( Arrays.asList( ResultSetUtils.getFieldListAsStringArray( drset ) ) );
		for( int i = 0; i < colsToRemove.size(); i++ ) {
			if( colsToRemove.get( i ).equalsIgnoreCase( column ) ) {
				colsToRemove.remove( i );
				break;
			}
		}
		drset.removeFields( Arrays.copyOf( colsToRemove.toArray(), colsToRemove.size(), String[].class ) );
	}
}