package main;

import config.Configuration;
import influxdb.InfluxDBPublisher;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.TimeoutException;
import rabbitmq.RabbitMQ;

/**
 * Runs application.
 * 
 * @author aaronzhang
 */
public class Main {
    
    /**
     * Usage error message.
     */
    private static final String USAGE = "Usage: ./run [configurationFile]";
    
    /**
     * Runs application.  If an argument is provided, the argument will be used
     * to find the configuration file.  Otherwise, search the current directory
     * for the configuration file
     * 
     * @param args arguments
     * @throws IOException if error reading configuration file
     * @throws ParseException if error parsing configuration file
     * @throws TimeoutException if error consuming from RabbitMQ
     */
    public static void main(String[] args) throws IOException, ParseException,
        TimeoutException {
        if (args.length > 1) {
            System.err.println(USAGE);
            System.exit(1);
        }
        // Read configuration file
        Configuration config = new Configuration(
            args.length == 0 ? ".rabbitmq-influxdb" : args[0]);
        config.read();
        // Build RabbitMQ
        RabbitMQ rabbitMQ = new RabbitMQ.Builder()
            .setHost(config.getOrDefault("RABBITMQ_HOST", "localhost"))
            .setPort(Integer.parseInt(
                config.getOrDefault("RABBITMQ_PORT", "5672")))
            .setUsername(config.get("RABBITMQ_USERNAME"))
            .setPassword(config.get("RABBITMQ_PASSWORD"))
            .setVirtualHost(config.getOrDefault("RABBITMQ_VIRTUAL_HOST", "/"))
            .setQueue(config.get("RABBITMQ_QUEUE"))
            .setBackupQueue(config.get("RABBITMQ_BACKUP_QUEUE"))
            .build();
        // Build InfluxDB
        InfluxDBPublisher influxDB = new InfluxDBPublisher.Builder()
            .setUrl(config.get("INFLUXDB_URL"))
            .setUsername(config.get("INFLUXDB_USERNAME"))
            .setPassword(config.get("INFLUXDB_PASSWORD"))
            .setDbName(config.get("INFLUXDB_DBNAME"))
            .setPointsToFlush(Integer.parseInt(
                config.getOrDefault("INFLUXDB_POINTS_FLUSH", "1000")))
            .setMillisToFlush(Integer.parseInt(
                config.getOrDefault("INFLUXDB_MILLIS_FLUSH", "5000")))
            .build();
        // Setup communication between RabbitMQ and InfluxDB
        rabbitMQ.addObserver(influxDB);
        // Consume messages from RabbitMQ
        rabbitMQ.consume();
    }
}