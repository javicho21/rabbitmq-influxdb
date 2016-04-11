package influxdb;

import rabbitmq.DefaultRabbitMQParser;
import rabbitmq.Payload;
import rabbitmq.RabbitMQParser;

import java.text.ParseException;
import java.util.Observable;
import java.util.Observer;

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
         * Builds publisher.
         *
         * @return publisher
         */
        public InfluxDBPublisher build() {
            return new InfluxDBPublisher(url, username, password, dbName,
                pointsToFlush, millisToFlush);
        }
    }

    /**
     * InfluxDB connection.
     */
    private final InfluxDB influxDB;

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
     * Parses RabbitMQ payload strings.
     */
    private static final RabbitMQParser parser = new DefaultRabbitMQParser();

    /**
     * New publisher with given url, username, password, and database name.
     *
     * @param url url
     * @param username username
     * @param password password
     * @param dbName database name
     */
    private InfluxDBPublisher(String url, String username, String password,
        String dbName, int pointsToFlush, int millisToFlush) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.dbName = dbName;
        this.pointsToFlush = pointsToFlush;
        this.millisToFlush = millisToFlush;
        influxDB = InfluxDBFactory.connect(url, username, password);
        influxDB.enableBatch(
            pointsToFlush, millisToFlush, TimeUnit.MILLISECONDS);
    }

    /**
     * Publishes the payload.
     *
     * @param o unused
     * @param arg payload string
     */
    @Override
    public void update(Observable o, Object arg) {
        if (!(arg instanceof String)) {
            throw new IllegalArgumentException(
                "InfluxDBPublisher must be updated with payload string");
        }
        try {
            Payload payload = parser.parse((String) arg);
            Point point = Point
                .measurement(payload.getMetric())
                .time(parseTimestamp(payload.getDatum("timestamp")),
                    TimeUnit.MILLISECONDS)
                .tag(payload.getTags())
                .field("value", payload.getDatum("value"))
                .build();
            influxDB.write(dbName, "default", point);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Parses timestamp string to timestamp in milliseconds.
     *
     * @param timestamp timestamp string
     * @return timestamp in milliseconds
     */
    private static long parseTimestamp(String timestamp) {
        long t = Long.parseLong(timestamp);
        if (t > 1_000_000_000_000L) {
            t /= 1000;
        }
        return t;
    }
}
