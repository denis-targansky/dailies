package com.denis_targansky.dailies.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.message.ParameterizedMessage;

import com.denis_targansky.dailies.Configuration;
import com.denis_targansky.dailies.Configuration.TaskConfiguration;

public class DiscordChatExporterTask extends AbstractTask {

	private static final String PROPERTY_DCE_PATH = "discord-chat-exporter.path";

	private static final String PROPERTY_DISCORD_AUTH_TOKEN = "discord.auth-token";

	private static final String PROPERTY_CHANNEL_PREFIX = "discord.channel.";

	private static final String PROPERTY_CHANNEL_ID_SUFFIX = ".id";

	private static final String PROPERTY_CHANNEL_LAST_QUERY_TIME_SUFFIX = ".last-query-time";

	private static final String PROPERTY_CHANNEL_FILTER_SUFFIX = ".filter";

	private static final String OUTPUT_FOLDER_CHATS = "chats";

	private static final String DCE_DLL_NAME = "DiscordChatExporter.Cli.dll";

	private final File dcePath;

	private final String discordAuthToken;

	private final Map<Integer, String> indexToChannelIdMap = new HashMap<>( );

	private final Map<Integer, LocalDateTime> indexToLastQueryTimeMap = new HashMap<>( );

	private final Map<Integer, String> indexToFilterMap = new HashMap<>( );

	public DiscordChatExporterTask( Configuration config, TaskConfiguration taskConfig )
			throws InvalidTaskConfigurationException {
		super( config, taskConfig );
		String dcePathName = taskProperties.get( PROPERTY_DCE_PATH );
		if ( dcePathName == null || dcePathName.isBlank( ) || !Files.exists( Path.of( dcePathName, DCE_DLL_NAME ) ) ) {
			throw new InvalidTaskConfigurationException(
					"Path to Discord Chat Exporter specified in " + PROPERTY_DCE_PATH + " property is invalid" );
		}
		dcePath = new File( dcePathName );

		discordAuthToken = taskProperties.get( PROPERTY_DISCORD_AUTH_TOKEN );
		if ( discordAuthToken == null || discordAuthToken.isBlank( ) ) {
			throw new InvalidTaskConfigurationException(
					"Discord auth token specified through " + PROPERTY_DISCORD_AUTH_TOKEN + " property is missing" );
		}

		for ( Map.Entry<String, String> taskPropertyEntry : taskProperties.entrySet( ) ) {
			String key = taskPropertyEntry.getKey( );
			String value = taskPropertyEntry.getValue( );

			if ( !key.startsWith( PROPERTY_CHANNEL_PREFIX ) ) {
				continue;
			}

			if ( value.isBlank( ) ) {
				continue;
			}

			int index = Integer.parseInt(
					key.substring( PROPERTY_CHANNEL_PREFIX.length( ), key.indexOf( '.', PROPERTY_CHANNEL_PREFIX.length( ) ) ) );
			if ( key.endsWith( PROPERTY_CHANNEL_ID_SUFFIX ) ) {
				indexToChannelIdMap.put( index, value );
			}

			if ( key.endsWith( PROPERTY_CHANNEL_LAST_QUERY_TIME_SUFFIX ) ) {
				indexToLastQueryTimeMap.put( index, LocalDateTime.parse( value ) );
			}

			if ( key.endsWith( PROPERTY_CHANNEL_FILTER_SUFFIX ) ) {
				indexToFilterMap.put( index, value );
			}
		}

		if ( indexToChannelIdMap.isEmpty( ) ) {
			throw new InvalidTaskConfigurationException( "Must specify at least one Discord channel ID through "
					+ PROPERTY_CHANNEL_PREFIX + "#" + PROPERTY_CHANNEL_ID_SUFFIX + " property" );
		}
	}

	@Override
	protected boolean runTask( ) {
		for ( Map.Entry<Integer, String> indexChannelIdEntry : indexToChannelIdMap.entrySet( ) ) {
			int index = indexChannelIdEntry.getKey( );
			String channelId = indexChannelIdEntry.getValue( );
			String filter = indexToFilterMap.get( index );
			List<String> arguments = new ArrayList<>( );
			arguments.add( "cmd.exe" );
			arguments.add( "/c" );
			arguments.add( "dotnet" );
			arguments.add( "\"" + DCE_DLL_NAME + "\"" );
			arguments.add( "export" );
			arguments.add( "-t" );
			arguments.add( discordAuthToken );
			arguments.add( "-c" );
			arguments.add( channelId );
			arguments.add( "-o" );
			arguments.add( "\"" + getEnsuredChatExportFolder( ).getAbsolutePath( ) + "\"" );
			arguments.add( "-f" );
			arguments.add( "Json" );
			arguments.add( "--after" );
			arguments.add( "\""
					+ indexToLastQueryTimeMap.getOrDefault( index, LocalDateTime.now( ).minusDays( 1 ) ).toString( ) + "\"" );
			if ( filter != null && !filter.isBlank( ) ) {
				arguments.add( "--filter" );
				arguments.add( filter );
			}
			ProcessBuilder processBuilder = new ProcessBuilder( arguments );
			processBuilder.directory( dcePath );

			processBuilder.redirectErrorStream( true );

			try {
				processBuilder.inheritIO( ).start( );
			} catch ( IOException e ) {
				log.error( new ParameterizedMessage( "Error running Discord chat exporter with arguments {}", arguments ), e );
			}

			updateChannelLastQuery( index, LocalDateTime.now( ) );
		}
		return true;
	}

	private void updateChannelLastQuery( int index, LocalDateTime lastQueryTime ) {
		indexToLastQueryTimeMap.put( index, lastQueryTime );
		try {
			updateTaskProperty( PROPERTY_CHANNEL_PREFIX + index + PROPERTY_CHANNEL_LAST_QUERY_TIME_SUFFIX,
					lastQueryTime.toString( ) );
		} catch ( IOException | ConfigurationException e ) {
			log.error( new ParameterizedMessage( "Could not update channel last query time for index {}", index ), e );
		}
	}

	private File getEnsuredChatExportFolder( ) {
		File outputFolder = new File( getEnsuredOutputFolder( ), OUTPUT_FOLDER_CHATS );
		if ( !outputFolder.exists( ) ) {
			outputFolder.mkdirs( );
		}

		return outputFolder;
	}

}
