package com.denis_targansky.dailies;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.denis_targansky.dailies.tasks.AbstractTask;

public class Configuration {

	private static final Logger ms = LogManager.getLogger( );

	private final File configFile;

	private final PropertiesConfiguration properties;

	private final String mainOutputFolder;

	private final List<TaskConfiguration> taskConfigurationList = new ArrayList<>( );

	public Configuration( String configurationFileName )
			throws IOException, InvalidConfigurationException, ConfigurationException {
		properties = new PropertiesConfiguration( );

		configFile = new File( configurationFileName );
		ms.info( "Reading configuration file from {}", configFile.getAbsolutePath( ) );
		try ( FileReader reader = new FileReader( configFile ) ) {
			properties.read( reader );
		}

		if ( properties.containsKey( "output-folder" ) ) {
			mainOutputFolder = properties.getString( "output-folder" );
		} else {
			mainOutputFolder = "./output";
		}

		int taskIndex = 1;

		while ( true ) {
			String taskPrefix = "task." + taskIndex + ".";
			String className = properties.getString( taskPrefix + "class-name" );

			if ( className == null ) {
				break;
			}

			if ( !properties.containsKey( taskPrefix + "period-seconds" ) ) {
				throw new InvalidConfigurationException( "Missing period-seconds property for task " + taskIndex );
			}

			long initialDelaySeconds = properties.getLong( taskPrefix + "initial-delay-seconds", 0 );
			long periodSeconds = properties.getLong( taskPrefix + "period-seconds" );

			Map<String, String> taskProperties = new HashMap<>( );
			String taskPropertyPrefix = taskPrefix + "property.";
			for ( Iterator<String> keyIter = properties
					.getKeys( taskPropertyPrefix.substring( 0, taskPropertyPrefix.length( ) - 1 ) ); keyIter.hasNext( ); ) {
				String taskPropertyKey = keyIter.next( );
				String taskPropertyName = taskPropertyKey
						.substring( taskPropertyKey.lastIndexOf( taskPropertyPrefix ) + taskPropertyPrefix.length( ) );
				String taskPropertyValue = properties.getString( taskPropertyKey );
				taskProperties.put( taskPropertyName, taskPropertyValue );
			}

			TaskConfiguration taskConfig;
			try {
				taskConfig = new TaskConfiguration( taskPropertyPrefix, className, initialDelaySeconds, periodSeconds, taskProperties );
			} catch ( ClassNotFoundException e ) {
				throw new InvalidConfigurationException( "Invalid class name for task " + taskIndex, e );
			}

			if ( !AbstractTask.class.isAssignableFrom( taskConfig.getTaskClass( ) ) ) {
				throw new InvalidConfigurationException(
						"Class '" + className + "' declared in task " + taskIndex + " is not runnable" );
			}

			taskConfigurationList.add( taskConfig );

			taskIndex++;
		}
	}

	public String getMainOutputFolder( ) {
		return mainOutputFolder;
	}

	public List<TaskConfiguration> getTaskConfigurationList( ) {
		return taskConfigurationList;
	}

	public void updateProperty( String key, Object value ) throws IOException, ConfigurationException {
		ms.debug( "Changing value of configuration property from {}={} to {}={}", key, properties.getProperty( key ), key,
				value );
		properties.setProperty( key, value );
		try ( FileWriter writer = new FileWriter( configFile ) ) {
			properties.write( writer );
		}
	}

	public static final class TaskConfiguration {

		private final String taskPropertyPrefix;

		private final Class taskClass;

		private final long initialDelaySeconds;

		private final long periodSeconds;

		private final Map<String, String> taskProperties;

		public TaskConfiguration( String taskPropertyPrefix, String className, long initialDelaySeconds, long periodSeconds,
				Map<String, String> taskProperties ) throws ClassNotFoundException {
			super( );
			this.taskPropertyPrefix = taskPropertyPrefix;
			this.taskClass = Class.forName( className );
			this.initialDelaySeconds = initialDelaySeconds;
			this.periodSeconds = periodSeconds;
			this.taskProperties = taskProperties;
		}

		public String getTaskPropertyPrefix( ) {
			return taskPropertyPrefix;
		}

		public Class getTaskClass( ) {
			return taskClass;
		}

		public long getInitialDelaySeconds( ) {
			return initialDelaySeconds;
		}

		public long getPeriodSeconds( ) {
			return periodSeconds;
		}

		public Map<String, String> getTaskProperties( ) {
			return taskProperties;
		}

	}

	public static final class InvalidConfigurationException extends Exception {

		public InvalidConfigurationException( String message ) {
			super( message );
		}

		public InvalidConfigurationException( String message, Throwable cause ) {
			super( message, cause );
		}
	}
}
