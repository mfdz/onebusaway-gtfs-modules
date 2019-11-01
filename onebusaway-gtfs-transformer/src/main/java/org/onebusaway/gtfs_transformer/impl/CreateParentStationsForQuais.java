/**
 * Copyright (C) 2019 Holger Bruch <hb@mfdz.de>
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
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Some GTFS-Feeds have stops with IFOPT quai IDs, but no parent stop_places.
 * Route planners like OpenTripPlanner may cluster quais using their common parentStation.
 * This strategy requires the quais stop ID to be in IFOPT format, and creates clusters for
 * all quais with a common stop_place portion. If no stop with this stop_place ID exists,
 * this strategy creates one.
 */
public class CreateParentStationsForQuais implements GtfsTransformStrategy {
    private static Logger _log = LoggerFactory.getLogger(CreateParentStationsForQuais.class);
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public void run(TransformContext context, GtfsMutableRelationalDao dao) {
        HashMap<AgencyAndId, Set<Stop>> clusters = collectClusters(dao);
        createAndSetParentStops(dao, clusters);
    }

    private void createAndSetParentStops(GtfsMutableRelationalDao dao,
            HashMap<AgencyAndId, Set<Stop>> clusters) {
        // iterate over all clusters, if no stop with that Id exists, create one
        for (Map.Entry<AgencyAndId, Set<Stop>> entry: clusters.entrySet()) {
            AgencyAndId parentStopId = entry.getKey();

            int cnt = 0;
            double lat = 0, lon = 0;
            String name = "";

            // now set parent station for all clustered stops (if not already the parent)
            for (Stop childStop: entry.getValue()) {
                // Note: there might be already a stop with stop_place ID, which wasn't yet a parent
                if (!childStop.getId().equals(parentStopId)) {
                    childStop.setParentStation(parentStopId.getId());
                } else {
                    // Existing stop probably is not yet a parent station
                    childStop.setLocationType(1);
                }
                lat += childStop.getLat();
                lon += childStop.getLon();
                cnt++;
                if ("".equals(name)) {
                    name = childStop.getName();
                }
            }

            if (dao.getStopForId(parentStopId) == null) {
                Stop stop = new Stop();
                stop.setId(parentStopId);
                stop.setName(name);
                stop.setLocationType(1);
                stop.setCode(parentStopId.getId());
                stop.setLat(lat / cnt);
                stop.setLon(lon / cnt);
                dao.saveEntity(stop);
            }
        }
    }

    private HashMap<AgencyAndId, Set<Stop>> collectClusters(GtfsMutableRelationalDao dao) {
        Pattern stopPlacePattern = Pattern.compile("(\\w*:\\w*:\\w*):?(.*)");
        HashMap<AgencyAndId, Set<Stop>> clusters = new HashMap<>();
        for (Stop stop: dao.getAllStops()) {
            Matcher stopPlaceMatcher = stopPlacePattern.matcher(stop.getId().getId());
            if (!stopPlaceMatcher.matches()) {
                _log.warn("Stop ID " + stop.getId().getId() + " did not match IFOPT format.");
                continue;
            }
            AgencyAndId newParentId = new AgencyAndId(stop.getId().getAgencyId(),
                    stopPlaceMatcher.group(1));

            if (!clusters.containsKey(newParentId)) {
                clusters.put(newParentId, new HashSet<>());
            }
            clusters.get(newParentId).add(stop);
        }
        return clusters;
    }
}
