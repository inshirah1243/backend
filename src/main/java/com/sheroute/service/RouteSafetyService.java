package com.sheroute.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sheroute.dto.CoordinateDto;

@Service
public class RouteSafetyService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RouteSafetyService.class);

    private final TimeSafetyService timeSafetyService;
    private final RoadSafetyService roadSafetyService;
    private final IsolationSafetyService isolationSafetyService;
    private final OsmRoadTypeService osmRoadTypeService;
    private final OsmIsolationService osmIsolationService;
    private final OsmCctvService osmCctvService;

    @Autowired
    public RouteSafetyService(
            TimeSafetyService timeSafetyService,
            RoadSafetyService roadSafetyService,
            IsolationSafetyService isolationSafetyService,
            OsmRoadTypeService osmRoadTypeService,
            OsmIsolationService osmIsolationService,
            OsmCctvService osmCctvService) {

        this.timeSafetyService = timeSafetyService;
        this.roadSafetyService = roadSafetyService;
        this.isolationSafetyService = isolationSafetyService;
        this.osmRoadTypeService = osmRoadTypeService;
        this.osmIsolationService = osmIsolationService;
        this.osmCctvService = osmCctvService;
    }

    public RouteSafetyService(
            TimeSafetyService timeSafetyService,
            RoadSafetyService roadSafetyService,
            IsolationSafetyService isolationSafetyService,
            OsmRoadTypeService osmRoadTypeService) {

        this(
                timeSafetyService,
                roadSafetyService,
                isolationSafetyService,
                osmRoadTypeService,
                null,
                null
        );
    }

    public RouteSafetyService(
            TimeSafetyService timeSafetyService,
            RoadSafetyService roadSafetyService,
            IsolationSafetyService isolationSafetyService,
            OsmRoadTypeService osmRoadTypeService,
            OsmIsolationService osmIsolationService) {

        this(
                timeSafetyService,
                roadSafetyService,
                isolationSafetyService,
                osmRoadTypeService,
                osmIsolationService,
                null
        );
    }

    public List<SegmentSafety> calculateRouteSafety(
            List<CoordinateDto> routePoints) {

        List<CoordinateDto> samplePoints = sampleRoute(routePoints);

        if (samplePoints.isEmpty()) {
            return List.of();
        }

        LOGGER.info(
                "Starting safety calculation for {} sample points",
                samplePoints.size()
        );

        double timeIndex =
                timeSafetyService.calculateTimeIndex();

        List<SegmentSafety> result =
                new ArrayList<>();

        for (int i = 0; i < samplePoints.size(); i++) {

            CoordinateDto point =
                    samplePoints.get(i);

            LOGGER.info(
                    "Safety point {} / {} started",
                    i + 1,
                    samplePoints.size()
            );

            // -----------------------------
            // ROAD TYPE
            // -----------------------------

            long roadStart =
                    System.currentTimeMillis();

            LOGGER.info(
                    "Safety point {}: checking Road Type",
                    i + 1
            );

            String roadType =
                    osmRoadTypeService.findNearestRoadType(point);

            LOGGER.info(
                    "Safety point {}: Road Type done: {} ({} ms)",
                    i + 1,
                    roadType,
                    System.currentTimeMillis() - roadStart
            );

            // -----------------------------
            // ISOLATION
            // -----------------------------

            long isolationStart =
                    System.currentTimeMillis();

            LOGGER.info(
                    "Safety point {}: checking Isolation",
                    i + 1
            );

            String landUse =
                    osmIsolationService == null
                            ? null
                            : osmIsolationService
                                    .findSurroundingLandUse(point);

            LOGGER.info(
                    "Safety point {}: Isolation done: {} ({} ms)",
                    i + 1,
                    landUse,
                    System.currentTimeMillis() - isolationStart
            );

            // -----------------------------
            // CCTV
            // -----------------------------

            long cctvStart =
                    System.currentTimeMillis();

            LOGGER.info(
                    "Safety point {}: checking CCTV",
                    i + 1
            );

            OsmCctvService.CctvProximity cctvProximity =
                    osmCctvService == null
                            ? OsmCctvService.CctvProximity.unavailable()
                            : osmCctvService
                                    .findCctvProximity(point);

            LOGGER.info(
                    "Safety point {}: CCTV done: present={} distance={} ({} ms)",
                    i + 1,
                    cctvProximity.cctvPresent(),
                    cctvProximity.cctvDistance(),
                    System.currentTimeMillis() - cctvStart
            );

            // -----------------------------
            // SAFETY SCORE
            // -----------------------------

            double roadIndex =
                    roadSafetyService
                            .calculateRoadSafetyIndex(
                                    roadType
                            );

            double isolationIndex =
                    isolationSafetyService
                            .calculateIsolationIndex(
                                    landUse
                            );

            double safetyScore =
                    (timeIndex
                            + roadIndex
                            + isolationIndex
                            + cctvProximity.cctvIndex())
                            / 4.0;

            result.add(
                    new SegmentSafety(
                            i + 1,
                            point,
                            roadType,
                            roadIndex,
                            landUse,
                            isolationIndex,
                            timeIndex,
                            cctvProximity.cctvDistance(),
                            cctvProximity.cctvPresent(),
                            cctvProximity.cctvIndex(),
                            safetyScore
                    )
            );

            LOGGER.info(
                    "Safety point {} completed. Score={}",
                    i + 1,
                    safetyScore
            );
        }

        LOGGER.info(
                "Safety calculation completed for {} points",
                samplePoints.size()
        );

        return result;
    }

    private List<CoordinateDto> sampleRoute(
            List<CoordinateDto> routePoints) {

        if (routePoints == null || routePoints.isEmpty()) {
            return List.of();
        }

        List<CoordinateDto> samples =
                new ArrayList<>();

        CoordinateDto previous = null;

        double distanceSinceLastSample = 0;

        for (CoordinateDto point : routePoints) {

            if (point == null) {
                continue;
            }

            if (previous == null) {

                samples.add(point);
                previous = point;

                continue;
            }

            distanceSinceLastSample +=
                    distanceInMeters(
                            previous,
                            point
                    );

            if (distanceSinceLastSample >= 2000) {

                samples.add(point);

                distanceSinceLastSample = 0;
            }

            previous = point;
        }

        if (previous != null
                && !samples
                        .get(samples.size() - 1)
                        .equals(previous)) {

            samples.add(previous);
        }

        return samples;
    }

    private double distanceInMeters(
            CoordinateDto first,
            CoordinateDto second) {

        final double earthRadius =
                6371000;

        double latitudeDifference =
                Math.toRadians(
                        second.latitude()
                                - first.latitude()
                );

        double longitudeDifference =
                Math.toRadians(
                        second.longitude()
                                - first.longitude()
                );

        double firstLatitude =
                Math.toRadians(
                        first.latitude()
                );

        double secondLatitude =
                Math.toRadians(
                        second.latitude()
                );

        double a =
                Math.sin(latitudeDifference / 2)
                        * Math.sin(latitudeDifference / 2)
                        + Math.cos(firstLatitude)
                        * Math.cos(secondLatitude)
                        * Math.sin(longitudeDifference / 2)
                        * Math.sin(longitudeDifference / 2);

        return earthRadius * 2
                * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );
    }

    public record SegmentSafety(
            int segmentNumber,
            CoordinateDto routePoint,
            String roadType,
            double roadSafetyIndex,
            String landUse,
            double isolationIndex,
            double timeIndex,
            Double cctvDistance,
            boolean cctvPresent,
            double cctvIndex,
            double safetyScore
    ) {
    }
}