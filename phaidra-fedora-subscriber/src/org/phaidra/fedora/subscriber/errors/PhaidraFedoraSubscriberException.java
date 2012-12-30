package org.phaidra.fedora.subscriber.errors;

import java.util.Date;

/**
 * the most general exception for the subscriber
 */
public class PhaidraFedoraSubscriberException extends java.rmi.RemoteException {

	private static final long serialVersionUID = 1L;

    public PhaidraFedoraSubscriberException(String message) {
        super(new Date()+" "+message);
    }
    
    public PhaidraFedoraSubscriberException(String message, Throwable cause) {
        super(new Date()+" "+message, cause);
    }
    
}