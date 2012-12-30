package org.phaidra.fedora.subscriber.errors;

public class ConfigException extends PhaidraFedoraSubscriberException {

	private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }
    
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }

}
