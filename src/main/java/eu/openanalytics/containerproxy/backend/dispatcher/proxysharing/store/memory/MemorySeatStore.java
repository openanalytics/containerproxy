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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.Seat;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemorySeatStore implements ISeatStore {

    // TODO synchronize on specId?
    private final ListMultimap<String, Seat> availableSeats = Multimaps.synchronizedListMultimap(ArrayListMultimap.create()); // SpecId -> Seat
    private final Map<String, Seat> claimedSeats = new ConcurrentHashMap<>(); // seat id -> Seat

    @Override
    public synchronized void addSeat(String specId, Seat seat) {
        availableSeats.put(specId, seat);
    }

    @Override
    public synchronized Optional<Seat> claimSeat(String specId, String claimingProxyId) {
        if (availableSeats.isEmpty()) {
            return Optional.empty();
        }
        Seat seat = availableSeats.get(specId).get(0);
        availableSeats.remove(specId, seat);
        seat.claim(claimingProxyId);
        claimedSeats.put(seat.getId(), seat);
        return Optional.of(seat);
    }

    @Override
    public synchronized void releaseSeat(String specId, String seatId) {
        Seat seat = claimedSeats.remove(seatId);
        seat.release();
        availableSeats.put(specId, seat);
    }

    @Override
    public synchronized Integer getNumSeatsAvailable(String specId) {
        return availableSeats.get(specId).size();
    }

    @Override
    public synchronized boolean removeSeats(String specId, List<String> seatIds) {
        for (String seatId : seatIds) {
            if (claimedSeats.containsKey(seatId)) {
                return false;
            }
        }
        availableSeats.get(specId).removeIf(s -> seatIds.contains(s.getId())); // TODO optimize
        return true;
    }

}
