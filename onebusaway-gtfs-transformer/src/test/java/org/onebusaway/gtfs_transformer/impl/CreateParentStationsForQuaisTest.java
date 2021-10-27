/**
 * Copyright (C) 2018 Tony Laidig <laidig@gmail.com>
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

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs.services.MockGtfs;
import org.onebusaway.gtfs_transformer.services.TransformContext;

import java.io.IOException;

public class CreateParentStationsForQuaisTest {
    private GtfsMutableRelationalDao _dao;

    private MockGtfs _gtfs;

    @Before
    public void setup() throws IOException {
        _dao = new GtfsRelationalDaoImpl();

        _gtfs = MockGtfs.create();
        _gtfs.putDefaultTrips();
        _gtfs.putDefaultStopTimes();
        _gtfs.putLines("stops.txt", "stop_id,stop_name,stop_lat,stop_lon",
                "100,The Stop,47.654403,-122.305211",
                "200,The Other Stop,47.656303,-122.315436",
                "de:08111:6115:1:1,Hauptbahnhof (oben),48.784748077,9.1832141876",
                "de:08111:6115:1:2,Hauptbahnhof (oben),48.784748077,9.1832141876",
                "de:08111:6115:2:3,Hauptbahnhof (oben),48.784748077,9.1832141876",
                "de:08111:6116:1:1,Stadtbibliothek,48.790687561,9.1811532974",
                "de:08111:6116:1:2,Stadtbibliothek,48.790687561,9.1812076569",
                "de:08111:6116:3:3,Stadtbibliothek,48.790660858,9.1805820465",
                "de:08111:6116:3:4,Stadtbibliothek,48.790660858,9.1805820465",
                "de:08111:6118:1:101,Hauptbahnhof (tief),48.783420563,9.1801605225",
                "de:08111:6118:1:102,Hauptbahnhof (tief),48.783367157,9.1803379059");
    }

    @Test
    public void testParentStationsAreCreated() throws IOException {
        CreateParentStationsForQuais _strategy = new CreateParentStationsForQuais();

        _dao = _gtfs.read();

        assertEquals(1, _dao.getAllAgencies().size());
        _strategy.run(new TransformContext(), _dao);

        assertEquals("de:08111:6115",
                _dao.getStopForId(new AgencyAndId("1", "de:08111:6115:1:1")).getParentStation());
        assertEquals("Stadtbibliothek",
                _dao.getStopForId(new AgencyAndId("1", "de:08111:6116")).getName());
        assertEquals(48.78339,
                _dao.getStopForId(new AgencyAndId("1", "de:08111:6118")).getLat(), 0.0001);
        assertEquals(9.1802,
                _dao.getStopForId(new AgencyAndId("1", "de:08111:6118")).getLon(), 0.0001);
        assertEquals(1,
                _dao.getStopForId(new AgencyAndId("1", "de:08111:6118")).getLocationType());

    }
}