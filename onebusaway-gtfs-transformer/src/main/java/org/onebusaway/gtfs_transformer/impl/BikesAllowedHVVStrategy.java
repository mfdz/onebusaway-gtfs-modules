/**
 * Copyright (C) 2020 MTFAHR|DE|ZENTRALE.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.gtfs_transformer.impl;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * This class attempts to enhance the bikes_allowed attributes for
 * the HVV feed according to https://www.hvv.de/de/fahrrad
 */
public class BikesAllowedHVVStrategy implements GtfsTransformStrategy {

    private final Logger _log = LoggerFactory.getLogger(BikesAllowedHVVStrategy.class);

    // Values for routes.bikes_allowed / trips.bikes_allowed
    public static final int UNKNOWN = 0;
    public static final int BIKES_ALLOWED = 1;
    public static final int NO_BIKES_ALLOWED = 2;
    // Introducing a new value would not be backward compatible, so we'll set UNKNOWN in this case
    // public static final int BIKES_STOP_TIME_DEPENDANT = 3;

    // For stopTimes, a new value is introduced: already boarded bike may stay, but no (un)boarding is allowed
    public static final int BIKES_ALLOWED_BUT_NO_BOARDING_UNBOARDING = 3;

    private final TimeRange TIME_0600_TO_0900 = TimeRange.create(6, 0, 9, 00);
    private final TimeRange TIME_1600_TO_1800 = TimeRange.create(16, 00, 18, 00);

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public void run(TransformContext context, GtfsMutableRelationalDao dao) {
        ArrayList<Trip> tripsToRemove = new ArrayList<>();
        _log.info("Total dao {}", dao.getAllTrips().size());
        
        ScheduleChecker check = new ScheduleChecker(dao);

        for (Trip trip : dao.getAllTrips()) {
            switch(trip.getRoute().getType()){
                // All ferries are allowed (though one requires additional ticket)
                case 1200: // Ferry Service
                    trip.setBikesAllowed(BIKES_ALLOWED);
                    break;
                case 109: // Suburban Railway
                case 402: // Underground Service
                case 2:
                    // time dependent, we don't know exactly:
                    if (check.runsOnWeekendOrHoliday(trip))
                        // sa, so, an gesetzlichen Feiertagen sowie am 24. und 31. Dezember jeweils ganztägig bis Betriebsschluss
                        trip.setBikesAllowed(BIKES_ALLOWED);
                    else {
                        check.disallowStopTimes(trip, TIME_0600_TO_0900, TIME_1600_TO_1800);
                    }
                    break;
                case 3: // Bus
                case 702: // Express Bus Service
                    trip.setBikesAllowed(NO_BIKES_ALLOWED);
                break;

            default:
                _log.warn("No bike information for trip {} with route type {}", trip, trip.getRoute().getType());
                trip.setBikesAllowed(UNKNOWN);
            }
        }

        _log.info("Total dao {}", dao.getAllTrips().size());
    }
    
    
    private class ScheduleChecker {
        private final GtfsMutableRelationalDao dao;

        public ScheduleChecker(GtfsMutableRelationalDao dao){
            this.dao = dao;
        }

        private boolean runsOnWeekendOrHoliday(Trip trip) {
            AgencyAndId serviceId = trip.getServiceId();
            ServiceCalendar calendarForServiceId = dao.getCalendarForServiceId(serviceId);
            if (calendarForServiceId.getSaturday() + calendarForServiceId.getSunday() > 0) {
                if (calendarForServiceId.getMonday() + calendarForServiceId.getTuesday() +
                        calendarForServiceId.getWednesday()+calendarForServiceId.getThursday()
                        +calendarForServiceId.getFriday()> 0) {
                    _log.warn("Trip {} runs on weekdays and weekend. Bike allowance might be incorrect", trip);
                }
                return true;
            }
            return false;
        }

        public void disallowStopTimes(Trip trip, TimeRange... disallowedTimeRanges) {

            boolean anyForbidden = false;
            boolean anyAllowed = false;
            List<StopTime> stopTimesForTrip = dao.getStopTimesForTrip(trip);
            for (int j = stopTimesForTrip.size() - 1; j >= 0 ; j--) {
                StopTime stopTime = stopTimesForTrip.get(j);
                if (stopTime.isDepartureTimeSet()) {
                    int departureTime = stopTime.getDepartureTime();
                    if (TimeRange.anyContains(disallowedTimeRanges, departureTime)) {
                        stopTime.setBikesAllowed(NO_BIKES_ALLOWED);
                        anyForbidden = true;
                    } else {
                        stopTime.setBikesAllowed(BIKES_ALLOWED);
                        anyAllowed = true;
                    }
                } else if (stopTime.isArrivalTimeSet()) {
                    int arrivalTime = stopTime.getArrivalTime();
                    if (TimeRange.anyContains(disallowedTimeRanges, arrivalTime)) {
                        stopTime.setBikesAllowed(NO_BIKES_ALLOWED);
                        anyForbidden = true;
                    } else {
                        stopTime.setBikesAllowed(BIKES_ALLOWED);
                        anyAllowed = true;
                    }
                } else
                    stopTime.setBikesAllowed(UNKNOWN);
            }
            if (anyForbidden && !anyAllowed) {
                trip.setBikesAllowed(NO_BIKES_ALLOWED);
            } else if (anyAllowed && !anyForbidden) {
                trip.setBikesAllowed(BIKES_ALLOWED);
            } else {
                // some stops are allowed, some not, so stop_times carry information
                trip.setBikesAllowed(UNKNOWN);
            }
        }

        public void allowFirstAndLastStopTime(Trip trip) {
            trip.setBikesAllowed(UNKNOWN);
            List<StopTime> stopTimesForTrip = dao.getStopTimesForTrip(trip);
            stopTimesForTrip.get(0).setBikesAllowed(BIKES_ALLOWED);
            stopTimesForTrip.get(stopTimesForTrip.size()-1).setBikesAllowed(BIKES_ALLOWED);
            for (int i = 1; i < stopTimesForTrip.size()-1; i++) {
                StopTime stopTime = stopTimesForTrip.get(i);
                stopTime.setBikesAllowed(BIKES_ALLOWED_BUT_NO_BOARDING_UNBOARDING);
            }
        }
    }

    private static class TimeRange {
        int start;
        int end;

        public TimeRange(int start, int end){
            this.start = start;
            this.end = end;
        }

        public static boolean anyContains(TimeRange[] disallowedTimeRanges, int time) {
            for (TimeRange timeRange: disallowedTimeRanges) {
                if (timeRange.contains(time)) {
                    return true;
                }
            }
            return false;
        }

        public boolean contains(int value) {
            return start <= value && value < end;
        }

        public static TimeRange create(int fromHour, int fromMinute, int toHour, int toMinute) {
            return new TimeRange(fromHour*3600+fromMinute*60, toHour*3600 + toMinute *60);
        }
    }
}
