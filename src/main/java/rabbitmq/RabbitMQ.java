package rabbitmq;

import java.io.IOException;
import java.util.Observable;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * Consumes messages from RabbitMQ. This class is observable and observers will
 * be notified when new payloads are consumed.
 *
 * @author aaronzhang
 */
public class RabbitMQ extends Observable {

    /**
     * Host.
     */
    private String host;

    /**
     * Port.
     */
    private int port;

    /**
     * Username.
     */
    private String username;

    /**
     * Password.
     */
    private String password;

    /**
     * Virtual host.
     */
    private String virtualHost;

    /**
     * Queue to consume.
     */
    private String queue;

    /**
     * Name of backup queue, or {@code null} if queue should not be backed up.
     */
    private String backupQueue;

    /**
     * RabbitMQ builder.
     */
    public static class Builder {

        /**
         * RabbitMQ being constructed.
         */
        private final RabbitMQ internal;

        /**
         * New builder.
         */
        public Builder() {
            internal = new RabbitMQ();
        }

        /**
         * Sets host.
         *
         * @param host host
         * @return this builder
         */
        public Builder setHost(String host) {
            internal.host = host;
            return this;
        }

        /**
         * Sets port.
         *
         * @param port port
         * @return this builder
         */
        public Builder setPort(int port) {
            internal.port = port;
            return this;
        }

        /**
         * Sets username.
         *
         * @param username username
         * @return this builder
         */
        public Builder setUsername(String username) {
            internal.username = username;
            return this;
        }

        /**
         * Sets password.
         *
         * @param password password
         * @return this builder
         */
        public Builder setPassword(String password) {
            internal.password = password;
            return this;
        }

        /**
         * Sets virtual host.
         *
         * @param virtualHost virtual host
         * @return this builder
         */
        public Builder setVirtualHost(String virtualHost) {
            internal.virtualHost = virtualHost;
            return this;
        }

        /**
         * Sets queue to consume.
         *
         * @param queue queue
         * @return this builder
         */
        public Builder setQueue(String queue) {
            internal.queue = queue;
            return this;
        }

        /**
         * Sets backup queue
         *
         * @param backupQueue backup queue
         * @return this builder
         */
        public Builder setBackupQueue(String backupQueue) {
            internal.backupQueue = backupQueue;
            return this;
        }

        /**
         * Builds RabbitMQ.
         *
         * @return RabbitMQ
         */
        public RabbitMQ build() {
            return internal;
        }
    }

    /**
     * Instantiates RabbitMQ.
     */
    private RabbitMQ() {

    }

    /**
     * Starts consuming data from RabbitMQ.
     *
     * @throws IOException error connecting to RabbitMQ
     * @throws TimeoutException error connecting to RabbitMQ
     */
    public void consume() throws IOException, TimeoutException {
        // Open a connection with the given parameters
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);
        Connection connection;
        try {
            connection = factory.newConnection();
        } catch (IOException e) {
            throw new IOException("Couldn't connect to RabbitMQ");
        } catch (TimeoutException e) {
            throw new TimeoutException("Couldn't connect to RabbitMQ");
        }

        // Channel and appropriate queues
        Channel channel = connection.createChannel();
        channel.queueDeclarePassive(queue);
        if (backupQueue != null) {
            channel.queueDeclarePassive(backupQueue);
        }

        // Consumer
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body)
                throws IOException {
                // Publish to backup queue, if any
                if (backupQueue != null) {
                    channel.basicPublish("", backupQueue, null, body);
                }
                // Notify observers
                notifyObservers(new String(body));
            }
        };
        channel.basicConsume(queue, true, consumer);
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
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
     * @return the virtual host
     */
    public String getVirtualHost() {
        return virtualHost;
    }

    /**
     * @return the queue
     */
    public String getQueue() {
        return queue;
    }

    /**
     * @return the backup queue
     */
    public String getBackupQueue() {
        return backupQueue;
    }
}
