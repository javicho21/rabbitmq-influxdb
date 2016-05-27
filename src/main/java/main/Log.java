package main;

import influxdb.InfluxDBPublisher;
import rabbitmq.RabbitMQ;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Logs messages from RabbitMQ and InfluxDB.
 *
 * @author aaronzhang
 */
public class Log {

    private final String normalLog;
    private final int normalLogNum;
    private final int normalLogSize;
    private final long normalLogInterval;
    private final String errorLog;
    private final int errorLogNum;
    private final int errorLogSize;
    
    /**
     * Logger.
     */
    private final Logger logger;
    
    /**
     * Number of payloads read from RabbitMQ since last normal log entry.
     */
    private final AtomicLong rabbitRead = new AtomicLong(0);
    
    /**
     * Number of payloads backed up to RabbitMQ since last normal log entry.
     */
    private final AtomicLong rabbitBacked = new AtomicLong(0);
    
    /**
     * Number of erroneous payloads from RabbitMQ since last normal log entry.
     * More detailed error messages will appear in the error log.
     */
    private final AtomicLong rabbitErrors = new AtomicLong(0);
    
    /**
     * Number of points written to InfluxDB since last normal log entry.
     */
    private final AtomicLong influxWrote = new AtomicLong(0);

    /**
     * Logger builder.
     */
    public static class Builder {

        private String normalLog;
        private int normalLogNum;
        private int normalLogSize;
        private long normalLogInterval;
        private String errorLog;
        private int errorLogNum;
        private int errorLogSize;
        
        /**
         * Instantiates builder.
         */
        public Builder() {
            
        }
        
        /**
         * Sets normal log.  If not set, will not log success messages.
         * 
         * @param normalLog normal log
         * @return this builder
         */
        public Builder setNormalLog(String normalLog) {
            this.normalLog = normalLog;
            return this;
        }
        
        /**
         * Sets maximum number of normal log files.  Defaults to 1.
         * 
         * @param normalLogNum
         * @return this builder
         */
        public Builder setNormalLogNum(int normalLogNum) {
            this.normalLogNum = normalLogNum;
            return this;
        }
        
        /**
         * Sets maximum size of each normal log file, in bytes.  Defaults to
         * 1000000 (1 MB).
         * 
         * @param normalLogSize
         * @return this builder
         */
        public Builder setNormalLogSize(int normalLogSize) {
            this.normalLogSize = normalLogSize;
            return this;
        }
        
        /**
         * Sets time, in milliseconds, between normal log entries.  Defaults to
         * 5000 (5 seconds). (Error messages will be reported immediately.)
         * 
         * @param normalLogInterval
         * @return this builder
         */
        public Builder setNormalLogInterval(long normalLogInterval) {
            this.normalLogInterval = normalLogInterval;
            return this;
        }
        
        /**
         * Sets error log.  If not set, will not log error messages.
         * 
         * @param errorLog error log
         * @return this builder
         */
        public Builder setErrorLog(String errorLog) {
            this.errorLog = errorLog;
            return this;
        }
        
        /**
         * Sets maximum number of error log files.  Defaults to 1.
         * 
         * @param errorLogNum
         * @return this builder
         */
        public Builder setErrorLogNum(int errorLogNum) {
            this.errorLogNum = errorLogNum;
            return this;
        }
        
        /**
         * Sets maximum size of each error log file, in bytes.  Defaults to
         * 1000000 (1 MB).
         * 
         * @param errorLogSize
         * @return this builder
         */
        public Builder setErrorLogSize(int errorLogSize) {
            this.errorLogSize = errorLogSize;
            return this;
        }
        
        /**
         * Builds logger.
         * 
         * @return logger
         * @throws IOException if invalid normal log or error log files
         */
        public Log build() throws IOException {
            if (normalLogNum <= 0) {
                normalLogNum = 1;
            }
            if (normalLogSize <= 0) {
                normalLogSize = 1_000_000;
            }
            if (normalLogInterval <= 0) {
                normalLogInterval = 5_000;
            }
            if (errorLogNum <= 0) {
                errorLogNum = 1;
            }
            if (errorLogSize <= 0) {
                errorLogSize = 1_000_000;
            }
            Log log = new Log(
                normalLog, normalLogNum, normalLogSize, normalLogInterval,
                errorLog, errorLogNum, errorLogSize);
            log.normalLog();
            return log;
        }
    }

    /**
     * Instantiates logger.
     *
     * @param normalLog
     * @param normalLogNum
     * @param normalLogSize
     * @param errorLog
     * @param errorLogNum
     * @param errorLogSize
     * @throws IOException
     */
    private Log(String normalLog, int normalLogNum, int normalLogSize,
        long normalLogInterval,
        String errorLog, int errorLogNum, int errorLogSize) throws IOException {
        this.normalLog = normalLog;
        this.normalLogNum = normalLogNum;
        this.normalLogSize = normalLogSize;
        this.normalLogInterval = normalLogInterval;
        this.errorLog = errorLog;
        this.errorLogNum = errorLogNum;
        this.errorLogSize = errorLogSize;
        
        // Make a new logger for each instance of this class
        logger = Logger.getLogger(super.toString());
        logger.setLevel(Level.ALL);
        if (normalLog != null) {
            FileHandler normalHandler = new FileHandler(
                normalLog, normalLogSize, normalLogNum, true);
            normalHandler.setFormatter(new SimpleFormatter());
            normalHandler.setFilter(
                l -> l.getLevel().intValue() <= Level.INFO.intValue());
            logger.addHandler(normalHandler);
        }
        if (errorLog != null) {
            FileHandler errorHandler = new FileHandler(
                errorLog, errorLogSize, errorLogNum, true);
            errorHandler.setFormatter(new SimpleFormatter());
            errorHandler.setLevel(Level.WARNING);
            logger.addHandler(errorHandler);
        }
    }
    
    /**
     * Indicates that RabbitMQ has been instantiated.
     * 
     * @param rabbit RabbitMQ
     */
    public void rabbitCreated(RabbitMQ rabbit) {
        logger.log(Level.INFO, "RabbitMQ created:\n{0}", rabbit);
    }
    
    /**
     * Indicates that a payload has been successfully read from RabbitMQ.
     */
    public void rabbitRead() {
        rabbitRead.incrementAndGet();
    }
    
    /**
     * Indicates that a payload has been successfully backed up to RabbitMQ.
     */
    public void rabbitBacked() {
        rabbitBacked.incrementAndGet();
    }
    
    /**
     * Indicates that a RabbitMQ payload is erroneous.
     * 
     * @param payload the erroneous payload
     * @param e exception thrown from processing the payload
     * @param backed whether the erroneous message has been backed up
     */
    public void rabbitError(String payload, Exception e, boolean backed) {
        rabbitErrors.incrementAndGet();
        logger.warning(String.format("erroneous payload:%n"
            + "%s%n"
            + "exception thrown:%n"
            + "%s%s",
            payload, e, backed ? "\npayload moved to error queue" : ""));
    }
    
    /**
     * Indicates that connection to RabbitMQ has been lost.
     * 
     * @param rabbit RabbitMQ
     */
    public void rabbitPingError(RabbitMQ rabbit) {
        logger.log(Level.SEVERE, "lost connection to RabbitMQ:\n{0}", rabbit);
    }
    
    /**
     * Indicates successful reconnection to RabbitMQ.
     * 
     * @param rabbit RabbitMQ
     */
    public void rabbitReconnectSuccess(RabbitMQ rabbit) {
        logger.log(Level.INFO,
            "successfully reconnected to RabbitMQ:\n{0}", rabbit);
    }
    
    /**
     * Indicates error reconnecting to RabbitMQ.
     * 
     * @param rabbit RabbitMQ
     * @param e exception
     */
    public void rabbitReconnectError(RabbitMQ rabbit, Exception e) {
        logger.severe(String.format("could not reconnect to RabbitMQ:%n"
            + "%s%n"
            + "exception thrown:%n"
            + "%s", rabbit, e));
    }
    
    /**
     * Indicates that InfluxDB has been instantiated.
     * 
     * @param influx InfluxDB
     */
    public void influxCreated(InfluxDBPublisher influx) {
        logger.log(Level.INFO, "InfluxDB created:\n{0}", influx);
    }
    
    /**
     * Indicates that a payload has been successfully written to InfluxDB.
     */
    public void influxWrote() {
        influxWrote.incrementAndGet();
    }
    
    /**
     * Indicates that the connection to InfluxDB has been lost.
     * 
     * @param influx InfluxDB
     * @param e exception thrown from pinging InfluxDB
     */
    public void influxPingError(InfluxDBPublisher influx, Exception e) {
        logger.severe(String.format("could not ping InfluxDB:%n"
            + "%s%n"
            + "exception thrown:%n"
            + "%s", influx, e));
    }
    
    /**
     * Indicates successful reconnection to InfluxDB.
     * 
     * @param influx InfluxDB
     */
    public void influxReconnectSuccess(InfluxDBPublisher influx) {
        logger.log(Level.INFO,
            "successfully reconnected to InfluxDB:\n{0}", influx);
    }
    
    /**
     * Indicates error reconnecting to InfluxDB.
     * 
     * @param influx InfluxDB
     * @param e exception
     */
    public void influxReconnectError(InfluxDBPublisher influx, Exception e) {
        logger.severe(String.format("could not reconnect to InfluxDB:%n"
            + "%s%n"
            + "exception thrown:%n"
            + "%s", influx, e));
    }
    
    /**
     * Starts normal logging.
     */
    private void normalLog() {
        if (normalLog != null) {
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    long errors = rabbitErrors.getAndSet(0);
                    String msg = String.format(
                        "read: %d, backed: %d, written: %d, %s: %d",
                        rabbitRead.getAndSet(0),
                        rabbitBacked.getAndSet(0),
                        influxWrote.getAndSet(0),
                        errors == 0 ? "errors" : "ERRORS", errors);
                    logger.fine(msg);
                }
            }, normalLogInterval, normalLogInterval);
        }
    }

    /**
     * @return the normal log
     */
    public String getNormalLog() {
        return normalLog;
    }

    /**
     * @return the maximum number of normal log files
     */
    public int getNormalLogNum() {
        return normalLogNum;
    }

    /**
     * @return the maximum size of each normal log file, in bytes
     */
    public int getNormalLogSize() {
        return normalLogSize;
    }

    /**
     * @return the error log
     */
    public String getErrorLog() {
        return errorLog;
    }

    /**
     * @return the maximum number of error log files
     */
    public int getErrorLogNum() {
        return errorLogNum;
    }

    /**
     * @return the maximum size of each error log file, in bytes
     */
    public int getErrorLogSize() {
        return errorLogSize;
    }
    
    @Override
    public String toString() {
        return String.format("[RabbitMQInfluxDBLogger:%n"
            + "normalLog=%s%n"
            + "normalLogNum=%d%n"
            + "normalLogSize=%d%n"
            + "errorLog=%s%n"
            + "errorLogNum=%d%n"
            + "errorLogSize=%d]",
            normalLog, normalLogNum, normalLogSize,
            errorLog, errorLogNum, errorLogSize);
    }
}
