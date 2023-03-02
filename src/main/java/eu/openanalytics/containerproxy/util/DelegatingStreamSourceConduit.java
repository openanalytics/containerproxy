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
package eu.openanalytics.containerproxy.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.ReadReadyHandler;
import org.xnio.conduits.StreamSourceConduit;

public class DelegatingStreamSourceConduit implements StreamSourceConduit {

	private StreamSourceConduit delegate;
	private Consumer<byte[]> readListener;
	
	public DelegatingStreamSourceConduit(StreamSourceConduit delegate, Consumer<byte[]> readListener) {
		this.delegate = delegate;
		this.readListener = readListener;
	}

	@Override
	public void terminateReads() throws IOException {
		delegate.terminateReads();
	}

	@Override
	public boolean isReadShutdown() {
		return delegate.isReadShutdown();
	}

	@Override
	public void resumeReads() {
		delegate.resumeReads();
	}

	@Override
	public void suspendReads() {
		delegate.suspendReads();
	}

	@Override
	public void wakeupReads() {
		delegate.wakeupReads();
	}

	@Override
	public boolean isReadResumed() {
		return delegate.isReadResumed();
	}

	@Override
	public void awaitReadable() throws IOException {
		delegate.awaitReadable();
	}

	@Override
	public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
		delegate.awaitReadable(time, timeUnit);
	}

	@Override
	public XnioIoThread getReadThread() {
		return delegate.getReadThread();
	}

	@Override
	public void setReadReadyHandler(ReadReadyHandler handler) {
		delegate.setReadReadyHandler(handler);
	}

	@Override
	public XnioWorker getWorker() {
		return delegate.getWorker();
	}

	@Override
	public long transferTo(long position, long count, FileChannel target) throws IOException {
		return delegate.transferTo(position, count, target);
	}

	@Override
	public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
		return delegate.transferTo(count, throughBuffer, target);
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		if (readListener == null) {
			return delegate.read(dst);
		} else {
			int read = delegate.read(dst);
			ByteBuffer copy = dst.duplicate();
			copy.flip();
			byte[] data = new byte[copy.remaining()];
			copy.get(data);
			readListener.accept(data);
			return read;
		}
	}

	@Override
	public long read(ByteBuffer[] dsts, int offs, int len) throws IOException {
		return delegate.read(dsts, offs, len);
	}
	

}
