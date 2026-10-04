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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sheroute.dto.CoordinateDto;

@Service
public class OsmCctvService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OsmCctvService.class);
    private static final String OSM_MAP_API =
            "https://api.openstreetmap.org/api/0.6/map";
    private static final String USER_AGENT =
            "SheRouteMajorProject/1.0";
    private static final double CCTV_RADIUS_METERS = 100.0;
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private final RestTemplate restTemplate;

    public OsmCctvService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CctvProximity findCctvProximity(CoordinateDto point) {
        if (point == null) {
            return CctvProximity.unavailable();
        }

        double latitudeBuffer = Math.toDegrees(
                CCTV_RADIUS_METERS / EARTH_RADIUS_METERS
        );
        double longitudeScale =
                Math.cos(Math.toRadians(point.latitude()));
        double longitudeBuffer = longitudeScale > 1.0e-6
                ? latitudeBuffer / longitudeScale
                : 180.0;
        double south = Math.max(-90.0, point.latitude() - latitudeBuffer);
        double north = Math.min(90.0, point.latitude() + latitudeBuffer);
        double west = Math.max(-180.0, point.longitude() - longitudeBuffer);
        double east = Math.min(180.0, point.longitude() + longitudeBuffer);
        String bbox = String.format(
                Locale.ROOT,
                "%.7f,%.7f,%.7f,%.7f",
                west,
                south,
                east,
                north
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
                return CctvProximity.unavailable();
            }

            Double nearestDistance =
                    findNearestCctvDistance(response.getBody(), point);
            if (nearestDistance == null) {
                return CctvProximity.unavailable();
            }

            boolean present = nearestDistance <= CCTV_RADIUS_METERS;
            return new CctvProximity(
                    nearestDistance,
                    present,
                    present ? 1.0 : 0.5
            );
        } catch (RestClientException e) {
            LOGGER.warn("Unable to retrieve OSM CCTV data near {}, {}",
                    point.latitude(), point.longitude(), e);
            return CctvProximity.unavailable();
        } catch (ParserConfigurationException | SAXException
                 | IOException | NumberFormatException e) {
            LOGGER.warn("Unable to parse OSM CCTV data near {}, {}",
                    point.latitude(), point.longitude(), e);
            return CctvProximity.unavailable();
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

    private Double findNearestCctvDistance(
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

        Document document = factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(response))
        );
        Map<String, CoordinateDto> nodes = readNodes(document);
        double nearestDistance = Double.MAX_VALUE;
        boolean foundCctv = false;

        NodeList nodeElements = document.getElementsByTagName("node");
        for (int i = 0; i < nodeElements.getLength(); i++) {
            Element node = (Element) nodeElements.item(i);
            if (!hasCctvTags(node)) {
                continue;
            }

            CoordinateDto camera = nodes.get(node.getAttribute("id"));
            if (camera != null) {
                nearestDistance = Math.min(
                        nearestDistance,
                        distanceInMeters(point, camera)
                );
                foundCctv = true;
            }
        }

        NodeList ways = document.getElementsByTagName("way");
        for (int i = 0; i < ways.getLength(); i++) {
            Element way = (Element) ways.item(i);
            if (!hasCctvTags(way)) {
                continue;
            }

            NodeList references = way.getElementsByTagName("nd");
            CoordinateDto previous = null;
            for (int j = 0; j < references.getLength(); j++) {
                Element reference = (Element) references.item(j);
                CoordinateDto current = nodes.get(reference.getAttribute("ref"));
                if (current == null) {
                    previous = null;
                    continue;
                }

                double distance = previous == null
                        ? distanceInMeters(point, current)
                        : distanceToSegmentInMeters(point, previous, current);
                nearestDistance = Math.min(nearestDistance, distance);
                foundCctv = true;
                previous = current;
            }
        }

        return foundCctv ? nearestDistance : null;
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

    private boolean hasCctvTags(Element element) {
        boolean surveillanceFeature = false;
        boolean cameraType = false;
        NodeList tags = element.getElementsByTagName("tag");

        for (int i = 0; i < tags.getLength(); i++) {
            Element tag = (Element) tags.item(i);
            String key = tag.getAttribute("k");
            String value = tag.getAttribute("v");

            if ("man_made".equals(key) && "surveillance".equals(value)) {
                surveillanceFeature = true;
            } else if ("surveillance:type".equals(key)
                    && "camera".equals(value)) {
                cameraType = true;
            } else if ("camera:type".equals(key)
                    && !value.isBlank()) {
                cameraType = true;
            }
        }

        return surveillanceFeature || cameraType;
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

    private double distanceInMeters(
            CoordinateDto first,
            CoordinateDto second) {

        double latitudeDifference =
                Math.toRadians(second.latitude() - first.latitude());
        double longitudeDifference =
                Math.toRadians(second.longitude() - first.longitude());
        double firstLatitude = Math.toRadians(first.latitude());
        double secondLatitude = Math.toRadians(second.latitude());
        double a = Math.sin(latitudeDifference / 2)
                * Math.sin(latitudeDifference / 2)
                + Math.cos(firstLatitude)
                * Math.cos(secondLatitude)
                * Math.sin(longitudeDifference / 2)
                * Math.sin(longitudeDifference / 2);

        return EARTH_RADIUS_METERS * 2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );
    }

    public record CctvProximity(
            Double cctvDistance,
            boolean cctvPresent,
            double cctvIndex
    ) {
        public static CctvProximity unavailable() {
            return new CctvProximity(null, false, 0.5);
        }
    }
}
