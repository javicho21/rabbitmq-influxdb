package main;

import java.util.concurrent.TimeUnit;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDB.ConsistencyLevel;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

/**
 * InfluxDB test from GitHub.
 * 
 * @author aaronzhang
 */
public class Test {
    public static void main(String[] args) {
        InfluxDB influxDB = InfluxDBFactory.connect(
            "http://localhost:8086", "root", "root");
        String dbName = "aTimeSeries";
        influxDB.createDatabase(dbName);

        BatchPoints batchPoints = BatchPoints
            .database(dbName)
            .tag("async", "true")
            .retentionPolicy("default")
            .consistency(ConsistencyLevel.ALL)
            .build();
        Point point1 = Point.measurement("cpu")
            .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .field("idle", 90L)
            .field("user", 9L)
            .field("system", 1L)
            .build();
        Point point2 = Point.measurement("disk")
            .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .field("used", 80L)
            .field("free", 1L)
            .build();
        batchPoints.point(point1);
        batchPoints.point(point2);
        influxDB.write(batchPoints);
        Query query = new Query("SELECT idle FROM cpu", dbName);
        System.out.println(influxDB.query(query));
        influxDB.deleteDatabase(dbName);
    }
}