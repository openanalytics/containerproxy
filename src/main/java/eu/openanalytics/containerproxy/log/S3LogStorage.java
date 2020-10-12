/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

//TODO Optimize flushing behaviour
public class S3LogStorage extends AbstractLogStorage {

	private AmazonS3 s3;
	private TransferManager transferMgr;
	
	private String bucketName;
	private String bucketPath;
	private boolean enableSSE;

	private Logger log = LogManager.getLogger(S3LogStorage.class);
	
	@Override
	public void initialize() throws IOException {
		super.initialize();

		String accessKey = environment.getProperty("proxy.container-log-s3-access-key");
		String accessSecret = environment.getProperty("proxy.container-log-s3-access-secret");
		String endpoint = environment.getProperty("proxy.container-log-s3-endpoint", "https://s3-eu-west-1.amazonaws.com");
		enableSSE = Boolean.valueOf(environment.getProperty("proxy.container-log-s3-sse", "false"));
		
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
		
		s3 = AmazonS3ClientBuilder.standard()
				.withEndpointConfiguration(new EndpointConfiguration(endpoint, null))
				.enablePathStyleAccess()
				.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, accessSecret)))
				.build();
		transferMgr = TransferManagerBuilder.standard()
				.withS3Client(s3)
				.build();
	}
	
	@Override
	public OutputStream[] createOutputStreams(Proxy proxy) throws IOException {
		String[] paths = getLogs(proxy);
		OutputStream[] streams = new OutputStream[2];
		for (int i = 0; i < streams.length; i++) {
			String fileName = paths[i].substring(paths[i].lastIndexOf("/") + 1);
			// TODO kubernetes never flushes. So perform timed flushes, and also flush upon container shutdown
			streams[i] = new BufferedOutputStream(new S3OutputStream(bucketPath + fileName), 1024*1024);
		}
		return streams;
	}
	
	private void doUpload(String key, byte[] bytes) throws IOException {
		byte[] bytesToUpload = bytes;
		
		byte[] originalBytes = getContent(key);
		if (originalBytes != null) {
			bytesToUpload = Arrays.copyOf(originalBytes, originalBytes.length + bytes.length);
			System.arraycopy(bytes, 0, bytesToUpload, originalBytes.length, bytes.length);
		}
		
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(bytesToUpload.length);
		if (enableSSE) metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
		
		if (log.isDebugEnabled()) log.debug(String.format("Writing log file to S3 [size: %d] [path: %s]", bytesToUpload.length, key));
		
		InputStream bufferedInput = new BufferedInputStream(new ByteArrayInputStream(bytesToUpload), 20*1024*1024);
		try {
			transferMgr.upload(bucketName, key, bufferedInput, metadata).waitForCompletion();
		} catch (AmazonClientException | InterruptedException e) {
			throw new IOException(e);
		}
	}
	
	private byte[] getContent(String key) throws IOException {
		if (s3.doesObjectExist(bucketName, key)) {
			S3Object o = s3.getObject(bucketName, key);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (InputStream in = o.getObjectContent()) {
				byte[] buffer = new byte[40*1024];
				int len = 0;
				while ((len = in.read(buffer)) > 0) {
					out.write(buffer, 0, len);
				}
			}
			return out.toByteArray();
		} else {
			return null;
		}
	}
	
	private class S3OutputStream extends OutputStream {
		
		private String s3Key;
		
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
