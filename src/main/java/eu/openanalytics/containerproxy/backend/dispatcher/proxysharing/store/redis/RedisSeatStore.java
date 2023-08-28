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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RedisSeatStore implements ISeatStore {

    private final BoundHashOperations<String, String, Seat> seatsOperations; // seat id -> Seat
    private final BoundSetOperations<String, String> unClaimedSeatsIdsOperations; // list of seatIds
    private final RedisTemplate<String, String> unClaimedSeatsIdsTemplate;
    private final String key;

    public RedisSeatStore(BoundHashOperations<String, String, Seat> seatsOperations, BoundSetOperations<String, String> unClaimedSeatsIdsOperations, RedisTemplate<String, String> unClaimedSeatsIdsTemplate, String key) {
        this.seatsOperations = seatsOperations;
        this.unClaimedSeatsIdsOperations = unClaimedSeatsIdsOperations;
        this.unClaimedSeatsIdsTemplate = unClaimedSeatsIdsTemplate;
        this.key = key;
    }

    @Override
    public void addSeat(Seat seat) {
        if (Boolean.TRUE.equals(seatsOperations.hasKey(seat.getId()))) {
            throw new IllegalArgumentException(String.format("Cannot add seat with id %s: seat already added", seat.getId()));
        }
        seatsOperations.put(seat.getId(), seat);
        if (seat.getClaimingProxyId() == null) {
            unClaimedSeatsIdsOperations.add(seat.getId());
        }
    }

    @Override
    public Optional<Seat> claimSeat(String claimingProxyId) {
        String seatId = unClaimedSeatsIdsOperations.pop();
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
        unClaimedSeatsIdsOperations.add(seatId);
    }

    @Override
    public boolean removeSeatsIfUnclaimed(Set<String> seatIds) {
        if (unClaimedSeatsIdsTemplate.execute(new UnclaimedSeatRemover(key, seatIds))) {
            seatsOperations.delete(seatIds.toArray());
            return true;
        }
        return false;
    }

    @Override
    public Long getNumUnclaimedSeats() {
        return unClaimedSeatsIdsOperations.size();
    }

    @Override
    public Long getNumClaimedSeats() {
        return (long) seatsOperations.size() - unClaimedSeatsIdsOperations.size();
    }

    public static class SeatClaimedDuringRemovalException extends RuntimeException {

    }

    private static class UnclaimedSeatRemover implements SessionCallback<Boolean> {

        private final String key;
        private final Object[] seatIds;
        private final Logger logger = LoggerFactory.getLogger(getClass());

        public UnclaimedSeatRemover(String key, Set<String> seatIds) {
            this.key = key;
            this.seatIds = seatIds.toArray();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <K, V> Boolean execute(@Nonnull RedisOperations<K, V> operations) throws DataAccessException {
            return internalExecute((RedisOperations<String, String>)operations);
        }

        private Boolean internalExecute(@Nonnull RedisOperations<String, String> operations) throws DataAccessException {
            for (int i = 0; i < 5; i++) {
                BoundSetOperations<String, String> ops = operations.boundSetOps(key);

                operations.watch(key);

                Long numUnclaimedSeats = ops.size();
                if (numUnclaimedSeats == null) {
                    return false;
                }

                Map<Object, Boolean> response = ops.isMember(seatIds);
                if (response == null) {
                    return false;
                }

                if (response.containsValue(false)) {
                    logger.debug("Not all seats are unclaimed, not removing seats.");
                    // seats are claimed -> don't try again
                    return false;
                }
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // seats are unclaimed, try to remove
                operations.multi();
                ops.remove(seatIds);
                var res = operations.exec();
                if (!res.isEmpty() && (Long) res.get(0) == seatIds.length) {
                    logger.debug("Seats removed successfully");
                    // seats were removed successfully
                    return true;
                }
                // failed to remove seats (a seat was claimed or unclaimed in the meantime)
                Long numUnclaimedSeats2 = ops.size();
                if (numUnclaimedSeats2 == null) {
                    return false;
                }
                if (numUnclaimedSeats2 < numUnclaimedSeats) {
                    logger.debug("Seats was claimed in meantime");
                    // a seat was claimed in the mean-time, stop removing seats
                    throw new SeatClaimedDuringRemovalException();
                }
                // a seat was released in the mean-time, try again
            }
            // failed to remove seats after 5 attempts
            return false;
        }
    }

}
