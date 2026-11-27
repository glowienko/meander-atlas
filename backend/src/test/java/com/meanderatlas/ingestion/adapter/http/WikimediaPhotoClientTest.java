package com.meanderatlas.ingestion.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meanderatlas.ingestion.domain.enrichment.OpenPoiCatalogPort.PhotoLookupResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WikimediaPhotoClientTest {

    private static EnrichmentProperties testProperties() {
        return new EnrichmentProperties(
                true,
                25,
                0,
                2,
                1000,
                5000,
                "https://overpass.example/api/interpreter",
                "https://photon.example/api",
                "https://commons.example/w/api.php",
                "https://{lang}.wikipedia.example/api/rest_v1",
                "https://wikidata.example/w/api.php",
                "MeanderAtlas/0.1-test");
    }

    @Test
    void resolvesCommonsWikipediaWikidataAndSafeImageWhileRejectingExternalUrls() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikimediaPhotoClient client = new WikimediaPhotoClient(builder, testProperties());

        server.expect(
                        requestTo(
                                "https://commons.example/w/api.php?action=query&titles=File:Wawel.jpg&prop=imageinfo&iiprop=url&iiurlwidth=640&format=json&formatversion=2"))
                .andExpect(header(HttpHeaders.USER_AGENT, "MeanderAtlas/0.1-test"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "query": {
                                    "pages": [
                                      {
                                        "imageinfo": [
                                          { "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/w/Wawel.jpg" }
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        PhotoLookupResult commons = client.resolve(Map.of("wikimedia_commons", "File:Wawel.jpg"));
        server.verify();
        server.reset();
        assertThat(commons.photoUrl())
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/thumb/w/Wawel.jpg");
        assertThat(commons.source()).isEqualTo("wikimedia_commons");

        server.expect(
                        requestTo(
                                "https://en.wikipedia.example/api/rest_v1/page/summary/Wawel_Castle"))
                .andExpect(header(HttpHeaders.USER_AGENT, "MeanderAtlas/0.1-test"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "thumbnail": { "source": "https://upload.wikimedia.org/wikipedia/commons/thumb/w/WikiWawel.jpg" }
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        PhotoLookupResult wikipedia = client.resolve(Map.of("wikipedia", "en:Wawel Castle"));
        server.verify();
        server.reset();
        assertThat(wikipedia.photoUrl())
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/thumb/w/WikiWawel.jpg");
        assertThat(wikipedia.source()).isEqualTo("wikipedia");

        server.expect(
                        requestTo(
                                "https://wikidata.example/w/api.php?action=wbgetentities&ids=Q12345&props=claims&format=json"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "entities": {
                                    "Q12345": {
                                      "claims": {
                                        "P18": [
                                          { "mainsnak": { "datavalue": { "value": "Castle_P18.jpg" } } }
                                        ]
                                      }
                                    }
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON));
        server.expect(
                        requestTo(
                                "https://commons.example/w/api.php?action=query&titles=File:Castle_P18.jpg&prop=imageinfo&iiprop=url&iiurlwidth=640&format=json&formatversion=2"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "query": {
                                    "pages": {
                                      "42": {
                                        "imageinfo": [
                                          { "url": "https://upload.wikimedia.org/wikipedia/commons/c/Castle_P18.jpg" }
                                        ]
                                      }
                                    }
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        PhotoLookupResult wikidata = client.resolve(Map.of("wikidata", "Q12345"));
        server.verify();
        server.reset();
        assertThat(wikidata.photoUrl())
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/c/Castle_P18.jpg");
        assertThat(wikidata.source()).isEqualTo("wikidata");

        PhotoLookupResult safeImage =
                client.resolve(
                        Map.of(
                                "image",
                                "https://upload.wikimedia.org/wikipedia/commons/a/Safe.jpg"));
        assertThat(safeImage.photoUrl())
                .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/a/Safe.jpg");

        PhotoLookupResult rejectedExternal =
                client.resolve(Map.of("image", "https://evil.example.com/photo.jpg"));
        assertThat(rejectedExternal.photoUrl()).isNull();

        server.expect(
                        requestTo(
                                "https://en.wikipedia.example/api/rest_v1/page/summary/Missing_Page"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        PhotoLookupResult failedLookup = client.resolve(Map.of("wikipedia", "en:Missing Page"));
        assertThat(failedLookup.photoUrl()).isNull();

        server.verify();
    }
}
