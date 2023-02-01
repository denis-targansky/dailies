package com.denis_targansky.dailies.tasks;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.denis_targansky.dailies.Configuration;
import com.denis_targansky.dailies.Configuration.TaskConfiguration;

public abstract class AbstractTask implements Runnable {

	protected final Logger log = LogManager.getLogger( );

	protected final Map<String, String> taskProperties;

	private final Configuration config;

	private final TaskConfiguration taskConfig;

	private final String mainOutputFolder;

	protected AbstractTask( Configuration config, TaskConfiguration taskConfig )
			throws InvalidTaskConfigurationException {
		super( );
		this.config = config;
		this.taskConfig = taskConfig;
		this.taskProperties = taskConfig.getTaskProperties( );
		this.mainOutputFolder = config.getMainOutputFolder( );
	}

	@Override
	public final void run( ) {
		log.info( "{} is running at {}", getClass( ).getName( ), LocalDateTime.now( ) );
		boolean taskSucceeded = runTask( );
		log.info( "{} has completed running {} at {}", getClass( ).getName( ),
				( taskSucceeded ? "successfully" : "with errors" ), LocalDateTime.now( ) );
	}

	protected abstract boolean runTask( );

	public static final class InvalidTaskConfigurationException extends Exception {

		public InvalidTaskConfigurationException( String message ) {
			super( message );
		}

		public InvalidTaskConfigurationException( String message, Throwable cause ) {
			super( message, cause );
		}
	}

	protected File getEnsuredOutputFolder( ) {
		File outputFolder = new File( mainOutputFolder + File.separator + getClass( ).getSimpleName( ) );
		if ( !outputFolder.exists( ) ) {
			outputFolder.mkdirs( );
		}

		return outputFolder;
	}

	protected void updateTaskProperty( String key, Object value ) throws IOException, ConfigurationException {
		config.updateProperty( taskConfig.getTaskPropertyPrefix( ) + key, value );
	}

}
