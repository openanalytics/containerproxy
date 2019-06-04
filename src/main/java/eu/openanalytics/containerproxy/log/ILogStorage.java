package eu.openanalytics.containerproxy.log;

import java.io.IOException;
import java.io.OutputStream;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

public interface ILogStorage {

	public void initialize() throws IOException;
	
	public String getStorageLocation();
	
	public OutputStream[] createOutputStreams(Proxy proxy) throws IOException;
	
	public String[] getLogs(Proxy proxy) throws IOException;

}
