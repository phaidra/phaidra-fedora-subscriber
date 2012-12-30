package org.phaidra.fedora.subscriber.errors;

public class FedoraObjectNotFoundException extends PhaidraFedoraSubscriberException {

	private static final long serialVersionUID = 1L;

    public FedoraObjectNotFoundException(String message) {
        super(message);
    }
    
    public FedoraObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
