package info.scheinfrei.log4j;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;

import info.scheinfrei.log4j.InfluxDbAppender;

/**
 * Basic test for setting appender properties.
 */
public class BasicTestSsl
{
	@Before
	public void setUp() throws Exception {
		// Programmatically set up out appender.
		Logger rootLogger = Logger.getRootLogger();
		Logger pkgLogger = rootLogger.getLoggerRepository().getLogger("info.scheinfrei.log4j");
		pkgLogger.setLevel(Level.INFO);
		InfluxDbAppender influxApp = new InfluxDbAppender();
		influxApp.setHost("127.0.0.1");
		influxApp.setPort(8086);
		influxApp.setSsl("yes");
		influxApp.setTruststore("C:\\TrustCerts.jks");
		influxApp.setAppName("unittest");
		influxApp.activateOptions();
		influxApp.setConsistencyLevelWrite("QUORUM");
		pkgLogger.addAppender(influxApp);
		
		pkgLogger.info("Testmessage");
	}

    @Test
    public void testSettingCorrectConsistencyLevels()
    {
        InfluxDbAppender influxApp = new InfluxDbAppender();
        influxApp.setConsistencyLevelWrite("QUORUM");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSettingWrongConsistencyLevel()
    {
        new InfluxDbAppender().setConsistencyLevelWrite("QIORUM");
    }

    @Test
    public void testThrowableSuccess() throws Exception
    {
        InfluxDbAppender appender = new InfluxDbAppender();
        LoggingEvent event = new LoggingEvent(BasicTestSsl.class.getName(),
                                              Logger.getLogger(BasicTestSsl.class),
                                              Level.WARN,
                                              "test 12",
                                              new Exception("boom"));
        appender.doAppend(event);
    }

    @Test
    public void testNoThrowableSuccess() throws Exception
    {
        InfluxDbAppender appender = new InfluxDbAppender();
        LoggingEvent event = new LoggingEvent(BasicTestSsl.class.getName(),
                                              Logger.getLogger(BasicTestSsl.class),
                                              Level.WARN,
                                              "test 12",
                                              null);
        appender.doAppend(event);
    }
}
