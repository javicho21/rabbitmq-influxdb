package rabbitmq;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A payload from RabbitMQ.
 */
public class Payload {

    /**
     * Metric.
     */
    private String metric = "";

    /**
     * Tags.
     */
    private final Map<String, String> tags = new HashMap<>();

    /**
     * Data.
     */
    private final Map<String, String> data = new HashMap<>();

    /**
     * Payload builder.
     */
    public static class Builder {

        /**
         * Payload being constructed.
         */
        private final Payload internal;

        /**
         * New builder.
         */
        public Builder() {
            internal = new Payload();
        }

        /**
         * Sets metric.
         *
         * @param metric metric
         * @return this builder
         */
        public Builder setMetric(String metric) {
            internal.metric = metric;
            return this;
        }

        /**
         * Adds tag.
         *
         * @param key key
         * @param value value
         * @return this builder
         */
        public Builder addTag(String key, String value) {
            internal.tags.put(key, value);
            return this;
        }

        /**
         * Adds data.
         *
         * @param key key
         * @param value value
         * @return this builder
         */
        public Builder addData(String key, String value) {
            internal.data.put(key, value);
            return this;
        }

        /**
         * Builds payload.
         *
         * @return payload
         */
        public Payload build() {
            return internal;
        }
    }

    /**
     * New payload.
     */
    private Payload() {

    }

    /**
     * @return the metric
     */
    public String getMetric() {
        return metric;
    }

    /**
     * @return the tags
     */
    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(tags);
    }
    
    /**
     * Gets value of tag.
     * 
     * @param key key
     * @return value
     */
    public String getTag(String key) {
        return tags.get(key);
    }

    /**
     * @return the data
     */
    public Map<String, String> getData() {
        return Collections.unmodifiableMap(data);
    }
    
    /**
     * Gets value of field.
     * 
     * @param key key
     * @return value
     */
    public String getDatum(String key) {
        return data.get(key);
    }
}
