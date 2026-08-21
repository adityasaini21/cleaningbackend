package com.premchemicals.cleaningbackend;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class TestCheckout {
    public static void main(String[] args) {
        try {
            String secret = "cleaning_backend_super_secret_key_2026";
            var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

            String token = Jwts.builder()
                    .setSubject("9795328275")
                    .claim("role", "ROLE_USER")
                    .claim("fullName", "Aditya Saini")
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 864000000))
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();

            System.out.println("🔥 Token: " + token);

            String jsonPayload = "{" +
                    "  \"shippingAddress\": \"123 Swarup Nagar, Kanpur\"," +
                    "  \"phoneNumber\": \"9795328275\"," +
                    "  \"pincode\": \"208002\"," +
                    "  \"paymentMethod\": \"ONLINE\"," +
                    "  \"items\": [" +
                    "    {" +
                    "      \"productId\": 2," +
                    "      \"quantity\": 5" +
                    "    }" +
                    "  ]" +
                    "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://cleaningbackend-production-cf45.up.railway.app/api/orders"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("==========================================");
            System.out.println("STATUS CODE: " + response.statusCode());
            System.out.println("RESPONSE BODY: " + response.body());
            System.out.println("==========================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
