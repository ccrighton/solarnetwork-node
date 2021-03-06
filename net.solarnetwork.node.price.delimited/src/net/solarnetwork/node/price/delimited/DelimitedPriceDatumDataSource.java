/* ===================================================================
 * DelimitedPriceDatumDataSource.java
 * 
 * Created Aug 8, 2009 2:09:30 PM
 * 
 * Copyright (c) 2009 Solarnetwork.net Dev Team.
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ===================================================================
 * $Id$
 * ===================================================================
 */

package net.solarnetwork.node.price.delimited;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import net.solarnetwork.node.DatumDataSource;
import net.solarnetwork.node.price.PriceDatum;
import net.solarnetwork.node.settings.SettingSpecifier;
import net.solarnetwork.node.settings.SettingSpecifierProvider;
import net.solarnetwork.node.settings.support.BasicTextFieldSettingSpecifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Implementation of {@link DatumDataSource} that parses a delimited text
 * resource from a URL.
 * 
 * <p>This class will make a URL request and parse the returned text as 
 * delimited lines of data. The references to <em>columns</em> in the
 * class properties refer to zero-based column numbers created after 
 * splitting the line of data into an array using the configured 
 * delimiter.</p>
 * 
 * <p>The configurable properties of this class are:</p>
 * 
 * <dl class="class-properties">
 *   <dt>url</dt>
 *   <dd>The URL template for accessing the delimited price data from. This
 *   will be passed through {@link String#format(String, Object...)} with the
 *   current date as the only parameter, allowing the URL to contain a date
 *   requeset parameter if needed. For example, a value of 
 *   {@code http://some.place/prices?date=%1$tY-%1$tm-%1$td} would resolve
 *   to something like {@code http://some.place/prices?date=2009-08-08}.</dd>
 *   
 *   <dt>delimiter</dt>
 *   <dd>A regular expression delimiter to split the lines of text with.
 *   Defaults to {@link #DEFAULT_DELIMITER}.</dd>
 *   
 *   <dt>skipLines</dt>
 *   <dd>The number of lines of text to skip. This is useful for skipping
 *   a "header" row with column names. Defaults to {@code 1}.</dd>
 *   
 *   <dt>connectionTimeout</dt>
 *   <dd>A URL connection timeout to apply when requesting the data.
 *   Defaults to {@link #DEFAULT_CONNECTION_TIMEOUT}.</dd>
 *   
 *   <dt>priceColumn</dt>
 *   <dd>The result column index for the price. This is assumed to be 
 *   parsable as a double value.</dd>
 *   
 *   <dt>sourceIdColumn</dt>
 *   <dd>An optional column index to use for the 
 *   {@link PriceDatum#getSourceId()} value. If not configured, the URL used
 *   to request the data will be used.</dd>
 *   
 *   <dt>dateTimeColumns</dt>
 *   <dd>An array of column indices to use as the 
 *   {@link PriceDatum#getCreated()} value. This is provided as an array
 *   in case the date and time of the price is split across multiple columns.
 *   If multiple columns are configured, they will be joined with a space
 *   character before parsing the result into a Date object.</dd>
 *   
 *   <dt>dateFormat</dt>
 *   <dd>The {@link SimpleDateFormat} format to use for parsing the price
 *   date value into a Date object. Defaults to 
 *   {@link #DEFAULT_DATE_FORMAT}.</dd>
 * </dl>
 *
 * @author matt
 * @version $Revision$ $Date$
 */
public class DelimitedPriceDatumDataSource implements DatumDataSource<PriceDatum>,
		SettingSpecifierProvider {
	
	/** The default value for the {@code delimiter} property. */
	public static final String DEFAULT_DELIMITER = ",";
	
	/** The default value for the {@code connectionTimeout} property. */
	public static final int DEFAULT_CONNECTION_TIMEOUT = 15000;

	/** The default value for the {@code dateFormat} property. */
	public static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy HH:mm";
	
	/** The default value for the {@code url} property. */
	public static final String DEFAULT_URL = "http://www.electricityinfo.co.nz/comitFta/five_min_prices.download?INchoice=HAY&INdate=%1$td/%1$tm/%1$tY&INgip=ABY0111&INperiodfrom=1&INperiodto=50&INtype=Price";

	/** The default value for the {@code sourceId} property. */
	public static final Integer DEFAULT_SOURCE_ID_COLUMN = 0;

	/** The default value for the {@code priceColumn} property. */
	public static final int DEFAULT_PRICE_COLUMN = 4;

	/** The default value for the {@code skipLines} property. */
	public static final int DEFAULT_SKIP_LINES = 1;

	private static final int[] DEFAULT_DATE_TIME_COLUMNS = new int[] { 1, 3 };

	private final Logger log = LoggerFactory.getLogger(DelimitedPriceDatumDataSource.class);
	
	private static final Object MONITOR = new Object();
	private static MessageSource MESSAGE_SOURCE;

	private String url = DEFAULT_URL;
	private String delimiter = DEFAULT_DELIMITER;
	private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
	private int skipLines = DEFAULT_SKIP_LINES;
	private int[] dateTimeColumns = DEFAULT_DATE_TIME_COLUMNS;
	private int priceColumn = DEFAULT_PRICE_COLUMN;
	private Integer sourceIdColumn = DEFAULT_SOURCE_ID_COLUMN;
	private String dateFormat = DEFAULT_DATE_FORMAT;

	public Class<? extends PriceDatum> getDatumType() {
		return PriceDatum.class;
	}

	@Override
	public String toString() {
		String host = "";
		try {
			URL theUrl = getFormattedUrl();
			host = theUrl.getHost();
		} catch (Exception e) {
			host = "unknown";
		}
		return "DelimitedPriceDatumDataSource{" + host + "}";
	}

	public PriceDatum readCurrentDatum() {
		URL theUrl = getFormattedUrl();
		String dataRow = readDataRow(theUrl);
		String[] data = dataRow.split(this.delimiter);
		
		// get price date, either from single column or combination of multiple
		// which might occur if date and time are in different columns
		String dateTimeStr = null;
		if ( dateTimeColumns.length == 1 ) {
			dateTimeStr = data[dateTimeColumns[0]];
		} else {
			StringBuilder buf = new StringBuilder();
			for ( int idx : dateTimeColumns ) {
				if ( buf.length() > 0 ) {
					buf.append(' ');
				}
				buf.append(data[idx]);
			}
			dateTimeStr = buf.toString();
		}
		if ( log.isTraceEnabled() ) {
			log.trace("Parsing price date [" +dateTimeStr +']');
		}
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
		Date created;
		try {
			created = sdf.parse(dateTimeStr);
		} catch ( ParseException e ) {
			throw new RuntimeException(e);
		}
		
		// set the sourceId to the URL, or a column if sourceIdColumn configured
		String sourceId = theUrl.toExternalForm();
		if ( sourceIdColumn != null ) {
			sourceId = data[sourceIdColumn];
		}
		
		double price = Double.parseDouble(data[priceColumn]);

		PriceDatum datum = new PriceDatum(sourceId, price, null);
		datum.setCreated(created);
		return datum;
	}
	
	private URL getFormattedUrl() {
		String theUrl = String.format(this.url, new Date());
		try {
			return new URL(theUrl);
		} catch ( MalformedURLException e ) {
			throw new RuntimeException(e);
		}
	}
	
	private String readDataRow(URL theUrl) {
		BufferedReader resp = null;
		if ( log.isDebugEnabled() ) {
			log.debug("Requesting price data from [" +theUrl +']');
		}
		try {
			URLConnection conn = theUrl.openConnection();
			conn.setConnectTimeout(this.connectionTimeout);
			conn.setReadTimeout(this.connectionTimeout);
			conn.setRequestProperty("Accept", "text/*");
			
			resp = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			
			String str;
			int skipCount = this.skipLines;
			while ( (str = resp.readLine()) != null ) {
				if ( skipCount > 0 ) {
					skipCount--;
					continue;
				}
				break;
			}
			if ( log.isTraceEnabled() ) {
				log.trace("Found price data: "+ str);
			}
			return str;
		} catch ( IOException e ) {
			throw new RuntimeException(e);
		} finally {
			if ( resp != null ) {
				try {
					resp.close();
				} catch ( IOException e ) {
					// ignore this
					log.debug("Exception closing URL stream", e);
				}
			}
		}
	}

	
	@Override
	public String getSettingUID() {
		return "net.solarnetwork.node.price.delimited";
	}

	@Override
	public String getDisplayName() {
		return "Delimited energy price lookup";
	}

	@Override
	public MessageSource getMessageSource() {
		synchronized (MONITOR) {
			if ( MESSAGE_SOURCE == null ) {
				ResourceBundleMessageSource source = new ResourceBundleMessageSource();
				source.setBundleClassLoader(getClass().getClassLoader());
				source.setBasename(getClass().getName());
				MESSAGE_SOURCE = source;
			}
		}
		return MESSAGE_SOURCE;
	}

	@Override
	public List<SettingSpecifier> getSettingSpecifiers() {
		return Arrays.asList(
				(SettingSpecifier) new BasicTextFieldSettingSpecifier("url", DEFAULT_URL),
				(SettingSpecifier) new BasicTextFieldSettingSpecifier("delimiter",
						DEFAULT_DELIMITER),
				(SettingSpecifier) new BasicTextFieldSettingSpecifier("sourceIdColumn",
						DEFAULT_SOURCE_ID_COLUMN.toString()),
				(SettingSpecifier) new BasicTextFieldSettingSpecifier("priceColumn", String
						.valueOf(DEFAULT_PRICE_COLUMN)),
				(SettingSpecifier) new BasicTextFieldSettingSpecifier("dateTimeColumns", "1,3"),
				(SettingSpecifier) new BasicTextFieldSettingSpecifier("dateFormat",
						DEFAULT_DATE_FORMAT),
				(SettingSpecifier) new BasicTextFieldSettingSpecifier("skipLines", String
						.valueOf(DEFAULT_SKIP_LINES)));
	}

	public String getUrl() {
		return url;
	}
	
	public void setUrl(String url) {
		this.url = url;
	}
	
	public String getDelimiter() {
		return delimiter;
	}
	
	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}
	
	public int getConnectionTimeout() {
		return connectionTimeout;
	}
	
	public void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	
	public int getSkipLines() {
		return skipLines;
	}
	
	public void setSkipLines(int skipLines) {
		this.skipLines = skipLines;
	}
	
	public int[] getDateTimeColumns() {
		return dateTimeColumns;
	}
	
	public void setDateTimeColumns(int[] dateTimeColumns) {
		this.dateTimeColumns = dateTimeColumns;
	}
	
	public int getPriceColumn() {
		return priceColumn;
	}
	
	public void setPriceColumn(int priceColumn) {
		this.priceColumn = priceColumn;
	}
	
	public Integer getSourceIdColumn() {
		return sourceIdColumn;
	}
	
	public void setSourceIdColumn(Integer sourceIdColumn) {
		this.sourceIdColumn = sourceIdColumn;
	}
	
	public String getDateFormat() {
		return dateFormat;
	}
	
	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}
	
}
