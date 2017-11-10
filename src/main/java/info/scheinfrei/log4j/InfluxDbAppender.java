package info.scheinfrei.log4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;

import com.google.common.base.Joiner;

import okhttp3.OkHttpClient;

/**
 * Main class that uses InfluxDb to store log entries into.
 * 
 */
public class InfluxDbAppender extends AppenderSkeleton {
	// InfluxDb configuration
	private String host = "localhost";
	private int port = 8086; // for the http connection of the Line Protocol,
								// 8086 is default
	private String username = "root";
	private String password = "";
	private static final String ip = getIP();
	private static final String hostname = getHostName();
	private String ssl = "no";
	private String truststore = "truststore.jks";

	// Database/Measurement information
	private String databaseName = "Logging";
	private String measurement = "log_entries";
	private String appName = "default";
	private String retentionPolicy = "autogen";
	private ConsistencyLevel consistencyLevelWrite = ConsistencyLevel.ONE;

	// series tag and field names
	public static final String ID = "key";
	public static final String HOST_IP = "host_ip";
	public static final String HOST_NAME = "host_name";
	public static final String APP_NAME = "app_name";
	public static final String LOGGER_NAME = "logger_name";
	public static final String LEVEL = "level";
	public static final String CLASS_NAME = "class_name";
	public static final String FILE_NAME = "file_name";
	public static final String LINE_NUMBER = "line_number";
	public static final String METHOD_NAME = "method_name";
	public static final String MESSAGE = "message";
	public static final String NDC = "ndc";
	public static final String APP_START_TIME = "app_start_time";
	public static final String THREAD_NAME = "thread_name";
	public static final String THROWABLE_STR = "throwable_str_rep";
	public static final String TIMESTAMP = "log_timestamp";

	// connection state
	private InfluxDB influxDB;
	private Pong connection;

	public void activateOptions() {

		try {
			// Use basic connection Url for InfluxDBFactory so we can connect
			// via http/https
			// Future versions should also allow udp connection
			// possibility...depending on
			// the influxdb-java driver here.
			if (this.ssl != null && this.ssl.equals("yes")) {
				influxDB = InfluxDBFactory.connect(getConnectUrl(), getUsername(), getPassword(), getSslHttpClientBuilder());
			} else
				influxDB = InfluxDBFactory.connect(getConnectUrl(), getUsername(), getPassword());

			// Create the database if not exists
			if (!influxDB.describeDatabases().contains(getDatabaseName())) {
				influxDB.createDatabase(getDatabaseName());
			}
		} catch (Exception e) {
			errorHandler.error("Error setting up InfluxDb logging Database at " + getConnectUrl() + ": " + e);

		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void append(LoggingEvent event) {
		// If this Appender has been closed or if there is no
		// connection to service, just return.
		if (influxDB == null) {
			return;
		}

		connection = influxDB.ping();

		System.out.println(connection.getVersion());

		if (connection.getVersion() == "") {
			return;
		}

		// Prepare some data
		String[] throwableStrs = event.getThrowableStrRep();

		LocationInfo locInfo = event.getLocationInformation();

		// create a measurement point
		Point eventpoint = Point.measurement(getMeasurement()).time(new Long(event.getTimeStamp()), TimeUnit.MILLISECONDS)

				.tag(HOST_IP, ip).tag(HOST_NAME, hostname).tag(APP_NAME, appName).tag(LOGGER_NAME, event.getLoggerName())
				.tag(LEVEL, event.getLevel().toString())

				.addField(CLASS_NAME, locInfo == null ? "" : locInfo.getClassName())
				.addField(FILE_NAME, locInfo == null ? "" : locInfo.getFileName())
				.addField(LINE_NUMBER, locInfo == null ? "" : locInfo.getLineNumber())
				.addField(METHOD_NAME, locInfo == null ? "" : locInfo.getMethodName())

				.addField(MESSAGE, event.getRenderedMessage()).addField(NDC, event.getNDC() == null ? "" : event.getNDC())
				.addField(APP_START_TIME, new Long(LoggingEvent.getStartTime())).addField(THREAD_NAME, event.getThreadName())
				.addField(THROWABLE_STR, throwableStrs == null ? "" : Joiner.on(", ").join(throwableStrs)).build();

		try {
			// write point to InfluxDb
			influxDB.write(getDatabaseName(), getRetentionPolicy(), eventpoint);

		} catch (Exception e) {
			LogLog.error("Error ", e);
			errorHandler.error("Error writing point to InfluxDb logging Database at " + getConnectUrl() + ": " + e);

		}

	}

	/**
	 * {@inheritDoc}
	 */
	public void close() {
		influxDB = null;

	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.apache.log4j.Appender#requiresLayout()
	 */
	public boolean requiresLayout() {
		return false;
	}

	private String getConnectUrl() {
		String connectUrl = "http://" + getHost() + ":" + getPort();
		if (this.ssl.equals("yes")) {
			connectUrl = "https://" + getHost() + ":" + getPort();
		}
		return connectUrl;
	}

	//
	// Boilerplate from here on out
	//

	public String getDatabaseName() {
		return databaseName;
	}

	public void setDatabaseName(String databaseName) {
		this.databaseName = databaseName;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = unescape(username);
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = unescape(password);
	}

	public String getMeasurement() {
		return measurement;
	}

	public void setMeasurement(String measurement) {
		this.measurement = measurement;
	}

	public String getRetentionPolicy() {
		return retentionPolicy;
	}

	public void setRetentionPolicy(String policy) {
		retentionPolicy = unescape(policy);
	}

	public String getConsistencyLevelWrite() {
		return consistencyLevelWrite.toString();
	}

	public void setConsistencyLevelWrite(String consistencyLevelWrite) {
		try {
			this.consistencyLevelWrite = ConsistencyLevel.valueOf(unescape(consistencyLevelWrite));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Consistency level " + consistencyLevelWrite + " wasn't found. Available levels: "
					+ Joiner.on(", ").join(ConsistencyLevel.values()));
		}
	}

	public String getTruststore() {
		return truststore;
	}

	public void setTruststore(String truststore) {
		this.truststore = truststore;
	}

	public String getSsl() {
		return ssl;
	}

	public void setSsl(String ssl) {
		this.ssl = ssl;
	}

	public String getAppName() {
		return appName;
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	private static String getHostName() {
		String hostname = "unknown";

		try {
			InetAddress addr = InetAddress.getLocalHost();
			hostname = addr.getHostName();
		} catch (Throwable t) {

		}
		return hostname;
	}

	private static String getIP() {
		String ip = "unknown";

		try {
			InetAddress addr = InetAddress.getLocalHost();
			ip = addr.getHostAddress();
		} catch (Throwable t) {

		}
		return ip;
	}

	/**
	 * Strips leading and trailing '"' characters
	 * 
	 * @param b
	 *            - string to unescape
	 * @return String - unexspaced string
	 */
	private static String unescape(String b) {
		if (b.charAt(0) == '\"' && b.charAt(b.length() - 1) == '\"')
			b = b.substring(1, b.length() - 1);
		return b;
	}

	private OkHttpClient.Builder getSslHttpClientBuilder() {
		TrustManagerFactory tmFac;
		OkHttpClient.Builder httpClient = new OkHttpClient.Builder();

		try {
			FileInputStream tStoreIS = new FileInputStream(new File(truststore));
			KeyStore TrustStore = KeyStore.getInstance("jks");
			TrustStore.load(tStoreIS, null);
			tmFac = TrustManagerFactory.getInstance("SunX509");
			tmFac.init(TrustStore);
			SSLContext ctx = SSLContext.getInstance("TLS");
			ctx.init(null, tmFac.getTrustManagers(), null);
			SSLSocketFactory socketFactory = ctx.getSocketFactory();
			httpClient.sslSocketFactory(socketFactory, (X509TrustManager) tmFac.getTrustManagers()[0]);
		} catch (NoSuchAlgorithmException | KeyManagementException | NullPointerException | CertificateException | IOException
				| KeyStoreException e) {
			errorHandler.error("Error createing SSL socket " + getConnectUrl() + ": " + e);
		}
		return httpClient;
	}

}
