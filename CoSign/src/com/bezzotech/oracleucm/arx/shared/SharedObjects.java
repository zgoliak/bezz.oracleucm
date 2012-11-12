package com.bezzotech.oracleucm.arx.shared;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import intradoc.common.ExecutionContext;
import intradoc.common.ServiceException;
import intradoc.common.Sort;
import intradoc.common.SortUtilsComparator;
import intradoc.data.DataBinder;
import intradoc.server.Service;

import static intradoc.shared.SharedObjects.getEnvironmentValue;

public class SharedObjects {
	/** An ExecutionContext associated with this request. */
	protected ExecutionContext m_context;

	/** Use getSharedObjects() to get a reference to a SharedObjects instance. */
	protected SharedObjects() {
	}

	/** Return a reference to a SharedObjects object that should be used
	 * by this ExecutionContext.
	 * @param context ExecutionContext to use for this SharedObjects instance.
	 * @return a SharedObjects instance based on context.
	 */
	static public SharedObjects getSharedObjects( ExecutionContext context ) {
		SharedObjects obj = ( SharedObjects )context.getCachedObject( "BezzotechSharedObjects" );
		if ( obj == null ) {
			obj = new SharedObjects();
			obj.m_context = context;
			context.setCachedObject( "BezzotechSharedObjects", obj );
		}
		return obj;
	}

	/** Get a DataBinder out of m_context.  If m_context is a pointer to a 
	 * <span class="code">intradoc.server.Service</span>, call <span class="code">getBinder()</span>
	 * otherwise use <span class="code">getCachedObject("DataBinder")</span> to get the binder.
	 * @return The DataBinder associated with this SharedObjects object, or null.
	 */
	protected DataBinder getBinder() {
		if ( m_context instanceof Service ) {
			return ( ( Service )m_context ).getBinder();
		} else {
			return ( DataBinder )m_context.getCachedObject( "DataBinder" );
		}
	}

	/** Return the value for the SharedObject variable named by key.
	 * If this SharedObjects instance has a DataBinder, it will be fetched from the 
	 * DataBinder rather than the global SharedObjects.
	 * @param key key name to lookup
	 * @return value of key
	 * @throws ServiceException if the key is not defined.
	 */
	public String getConfig( String key ) throws ServiceException {
		String val = null;
		DataBinder binder = getBinder();
		if ( binder != null ) val = binder.getEnvironmentValue( key );
		if ( val == null ) val = getEnvironmentValue( key );
		if ( val == null ) throw new ServiceException( null, "csRequiredConfigFieldMissing", key );
		return val;
	}

	/** Return the value for the SharedObject variable named by key.
	 * If this SharedObjects instance has a DataBinder, it will be fetched from the 
	 * DataBinder rather than the global SharedObjects.  Generally speaking, this
	 * method should be avoided and getConfig() should be used again.  Any optional 
	 * config * entries should have a default value provided either by a component 
	 * environment file or component preference prompts.
	 * @see #getConfig
	 * @param key key name to lookup
	 * @return value of key or null if not defined
	 */
	public String checkConfig( String key ) {
		String val = null;
		DataBinder binder = getBinder();
		if ( binder != null ) val = binder.getEnvironmentValue( key );
		if ( val == null ) val = getEnvironmentValue( key );
		return val;
	}

	/** Return a list of values for a given key prefix.
	 * @param keyPrefix prefix to look for key names to start with.
	 * @return a List of values for the key starting with keyPrefix.
	 */
	public Map < String, String > getConfigSet( String keyPrefix ) {
		Map < String, String > l = new HashMap();
		Properties props = intradoc.shared.SharedObjects.getSafeEnvironment();
		for ( Object key : props.keySet() ) {
			if ( key instanceof String == false ) continue;
			String keyString = ( String )key;
			if ( keyString.startsWith( keyPrefix ) )
				l.put( keyString.substring( keyPrefix.length() + 1 ), props.getProperty( keyString ) );
		}
		return l;
	}
}