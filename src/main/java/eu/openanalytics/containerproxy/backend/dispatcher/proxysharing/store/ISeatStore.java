/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.Seat;

import java.util.Optional;
import java.util.Set;

public interface ISeatStore {

    void addSeat(Seat seat);

    Seat getSeat(String seatId);

    Optional<Seat> claimSeat(String claimingProxyId);

    void releaseSeat(String seatId);

    void addToUnclaimedSeats(String seatId);

    boolean removeSeatsIfUnclaimed(Set<String> seatIds);

    Long getNumUnclaimedSeats();

    Long getNumClaimedSeats();

    Long getNumSeats();

    void removeSeatInfo(String seatId);
}
