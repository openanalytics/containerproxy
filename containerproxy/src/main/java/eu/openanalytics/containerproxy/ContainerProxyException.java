package eu.openanalytics.containerproxy;

public class ContainerProxyException extends RuntimeException {

	private static final long serialVersionUID = 5221979016901962537L;

	public ContainerProxyException(String message) {
		super(message);
	}
	
	public ContainerProxyException(String message, Throwable cause) {
		super(message, cause);
	}

}
