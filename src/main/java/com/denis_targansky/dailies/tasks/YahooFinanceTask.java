package com.denis_targansky.dailies.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import com.denis_targansky.dailies.Configuration;
import com.denis_targansky.dailies.Configuration.TaskConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import yahoofinance.Stock;
import yahoofinance.Utils;
import yahoofinance.YahooFinance;
import yahoofinance.quotes.stock.StockQuote;
import yahoofinance.quotes.stock.StockStats;
import yahoofinance.util.RedirectableRequest;

public class YahooFinanceTask extends AbstractTask {

	private static final String PROPERTY_TEMPLATE_FILE_PATH = "template-file-path";

	private static final String PROPERTY_TICKERS = "tickers";

	private static final String TERM_COMPANY_NAME = "{company_name}";

	private static final String TERM_TICKER = "{ticker}";

	private static final String TERM_STOCK_PRICE = "{stock_price}";

	private static final String TERM_MARKET_CAP = "{market_cap}";

	private static final String TERM_52_WEEK_LOW = "{52_week_low}";

	private static final String TERM_52_WEEK_HIGH = "{52_week_high}";

	private static final String TERM_AVERAGE_VOLUME = "{average_volume}";

	private static final String TERM_SCRAPE_DATE = "{scrape_date}";

	private static final String TERM_HEADLINE_TITLE = "{headline_title_###}";

	private static final String TERM_HEADLINE_LINK = "{headline_link_###}";

	private static final String YAHOO_FINANCE_API_SEARCH_URL = "https://query2.finance.yahoo.com/v1/finance/search";

	private static final ObjectMapper objectMapper = new ObjectMapper( );

	private final String templateFilePath;

	private final List<String> tickers;

	public YahooFinanceTask( Configuration config, TaskConfiguration taskConfig )
			throws InvalidTaskConfigurationException {
		super( config, taskConfig );
		templateFilePath = taskProperties.get( PROPERTY_TEMPLATE_FILE_PATH );
		if ( templateFilePath == null || templateFilePath.isEmpty( ) ) {
			throw new InvalidTaskConfigurationException( "Missing " + PROPERTY_TEMPLATE_FILE_PATH + " property" );
		}
		File templateFile = new File( templateFilePath );
		if ( !templateFile.exists( ) ) {
			throw new InvalidTaskConfigurationException( "File " + templateFile.getAbsolutePath( ) + " does not exist" );
		}

		tickers = Arrays.asList( taskProperties.get( PROPERTY_TICKERS ).split( "," ) ).stream( )
				.filter( Predicate.not( String::isEmpty ) ).toList( );
		if ( tickers.isEmpty( ) ) {
			throw new InvalidTaskConfigurationException(
					"Missing comma separated tickers in " + PROPERTY_TICKERS + " property" );
		}
	}

	@Override
	protected boolean runTask( ) {
		boolean errors = false;
		LocalDateTime now = LocalDateTime.now( );
		File templateFile = new File( templateFilePath );
		String datedFileName = templateFile.getName( ).replace( "yyyy", String.format( "%04d", now.getYear( ) ) )
				.replace( "MM", String.format( "%02d", now.getMonthValue( ) ) )
				.replace( "dd", String.format( "%02d", now.getDayOfMonth( ) ) )
				.replace( "HH", String.format( "%02d", now.getHour( ) ) )
				.replace( "mm", String.format( "%02d", now.getMinute( ) ) )
				.replace( "ss", String.format( "%02d", now.getSecond( ) ) );
		for ( String ticker : tickers ) {
			ticker = ticker.trim( ).toUpperCase( );
			Stock stock;
			try {
				stock = YahooFinance.get( ticker );
			} catch ( IOException e ) {
				log.error( new ParameterizedMessage( "Error retrieving information for ticker '{}'", ticker ), e );
				errors = true;
				continue;
			}

			StockQuote quote = stock.getQuote( );
			StockStats stats = stock.getStats( );
			List<Article> articles = getTickerNews( ticker, true );

			Map<String, String> termToValueMap = new HashMap<>( );
			termToValueMap.put( TERM_COMPANY_NAME, stock.getName( ) );
			termToValueMap.put( TERM_TICKER, ticker );
			termToValueMap.put( TERM_STOCK_PRICE, quote.getPrice( ).toString( ) );
			termToValueMap.put( TERM_MARKET_CAP, stats.getMarketCap( ).toString( ) );
			termToValueMap.put( TERM_52_WEEK_LOW, quote.getYearLow( ).toString( ) );
			termToValueMap.put( TERM_52_WEEK_HIGH, quote.getYearHigh( ).toString( ) );
			termToValueMap.put( TERM_AVERAGE_VOLUME, quote.getAvgVolume( ).toString( ) );
			termToValueMap.put( TERM_SCRAPE_DATE, now.toLocalDate( ).toString( ) );
			for ( int index = 0; index < articles.size( ); index++ ) {
				Article article = articles.get( index );
				termToValueMap.put( TERM_HEADLINE_TITLE.replace( "###", String.valueOf( index + 1 ) ), article.getTitle( ) );
				termToValueMap.put( TERM_HEADLINE_LINK.replace( "###", String.valueOf( index + 1 ) ), article.getLink( ) );
			}

			log.debug( "Term to value map for ticker {}: {}", ticker, termToValueMap );

			File destinationFile = new File( getEnsureTickerOutputFolder( ticker ).getAbsolutePath( ) + File.separator
					+ datedFileName.replace( "TCKR", ticker ) );
			try ( XWPFDocument document = new XWPFDocument( new FileInputStream( templateFile ) ) ) {
				boolean changesMade = false;
				for ( XWPFParagraph paragraph : document.getParagraphs( ) ) {
					for ( XWPFRun run : paragraph.getRuns( ) ) {
						String runText = run.getText( 0 );
						if ( runText == null || runText.isEmpty( ) ) {
							continue;
						}

						boolean replacementOccurred = false;
						for ( Entry<String, String> termEntry : termToValueMap.entrySet( ) ) {
							String term = termEntry.getKey( );
							String value = termEntry.getValue( );
							if ( runText.contains( term ) ) {
								runText = runText.replace( term, value );
								replacementOccurred = true;
								log.debug( "Replaced match for {} to {}", term, value );
							}
						}

						if ( replacementOccurred ) {
							run.setText( runText, 0 );
							changesMade = true;
						}
					}
				}

				if ( changesMade ) {
					try ( FileOutputStream out = new FileOutputStream( destinationFile ) ) {
						document.write( out );
					} catch ( IOException e ) {
						log.error( new ParameterizedMessage( "Error writing template file to '{}' for ticker '{}'",
								destinationFile.getAbsolutePath( ), ticker ), e );
						errors = true;
					}
				}
			} catch ( IOException e ) {
				log.error( new ParameterizedMessage( "Error reading template file '{}' for ticker '{}'",
						templateFile.getAbsolutePath( ), ticker ), e );
				errors = true;
			}
		}

		return !errors;
	}

	private List<Article> getTickerNews( String ticker, boolean onlyExclusive ) {
		Map<String, String> params = new LinkedHashMap<>( );
		params.put( "q", ticker );

		String url = YAHOO_FINANCE_API_SEARCH_URL + "?" + Utils.getURLParameters( params );

		// Get JSON from Yahoo
		log.info( "Sending request: {}", url );

		List<Article> articles = new ArrayList<>( );
		JsonNode node;
		try {
			URL request = new URL( url );
			RedirectableRequest redirectableRequest = new RedirectableRequest( request, 5 );
			redirectableRequest.setConnectTimeout( YahooFinance.CONNECTION_TIMEOUT );
			redirectableRequest.setReadTimeout( YahooFinance.CONNECTION_TIMEOUT );
			URLConnection connection = redirectableRequest.openConnection( );

			InputStreamReader is = new InputStreamReader( connection.getInputStream( ) );
			node = objectMapper.readTree( is );
		} catch ( IOException e ) {
			log.error( new ParameterizedMessage( "Error searching news for ticker {}", ticker ), e );
			return articles;
		}

		if ( !node.has( "news" ) || !node.get( "news" ).has( 0 ) ) {
			log.info( "No news found for ticker '{}'", ticker );
			return articles;
		}

		node = node.get( "news" );
		for ( int i = 0; i < node.size( ); i++ ) {
			Article article = new Article( node.get( i ) );
			if ( article.getRelatedTickers( ).isEmpty( ) ) {
				log.error( "Could not parse related tickers on article found for ticker {}: {}", ticker, article );
				continue;
			}

			if ( !article.getRelatedTickers( ).contains( ticker ) ) {
				log.debug( "Filtering out unrelated article for ticker {}: {}", ticker, article );
				continue;
			}

			articles.add( article );
		}

		if ( onlyExclusive ) {
			for ( Iterator<Article> articleIter = articles.iterator( ); articleIter.hasNext( ); ) {
				Article article = articleIter.next( );
				if ( article.getRelatedTickers( ).size( ) > 1 ) {
					log.debug( "Filtering out non-exclusive article for ticker {}: {}", ticker, article );
					articleIter.remove( );
				}
			}
		}

		return articles;
	}

	private File getEnsureTickerOutputFolder( String ticker ) {
		File outputFolder = new File( getEnsuredOutputFolder( ).getAbsolutePath( ) + File.separator + ticker );
		if ( !outputFolder.exists( ) ) {
			outputFolder.mkdirs( );
		}

		return outputFolder;
	}

	private static final class Article {

		private final String uuid;

		private final String title;

		private final String link;

		private final ZonedDateTime providerPublishTime;

		private final String publisher;

		private final List<String> relatedTickers = new ArrayList<>( );

		public Article( JsonNode node ) {
			super( );
			this.uuid = getStringValue( node, "uuid" );
			this.title = getStringValue( node, "title" );
			this.link = getStringValue( node, "link" );
			this.providerPublishTime = ZonedDateTime
					.ofInstant( Instant.ofEpochSecond( getLongValue( node, "providerPublishTime" ) ), ZoneId.of( "GMT" ) );
			this.publisher = getStringValue( node, "publisher" );

			if ( node.has( "relatedTickers" ) ) {
				JsonNode tickersNode = node.get( "relatedTickers" );
				for ( int index = 0; index < tickersNode.size( ); index++ ) {
					String ticker = getStringValue( tickersNode, index );
					if ( ticker == null || ticker.isEmpty( ) ) {
						break;
					}

					relatedTickers.add( ticker );
				}
			}
		}

		public String getUuid( ) {
			return uuid;
		}

		public String getTitle( ) {
			return title;
		}

		public String getLink( ) {
			return link;
		}

		public ZonedDateTime getProviderPublishTime( ) {
			return providerPublishTime;
		}

		public String getPublisher( ) {
			return publisher;
		}

		public List<String> getRelatedTickers( ) {
			return relatedTickers;
		}

		@Override
		public String toString( ) {
			return "Article [uuid=" + uuid + ", title=" + title + ", link=" + link + ", providerPublishTime="
					+ providerPublishTime + ", publisher=" + publisher + ", relatedTickers=" + relatedTickers + "]";
		}

		private static String getStringValue( JsonNode node, String field ) {
			if ( node.has( field ) ) {
				return node.get( field ).asText( );
			}
			return null;
		}

		private static String getStringValue( JsonNode node, int index ) {
			if ( node.has( index ) ) {
				return node.get( index ).asText( );
			}
			return null;
		}

		private static long getLongValue( JsonNode node, String field ) {
			if ( node.has( field ) ) {
				return node.get( field ).asLong( );
			}
			return 0;
		}

	}

}
