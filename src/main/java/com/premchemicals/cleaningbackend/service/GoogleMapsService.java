package com.premchemicals.cleaningbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleMapsService {

    @Value("${google.maps.api-key:${GOOGLE_MAPS_API_KEY:}}")
    private String apiKey;

    @Value("${store.latitude:26.4502508}")
    private double storeLatitude;

    @Value("${store.longitude:80.2958044}")
    private double storeLongitude;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Calculates the road driving distance in meters using Google Maps Routes API.
     * Falls back to mathematical Haversine distance if the API call fails or apiKey is missing.
     */
    public double getRoadDistanceInMeters(double customerLatitude, double customerLongitude) {
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠️ Google Maps API Key is missing. Falling back to straight-line Haversine calculation.");
            return calculateHaversineDistance(storeLatitude, storeLongitude, customerLatitude, customerLongitude);
        }

        try {
            String requestBody = String.format(
                "{" +
                "  \"origin\": {\"location\": {\"latLng\": {\"latitude\": %f, \"longitude\": %f}}}," +
                "  \"destination\": {\"location\": {\"latLng\": {\"latitude\": %f, \"longitude\": %f}}}," +
                "  \"travelMode\": \"DRIVE\"," +
                "  \"routingPreference\": \"TRAFFIC_UNAWARE\"" +
                "}",
                storeLatitude, storeLongitude, customerLatitude, customerLongitude
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://routes.googleapis.com/directions/v2:computeRoutes"))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", apiKey.trim())
                    .header("X-Goog-FieldMask", "routes.distanceMeters")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                // Simple regex to parse routes[0].distanceMeters from JSON to avoid adding bulky dependency
                Pattern pattern = Pattern.compile("\"distanceMeters\"\\s*:\\s*(\\d+)");
                Matcher matcher = pattern.matcher(body);
                if (matcher.find()) {
                    System.out.println("✅ [Google Maps] Route API hit successfully! Road distance: " + matcher.group(1) + " meters.");
                    return Double.parseDouble(matcher.group(1));
                }
            } else {
                System.out.println("⚠️ Google Routes API returned error status: " + response.statusCode() + ", body: " + response.body());
            }

        } catch (Exception e) {
            System.out.println("⚠️ Error calling Google Routes API: " + e.getMessage());
        }

        // Fallback to straight line (Haversine) distance in case of API failure
        System.out.println("⚠️ Falling back to straight-line Haversine calculation.");
        return calculateHaversineDistance(storeLatitude, storeLongitude, customerLatitude, customerLongitude);
    }

    /**
     * Calculates straight-line distance in meters between two coordinates.
     */
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // Return in meters
    }

    /**
     * Converts a text address/pincode into coordinates {latitude, longitude} using Google Geocoding API.
     * Returns null if it fails.
     */
    public double[] getCoordinatesFromAddress(String address, String pincode) {
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠️ Google Maps API Key is missing. Cannot perform geocoding.");
            return null;
        }

        try {
            String fullQuery = (address != null ? address : "") + " " + (pincode != null ? pincode : "") + ", Kanpur, Uttar Pradesh, India";
            String encodedQuery = URLEncoder.encode(fullQuery, StandardCharsets.UTF_8);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://maps.googleapis.com/maps/api/geocode/json?address=" + encodedQuery + "&key=" + apiKey.trim()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                Pattern latPattern = Pattern.compile("\"lat\"\\s*:\\s*(-?\\d+\\.?\\d*)");
                Pattern lngPattern = Pattern.compile("\"lng\"\\s*:\\s*(-?\\d+\\.?\\d*)");
                
                Matcher latMatcher = latPattern.matcher(body);
                Matcher lngMatcher = lngPattern.matcher(body);
                
                if (latMatcher.find() && lngMatcher.find()) {
                    double lat = Double.parseDouble(latMatcher.group(1));
                    double lng = Double.parseDouble(lngMatcher.group(1));
                    System.out.println("✅ [Google Maps] Geocoding API hit successfully! Coordinates: lat=" + lat + ", lng=" + lng);
                    return new double[]{lat, lng};
                }
            } else {
                System.out.println("⚠️ Google Geocoding API returned status: " + response.statusCode());
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error during Google Geocoding: " + e.getMessage());
        }
        return null;
    }

    /**
     * Converts coordinates {latitude, longitude} into address components using Google Geocoding API,
     * cross-referenced with India Post official registry for 100% postal code accuracy.
     * Returns null if it fails or API key is missing.
     */
    public Map<String, Object> reverseGeocode(double latitude, double longitude) {
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠️ Google Maps API Key is missing. Cannot perform reverse geocoding.");
            return null;
        }

        try {
            String url = String.format(
                Locale.US,
                "https://maps.googleapis.com/maps/api/geocode/json?latlng=%.7f,%.7f&key=%s&language=en",
                latitude, longitude, apiKey.trim()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());
                String status = root.path("status").asText();

                if ("OK".equalsIgnoreCase(status) && root.path("results").isArray() && root.path("results").size() > 0) {
                    return parseGeocodingResults(root.path("results"));
                } else {
                    System.out.println("⚠️ Google Reverse Geocoding returned status: " + status + ", error: " + root.path("error_message").asText(""));
                }
            } else {
                System.out.println("⚠️ Google Geocoding API returned HTTP status: " + response.statusCode());
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error during Google Reverse Geocoding: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parses Google Geocoding JSON results across results[0], results[1], and results[2]
     * to preserve specific lane/route numbers, plot numbers, premises, and blocks without loss.
     */
    public Map<String, Object> parseGeocodingResults(JsonNode results) {
        if (results == null || !results.isArray() || results.size() == 0) {
            return null;
        }

        JsonNode primary = results.get(0);
        String primaryFormatted = primary.path("formatted_address").asText("");

        String subpremise = "";
        String premise = "";
        String streetNumber = "";
        String route = "";
        String sublocality3 = "";
        String sublocality2 = "";
        String sublocality1 = "";
        String locality = "";
        String administrativeArea = "";
        String postalCode = "";

        // Track the most detailed street-level formatted address from results
        String bestFormattedAddress = primaryFormatted;
        boolean foundStreetLevelFormatted = false;

        // Scan results[0], results[1], and results[2] to capture lane number (route) and street details
        for (int i = 0; i < results.size(); i++) {
            JsonNode r = results.get(i);
            List<String> resultTypes = new ArrayList<>();
            r.path("types").forEach(t -> resultTypes.add(t.asText()));

            String currentFormatted = r.path("formatted_address").asText("");
            if (!foundStreetLevelFormatted && (resultTypes.contains("street_address") || resultTypes.contains("premise"))) {
                if (!currentFormatted.isBlank() && !currentFormatted.matches("^[A-Z0-9]{4,8}\\+[A-Z0-9]{2,}.*")) {
                    bestFormattedAddress = currentFormatted;
                    foundStreetLevelFormatted = true;
                }
            }

            JsonNode compList = r.path("address_components");
            if (compList.isArray()) {
                for (JsonNode comp : compList) {
                    String longName = comp.path("long_name").asText("").trim();
                    List<String> types = new ArrayList<>();
                    comp.path("types").forEach(t -> types.add(t.asText()));

                    if (subpremise.isEmpty() && types.contains("subpremise")) {
                        subpremise = longName;
                    } else if (premise.isEmpty() && types.contains("premise")) {
                        premise = longName;
                    } else if (streetNumber.isEmpty() && types.contains("street_number")) {
                        streetNumber = longName;
                    } else if ((route.isEmpty() || route.toLowerCase().contains("unnamed")) && types.contains("route")) {
                        if (!longName.toLowerCase().contains("unnamed")) {
                            route = longName;
                        }
                    } else if (sublocality3.isEmpty() && types.contains("sublocality_level_3")) {
                        sublocality3 = longName;
                    } else if (sublocality2.isEmpty() && types.contains("sublocality_level_2")) {
                        sublocality2 = longName;
                    } else if (sublocality1.isEmpty() && (types.contains("sublocality_level_1") || types.contains("sublocality") || types.contains("neighborhood"))) {
                        sublocality1 = longName;
                    } else if (locality.isEmpty() && types.contains("locality")) {
                        locality = longName;
                    } else if (administrativeArea.isEmpty() && types.contains("administrative_area_level_1")) {
                        administrativeArea = longName;
                    } else if (postalCode.isEmpty() && types.contains("postal_code")) {
                        postalCode = longName.replaceAll("[^0-9]", "");
                    }
                }
            }
        }

        // Build address parts cleanly: [subpremise, premise, streetNumber, route, sublocality3, sublocality2, sublocality1]
        List<String> addressParts = new ArrayList<>();
        if (!subpremise.isEmpty()) addressParts.add(subpremise);
        if (!premise.isEmpty() && !addressParts.contains(premise)) addressParts.add(premise);
        if (!streetNumber.isEmpty() && !addressParts.contains(streetNumber)) addressParts.add(streetNumber);
        if (!route.isEmpty() && !route.toLowerCase().contains("unnamed") && !addressParts.contains(route)) addressParts.add(route);
        if (!sublocality3.isEmpty() && !addressParts.contains(sublocality3)) addressParts.add(sublocality3);
        if (!sublocality2.isEmpty() && !addressParts.contains(sublocality2)) addressParts.add(sublocality2);
        if (!sublocality1.isEmpty() && !addressParts.contains(sublocality1)) addressParts.add(sublocality1);

        String detailedAddress = String.join(", ", addressParts);

        // Clean street address from formatted_address
        String cleanedStreet = cleanStreetAddress(bestFormattedAddress, locality, administrativeArea);

        // Ensure lane/route and street details are never omitted
        String resolvedAddress;
        if (!route.isEmpty() && !cleanedStreet.toLowerCase().contains(route.toLowerCase())) {
            // cleanedStreet missed the lane number (e.g. was just the broad locality) -> use detailedAddress
            resolvedAddress = !detailedAddress.isEmpty() ? detailedAddress : cleanedStreet;
        } else if (!streetNumber.isEmpty() && !cleanedStreet.toLowerCase().contains(streetNumber.toLowerCase())) {
            // cleanedStreet missed the house/street number -> use detailedAddress
            resolvedAddress = !detailedAddress.isEmpty() ? detailedAddress : cleanedStreet;
        } else if (!cleanedStreet.isEmpty() && !cleanedStreet.equalsIgnoreCase(sublocality1) && !cleanedStreet.equalsIgnoreCase(sublocality2)) {
            // cleanedStreet has specific details
            resolvedAddress = cleanedStreet;
        } else {
            resolvedAddress = !detailedAddress.isEmpty() ? detailedAddress : (cleanedStreet.isEmpty() ? bestFormattedAddress : cleanedStreet);
        }

        String landmark = !sublocality2.isEmpty() ? sublocality2 : (!sublocality1.isEmpty() ? sublocality1 : (!route.isEmpty() ? route : premise));

        // Option 1: Cross-verify & refine postal code with official India Post registry
        String refinedPincode = refinePincodeWithIndiaPost(sublocality1, locality, administrativeArea, postalCode);
        if ((refinedPincode == null || refinedPincode.equals(postalCode)) && !sublocality2.isEmpty()) {
            refinedPincode = refinePincodeWithIndiaPost(sublocality2, locality, administrativeArea, postalCode);
        }
        if (refinedPincode != null && refinedPincode.length() == 6) {
            postalCode = refinedPincode;
        }

        // Fast locality fallback for known Kanpur delivery hubs
        postalCode = refineKanpurPincode(sublocality1, sublocality2, resolvedAddress, postalCode);

        Map<String, Object> result = new HashMap<>();
        result.put("address", resolvedAddress);
        result.put("landmark", landmark);
        result.put("pincode", postalCode);
        result.put("city", locality);
        result.put("state", administrativeArea);
        result.put("fullFormattedAddress", bestFormattedAddress);
        result.put("source", "Option 1: Google Maps Geocoding API + India Post (Backend)");

        return result;
    }

    /**
     * Cleans formatted address string by stripping trailing country, state, pincode, and city,
     * leaving the natural street/house/colony address.
     */
    public String cleanStreetAddress(String formattedAddress, String locality, String administrativeArea) {
        if (formattedAddress == null || formattedAddress.isBlank()) {
            return "";
        }

        String cleaned = formattedAddress.trim();

        // 1. Strip plus code if present at the start (e.g. "7MR3+2G Kanpur, ...")
        cleaned = cleaned.replaceAll("^[A-Z0-9]{4,8}\\+[A-Z0-9]{2,},?\\s*", "").trim();

        // 2. Strip trailing country ", India"
        cleaned = cleaned.replaceAll(",?\\s*India\\s*$", "").trim();

        // 3. Strip trailing pincode & state (e.g. ", Uttar Pradesh 208006", ", Uttar Pradesh", ", 208006")
        if (administrativeArea != null && !administrativeArea.isBlank()) {
            cleaned = cleaned.replaceAll(",?\\s*" + Pattern.quote(administrativeArea) + "(\\s*\\d{6})?\\s*$", "").trim();
        }
        cleaned = cleaned.replaceAll(",?\\s*\\b\\d{6}\\b\\s*$", "").trim();

        // 4. Strip trailing city/locality (e.g. ", Kanpur Nagar", ", Kanpur")
        if (locality != null && !locality.isBlank()) {
            cleaned = cleaned.replaceAll(",?\\s*" + Pattern.quote(locality) + "(\\s+Nagar)?\\s*$", "").trim();
        }
        cleaned = cleaned.replaceAll(",?\\s*Kanpur(\\s+Nagar)?\\s*$", "").trim();

        // Clean any trailing commas
        cleaned = cleaned.replaceAll(",+\\s*$", "").trim();

        return cleaned;
    }

    /**
     * Queries the official India Post API to refine the pincode based on sublocality and locality/city.
     * Returns the 6-digit postal code if verified, or the fallback pincode if not found or on timeout/error.
     */
    public String refinePincodeWithIndiaPost(String sublocality, String locality, String administrativeArea, String fallbackPincode) {
        if (sublocality == null || sublocality.isBlank()) {
            return fallbackPincode;
        }

        try {
            // Encode sublocality with %20 (India Post API returns 404 with '+')
            String encodedSublocality = URLEncoder.encode(sublocality.trim(), StandardCharsets.UTF_8).replace("+", "%20");
            URI uri = URI.create("https://api.postalpincode.in/postoffice/" + encodedSublocality);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "NuKleanBackend/1.0")
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());
                if (root.isArray() && root.size() > 0) {
                    JsonNode firstNode = root.get(0);
                    if ("Success".equalsIgnoreCase(firstNode.path("Status").asText())) {
                        JsonNode poList = firstNode.path("PostOffice");
                        if (poList.isArray() && poList.size() > 0) {
                            String normCity = locality != null ? locality.toLowerCase().replaceAll("[^a-z0-9]", "") : "";
                            String normState = administrativeArea != null ? administrativeArea.toLowerCase().replaceAll("[^a-z0-9]", "") : "";

                            for (JsonNode po : poList) {
                                String poDistrict = po.path("District").asText("").toLowerCase().replaceAll("[^a-z0-9]", "");
                                String poDivision = po.path("Division").asText("").toLowerCase().replaceAll("[^a-z0-9]", "");
                                String poName = po.path("Name").asText("").toLowerCase().replaceAll("[^a-z0-9]", "");
                                String poState = po.path("State").asText("").toLowerCase().replaceAll("[^a-z0-9]", "");

                                boolean stateMatch = normState.isEmpty() || poState.contains(normState) || normState.contains(poState);
                                boolean cityMatch = normCity.isEmpty() || poDistrict.contains(normCity) || normCity.contains(poDistrict)
                                        || poDivision.contains(normCity) || normCity.contains(poDivision)
                                        || poName.contains(normCity);

                                if (stateMatch && cityMatch) {
                                    String exactPin = po.path("Pincode").asText("").replaceAll("[^0-9]", "").trim();
                                    if (exactPin.length() == 6) {
                                        return exactPin;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Silently fallback without logging customer data
        }

        return fallbackPincode;
    }

    private static final Map<String, String> KANPUR_PINCODE_MAP = Map.ofEntries(
            Map.entry("govind nagar", "208006"),
            Map.entry("kidwai nagar", "208011"),
            Map.entry("kakadeo", "208025"),
            Map.entry("kalyanpur", "208017"),
            Map.entry("swarup nagar", "208002"),
            Map.entry("arya nagar", "208002"),
            Map.entry("civil lines", "208001"),
            Map.entry("shastri nagar", "208005"),
            Map.entry("barra", "208027"),
            Map.entry("yashoda nagar", "208011"),
            Map.entry("rawatpur", "208019"),
            Map.entry("panki", "208020"),
            Map.entry("chakeri", "208007"),
            Map.entry("fazalganj", "208012"),
            Map.entry("dada nagar", "208022"),
            Map.entry("vijay nagar", "208005"),
            Map.entry("ashok nagar", "208012"),
            Map.entry("harsh nagar", "208012"),
            Map.entry("cantt", "208004"),
            Map.entry("armapur", "208009"),
            Map.entry("shyam nagar", "208013"),
            Map.entry("naubasta", "208021")
    );

    /**
     * Refines postal code for known Kanpur localities if India Post or Google provided a generic code.
     */
    public String refineKanpurPincode(String sublocality1, String sublocality2, String resolvedAddress, String currentPincode) {
        String combined = ((sublocality1 != null ? sublocality1 : "") + " " +
                           (sublocality2 != null ? sublocality2 : "") + " " +
                           (resolvedAddress != null ? resolvedAddress : "")).toLowerCase();

        for (Map.Entry<String, String> entry : KANPUR_PINCODE_MAP.entrySet()) {
            if (combined.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return currentPincode;
    }
}
