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

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
				Report.trace( "bezzotechcosign", "Granting the 'admin' role.", null );
			}
			return FINISHED;
		}
		// CoSign clean-up if document signing not complete
		else if( s.equals( "CoSignFrequentEvent" ) ) {
			Report.trace( "bezzotechcosign", "Running Clean-up Event now!", null );

		 // Locate and checkout content that is involved in CoSign for too long
			ResultSet rset = CMUtils.createResultSet( "QcheckedoutCoSignContent", db );
			if( rset != null && !rset.isEmpty() ) {
				Date dateNow = new Date ();
				SimpleDateFormat dateformatMMDDYYYY = new SimpleDateFormat("MM/dd/yyyy HH:mm");
				String now = dateformatMMDDYYYY.format( dateNow );
				Report.debug( "bezzotechcosign", "Timestamp generated: " + now, null );
				DataResultSet drset = m_shared.getResultSet( "CoSignCheckedOutItems", false );
				if( drset == null ) {
					drset = new DataResultSet( new String[] { "dDocName", "tsDateTime" } );
				}
				if( drset.isEmpty() ) {
					drset.copy( rset );
				}
				else {
					Report.debug( "bezzotechcosign", "Cached table: " + drset.toString(), null );
					DataResultSet tmpRS = new DataResultSet();
					tmpRS.copy( rset );
					do {
						String id = tmpRS.getStringValueByName( "dDocName" );
						List theRow = drset.findRow( drset.getFieldInfoIndex( "dDocName" ), id );
						String dateStr = ( String )theRow.get( drset.getFieldInfoIndex( "tsDateTime" ) );
						Report.debug( "bezzotechcosign", "Timestamp found: " + dateStr, null );
						Date date = dateformatMMDDYYYY.parse( dateStr, new ParsePosition( 0 ) );
						Report.debug( "bezzotechcosign", "Timestamp parsed: " + date.toString(), null );

						// Get msec from each, and subtract.
						long diff = dateNow.getTime() - date.getTime();
						long diffMinutes = diff / ( 60 * 1000 ) % 60;
						Report.debug( "bezzotechcosign", "Date difference found: " + diff + " > " + diffMinutes, null );

						if( ( theRow != null ) && diffMinutes >= 5 ) {
							Report.trace( "bezzotechcosign", "Removing " + id + " from Global Table", null );

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
				Report.debug( "bezzotechcosign", "Cached table: " + drset.toString(), null );
				m_shared.putResultSet( "CoSignCheckedOutItems", drset );
			}
			return FINISHED;
		}
		return CONTINUE;
	}

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