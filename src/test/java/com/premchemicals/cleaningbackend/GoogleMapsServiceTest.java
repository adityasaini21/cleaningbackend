package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.service.GoogleMapsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GoogleMapsServiceTest {

    private final GoogleMapsService googleMapsService = new GoogleMapsService();

    @Test
    @DisplayName("Verify cleanStreetAddress accurately strips city, state, country and pincode")
    void testCleanStreetAddress() {
        String formatted = "120/45, Block 5, Govind Nagar, Kanpur, Uttar Pradesh 208006, India";
        String cleaned = googleMapsService.cleanStreetAddress(formatted, "Kanpur", "Uttar Pradesh");
        assertEquals("120/45, Block 5, Govind Nagar", cleaned);

        String coarse = "Govind Nagar, Kanpur, Uttar Pradesh, India";
        String cleanedCoarse = googleMapsService.cleanStreetAddress(coarse, "Kanpur", "Uttar Pradesh");
        assertEquals("Govind Nagar", cleanedCoarse);

        String plusCode = "7MR3+2G Kanpur, Uttar Pradesh 208002, India";
        String cleanedPlus = googleMapsService.cleanStreetAddress(plusCode, "Kanpur", "Uttar Pradesh");
        assertTrue(cleanedPlus.isEmpty());
    }

    @Test
    @DisplayName("Verify India Post official registry refines Govind Nagar pincode to 208006")
    void testRefinePincodeWithIndiaPost() {
        String refined = googleMapsService.refinePincodeWithIndiaPost("Govind Nagar", "Kanpur", "Uttar Pradesh", "208002");
        assertEquals("208006", refined, "India Post API must resolve Govind Nagar Kanpur to 208006");
    }

    @Test
    @DisplayName("Verify Kanpur locality fallback correctly maps Govind Nagar to 208006")
    void testRefineKanpurPincodeFallback() {
        String refined = googleMapsService.refineKanpurPincode("Govind Nagar", "Block 5", "120/45 Block 5", "208002");
        assertEquals("208006", refined);

        String kidwai = googleMapsService.refineKanpurPincode("Kidwai Nagar", "", "Block Y", "208002");
        assertEquals("208011", kidwai);
    }

    @Test
    @DisplayName("Verify parseGeocodingResults preserves lane number and street details from results[1]")
    void testParseGeocodingResultsPreservesLaneAndPlotNumber() throws Exception {
        String json = """
        [
          {
            "formatted_address": "Govind Nagar, Kanpur, Uttar Pradesh 208006, India",
            "types": ["sublocality_level_1"],
            "address_components": [
              { "long_name": "Govind Nagar", "types": ["sublocality_level_1", "sublocality"] },
              { "long_name": "Kanpur", "types": ["locality"] },
              { "long_name": "Uttar Pradesh", "types": ["administrative_area_level_1"] },
              { "long_name": "208002", "types": ["postal_code"] }
            ]
          },
          {
            "formatted_address": "120/45, Lane No. 4, Block 5, Govind Nagar, Kanpur, Uttar Pradesh 208006, India",
            "types": ["street_address"],
            "address_components": [
              { "long_name": "120/45", "types": ["street_number"] },
              { "long_name": "Lane No. 4", "types": ["route"] },
              { "long_name": "Block 5", "types": ["sublocality_level_2"] },
              { "long_name": "Govind Nagar", "types": ["sublocality_level_1"] }
            ]
          }
        ]
        """;

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);

        java.util.Map<String, Object> result = googleMapsService.parseGeocodingResults(root);
        assertNotNull(result);

        String address = (String) result.get("address");
        assertTrue(address.contains("Lane No. 4"), "Address must preserve Lane No. 4 from secondary results. Got: " + address);
        assertTrue(address.contains("120/45"), "Address must preserve house number 120/45. Got: " + address);
        assertTrue(address.contains("Block 5"), "Address must preserve Block 5. Got: " + address);
        assertEquals("208006", result.get("pincode"), "Pincode must be verified to 208006");
    }
}
