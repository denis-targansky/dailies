package com.denis_targansky.dailies;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.denis_targansky.dailies.tasks.AbstractTask;

public class Configuration {

	private static final Logger ms = LogManager.getLogger( );

	private final String mainOutputFolder;

	private final List<TaskConfiguration> taskConfigurationList = new ArrayList<>( );

	public Configuration( String configurationFileName ) throws IOException, InvalidConfigurationException {
		Properties properties = new Properties( );
		File configurationFile = new File( configurationFileName );

		ms.info( "Reading configuration file from {}", configurationFile.getAbsolutePath( ) );
		try ( FileReader reader = new FileReader( configurationFile ) ) {
			properties.load( reader );
		}

		if ( properties.containsKey( "output-folder" ) ) {
			mainOutputFolder = properties.getProperty( "output-folder" );
		} else {
			mainOutputFolder = "./output";
		}

		if ( properties.keySet( ).stream( ).anyMatch( key -> ( (String) key ).contains( "task." ) ) ) {
			int taskIndex = 1;

			while ( true ) {
				String taskPrefix = "task." + taskIndex + ".";
				String className = properties.getProperty( taskPrefix + "class-name" );

				if ( className == null ) {
					break;
				}

				if ( !properties.containsKey( taskPrefix + "period-seconds" ) ) {
					throw new InvalidConfigurationException( "Missing period-seconds property for task " + taskIndex );
				}

				long initialDelaySeconds = Long
						.parseLong( properties.getProperty( taskPrefix + "initial-delay-seconds", "0" ) );
				long periodSeconds = Long.parseLong( properties.getProperty( taskPrefix + "period-seconds" ) );

				Map<String, String> taskProperties = new HashMap<>( );
				String taskPropertyPrefix = taskPrefix + "property.";
				Set<String> taskPropertyKeys = properties.keySet( ).stream( ).map( String.class::cast )
						.filter( key -> key.contains( taskPropertyPrefix ) ).collect( Collectors.toSet( ) );
				for ( String taskPropertyKey : taskPropertyKeys ) {
					String taskPropertyName = taskPropertyKey
							.substring( taskPropertyKey.lastIndexOf( taskPropertyPrefix ) + taskPropertyPrefix.length( ) );
					String taskPropertyValue = properties.getProperty( taskPropertyKey );
					taskProperties.put( taskPropertyName, taskPropertyValue );
				}

				TaskConfiguration taskConfig;
				try {
					taskConfig = new TaskConfiguration( className, initialDelaySeconds, periodSeconds, taskProperties );
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
	}

	public String getMainOutputFolder( ) {
		return mainOutputFolder;
	}

	public List<TaskConfiguration> getTaskConfigurationList( ) {
		return taskConfigurationList;
	}

	public static final class TaskConfiguration {

		private final Class taskClass;

		private final long initialDelaySeconds;

		private final long periodSeconds;

		private final Map<String, String> taskProperties;

		public TaskConfiguration( String className, long initialDelaySeconds, long periodSeconds,
				Map<String, String> taskProperties ) throws ClassNotFoundException {
			super( );
			this.taskClass = Class.forName( className );
			this.initialDelaySeconds = initialDelaySeconds;
			this.periodSeconds = periodSeconds;
			this.taskProperties = taskProperties;
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
