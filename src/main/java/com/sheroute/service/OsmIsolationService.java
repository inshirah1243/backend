package com.sheroute.service;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.sheroute.dto.CoordinateDto;

@Service
public class OsmIsolationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OsmIsolationService.class);
    private static final String OSM_MAP_API =
            "https://api.openstreetmap.org/api/0.6/map";
    private static final String USER_AGENT =
            "SheRouteMajorProject/1.0";
    private static final double BBOX_BUFFER_DEGREES = 0.001;
    private static final double EARTH_RADIUS_METERS = 6371000;
    private static final Set<String> SUPPORTED_LAND_USE_CATEGORIES = Set.of(
            "residential",
            "commercial",
            "institutional",
            "mixed",
            "industrial",
            "forest",
            "farmland",
            "grass",
            "meadow"
    );

    private final RestTemplate restTemplate;

    public OsmIsolationService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String findSurroundingLandUse(CoordinateDto point) {
        if (point == null) {
            return null;
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
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                    .build();
            ResponseEntity<String> response =
                    restTemplate.exchange(request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()
                    || !isXmlContentType(response.getHeaders().getContentType())
                    || !hasOsmXmlPrefix(response.getBody())) {
                return null;
            }

            return findNearestLandUse(response.getBody(), point);
        } catch (RestClientException e) {
            LOGGER.warn("Unable to retrieve OSM land use near {}, {}",
                    point.latitude(), point.longitude(), e);
            return null;
        } catch (ParserConfigurationException | SAXException
                 | IOException | NumberFormatException e) {
            LOGGER.warn("Unable to parse OSM land use near {}, {}",
                    point.latitude(), point.longitude(), e);
            return null;
        }
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

        return body.startsWith("<?xml") || body.startsWith("<osm");
    }

    private String findNearestLandUse(
            String response,
            CoordinateDto point)
            throws ParserConfigurationException, SAXException, IOException {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler());
        Document document = builder.parse(
                new InputSource(new StringReader(response))
        );
        Map<String, CoordinateDto> nodes = readNodes(document);
        NodeList ways = document.getElementsByTagName("way");

        String nearestLandUse = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < ways.getLength(); i++) {
            Element way = (Element) ways.item(i);
            String landUse = landUseTag(way);
            if (landUse == null) {
                continue;
            }

            NodeList references = way.getElementsByTagName("nd");
            List<CoordinateDto> wayPoints = new ArrayList<>();
            CoordinateDto first = null;
            CoordinateDto previous = null;
            double wayDistance = Double.MAX_VALUE;

            for (int j = 0; j < references.getLength(); j++) {
                Element reference = (Element) references.item(j);
                CoordinateDto current = nodes.get(reference.getAttribute("ref"));
                if (current == null) {
                    previous = null;
                    continue;
                }

                if (first == null) {
                    first = current;
                }
                wayPoints.add(current);

                if (previous != null) {
                    wayDistance = Math.min(
                            wayDistance,
                            distanceToSegmentInMeters(point, previous, current)
                    );
                }
                previous = current;
            }

            if (isClosedWay(first, previous, references.getLength())
                    && isPointInsidePolygon(point, wayPoints)) {
                wayDistance = 0;
            }

            if (wayDistance < nearestDistance) {
                nearestDistance = wayDistance;
                nearestLandUse = landUse;
            }
        }

        return nearestLandUse;
    }

    private Map<String, CoordinateDto> readNodes(Document document) {
        Map<String, CoordinateDto> nodes = new HashMap<>();
        NodeList elements = document.getElementsByTagName("node");

        for (int i = 0; i < elements.getLength(); i++) {
            Element node = (Element) elements.item(i);
            nodes.put(
                    node.getAttribute("id"),
                    new CoordinateDto(
                            Double.parseDouble(node.getAttribute("lat")),
                            Double.parseDouble(node.getAttribute("lon"))
                    )
            );
        }

        return nodes;
    }

    private String landUseTag(Element way) {
        NodeList tags = way.getElementsByTagName("tag");
        for (int i = 0; i < tags.getLength(); i++) {
            Element tag = (Element) tags.item(i);
            if ("landuse".equals(tag.getAttribute("k"))) {
                String value = tag.getAttribute("v");
                return SUPPORTED_LAND_USE_CATEGORIES.contains(value)
                        ? value
                        : null;
            }
        }
        return null;
    }

    private boolean isClosedWay(
            CoordinateDto first,
            CoordinateDto last,
            int referenceCount) {

        return first != null
                && last != null
                && referenceCount >= 4
                && isSameLocation(first, last);
    }

    private boolean isPointInsidePolygon(
            CoordinateDto point,
            List<CoordinateDto> polygon) {

        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            CoordinateDto current = polygon.get(i);
            CoordinateDto previous = polygon.get(j);
            boolean crossesLatitude =
                    (current.latitude() > point.latitude())
                            != (previous.latitude() > point.latitude());

            if (crossesLatitude) {
                double crossingLongitude =
                        (previous.longitude() - current.longitude())
                                * (point.latitude() - current.latitude())
                                / (previous.latitude() - current.latitude())
                                + current.longitude();
                if (point.longitude() < crossingLongitude) {
                    inside = !inside;
                }
            }
        }

        return inside;
    }

    private boolean isSameLocation(
            CoordinateDto first,
            CoordinateDto second) {

        return first.latitude() == second.latitude()
                && first.longitude() == second.longitude();
    }

    private double distanceToSegmentInMeters(
            CoordinateDto point,
            CoordinateDto start,
            CoordinateDto end) {

        double latitudeRadians = Math.toRadians(point.latitude());
        double startX = longitudeOffsetMeters(point, start, latitudeRadians);
        double startY = latitudeOffsetMeters(point, start);
        double endX = longitudeOffsetMeters(point, end, latitudeRadians);
        double endY = latitudeOffsetMeters(point, end);
        double segmentX = endX - startX;
        double segmentY = endY - startY;
        double segmentLengthSquared =
                segmentX * segmentX + segmentY * segmentY;
        double projection = segmentLengthSquared == 0
                ? 0
                : -(startX * segmentX + startY * segmentY)
                        / segmentLengthSquared;
        double clampedProjection = Math.max(0, Math.min(1, projection));

        return Math.hypot(
                startX + clampedProjection * segmentX,
                startY + clampedProjection * segmentY
        );
    }

    private double longitudeOffsetMeters(
            CoordinateDto origin,
            CoordinateDto point,
            double originLatitudeRadians) {

        return EARTH_RADIUS_METERS
                * Math.toRadians(point.longitude() - origin.longitude())
                * Math.cos(originLatitudeRadians);
    }

    private double latitudeOffsetMeters(
            CoordinateDto origin,
            CoordinateDto point) {

        return EARTH_RADIUS_METERS
                * Math.toRadians(point.latitude() - origin.latitude());
    }
}
