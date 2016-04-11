package rabbitmq;

import java.text.ParseException;

/**
 * Parses strings into {@link Payload} objects.
 * 
 * @author aaronzhang
 */
@FunctionalInterface
public interface RabbitMQParser {
    Payload parse(String str) throws ParseException;
}