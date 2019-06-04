package eu.openanalytics.containerproxy.log;

import java.io.OutputStream;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

public class NoopLogStorage extends AbstractLogStorage {

	@Override
	public void initialize() {
		// Do nothing.
	}
	
	@Override
	public OutputStream[] createOutputStreams(Proxy proxy) {
		return null;
	}
	
	@Override
	public String[] getLogs(Proxy proxy) {
		return null;
	}

}
