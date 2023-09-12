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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.memory;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.Seat;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MemorySeatStore implements ISeatStore {

    private final HashSet<String> unClaimSeatIds = new HashSet<>();

    private final Map<String, Seat> seats = new HashMap<>(); // seat id -> Seat

    @Override
    public synchronized void addSeat(Seat seat) {
        if (seats.containsKey(seat.getId())) {
            throw new IllegalArgumentException(String.format("Cannot add seat with id %s: seat already added", seat.getId()));
        }
        seats.put(seat.getId(), seat);
        if (seat.getDelegatingProxyId() == null) {
            unClaimSeatIds.add(seat.getId());
        }
    }

    @Override
    public Seat getSeat(String seatId) {
        return seats.get(seatId);
    }

    @Override
    public synchronized Optional<Seat> claimSeat(String claimingProxyId) {
        if (unClaimSeatIds.isEmpty()) {
            return Optional.empty();
        }
        String seatId = unClaimSeatIds.iterator().next();
        unClaimSeatIds.remove(seatId);
        Seat seat = seats.get(seatId);
        seat.claim(claimingProxyId);
        return Optional.of(seat);
    }

    @Override
    public synchronized void releaseSeat(String seatId) {
        Seat seat = seats.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException(String.format("Cannot release seat with id %s: seat not found in SeatStore", seatId));
        }
        seat.release();
    }

    @Override
    public void addToUnclaimedSeats(String seatId) {
        unClaimSeatIds.add(seatId);
    }

    @Override
    public boolean removeSeatsIfUnclaimed(Set<String> seatIds) {
        if (unClaimSeatIds.containsAll(seatIds)) {
            unClaimSeatIds.removeAll(seatIds);
            seatIds.forEach(seats::remove);
            return true;
        }
        return false;
    }

    @Override
    public synchronized Long getNumUnclaimedSeats() {
        return (long) unClaimSeatIds.size();
    }

    @Override
    public synchronized Long getNumClaimedSeats() {
        return (long) (seats.size() - unClaimSeatIds.size());
    }

}
