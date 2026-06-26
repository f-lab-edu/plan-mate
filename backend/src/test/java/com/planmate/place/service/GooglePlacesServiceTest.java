package com.planmate.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.PlaceTextSearchRequest;
import com.planmate.place.dto.PlaceTextSearchResponse;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.place.exception.InvalidPlaceIdException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

class GooglePlacesServiceTest {

    @Test
    void resolveDestinationReturnsInternalDto() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesService service = new GooglePlacesService(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places/place-kyoto")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-FieldMask", GooglePlacesService.DESTINATION_DETAILS_FIELD_MASK))
                .andRespond(withSuccess(fixture("google/place-details-kyoto.json"), MediaType.APPLICATION_JSON));

        ResolvedDestination destination = service.resolveDestination("place-kyoto", "ko");

        assertThat(destination.placeId()).isEqualTo("place-kyoto");
        assertThat(destination.displayName()).isEqualTo("Kyoto");
        assertThat(destination.location().latitude()).isEqualTo(35.0116);
        assertThat(destination.viewport()).isNotNull();
        server.verify();
    }

    @Test
    void resolveDestinationRejectsInvalidPlaceId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesService service = new GooglePlacesService(builder, "test-key", 30000);

        server.expect(requestTo(containsString("/places/bad-place")))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> service.resolveDestination("bad-place", "ko"))
                .isInstanceOf(InvalidPlaceIdException.class);
        server.verify();
    }

    @Test
    void searchTextSendsLocationRestrictionAndReturnsCandidates() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlacesService service = new GooglePlacesService(builder, "test-key", 30000);
        ResolvedDestination destination = new ResolvedDestination(
                "place-kyoto",
                "Kyoto",
                "Kyoto, Japan",
                new GeoPoint(35.0116, 135.7681),
                new GeoViewport(new GeoPoint(34.8, 135.5), new GeoPoint(35.2, 136.0)),
                List.of("locality"),
                "locality"
        );

        server.expect(requestTo(containsString("/places:searchText")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-FieldMask", GooglePlacesService.TEXT_SEARCH_FIELD_MASK))
                .andExpect(jsonPath("$.textQuery").value("Kyoto popular attractions"))
                .andExpect(jsonPath("$.locationRestriction.rectangle.low.latitude").value(34.8))
                .andRespond(withSuccess(fixture("google/text-search-kyoto.json"), MediaType.APPLICATION_JSON));

        PlaceTextSearchResponse response = service.searchText(new PlaceTextSearchRequest(
                "Kyoto popular attractions",
                "ko",
                20,
                destination,
                null
        ));

        assertThat(response.places()).hasSize(1);
        assertThat(response.places().getFirst().placeId()).isEqualTo("candidate-1");
        assertThat(response.nextPageToken()).isEqualTo("next-token");
        server.verify();
    }

    private String fixture(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
