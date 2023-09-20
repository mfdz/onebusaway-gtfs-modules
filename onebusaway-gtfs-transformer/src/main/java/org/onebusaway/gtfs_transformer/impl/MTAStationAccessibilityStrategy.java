/**
 * Copyright (C) 2023 Cambridge Systematics, Inc.
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

import org.onebusaway.cloud.api.ExternalServices;
import org.onebusaway.cloud.api.ExternalServicesBridgeFactory;
import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.gtfs.model.FeedInfo;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.csv.MTAStation;
import org.onebusaway.gtfs_transformer.services.GtfsTransformStrategy;
import org.onebusaway.gtfs_transformer.services.TransformContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.onebusaway.gtfs_transformer.csv.CSVUtil.readCsv;
import static org.onebusaway.gtfs_transformer.impl.MTAEntrancesStrategy.WHEELCHAIR_ACCESSIBLE;

/**
 * Based on a CSV of MTAStations set the associated stops accessible as specified.
 */
public class MTAStationAccessibilityStrategy implements GtfsTransformStrategy {

  private static final Logger _log = LoggerFactory.getLogger(MTAStationAccessibilityStrategy.class);
  private String stationsCsv;

  @CsvField(ignore = true)
  private Set<Stop> accessibleStops = new HashSet<>();
  
  @CsvField(ignore = true)
  private Map<String, Stop> idToStopMap = new HashMap<>();

  @Override
  public String getName() {
    return this.getClass().getName();
  }

  @Override
  public void run(TransformContext context, GtfsMutableRelationalDao dao) {

    ExternalServices es =  new ExternalServicesBridgeFactory().getExternalServices();
    Collection<FeedInfo> feedInfos = dao.getAllFeedInfos();

    // name the feed for logging/reference
    String feed = null;
    if(feedInfos.size() > 0)
      feed = feedInfos.iterator().next().getPublisherName();

    // stops are unqualified, build up a map of them for lookups
    for (Stop stop : dao.getAllStops()) {
      idToStopMap.put(stop.getId().getId(), stop);
    }

    File stationsFile = new File(stationsCsv);
    if (!stationsFile.exists()) {
      es.publishMultiDimensionalMetric(getNamespace(), "MissingControlFiles",
              new String[]{"feed", "controlFileName"},
              new String[]{feed, stationsCsv}, 1);
      throw new IllegalStateException(
              "Entrances file does not exist: " + stationsFile.getName());
    }

    // we have a file, load the contents
    List<MTAStation> stations = getStations();
    for (MTAStation station : stations) {
      if (station.getAda() == MTAStation.ADA_FULLY_ACCESSIBLE) {
        markStopAccessible(dao, station.getStopId(), "N");
        markStopAccessible(dao, station.getStopId(), "S");
      } else if (station.getAda() == MTAStation.ADA_PARTIALLY_ACCESSIBLE) {
        if (station.getAdaNorthBound() == WHEELCHAIR_ACCESSIBLE) {
          markStopAccessible(dao, station.getStopId(), "N");
        }
        if (station.getAdaSouthBound() == WHEELCHAIR_ACCESSIBLE) {
          markStopAccessible(dao, station.getStopId(), "S");
        }
      }
    }

    _log.info("marking {} stops as accessible", accessibleStops.size());
    for (Stop accessibleStop : this.accessibleStops) {
      // save the changes
      dao.updateEntity(accessibleStop);
    }

  }

  private void markStopAccessible(GtfsMutableRelationalDao dao, String stopId, String compassDirection) {
    String unqualifedStopId = stopId + compassDirection;
    Stop stopForId = idToStopMap.get(unqualifedStopId);
    if (stopForId == null) {
      _log.error("no such stop for stopId {}", unqualifedStopId);
      return;
    }
    stopForId.setWheelchairBoarding(WHEELCHAIR_ACCESSIBLE);
    this.accessibleStops.add(stopForId);
  }


  private List<MTAStation> getStations() {
    return readCsv(MTAStation.class, stationsCsv);
  }

  public void setStationsCsv(String stationsCsv) {
    this.stationsCsv = stationsCsv;
  }

  private String getNamespace(){
    return System.getProperty("cloudwatch.namespace");
  }

}