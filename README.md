# rabbitmq-influxdb
RabbitMQ to InfluxDB

Package with:
mvn package

Run with:
./run [--verify-timestamp] [configFile]
--verify-timestamp: tries to infer the timestamp unit (milliseconds, microseconds, etc.) from the number of digits; if not given, assumes the timestamp is in nanoseconds
configFile: specifies the configuration file; if not given, checks the current directory for .rabbitmq-influxdb
