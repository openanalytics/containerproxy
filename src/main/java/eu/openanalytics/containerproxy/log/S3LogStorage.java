/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.log;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.util.ProxyHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores logs in S3. Because S3 requests come with a cost and in order to reduce CPU usage, this class buffers the logs
 * and only write the logs to S3 either every 10 seconds or when the buffer reaches 1MB.
 */
public class S3LogStorage extends AbstractLogStorage {

	private String bucketName;
	private String bucketPath;
	private boolean enableSSE;

	private final Logger log = LogManager.getLogger(S3LogStorage.class);

	private ConcurrentHashMap<String, LogStreams> proxyStreams = ProxyHashMap.create();

	private S3Client s3Client;

	@Override
	public void initialize() throws IOException {
		super.initialize();
		S3ClientBuilder s3ClientBuilder = S3Client.builder();

		String accessKey = environment.getProperty("proxy.container-log-s3-access-key");
		String accessSecret = environment.getProperty("proxy.container-log-s3-access-secret");

		if (accessKey != null && accessSecret != null) {
			AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
					accessKey,
					accessSecret);
			s3ClientBuilder.credentialsProvider(StaticCredentialsProvider.create(awsCreds));
		}

		String endpoint = environment.getProperty("proxy.container-log-s3-endpoint");
		if (endpoint != null) {
			s3ClientBuilder.endpointOverride(URI.create(endpoint));
		}

		s3ClientBuilder.serviceConfiguration(s -> s.pathStyleAccessEnabled(true));

		s3Client = s3ClientBuilder.build();
		enableSSE = environment.getProperty("proxy.container-log-s3-sse", Boolean.class, false);

		String subPath = containerLogPath.substring("s3://".length()).trim();
		if (subPath.endsWith("/")) subPath = subPath.substring(0, subPath.length() - 1);

		int bucketPathIndex = subPath.indexOf("/");
		if (bucketPathIndex == -1) {
			bucketName = subPath;
			bucketPath = "";
		} else {
			bucketName = subPath.substring(0, bucketPathIndex);
			bucketPath = subPath.substring(bucketPathIndex + 1) + "/";
		}

		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				flushAllStreams();
			}
		}, 10000, 10000);
	}

	/**
	 * Creates OutputStreams for the given Proxy object.
	 *
	 * <p>
	 *     The {@link S3OutputStream} is wrapped into a {@link BufferedOutputStream} with a buffer of 1MB.
	 *     When this buffer is full, it will get flushed and the logs are written to S3.
	 *     The {@link BufferedOutputStream} is stored in this class and a timer calls the flush method every 10 seconds.
	 *     Therefore, the latest logs (if any) are written to S3 every 10 seconds.
	 *     Finally, the {@link BufferedOutputStream} are wrapped in {@link IgnoreFlushOutputStream} streams, such that
	 *     the flush method is ignored. This is important, since some container backends (e.g. Docker) flushes the logs
	 *     for ever write, which would mean we re-upload the log file to S3 for every write.
	 * </p>
	 *
	 * @param proxy the proxy to create outputstreams for
	 * @return the streams for this proxy
	 */
	@Override
	public LogStreams createOutputStreams(Proxy proxy) {
		LogPaths paths = getLogs(proxy);
		BufferedOutputStream stdout = new BufferedOutputStream(new S3OutputStream(bucketPath + paths.getStdout().getFileName().toString()), 1024 * 1024);
		BufferedOutputStream stderr = new BufferedOutputStream(new S3OutputStream(bucketPath + paths.getStderr().getFileName().toString()), 1024 * 1024);
		proxyStreams.put(proxy.getId(), new LogStreams(stdout, stderr));
		return new LogStreams(new IgnoreFlushOutputStream(stdout), new IgnoreFlushOutputStream(stderr));
	}

	@Override
	public void stopService() {
		super.stopService();
		proxyStreams = ProxyHashMap.create();
	}

	private void doUpload(String key, byte[] bytes) throws IOException {
        byte[] bytesToUpload = bytes;

        byte[] originalBytes = getContent(key);
        if (originalBytes != null) {
            bytesToUpload = Arrays.copyOf(originalBytes, originalBytes.length + bytes.length);
            System.arraycopy(bytes, 0, bytesToUpload, originalBytes.length, bytes.length);
        }

		if (log.isDebugEnabled()) log.debug(String.format("Writing log file to S3 [size: %d] [path: %s]", bytesToUpload.length, key));

        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key);

		if (enableSSE) {
			builder.serverSideEncryption(ServerSideEncryption.AES256);
		}

        try {
             s3Client.putObject(builder.build(), RequestBody.fromBytes(bytesToUpload));
        } catch (S3Exception e) {
            throw new IOException(e);
        }
	}

	private byte[] getContent(String key) {
		try {
			ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
					GetObjectRequest.builder()
							.bucket(bucketName)
							.key(key)
							.build());

			return object.asByteArray();
		} catch (NoSuchKeyException e) {
			return null;
		}
	}

	/**
	 * Flushes all streams of all proxies.
	 */
	private void flushAllStreams() {
		for (LogStreams streams : proxyStreams.values()) {
			try {
				streams.getStdout().flush();
			} catch (IOException e) {
				log.error("Failed to flush S3 log stream", e);
			}
			try {
				streams.getStderr().flush();
			} catch (IOException e) {
				log.error("Failed to flush S3 log stream", e);
			}
		}
	}

	/**
	 * A {@link OutputStream} that wraps a {@link BufferedOutputStream} and ignores any calls to {@link #flush()}.
	 */
	private static class IgnoreFlushOutputStream extends OutputStream {

		private final BufferedOutputStream bufferedOutputStream;

		public IgnoreFlushOutputStream(BufferedOutputStream bufferedOutputStream) {
			this.bufferedOutputStream = bufferedOutputStream;
		}

		@Override
		public void write(int i) throws IOException {
			bufferedOutputStream.write(i);

		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			bufferedOutputStream.write(b, off, len);
		}

		@Override
		public void flush() {
			// ignore external flush requests
		}

	}

	private class S3OutputStream extends OutputStream {

		private final String s3Key;

		public S3OutputStream(String s3Key) {
			this.s3Key = s3Key;
		}

		@Override
		public void write(int b) throws IOException {
			// Warning: highly inefficient. Always write arrays.
			byte[] bytesToCopy = new byte[] { (byte) b };
			write(bytesToCopy, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			byte[] bytesToCopy = new byte[len];
			System.arraycopy(b, off, bytesToCopy, 0, len);
			doUpload(s3Key, bytesToCopy);
		}
	}
}
