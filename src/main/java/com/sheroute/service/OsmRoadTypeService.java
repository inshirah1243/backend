package com.sheroute.service;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.sheroute.dto.CoordinateDto;

@Service
public class OsmRoadTypeService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OsmRoadTypeService.class);

    private static final String OSM_MAP_API =
            "https://api.openstreetmap.org/api/0.6/map";

    private static final String USER_AGENT =
            "SheRouteMajorProject/1.0";

    private static final double BBOX_BUFFER_DEGREES = 0.001;
    private static final double EARTH_RADIUS_METERS = 6371000;

    /*
     * Cache key grid size.
     *
     * Nearby route points fall into the same grid cell,
     * so we can reuse an existing OSM result instead of
     * making another network request.
     */
    private static final double CACHE_GRID_SIZE = 0.001;

    private final RestTemplate restTemplate;

    private final Map<String, String> roadTypeCache =
            new HashMap<>();

    public OsmRoadTypeService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String findNearestRoadType(CoordinateDto point) {

        if (point == null) {
            return null;
        }

        String cacheKey = createCacheKey(point);

        if (roadTypeCache.containsKey(cacheKey)) {
            LOGGER.info("Using cached OSM road type for {}", cacheKey);
            return roadTypeCache.get(cacheKey);
        }

        String bbox = String.format(
                Locale.ROOT,
                "%.6f,%.6f,%.6f,%.6f",
                point.longitude() - BBOX_BUFFER_DEGREES,
                point.latitude() - BBOX_BUFFER_DEGREES,
                point.longitude() + BBOX_BUFFER_DEGREES,
                point.latitude() + BBOX_BUFFER_DEGREES
        );

        URI uri = URI.create(OSM_MAP_API + "?bbox=" + bbox);

        try {

            RequestEntity<Void> request = RequestEntity
                    .get(uri)
                    .header("User-Agent", USER_AGENT)
                    .header(
                            HttpHeaders.ACCEPT,
                            MediaType.APPLICATION_XML_VALUE
                    )
                    .build();

            ResponseEntity<String> response =
                    restTemplate.exchange(request, String.class);

            MediaType contentType =
                    response.getHeaders().getContentType();

            String body = response.getBody();

            LOGGER.info(
                    "OSM map response: status={}, contentType={}, bodyPrefix={}",
                    response.getStatusCode().value(),
                    contentType,
                    bodyPrefix(body)
            );

            if (!response.getStatusCode().is2xxSuccessful()
                    || !isXmlContentType(contentType)
                    || !hasOsmXmlPrefix(body)) {

                return null;
            }

            String roadType =
                    findNearestHighwayType(body, point);

            /*
             * Store only successful results.
             * This keeps the existing behavior when OSM
             * does not provide a road type.
             */
            if (roadType != null) {
                roadTypeCache.put(cacheKey, roadType);
            }

            return roadType;

        } catch (HttpStatusCodeException e) {

            LOGGER.warn(
                    "OSM map response: status={}, contentType={}, bodyPrefix={}",
                    e.getStatusCode().value(),
                    e.getResponseHeaders() == null
                            ? null
                            : e.getResponseHeaders().getContentType(),
                    bodyPrefix(e.getResponseBodyAsString())
            );

            return null;

        } catch (RestClientException e) {

            LOGGER.warn(
                    "Unable to retrieve OSM road types near {}, {}",
                    point.latitude(),
                    point.longitude(),
                    e
            );

            return null;

        } catch (ParserConfigurationException
                 | SAXException
                 | IOException
                 | NumberFormatException e) {

            LOGGER.warn(
                    "Unable to parse OSM road types near {}, {}",
                    point.latitude(),
                    point.longitude(),
                    e
            );

            return null;
        }
    }

    private String createCacheKey(CoordinateDto point) {

        long latitudeCell =
                Math.round(point.latitude() / CACHE_GRID_SIZE);

        long longitudeCell =
                Math.round(point.longitude() / CACHE_GRID_SIZE);

        return latitudeCell + ":" + longitudeCell;
    }

    private boolean isXmlContentType(MediaType contentType) {

        return contentType != null
                && (MediaType.APPLICATION_XML.isCompatibleWith(contentType)
                || MediaType.TEXT_XML.isCompatibleWith(contentType));
    }

    private boolean hasOsmXmlPrefix(String response) {

        if (response == null || response.isBlank()) {
            return false;
        }

        String body = response.stripLeading();

        if (!body.isEmpty() && body.charAt(0) == '\uFEFF') {
            body = body.substring(1).stripLeading();
        }

        return body.startsWith("<?xml")
                || body.startsWith("<osm");
    }

    private String bodyPrefix(String body) {

        if (body == null) {
            return "<empty>";
        }

        String prefix =
                body.substring(0, Math.min(body.length(), 160))
                        .replace('\r', ' ')
                        .replace('\n', ' ')
                        .replace('\t', ' ');

        return prefix;
    }

    private String findNearestHighwayType(
            String response,
            CoordinateDto point)
            throws ParserConfigurationException,
            SAXException,
            IOException {

        if (response == null || response.isBlank()) {
            return null;
        }

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setFeature(
                XMLConstants.FEATURE_SECURE_PROCESSING,
                true
        );

        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                ""
        );

        factory.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                ""
        );

        var builder = factory.newDocumentBuilder();

        builder.setErrorHandler(new DefaultHandler() {

            @Override
            public void error(SAXParseException e)
                    throws SAXException {
                throw e;
            }

            @Override
            public void fatalError(SAXParseException e)
                    throws SAXException {
                throw e;
            }
        });

        Document document =
                builder.parse(
                        new InputSource(
                                new StringReader(response)
                        )
                );

        Map<String, CoordinateDto> nodes =
                readNodes(document);

        NodeList ways =
                document.getElementsByTagName("way");

        String nearestRoadType = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < ways.getLength(); i++) {

            Element way =
                    (Element) ways.item(i);

            String highway =
                    highwayTag(way);

            if (highway == null) {
                continue;
            }

            NodeList references =
                    way.getElementsByTagName("nd");

            CoordinateDto previous = null;

            for (int j = 0;
                 j < references.getLength();
                 j++) {

                Element reference =
                        (Element) references.item(j);

                CoordinateDto current =
                        nodes.get(
                                reference.getAttribute("ref")
                        );

                if (current == null) {
                    previous = null;
                    continue;
                }

                double distance =
                        previous == null
                                ? distanceInMeters(
                                        point,
                                        current
                                )
                                : distanceToSegmentInMeters(
                                        point,
                                        previous,
                                        current
                                );

                if (distance < nearestDistance) {

                    nearestDistance = distance;
                    nearestRoadType = highway;
                }

                previous = current;
            }
        }

        return nearestRoadType;
    }

    private Map<String, CoordinateDto> readNodes(
            Document document) {

        Map<String, CoordinateDto> nodes =
                new HashMap<>();

        NodeList elements =
                document.getElementsByTagName("node");

        for (int i = 0;
             i < elements.getLength();
             i++) {

            Element node =
                    (Element) elements.item(i);

            nodes.put(
                    node.getAttribute("id"),
                    new CoordinateDto(
                            Double.parseDouble(
                                    node.getAttribute("lat")
                            ),
                            Double.parseDouble(
                                    node.getAttribute("lon")
                            )
                    )
            );
        }

        return nodes;
    }

    private String highwayTag(Element way) {

        NodeList tags =
                way.getElementsByTagName("tag");

        for (int i = 0;
             i < tags.getLength();
             i++) {

            Element tag =
                    (Element) tags.item(i);

            if ("highway".equals(
                    tag.getAttribute("k"))) {

                String value =
                        tag.getAttribute("v");

                return value.isBlank()
                        ? null
                        : value;
            }
        }

        return null;
    }

    private double distanceToSegmentInMeters(
            CoordinateDto point,
            CoordinateDto start,
            CoordinateDto end) {

        double latitudeRadians =
                Math.toRadians(point.latitude());

        double startX =
                longitudeOffsetMeters(
                        point,
                        start,
                        latitudeRadians
                );

        double startY =
                latitudeOffsetMeters(
                        point,
                        start
                );

        double endX =
                longitudeOffsetMeters(
                        point,
                        end,
                        latitudeRadians
                );

        double endY =
                latitudeOffsetMeters(
                        point,
                        end
                );

        double segmentX =
                endX - startX;

        double segmentY =
                endY - startY;

        double segmentLengthSquared =
                segmentX * segmentX
                        + segmentY * segmentY;

        double projection =
                segmentLengthSquared == 0
                        ? 0
                        : -(startX * segmentX
                        + startY * segmentY)
                        / segmentLengthSquared;

        double clampedProjection =
                Math.max(
                        0,
                        Math.min(1, projection)
                );

        double nearestX =
                startX
                        + clampedProjection * segmentX;

        double nearestY =
                startY
                        + clampedProjection * segmentY;

        return Math.hypot(
                nearestX,
                nearestY
        );
    }

    private double longitudeOffsetMeters(
            CoordinateDto origin,
            CoordinateDto point,
            double originLatitudeRadians) {

        return EARTH_RADIUS_METERS
                * Math.toRadians(
                        point.longitude()
                                - origin.longitude()
                )
                * Math.cos(
                        originLatitudeRadians
                );
    }

    private double latitudeOffsetMeters(
            CoordinateDto origin,
            CoordinateDto point) {

        return EARTH_RADIUS_METERS
                * Math.toRadians(
                        point.latitude()
                                - origin.latitude()
                );
    }

    private double distanceInMeters(
            CoordinateDto first,
            CoordinateDto second) {

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

        return EARTH_RADIUS_METERS * 2
                * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );
    }
}