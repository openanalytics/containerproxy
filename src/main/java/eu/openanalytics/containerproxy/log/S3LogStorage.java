/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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

//TODO Optimize flushing behaviour
public class S3LogStorage extends AbstractLogStorage {


	private String bucketName;
	private String bucketPath;
	private boolean enableSSE;

	private final Logger log = LogManager.getLogger(S3LogStorage.class);

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
	}

	@Override
	public LogStreams createOutputStreams(Proxy proxy) throws IOException {
		LogPaths paths = getLogs(proxy);
		// TODO kubernetes never flushes. So perform timed flushes, and also flush upon container shutdown
		return new LogStreams(
				new BufferedOutputStream(new S3OutputStream(bucketPath + paths.getStdout().getFileName().toString()), 1024 * 1024),
				new BufferedOutputStream(new S3OutputStream(bucketPath + paths.getStderr().getFileName().toString()), 1024 * 1024)
		);
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

	private byte[] getContent(String key) throws IOException {
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
