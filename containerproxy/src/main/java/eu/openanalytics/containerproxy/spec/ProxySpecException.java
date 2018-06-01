package eu.openanalytics.containerproxy.spec;

public class ProxySpecException extends RuntimeException {

	private static final long serialVersionUID = 331280631122317780L;

	public ProxySpecException(String message) {
		super(message);
	}
	
	public ProxySpecException(String message, Throwable cause) {
		super(message, cause);
	}

}
