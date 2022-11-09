package com.denis_targansky.dailies.tasks;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractTask implements Runnable {

	protected final Logger log = LogManager.getLogger( );

	protected final Map<String, String> taskProperties;

	private final String mainOutputFolder;

	protected AbstractTask( Map<String, String> taskProperties, String mainOutputFolder )
			throws InvalidTaskConfigurationException {
		super( );
		this.taskProperties = taskProperties;
		this.mainOutputFolder = mainOutputFolder;
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

}
