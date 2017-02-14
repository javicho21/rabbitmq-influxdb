package influxdb;

import main.Log;
import rabbitmq.Payload;

import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;

/**
 * Publishes messages to InfluxDB.
 *
 * @author aaronzhang
 */
public class InfluxDBPublisher implements Observer {

    /**
     * Publisher builder.
     */
    public static class Builder {

        // Publisher fields
        private String url = "";
        private String username = "";
        private String password = "";
        private String dbName = "";
        private int pointsToFlush = 1000;
        private int millisToFlush = 5000;
        private long ping = 0;
        private Log log;

        /**
         * New builder.
         */
        public Builder() {

        }

        /**
         * Sets url.
         *
         * @param url url
         * @return this builder
         */
        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        /**
         * Sets username.
         *
         * @param username username
         * @return this builder
         */
        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets password.
         *
         * @param password password
         * @return this builder
         */
        public Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets database name.
         *
         * @param dbName database name
         * @return this builder
         */
        public Builder setDbName(String dbName) {
            this.dbName = dbName;
            return this;
        }

        /**
         * Sets number of points before flushing. Defaults to 1000.
         *
         * @param pointsToFlush points before flushing
         * @return this builder
         */
        public Builder setPointsToFlush(int pointsToFlush) {
            if (pointsToFlush > 0) {
                this.pointsToFlush = pointsToFlush;
            }
            return this;
        }

        /**
         * Sets number of milliseconds before flushing. Defaults to 5000.
         *
         * @param millisToFlush milliseconds before flushing
         * @return this builder
         */
        public Builder setMillisToFlush(int millisToFlush) {
            if (millisToFlush > 0) {
                this.millisToFlush = millisToFlush;
            }
            return this;
        }

        /**
         * Sets number of milliseconds between pings, or 0 to disable pings.
         *
         * @param ping ping interval, in milliseconds
         * @return this builder
         */
        public Builder setPing(long ping) {
            this.ping = ping;
            return this;
        }

        /**
         * Sets log. If not set, this object will not log messages.
         *
         * @param log log
         * @return this builder
         */
        public Builder setLog(Log log) {
            this.log = log;
            return this;
        }

        /**
         * Builds publisher.
         *
         * @return publisher
         */
        public InfluxDBPublisher build() {
            InfluxDBPublisher influx = new InfluxDBPublisher(
                url, username, password, dbName,
                pointsToFlush, millisToFlush, ping, log);
            if (log != null) {
                log.influxCreated(influx);
            }
            return influx;
        }
    }

    /**
     * InfluxDB connection.
     */
    private InfluxDB influxDB;

    /**
     * Guaranteed to flush after receiving this many points.
     */
    private final int pointsToFlush;

    /**
     * Guaranteed to flush after this many milliseconds.
     */
    private final int millisToFlush;

    /**
     * URL.
     */
    private final String url;

    /**
     * Username.
     */
    private final String username;

    /**
     * Password.
     */
    private final String password;

    /**
     * Database name.
     */
    private final String dbName;

    /**
     * Ping interval, in milliseconds.
     */
    private final long ping;

    /**
     * Log.
     */
    private final Log log;

    /**
     * Instantiates InfluxDB publisher with given parameters.
     *
     * @param url
     * @param username
     * @param password
     * @param dbName
     * @param pointsToFlush
     * @param millisToFlush
     * @param ping
     */
    private InfluxDBPublisher(String url, String username, String password,
        String dbName, int pointsToFlush, int millisToFlush, long ping,
        Log log) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.dbName = dbName;
        this.pointsToFlush = pointsToFlush;
        this.millisToFlush = millisToFlush;
        this.ping = ping;
        this.log = log;
        influxDB = InfluxDBFactory.connect(url, username, password);
        influxDB.enableBatch(
            pointsToFlush, millisToFlush, TimeUnit.MILLISECONDS);
    }

    /**
     * Start pinging. Stops pinging if an exception is thrown.
     */
    public void ping() {
        if (ping > 0) {
            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        influxDB.ping();
                    } catch (Exception e) {
                        timer.cancel();
                        if (log != null) {
                            log.influxPingError(InfluxDBPublisher.this, e);
                        }
                        // Attempt to reconnect
                        reconnect();
                        ping();
                    }
                }
            }, ping, ping);
        }
    }

    /**
     * Attempts to reconnect to InfluxDB.
     */
    private void reconnect() {
        for (int i = 0; i < 4; i++) {
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {

            }
            try {
                // Try to reconnect
                influxDB = InfluxDBFactory.connect(url, username, password);
                influxDB.enableBatch(
                    pointsToFlush, millisToFlush, TimeUnit.MILLISECONDS);
                influxDB.ping();
                log.influxReconnectSuccess(this);
                return;
            } catch (Exception e) {
                log.influxReconnectError(this, e);
            }
        }
        // Stop trying after 4 failed attempts
        System.exit(1);
    }

    /**
     * Publishes the payload.
     *
     * @param o unused
     * @param arg payload string
     */
    @Override
    public void update(Observable o, Object arg) {
        if (!(arg instanceof Payload)) {
            throw new IllegalArgumentException(
                "InfluxDBPublisher must be updated with payload");
        }
        Payload payload = (Payload) arg;
        Point point = Point
            .measurement(payload.getMetric())
            .time(payload.getTimestampValue(), payload.getTimestampUnit())
            .tag(payload.getTags())
            .fields(payload.getFields())
            .build();
        influxDB.write(dbName, "autogen", point);
        if (log != null) {
            log.influxWrote();
        }
    }

    /**
     * @return number of points before flushing
     */
    public int getPointsToFlush() {
        return pointsToFlush;
    }

    /**
     * @return number of milliseconds before flushing
     */
    public int getMillisToFlush() {
        return millisToFlush;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @return the database
     */
    public String getDbName() {
        return dbName;
    }

    /**
     * @return the ping interval, in milliseconds
     */
    public long getPing() {
        return ping;
    }

    @Override
    public String toString() {
        return String.format("[InfluxDBPublisher:%n"
            + "url=%s%n"
            + "db=%s%n"
            + "pointsToFlush=%d%n"
            + "millisToFlush=%d%n"
            + "ping=%d]", url, dbName, pointsToFlush, millisToFlush, ping);
    }
}
