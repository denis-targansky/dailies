package com.denis_targansky.dailies.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.denis_targansky.dailies.Configuration;
import com.denis_targansky.dailies.Configuration.TaskConfiguration;

public class WebScraperTask extends AbstractTask {

	private static final String PROPERTY_SCRAPING_URL_PREFIX = "scraping-url";

	private final List<String> scrapingUrls = new ArrayList<>( );

	public WebScraperTask( Configuration config, TaskConfiguration taskConfig ) throws InvalidTaskConfigurationException {
		super( config, taskConfig );
		for ( Map.Entry<String, String> taskPropertyEntry : taskProperties.entrySet( ) ) {
			String key = taskPropertyEntry.getKey( );
			String value = taskPropertyEntry.getValue( );

			if ( key.isBlank( ) ) {
				continue;
			}

			if ( key.matches( PROPERTY_SCRAPING_URL_PREFIX + "\\.\\d+" ) ) {
				scrapingUrls.add( value );
			}
		}

		if ( scrapingUrls.isEmpty( ) ) {
			throw new InvalidTaskConfigurationException(
					"No URLs specified through " + PROPERTY_SCRAPING_URL_PREFIX + " property" );
		}
	}

	@Override
	protected boolean runTask( ) {
		for ( String scrapingUrl : scrapingUrls ) {
			Connection connection = Jsoup.connect( scrapingUrl );
			try {
				Document document = connection.get( );
			} catch ( IOException e ) {
				log.error( "Error getting document from {}", scrapingUrl );
				continue;
			}

		}
		return true;
	}

}
