package eu.openanalytics.containerproxy.log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

public class FileLogStorage extends AbstractLogStorage {

	@Override
	public void initialize() throws IOException {
		super.initialize();
		Files.createDirectories(Paths.get(containerLogPath));
	}
	
	@Override
	public OutputStream[] createOutputStreams(Proxy proxy) throws IOException {
		String[] paths = getLogs(proxy);
		return new OutputStream[] {
				new FileOutputStream(paths[0]),
				new FileOutputStream(paths[1])
		};
	}
	
}
