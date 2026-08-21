package com.premchemicals.cleaningbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleMapsService {

    @Value("${GOOGLE_MAPS_API_KEY:}")
    private String apiKey;

    @Value("${store.latitude:26.4764}")
    private double storeLatitude;

    @Value("${store.longitude:80.3124}")
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
}
