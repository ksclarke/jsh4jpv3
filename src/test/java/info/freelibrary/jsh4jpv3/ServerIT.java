
package info.freelibrary.jsh4jpv3;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A test of the server's endpoints.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerIT {

    /** The location of the test server. */
    private static final String BASE_URL = "http://localhost:" + System.getProperty("server.port", "8888") + "/";

    /** The test code snippet. */
    private static final String CODE_SNIPPET = """
        var manifest = new Manifest("https://iiif.io/api/cookbook/recipe/0001-mvm-image/manifest",
          new Label("en", "Single Image Example"));
        var canvas = new Canvas(MinterFactory.getMinter(manifest)).setWidthHeight(1200, 1800);
        var imageContent =
          new ImageContent("https://iiif.io/api/presentation/2.1/example/fixtures/resources/page1-full.png");

        canvas.paintWith(imageContent.setWidthHeight(1200, 1800));
        manifest.setCanvases(canvas);

        System.out.println(manifest);
        """;

    /** An HTTP request body publisher. */
    private static final BodyPublisher BODY_PUBLISHER =
            HttpRequest.BodyPublishers.ofString("code=" + URLEncoder.encode(CODE_SNIPPET, StandardCharsets.UTF_8));

    /** An HTTP response body publisher. */
    private static final BodyHandler<String> BODY_HANDLER = HttpResponse.BodyHandlers.ofString();

    /** An HTTP client to use to test the server's endpoint. */
    private HttpClient myHttpClient;

    /**
     * Set up the testing environment.
     */
    @BeforeAll
    final void setupTestEnv() {
        myHttpClient = HttpClient.newHttpClient();
    }

    /**
     * Test sending an unsupported DELETE to the endpoint.
     */
    @Test
    final void testDeleteReq() {
        final HttpRequest request = HttpRequest.newBuilder().DELETE().uri(URI.create(BASE_URL)).build();
        final CompletableFuture<HttpResponse<String>> future = myHttpClient.sendAsync(request, BODY_HANDLER);

        future.thenAccept(response -> {
            Assertions.assertEquals(405, response.statusCode());
            Assertions.assertEquals("Method Not Allowed", response.body());
        });

        // Block until the response arrives
        future.join();
    }

    /**
     * Test sending a valid POST to the endpoint.
     */
    @Test
    final void testValidPOST() {
        final Builder request = HttpRequest.newBuilder().POST(BODY_PUBLISHER).uri(URI.create(BASE_URL + "/submit"));
        final CompletableFuture<HttpResponse<String>> future = myHttpClient.sendAsync(request.build(), BODY_HANDLER);

        future.thenAccept(response -> {
            // System.out.println(response.statusCode());
            System.out.println(response.body());
        });

        // Block until the response arrives
        future.join();
    }

    /**
     * Test sending an invalid POST to the endpoint.
     */
    @Test
    final void testInvalidPOST() {
        final HttpRequest request = HttpRequest.newBuilder().POST(BODY_PUBLISHER).uri(URI.create(BASE_URL)).build();
        final CompletableFuture<HttpResponse<String>> future = myHttpClient.sendAsync(request, BODY_HANDLER);

        future.thenAccept(response -> {
            Assertions.assertEquals(404, response.statusCode());
            Assertions.assertEquals("Not Found", response.body());
        });

        // Block until the response arrives
        future.join();
    }
}
