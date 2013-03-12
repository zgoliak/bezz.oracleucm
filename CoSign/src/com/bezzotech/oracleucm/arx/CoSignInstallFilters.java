package com.bezzotech.oracleucm.arx;

import intradoc.common.*;
import intradoc.data.*;
import intradoc.provider.Provider;
import intradoc.provider.Providers;
import intradoc.server.IdcExtendedLoader;
import intradoc.server.Service;
import intradoc.shared.*;
import java.io.PrintStream;
import java.util.Properties;

public class CoSignInstallFilters implements FilterImplementor {
	protected Workspace m_dbCSWorkspace;

	public CoSignInstallFilters() {
		m_dbCSWorkspace = null;
	}

	public int doFilter(Workspace ws, DataBinder binder, ExecutionContext cxt)
		throws DataException, ServiceException {
		// CS version must be greater than 7.0 to run this install filter.
		if (getSignificantVersion() <= 6) {
			return CONTINUE;
		}
		String param = null;
		Object paramObj = cxt.getCachedObject("filterParameter");
		if (paramObj == null || (paramObj instanceof String) == false) {
			return CONTINUE;
		}
		param = (String)paramObj;

		Service s = null;
		IdcExtendedLoader loader = null;

		if (cxt instanceof IdcExtendedLoader) {
			loader = (IdcExtendedLoader) cxt;
			if (ws == null) {
				ws = loader.getLoaderWorkspace();
			}
		}
		else if (cxt instanceof Service) {
			s = (Service)cxt;
			loader = (IdcExtendedLoader)ComponentClassFactory.createClassInstance("IdcExtendedLoader",
					"intradoc.server.IdcExtendedLoader", "!csCustomInitializerConstructionError");
		}

		// Called after environment data has been loaded and directory locations
		// have been determined but before database has been accessed.
		if (param.equals("extraAfterConfigInit")) {
		}
		// Called after initial connection to database and queries have been
		// loaded but before database is used to load data into application.
		// This is a good service for performing database table manipulation.
		else if (param.equals("extraBeforeCacheLoadInit")) {
		}
		// Called after the last standard activity of a
		// server side application initialization.  This is a good place
		// to manipulate cached data or override standard configuration
		// data.
		else if (param.equals("extraAfterServicesLoadInit")) {
			trace(false, "doFilter", "Called with 'extraAfterServicesLoadInit'");
			if(ws != null) {
//				doMetadataFieldSetup(ws);
				doCheckDatabase(loader, ws, binder, cxt);
			}
		}
		// Called after loading cached tables.
		else if (param.equals("initSubjects")) {
		}
		// Called for custom uninstallation steps.
		// NOTE: Change the uninstall filter name to have your component name prefix.
		// For example:  MyTestCompnentUninstallFilter
		else if (param.equals("<MyComponentName>ComponentUninstallFilter")) {
		}
		return CONTINUE;
	}

	protected int getSignificantVersion() {
		String strVersion=SystemUtils.getProductVersionInfo();
		int nIndex=strVersion.indexOf(".");
		if (nIndex != -1) {
			strVersion=strVersion.substring(0,nIndex);
		}
		return(Integer.valueOf(strVersion).intValue());
	}

	protected void doCheckDatabase(IdcExtendedLoader iel, Workspace ws, DataBinder db, ExecutionContext ec)
			throws DataException, ServiceException {
		trace(false, "doCheckDatabase", "Function entry ,,,");
		initDBConnection();
		try {
			trace(false, "doCheckDatabase", "Calling checkAndCreateTable( )");
			checkAndCreateTable();
			trace(false, "doInstall", "Calling checkAndCreateSequenceCounters( )");
			checkAndCreateCounter("ESIG_SEQ");
			trace(false, "doCheckDatabase", "Component Configuration Complete.");
		} catch(Exception e) {
			trace(false, "doCheckDatabase",
					(new StringBuilder()).append("Fatal Error: Unexpected Exception ").append(e).toString());
			String errMsg = LocaleUtils.encodeMessage("csSctErrUnexExceptionInitingDatabaseProvider", null, e);
			showError(LocaleResources.localizeMessage(errMsg, null));
		}
		trace(false, "doCheckDatabase", "Normal exit ...");
	}

	private void initDBConnection() throws ServiceException {
		trace(false, "initDBConnection", "Checking SystemDatabase Provider");
		Provider p = Providers.getProvider("SystemDatabase");
		if(p != null) {
			m_dbCSWorkspace = (Workspace)p.getProvider();
			if(m_dbCSWorkspace == null) {
				String errMsg = LocaleUtils.encodeMessage("csSctErrCannotConnectToSystemDatabaseWorkspace", null);
				showError(LocaleResources.localizeMessage(errMsg, null));
			}
		} else {
			String errMsg = LocaleUtils.encodeMessage("csSctErrNoSystemDatabaseProvider", null);
			showError(LocaleResources.localizeMessage(errMsg, null));
		}
	}

	private void checkAndCreateTable() throws DataException, ServiceException {
		trace(false, "checkAndCreateTable", "Function Entry - Creating SctTableWriter");
//		ESigTableWriter tblWriter = new ESigTableWriter(false);
		trace(false, "checkAndCreateTables", "Calling ESigTableWriter.createTable( )");
//		tblWriter.createTable();
		trace(false, "checkAndCreateTables", "Normal exit ...");
	}

	private void checkAndCreateCounter(String counterName) throws DataException {
		int blockSize = 1;
		trace(false, "checkAndCreateCounter",
				(new StringBuilder()).append("Using blockSize ").append(blockSize).toString());
		trace(false, "checkAndCreateCounter",
				(new StringBuilder()).append("Checking for existing counter ").append(counterName).toString());
		IdcCounter ctr = IdcCounterUtils.getCounter(m_dbCSWorkspace, counterName);
		if(ctr == null) {
			trace(false, "checkAndCreateCounter",
					(new StringBuilder()).append("Creating counter ").append(counterName).toString());
			if(!IdcCounterUtils.registerCounter(m_dbCSWorkspace, counterName, 1L, blockSize))
				trace(false, "checkAndCreateCounter",
						(new StringBuilder())
								.append("INTERNAL ERROR: Counter ")
								.append(counterName)
								.append(" *NOT* created").toString());
			else
				trace(false, "checkAndCreateCounter",
						(new StringBuilder()).append("Counter ").append(counterName).append(" created").toString());
		} else {
			trace(false, "checkAndCreateCounter",
					(new StringBuilder()).append("Found existing counter ").append(counterName).toString());
			if(ctr.m_increment != blockSize) {
				trace(false, "checkAndCreateCounter",
						(new StringBuilder())
								.append("Changing increment from ")
								.append(ctr.m_increment)
								.append(" to ")
								.append(blockSize).toString());
				long nxtVal = ctr.nextValue(m_dbCSWorkspace);
				trace(false, "checkAndCreateCounter",
						(new StringBuilder()).append("Got counter nextValue ").append(nxtVal).toString());
				if(!IdcCounterUtils.registerCounter(m_dbCSWorkspace, counterName, nxtVal, blockSize))
					trace(false, "checkAndCreateCounter",
							(new StringBuilder())
									.append("INTERNAL ERROR: Counter ")
									.append(counterName)
									.append(" *NOT* recreated").toString());
			}
		}
	}

	protected void showError(String msg) throws ServiceException {
		String errMsg = LocaleUtils.encodeMessage("csESigErrConfiguringCoSign", null, msg);
		Log.error(LocaleResources.localizeMessage(errMsg, null));
		throw new ServiceException(LocaleResources.localizeMessage(errMsg, null));
	}

	protected void trace(String caller, String msg) {
		trace(false, caller, msg);
	}

	protected void trace(boolean force, String caller, String msg) {
		String origin = (new StringBuilder())
				.append(getClass().getName())
				.append(".")
				.append(caller)
				.append("( )").toString();
		String fullMsg = (new StringBuilder()).append(origin).append(" - ").append(msg).toString();
		Report.trace("bezzotechinstall", fullMsg, null);
		if(force)
			System.out.println(fullMsg);
	}
}