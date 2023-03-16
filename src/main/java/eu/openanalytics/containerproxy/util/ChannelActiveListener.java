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

/**
 * A listener that keeps track of whether a channel is active.
 *
 */
public class ChannelActiveListener implements Runnable {

	private long lastWrite = 0;
	
	@Override
	public void run() {
		lastWrite = System.currentTimeMillis();
	}
	
	/**
	 * Checks whether the channel was active in the provided period.
	 */
	public boolean isActive(long period) {
		long diff = System.currentTimeMillis() - lastWrite;
		
		// make sure the period is at least 5 seconds
		// this ensures that when the socket is active, the ping is delayed for at least 5 seconds
		if (period < 5000) {
			period = 5000;
		}

		if (diff <= period) {
			return true;
		}
		return false;
	}

}
