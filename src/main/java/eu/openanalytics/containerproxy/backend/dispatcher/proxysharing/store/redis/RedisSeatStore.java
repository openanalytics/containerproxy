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
package eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.redis;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.Seat;
import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.store.ISeatStore;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundListOperations;

import java.util.Optional;
import java.util.Set;

public class RedisSeatStore implements ISeatStore {

    private final BoundHashOperations<String, String, Seat> seatsOperations; // seat id -> Seat
    private final BoundListOperations<String, String> unClaimedSeatsIdsOperations; // list of seatIds

    public RedisSeatStore(BoundHashOperations<String, String, Seat> seatsOperations, BoundListOperations<String, String> unClaimedSeatsIdsOperations) {
        this.seatsOperations = seatsOperations;
        this.unClaimedSeatsIdsOperations = unClaimedSeatsIdsOperations;
    }

    @Override
    public void addSeat(Seat seat) {
        if (Boolean.TRUE.equals(seatsOperations.hasKey(seat.getId()))) {
            throw new IllegalArgumentException(String.format("Cannot add seat with id %s: seat already added", seat.getId()));
        }
        seatsOperations.put(seat.getId(), seat);
        if (seat.getClaimingProxyId() == null) {
            unClaimedSeatsIdsOperations.leftPush(seat.getId());
        }
    }

    @Override
    public Optional<Seat> claimSeat(String claimingProxyId) {
        // TODO enough locking?
        String seatId = unClaimedSeatsIdsOperations.leftPop();
        if (seatId == null) {
            return Optional.empty();
        }
        Seat seat = seatsOperations.get(seatId);
        if (seat == null) {
            throw new IllegalStateException("Claimed seat not found");
        }
        seat.claim(claimingProxyId);
        seatsOperations.put(seatId, seat);
        return Optional.of(seat);
    }

    @Override
    public void releaseSeat(String seatId) {
        Seat seat = seatsOperations.get(seatId);
        if (seat == null) {
            throw new IllegalArgumentException(String.format("Cannot release seat with id %s: seat not found in SeatStore", seatId));
        }
        seat.release();
        seatsOperations.put(seatId, seat);
        unClaimedSeatsIdsOperations.leftPush(seatId);
    }

    @Override
    public boolean removeSeats(Set<String> seatIds) {
        // TODO check whether all seats are unclaimed
        seatIds.forEach(s -> unClaimedSeatsIdsOperations.remove(1, s));
//        seatsOperations.delete(seatIds); // TODO broken
        return true;
    }

    @Override
    public Long getNumUnclaimedSeats() {
        return unClaimedSeatsIdsOperations.size();
    }

    @Override
    public Long getNumClaimedSeats() {
        return (long) seatsOperations.size() - unClaimedSeatsIdsOperations.size();
    }

}
