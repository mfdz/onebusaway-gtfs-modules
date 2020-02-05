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

import java.util.*;

public class VVSBikesAllowedStrategy implements GtfsTransformStrategy {

    private final Logger _log = LoggerFactory.getLogger(VVSBikesAllowedStrategy.class);

    // Values for routes.bikes_allowed / trips.bikes_allowed
    public static final int UNKNOWN = 0;
    public static final int BIKES_ALLOWED = 1;
    public static final int NO_BIKES_ALLOWED = 2;
    // Introducing a new value would not be backward compatible, so we'll set UNKNOWN in this case
    // public static final int BIKES_STOP_TIME_DEPENDANT = 3;


    public static final int NO_BIKES_PICKUP_INFORMATION = 0;
    public static final int BIKES_PICKUP_ALLOWED = 1;
    public static final int NO_BIKES_PICKUP = 2;

    public static final int NO_BIKES_DROPOFF_INFORMATION = 0;
    public static final int BIKES_DROPOFF_POSSIBLE = 1;
    public static final int NO_BIKES_DROPOFF = 2;
    public static final int BIKES_DROPOFF_MANDATORY = 3;

    private final TimeRange MIDNIGHT_TO_1830 = TimeRange.create(0, 0, 18, 30);
    private final TimeRange TIME_0600_TO_0830 = TimeRange.create(6, 0, 8, 30);
    private final TimeRange TIME_1130_TO_1400 = TimeRange.create(11, 30, 14, 00);
    private final TimeRange TIME_1500_TO_1700 = TimeRange.create(15, 00, 17, 00);
    private final TimeRange TIME_1600_TO_1830 = TimeRange.create(16, 00, 18, 30);

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public void run(TransformContext context, GtfsMutableRelationalDao dao) {
        ArrayList<Trip> tripsToRemove = new ArrayList<>();
        _log.info("Total dao {}", dao.getAllTrips().size());
        
        ScheduleChecker check = new ScheduleChecker(dao);

        // See https://www.sve-es.de/start/Fahrplanaenderungen/fahrradmitnahme.html
        String[] linesWithVVSBusRulesArray = {"106","114", "115", "116", "119", "120", "121", "122",
                "131","132","138"};

        // See https://www.sve-es.de/start/Fahrplanaenderungen/fahrradmitnahme.html
        String[] linesWithEsslingenBusRulesArray = {"101","102", "103", "104", "105", "108", "109", "110",
                "111","112","113", "118"};

        // See https://www.vvs.de/no_cache/fahrradmitnahme/
        String[] busRoutesWithBikeAllowanceArray = {"X10", "X20", "X60", "310",
                // see http://www.orange-seiten.de/freizeitbusse/
                "176", "177.1", "572", "191", "RW1", "170", "464", "467", "375", "376", "265", "385"};
        String[] busRoutesWithWeekendBikeAllowanceArray = {"244"};
        String[] busRoutesWithMoFrBikeAllowanceArray = {"393"};

        HashSet<String> linesWithVVSBusRules = new HashSet<>(Arrays.asList(linesWithVVSBusRulesArray));
        HashSet<String> linesWithEsslingenBusRules = new HashSet<>(Arrays.asList(linesWithEsslingenBusRulesArray));
        HashSet<String> busRoutesWithBikeAllowance = new HashSet<>(Arrays.asList(busRoutesWithBikeAllowanceArray));
        HashSet<String> busRoutesWithWeekendBikeAllowance = new HashSet<>(Arrays.asList(busRoutesWithWeekendBikeAllowanceArray));
        HashSet<String> busRoutesWithMoFrBikeAllowance = new HashSet<>(Arrays.asList(busRoutesWithMoFrBikeAllowanceArray));

        for (Trip trip : dao.getAllTrips()) {
            // See https://www.vvs.de/fahrradmitnahme/ for rules
            switch(trip.getRoute().getType()){
                case 1400:
                    // Kostenlose Fahrradmitnahme auf dem Vorstellwagen der Zahnradbahn nur bergauf von
                    // Marienplatz bis Degerloch (unterwegs kein Be-/Entladen möglich).
                    // In der Seilbahn ist keine Fahrradmitnahme möglich.
                    if ("Degerloch".equals(trip.getTripHeadsign())) {
                        check.allowFirstAndLastStopTime(trip);
                    } else {
                        trip.setBikesAllowed(NO_BIKES_ALLOWED);
                    }
                    break;
                case 109:
                case 2:
                    // allowed, but TODO note that fees are time dependent
                    trip.setBikesAllowed(BIKES_ALLOWED);
                    break;
                case 402:
                    // time dependent, we don't know exactly:
                    // Montag bis Freitag (ausgenommen Feiertage) von 6:00 bis 8:30 Uhr und 16:00 bis
                    // 18:30 Uhr ist in der Stadtbahn keine Mitnahme möglich.
                    // Außerhalb der genannten Zeiten sowie samstags, sonntags und feiertags können
                    // Fahrräder kostenlos mitgenommen werden.
                    if (check.runsOnWeekendOrHoliday(trip))
                        trip.setBikesAllowed(BIKES_ALLOWED);
                    else {
                        check.disallowStopTimes(trip, TIME_0600_TO_0830, TIME_1600_TO_1830);
                    }

                    break;
                case 715:
                case 717:
                    trip.setBikesAllowed(NO_BIKES_ALLOWED);
                    break;
                case 3:
                    String routeShortName = trip.getRoute().getShortName();
                    if (busRoutesWithBikeAllowance.contains(routeShortName)) {
                        trip.setBikesAllowed(BIKES_ALLOWED);
                    } else {
                        if (linesWithVVSBusRules.contains(routeShortName)) {
                            // mo-fr after 18:30, sa,so,hld => ok
                            if (check.runsOnWeekendOrHoliday(trip))
                                trip.setBikesAllowed(BIKES_ALLOWED);
                            else {
                                check.disallowStopTimes(trip, MIDNIGHT_TO_1830);
                            }
                        } else if (linesWithEsslingenBusRules.contains(routeShortName)) {
                            // mo-fr 6:00-8:30, 11:30-14:00, 15:00-17:00 disallowed
                            if (check.runsOnWeekendOrHoliday(trip))
                                trip.setBikesAllowed(BIKES_ALLOWED);
                            else {
                                check.disallowStopTimes(trip, TIME_0600_TO_0830, TIME_1130_TO_1400, TIME_1500_TO_1700);
                            }
                        } else if (busRoutesWithMoFrBikeAllowance.contains(routeShortName)) {
                            trip.setBikesAllowed(check.runsOnWeekendOrHoliday(trip) ? NO_BIKES_ALLOWED : BIKES_ALLOWED);
                        } else if (busRoutesWithWeekendBikeAllowance.contains(routeShortName)) {
                            trip.setBikesAllowed(check.runsOnWeekendOrHoliday(trip) ? BIKES_ALLOWED : NO_BIKES_ALLOWED);
                        } else {
                            _log.warn("No bike information for trip {} of route {}", trip, routeShortName);
                            trip.setBikesAllowed(UNKNOWN);
                        }
                    }
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
            trip.setBikesAllowed(UNKNOWN);
            List<StopTime> stopTimesForTrip = dao.getStopTimesForTrip(trip);
            for (int j = stopTimesForTrip.size() - 1; j >= 0 ; j--) {
                StopTime stopTime = stopTimesForTrip.get(j);

                if (stopTime.isDepartureTimeSet()) {
                    int departureTime = stopTime.getDepartureTime();
                    if (TimeRange.anyContains(disallowedTimeRanges, departureTime)) {
                        stopTime.setBikePickupType(NO_BIKES_PICKUP);
                    } else {
                        stopTime.setBikePickupType(BIKES_PICKUP_ALLOWED);
                    }
                } else
                    stopTime.setBikePickupType(NO_BIKES_PICKUP_INFORMATION);
                if (stopTime.isArrivalTimeSet()) {
                    int arrivalTime = stopTime.getArrivalTime();
                    if (TimeRange.anyContains(disallowedTimeRanges, arrivalTime)) {
                        stopTime.setBikeDropOffType(BIKES_DROPOFF_MANDATORY);
                    } else {
                        stopTime.setBikeDropOffType(BIKES_DROPOFF_POSSIBLE);
                    }
                } else
                    stopTime.setBikeDropOffType(NO_BIKES_DROPOFF_INFORMATION);
            }
        }

        public void allowFirstAndLastStopTime(Trip trip) {
            trip.setBikesAllowed(UNKNOWN);
            List<StopTime> stopTimesForTrip = dao.getStopTimesForTrip(trip);
            stopTimesForTrip.get(0).setPickupType(BIKES_PICKUP_ALLOWED);
            stopTimesForTrip.get(stopTimesForTrip.size()-1).setBikeDropOffType(BIKES_DROPOFF_MANDATORY);
            for (int i = 1; i < stopTimesForTrip.size()-1; i++) {
                StopTime stopTime = stopTimesForTrip.get(i);
                stopTime.setBikeDropOffType(NO_BIKES_DROPOFF);
                stopTime.setBikePickupType(NO_BIKES_PICKUP);
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
