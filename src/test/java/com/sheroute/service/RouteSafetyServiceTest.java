package com.sheroute.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;

import com.sheroute.dto.CoordinateDto;

class RouteSafetyServiceTest {

    @Test
    void calculatesSafetyWithoutInfrastructureLookup() {
        CoordinateDto routePoint = new CoordinateDto(17.412, 78.353);
        TimeSafetyService timeSafetyService = mock(TimeSafetyService.class);
        OsmRoadTypeService osmRoadTypeService =
                mock(OsmRoadTypeService.class);
        when(timeSafetyService.calculateTimeIndex()).thenReturn(0.6);
        when(osmRoadTypeService.findNearestRoadType(routePoint))
                .thenReturn("primary");

        RouteSafetyService service = new RouteSafetyService(
                timeSafetyService,
                new RoadSafetyService(),
                new IsolationSafetyService(),
                osmRoadTypeService
        );

        List<RouteSafetyService.SegmentSafety> result =
                service.calculateRouteSafety(List.of(routePoint));

        assertThat(result).containsExactly(
                new RouteSafetyService.SegmentSafety(
                        1,
                        routePoint,
                        "primary",
                        0.6,
                        null,
                        0.5,
                        0.6,
                        null,
                        false,
                        0.5,
                        (0.6 + 0.6 + 0.5 + 0.5) / 4.0
                )
        );
    }

    @Test
    void usesUnknownCategoryDefaultsWhenInfrastructureIsUnavailable() {
        CoordinateDto routePoint = new CoordinateDto(17.412, 78.353);
        TimeSafetyService timeSafetyService = mock(TimeSafetyService.class);
        OsmRoadTypeService osmRoadTypeService =
                mock(OsmRoadTypeService.class);
        when(timeSafetyService.calculateTimeIndex()).thenReturn(1.0);

        RouteSafetyService service = new RouteSafetyService(
                timeSafetyService,
                new RoadSafetyService(),
                new IsolationSafetyService(),
                osmRoadTypeService
        );

        List<RouteSafetyService.SegmentSafety> result =
                service.calculateRouteSafety(List.of(routePoint));

        assertThat(result).containsExactly(
                new RouteSafetyService.SegmentSafety(
                        1,
                        routePoint,
                        null,
                        0.5,
                        null,
                        0.5,
                        1.0,
                        null,
                        false,
                        0.5,
                        (1.0 + 0.5 + 0.5 + 0.5) / 4.0
                )
        );
    }
}
