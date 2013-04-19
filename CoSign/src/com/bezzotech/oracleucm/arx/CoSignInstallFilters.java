package com.bezzotech.oracleucm.arx;

import intradoc.common.*;
import intradoc.data.*;
import intradoc.shared.*;
import intradoc.jdbc.JdbcManager;
import intradoc.jdbc.JdbcWorkspace;
import intradoc.loader.IdcClassLoader;
import intradoc.provider.Provider;
import intradoc.provider.Providers;
import intradoc.server.IdcExtendedLoader;
import intradoc.server.Service;
import intradoc.server.utils.CompInstallUtils;

import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

public class CoSignInstallFilters implements FilterImplementor {
	protected Workspace m_dbCSWorkspace;

	public CoSignInstallFilters() {
		m_dbCSWorkspace = null;
		m_strInstallVersion = "0";
		m_strAdminRole = "CoSignAdmin";
		m_strPrivRole = "CoSignSigner";
		m_strSecurityGroup = "CoSign";
	}

	public int doFilter( Workspace ws, DataBinder binder, ExecutionContext cxt )
			throws DataException, ServiceException {
		// CS version must be greater than 7.0 to run this install filter.
		if( getSignificantVersion() <= 6 ) {
			return CONTINUE;
		}
		String param = null;
		Object paramObj = cxt.getCachedObject( "filterParameter" );
		if( paramObj == null || ( paramObj instanceof String ) == false ) {
			return CONTINUE;
		}
		param = ( String )paramObj;

		Service s = null;
		IdcExtendedLoader loader = null;

		boolean isProvisional = SharedObjects.getEnvValueAsBoolean( "IsProvisionalServer", false );

		if( cxt instanceof IdcExtendedLoader ) {
			loader = ( IdcExtendedLoader ) cxt;
			if( ws == null ) {
				ws = loader.getLoaderWorkspace();
			}
		}
		else if( cxt instanceof Service ) {
			s = ( Service )cxt;
			loader = ( IdcExtendedLoader )ComponentClassFactory.createClassInstance( "IdcExtendedLoader",
					"intradoc.server.IdcExtendedLoader", "!csCustomInitializerConstructionError" );
		}

		/* Called after environment data has been loaded and directory locations
		 * have been determined but before database has been accessed.
			*/
		if( param.equals( "extraAfterConfigInit" ) ) {
		}
		/* Called after initial connection to database and queries have been
		 * loaded but before database is used to load data into application.
		 * This is a good service for performing database table manipulation.
			*/
		else if( param.equals( "extraBeforeCacheLoadInit" ) ) {
		}
		/* Called after the last standard activity of a
		 * server side application initialization.  This is a good place
		 * to manipulate cached data or override standard configuration
		 * data.
			*/
		else if( param.equals( "extraAfterServicesLoadInit" ) && !isProvisional ) {
			trace( false, "doFilter", "Called with 'extraAfterServicesLoadInit'" );
			if( ws != null ) {
				doCheckDatabase( loader, ws, binder, cxt );
				doInstall( loader, ws, binder, cxt );
			}
		}
		// Called after loading cached tables.
		else if( param.equals( "initSubjects" ) ) {
		}
		/* Called for custom uninstallation steps.
		 * NOTE: Change the uninstall filter name to have your component name prefix.
		 * For example:  MyTestCompnentUninstallFilter
			*/
		else if( param.equals( "<MyComponentName>ComponentUninstallFilter" ) ) {
		}
		return CONTINUE;
	}

	protected int getSignificantVersion() {
		String strVersion=SystemUtils.getProductVersionInfo();
		int nIndex=strVersion.indexOf( "." );
		if( nIndex != -1 ) {
			strVersion = strVersion.substring( 0,nIndex );
		}
		return( Integer.valueOf( strVersion ).intValue() );
	}

	protected void doCheckDatabase( IdcExtendedLoader iel, Workspace ws, DataBinder db,
			ExecutionContext ec ) throws DataException, ServiceException {
		trace( false, "doCheckDatabase", "Function entry ,,," );
		initDBConnection();
		try {
			trace( false, "doInstall", "Calling checkAndCreateSequenceCounters( )" );
			checkAndCreateCounter( "ESIG_SEQ" );
			trace( false, "doCheckDatabase", "Component Configuration Complete." );
		}
		catch( Exception e ) {
			trace( false, "doCheckDatabase",
					( new StringBuilder() ).append( "Fatal Error: Unexpected Exception " ).append( e ).toString() );
			String errMsg = LocaleUtils.encodeMessage( "csSctErrUnexExceptionInitingDatabaseProvider", null, e );
			showError( LocaleResources.localizeMessage( errMsg, null ) );
		}
		trace( false, "doCheckDatabase", "Normal exit ..." );
	}

	private void initDBConnection() throws ServiceException {
		trace( false, "initDBConnection", "Checking SystemDatabase Provider" );
		Provider p = Providers.getProvider( "SystemDatabase" );
		if( p != null ) {
			m_dbCSWorkspace = ( Workspace )p.getProvider();
			if( m_dbCSWorkspace == null ) {
				String errMsg = LocaleUtils.encodeMessage( "csSctErrCannotConnectToSystemDatabaseWorkspace", null );
				showError( LocaleResources.localizeMessage( errMsg, null ) );
			}
		}
		else {
			String errMsg = LocaleUtils.encodeMessage( "csSctErrNoSystemDatabaseProvider", null );
			showError( LocaleResources.localizeMessage( errMsg, null ) );
		}
	}

	private void checkAndCreateCounter( String counterName ) throws DataException {
		int blockSize = 1;
		trace( false, "checkAndCreateCounter",
				( new StringBuilder() ).append( "Using blockSize " ).append( blockSize ).toString() );
		trace( false, "checkAndCreateCounter",
				( new StringBuilder() ).append( "Checking for existing counter " ).append( counterName ).toString() );
		IdcCounter ctr = IdcCounterUtils.getCounter( m_dbCSWorkspace, counterName );
		if( ctr == null ) {
			trace( false, "checkAndCreateCounter",
					( new StringBuilder() ).append( "Creating counter " ).append( counterName ).toString() );
			if( !IdcCounterUtils.registerCounter( m_dbCSWorkspace, counterName, 1L, blockSize ) )
				trace( false, "checkAndCreateCounter",
						( new StringBuilder() ).append( "INTERNAL ERROR: Counter " ).append( counterName ).append( " *NOT* created" ).toString() );
			else
				trace( false, "checkAndCreateCounter",
						( new StringBuilder() ).append( "Counter " ).append( counterName ).append( " created" ).toString() );
		}
		else {
			trace( false, "checkAndCreateCounter",
					( new StringBuilder() ).append( "Found existing counter " ).append( counterName ).toString() );
			if( ctr.m_increment != blockSize ) {
				trace( false, "checkAndCreateCounter", ( new StringBuilder() ).append( "Changing increment from " )
						.append( ctr.m_increment ).append( " to " ).append( blockSize ).toString() );
				long nxtVal = ctr.nextValue( m_dbCSWorkspace );
				trace( false, "checkAndCreateCounter", ( new StringBuilder() ).append( "Got counter nextValue " )
						.append( nxtVal ).toString() );
				if( !IdcCounterUtils.registerCounter( m_dbCSWorkspace, counterName, nxtVal, blockSize ) )
					trace( false, "checkAndCreateCounter", ( new StringBuilder() ).append( "INTERNAL ERROR: Counter " )
							.append( counterName ).append( " *NOT* recreated" ).toString() );
			}
		}
	}

	protected void showError( String msg ) throws ServiceException {
		String errMsg = LocaleUtils.encodeMessage( "csESigErrConfiguringCoSign", null, msg );
		Log.error( LocaleResources.localizeMessage( errMsg, null ) );
		throw new ServiceException( LocaleResources.localizeMessage( errMsg, null ) );
	}

	protected void trace( String caller, String msg ) {
		trace( false, caller, msg );
	}

	protected void trace( boolean force, String caller, String msg ) {
		String origin = ( new StringBuilder() ).append( getClass().getName() ).append( "." ).append( caller )
				.append( "( )" ).toString();
		String fullMsg = ( new StringBuilder() ).append( origin ).append( " - " ).append( msg ).toString();
		Report.trace( "bezzotechinstall", fullMsg, null );
		if( force )
			System.out.println( fullMsg );
	}

	protected int doInstall( IdcExtendedLoader loader, Workspace ws, DataBinder binder,
			ExecutionContext cxt ) {
		trace( false, "doInstall", "Function entry ,,," );
		String compName = "CoSign";
		if( binder == null )
			binder = new DataBinder();
		try {
			String install = loader.getDBConfigValue( "ComponentInstall", compName, "" );
			if( install == null )
				install = loader.getDBConfigValue( "ComponentInstall", compName, "0" );
			String update = loader.getDBConfigValue( "ComponentUpdate", compName, m_strInstallVersion );
			boolean isSchemaEnabled = SharedObjects.getEnvValueAsBoolean( "EnableSchemaPublish", false );
			if( update == null || install == null || update.equals( "0" ) ) {
				if( isSchemaEnabled )
					SharedObjects.putEnvironmentValue( "EnableSchemaPublish", "false" );
				if( update == null || install == null || update.equals( "0" ) ) {
					String installID = CompInstallUtils.getInstallID( compName );
					if( !hasRole( m_strAdminRole, ws ) )
						addRole( m_strAdminRole, ws );
					checkAndAddRoleToSysadmin( m_strAdminRole, ws );
					if( !hasRole( m_strPrivRole, ws ) )
						addRole( m_strPrivRole, ws );
					checkAndAddRoleToSysadmin( m_strPrivRole, ws );
					if( !hasSecurityGroup( m_strSecurityGroup, ws ) ) {
						addSecurityGroup( m_strSecurityGroup, m_strAdminRole, ws );
						if( hasRole( m_strAdminRole, ws ) ) {
							DataBinder db = new DataBinder();
							db.putLocal( "dRoleName", m_strAdminRole );
							ResultSet rs = ws.createResultSet( "QroleDisplayName", db );
							String displayName = rs.getStringValueByName( "dRoleDisplayName" );
							updateRole( m_strAdminRole, m_strSecurityGroup, 15L, displayName, ws );
						}
					}
					String olKey = getDocMetaDefOptionListKey("xSignatureStatus");
					if(olKey != null && olKey.length() > 0)
						addToOptionList(ws, olKey, SIGNATURE_STATUS);
					olKey = getDocMetaDefOptionListKey("xCoSignRequiredSignatures");
					if(olKey != null && olKey.length() > 0)
						addToOptionList(ws, olKey, REQUIRED_SIGNATURES);
					olKey = getDocMetaDefOptionListKey("xCoSignSignatureReasons");
					if(olKey != null && olKey.length() > 0)
						addToOptionList(ws, olKey, SIGNATURE_REASONS);
					olKey = getDocMetaDefOptionListKey("xCoSignSignatureTag");
					if(olKey != null && olKey.length() > 0)
						addToOptionList(ws, olKey, SIGNATUR_PROFILE);
				}
				update = "1";
				loader.setDBConfigValue( "ComponentUpdate", compName, m_strInstallVersion, update );
				if( install == null )
					loader.setDBConfigValue( "ComponentInstall", compName, "0", "0" );
			}
		}
		catch( Exception e ) {
			e.printStackTrace();
			SystemUtils.err( e, LocaleUtils.encodeMessage( "csUnableToExecuteInstallFilter", compName ) );
		}
		trace( false, "doInstall", "Normal exit ..." );
		return 0;
	}

	protected boolean hasRole( String strRole, Workspace ws ) throws DataException {
		DataBinder db = new DataBinder();
		db.putLocal( "dRoleName", strRole );
		ResultSet rs = ws.createResultSet( "Qrole", db );
		return !rs.isEmpty();
	}

	protected void updateRole( String strRole, String strSecurityGroup, long nPrivilege,
			String strDisplayName, Workspace ws ) throws ServiceException {
		DataBinder db = new DataBinder();
		db.putLocal( "dRoleName", strRole );
		db.putLocal( "dGroupName", strSecurityGroup );
		db.putLocal( "dPrivilege", String.valueOf( nPrivilege ) );
		db.putLocal( "dRoleDisplayName", strDisplayName );
		CompInstallUtils.executeService( ws, "EDIT_ROLE", db );
	}

	protected void addRole( String strRole, Workspace ws ) throws ServiceException, DataException {
		DataBinder db = new DataBinder();
		db.putLocal( "dRoleName", strRole );
		db.putLocal( "dPrivilege", "0" );
		CompInstallUtils.executeService( ws, "ADD_ROLE", db );
		ws.executeSQL( ( new StringBuilder() ).append( "insert into UserSecurityAttributes (dUserName, dAttributeName, dAttributeType,dAttributePrivilege) values ('sysadmin', '" ).append( strRole ).append( "', 'role', 15)" ).toString() );
	}

	protected void checkAndAddRoleToSysadmin( String strRole, Workspace ws )
			throws ServiceException, DataException {
		String query = ( new StringBuilder() ).append( "SELECT * FROM UserSecurityAttributes WHERE dUserName = 'sysadmin' AND dAttributeType = 'role' AND dAttributeName = '" ).append( strRole ).append( "'" ).toString();
		ResultSet rset = ws.createResultSetSQL( query );
		if( rset == null || rset.isEmpty() )
			ws.executeSQL( ( new StringBuilder() ).append( "insert into UserSecurityAttributes (dUserName, dAttributeName, dAttributeType,dAttributePrivilege) values ('sysadmin', '" ).append( strRole ).append( "', 'role', 15)" ).toString() );
	}

	protected boolean hasSecurityGroup( String strSecurityGroup, Workspace ws ) throws DataException {
		DataBinder db = new DataBinder();
		db.putLocal( "dGroupName", strSecurityGroup );
		ResultSet rs = ws.createResultSet( "Qgroup", db );
		return !rs.isEmpty();
	}

	protected void addSecurityGroup( String strSecurityGroup, String strRoleName, Workspace ws ) throws ServiceException {
		DataBinder db = new DataBinder();
		db.putLocal( "dGroupName", strSecurityGroup );
		db.putLocal( "dDescription", strSecurityGroup );
		db.putLocal( "dRoleName", strRoleName );
		db.putLocal( "dPrivilege", "0" );
		CompInstallUtils.executeService( ws, "ADD_GROUP", db );
	}

	public static void addToOptionList( Workspace workspace, String s, String as[] )
			throws ServiceException, DataException {
		boolean flag = false;
		Vector vector = SharedObjects.getOptList( s );
		if( vector == null )
			vector = new Vector();
		for( int i = 0; as != null && i < as.length; i++ ) {
			String s2 = as[ i ];
			if( s2 == null || vector.contains( s2 ) )
				continue;
			vector = ( Vector )vector.clone();
			int j = -1;
			if( i > 0 ) {
				String s3 = as[ i - 1 ];
				j = vector.indexOf( s3 );
				if( j != -1 )
					j++;
			}
			else if( as.length >= 2 ) {
				String s4 = as[ 1 ];
				j = vector.indexOf( s4 );
			}
			if( j == -1 )
				j = vector.size();
			vector.insertElementAt( s2, j );
			flag = true;
		}

		if( flag ) {
			String s1 = "";
			boolean flag1 = true;
			for( Enumeration enumeration = vector.elements(); enumeration.hasMoreElements(); ) {
				if( !flag1 )
					s1 = s1 + "\n";
				String s5 = ( String )enumeration.nextElement();
				s1 = s1 + s5;
				flag1 = false;
			}
			CompInstallUtils.setOptionList( workspace, s, s1 );
		}
	}

	public static String getDocMetaDefOptionListKey( String s ) {
		DataResultSet dataresultset = SharedObjects.getTable( "DocMetaDefinition" );
		if( dataresultset.isEmpty() )
			return null;
		if( dataresultset.findRow( dataresultset.getFieldInfoIndex( "dName" ), s ) != null ) {
			String s1 = ResultSetUtils.getValue( dataresultset, "dOptionListKey" );
			return s1;
		}
		else {
			return null;
		}
	}

	protected static final String SIGNATUR_PROFILE[] = { "Externally Signed" };
	protected static final String SIGNATURE_REASONS[] = {
			"I am the author", "I approve this", "I am required to sign"
	};
	protected static final String REQUIRED_SIGNATURES[] = { "" };
	protected static final String SIGNATURE_STATUS[] = {
			"", "Valid", "Invalid", "Error", "Incomplete", "Unknown", "Empty", "sent-to-cosign"
	};
	String m_strInstallVersion;
	String m_strAdminRole;
	String m_strPrivRole;
	String m_strSecurityGroup;
}