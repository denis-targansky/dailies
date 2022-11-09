package com.denis_targansky.dailies;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

import com.denis_targansky.dailies.Configuration.InvalidConfigurationException;
import com.denis_targansky.dailies.Configuration.TaskConfiguration;
import com.denis_targansky.dailies.tasks.AbstractTask;

public class Main implements WrapperListener {

	private static final Logger ms = LogManager.getLogger( );

	private static final int EXIT_CODE_CONFIG_ERROR = -1;

	private static final int EXIT_CODE_TASK_ERROR = -2;

	private ScheduledExecutorService executor;

	@Override
	public Integer start( String[] args ) {
		if ( args.length == 0 ) {
			ms.error( "Missing configuration file name in parameters" );
			return EXIT_CODE_CONFIG_ERROR;
		}

		String configurationFileName = args[0];
		Configuration config;

		try {
			config = new Configuration( configurationFileName );
		} catch ( IOException | InvalidConfigurationException e ) {
			ms.error( new ParameterizedMessage( "Error reading configuration file '{}'", configurationFileName ), e );
			return EXIT_CODE_CONFIG_ERROR;
		}

		executor = new ScheduledThreadPoolExecutor( 1000 );
		ms.info( "Scheduling {} tasks", config.getTaskConfigurationList( ).size( ) );
		for ( TaskConfiguration taskConfig : config.getTaskConfigurationList( ) ) {
			Class taskClass = taskConfig.getTaskClass( );
			AbstractTask task;
			try {
				task = AbstractTask.class.cast( taskClass.getDeclaredConstructor( Map.class, String.class )
						.newInstance( taskConfig.getTaskProperties( ), config.getMainOutputFolder( ) ) );
			} catch ( InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e ) {
				ms.error( new ParameterizedMessage( "Error creating task '{}'", taskClass.getCanonicalName( ) ), e );
				return EXIT_CODE_TASK_ERROR;
			}

			executor.scheduleAtFixedRate( task, taskConfig.getInitialDelaySeconds( ), taskConfig.getPeriodSeconds( ),
					TimeUnit.SECONDS );
			ms.info( "Scheduled task {} with initial delay {} and period {}", taskClass.getName( ),
					Duration.ofSeconds( taskConfig.getInitialDelaySeconds( ) ),
					Duration.ofSeconds( taskConfig.getPeriodSeconds( ) ) );
		}

		ms.info( "Completed scheduling all tasks" );

		return null;
	}

	@Override
	public int stop( int exitCode ) {
		ms.info( "Shutdown initiated with code {}", exitCode );
		executor.shutdown( );
		return exitCode;
	}

	@Override
	public void controlEvent( int event ) {
		ms.debug( "Received event {}", event );
		WrapperManager.stop( 0 );
	}

	public static void main( String[] args ) {
		WrapperManager.start( new Main( ), args );
	}

}
