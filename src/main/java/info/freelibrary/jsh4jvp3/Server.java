
package info.freelibrary.jsh4jvp3;

import static info.freelibrary.jsh4jvp3.HttpResponse.METHOD_NOT_ALLOWED;
import static info.freelibrary.jsh4jvp3.HttpResponse.NOT_FOUND;
import static info.freelibrary.jsh4jvp3.HttpResponse.OK;
import static info.freelibrary.util.Constants.EMPTY;
import static info.freelibrary.util.Constants.EOL;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import org.microhttp.DebugLogger;
import org.microhttp.EventLoop;
import org.microhttp.Handler;
import org.microhttp.Header;
import org.microhttp.Options;
import org.microhttp.Request;
import org.microhttp.Response;

import info.freelibrary.util.Env;

import jdk.jshell.JShell;
import jdk.jshell.JShellException;
import jdk.jshell.Snippet;

/**
 * A server that evaluates JPv3 code snippets using JShell.
 */
public class Server {

    /** The host address on which the server will listen. */
    private static final String ANY_ADDRESS = "0.0.0.0";

    /** The default port at which the server listens. */
    private static final int DEFAULT_PORT = 8888;

    /** The request timeout. */
    private static final long DEFAULT_REQ_TIMEOUT = 60L;

    /** The read buffer size. */
    private static final int DEFAULT_READ_BUF_SIZE = 1_024 * 64;

    /** The maximum request size. */
    private static final int DEFAULT_MAX_REQ_SIZE = 1_024 * 1_024;

    /**
     * Runs the server.
     *
     * @param anArgsArray An array of arguments
     * @throws IOException If there is trouble starting the server
     */
    public static void main(final String[] anArgsArray) throws IOException, InterruptedException {
        final EventLoop eventLoop = new EventLoop(getOptions(), new DebugLogger(), new JPv3Handler());

        eventLoop.start();
        eventLoop.join();
    }

    /**
     * Gets the configuration of the event loop.
     *
     * @return An event loop configuration
     */
    private static final Options getOptions() {
        final int port = Env.get(Config.HTTP_PORT, DEFAULT_PORT);
        final int reqSize = Env.get(Config.MAX_REQUEST_SIZE, DEFAULT_MAX_REQ_SIZE);
        final int bufSize = Env.get(Config.READ_BUFFER_SIZE, DEFAULT_READ_BUF_SIZE);
        final long timeout = (long) Env.get(Config.REQUEST_TIMEOUT, DEFAULT_REQ_TIMEOUT);

        return Options.builder().withHost(ANY_ADDRESS).withPort(port).withMaxRequestSize(reqSize)
                .withRequestTimeout(Duration.ofSeconds(timeout)).withReadBufferSize(bufSize).build();
    }

    /**
     * An event handler for code evaluation requests.
     */
    private static class JPv3Handler implements Handler {

        /** The response headers that are returned for plain text responses. */
        private static final List<Header> TEXT_CONTENT_TYPE = List.of(new Header("Content-Type", "text/plain"));

        /** The response headers that are returned. */
        private static final List<Header> HTML_CONTENT_TYPE = List.of(new Header("Content-Type", "text/html"));

        /** The delimiter that indicates a submitted code block. */
        private static final String CODE_DELIM = "code=";

        /** An empty response body. */
        private static final byte[] EMPTY_BODY = {};

        /** A cached {@code WebResource}. */
        private final byte[] myHTML;

        /** The Java shell environment. */
        private final JShell myShell;

        /** An output stream for the shell. */
        private JShellOutputStream myOutputStream;

        /**
         * Creates a new {@code JPv3Handler}.
         *
         * @throws IOException If there is trouble reading the {@code WebResource}
         */
        public JPv3Handler() throws IOException {
            myShell = JShell.builder().compilerOptions("--enable-preview", "--source", "21").build();
            myHTML = new WebResource("index.html").getBytes();

            // Problem only if this is here, not if in handle() below. TODO: run diag for each event
            try {
                listImports().forEach(myShell::eval);
            } catch (final IOException details) {
                System.err.println(details);
            }

            System.setOut(new PrintStream(myOutputStream = new JShellOutputStream()));
        }

        @Override
        public void handle(final Request aRequest, final Consumer<Response> aCallback) {
            final Response response;

            switch (aRequest.method()) {
                case "POST" -> {
                    final String uri = aRequest.uri();

                    if (uri.endsWith("submit") || uri.endsWith("submit/")) {
                        final StringBuilder submission = new StringBuilder();
                        final StringBuilder result = new StringBuilder();

                        submission.append(decode(aRequest.body()));

                        try {
                            final String code = submission.toString();

                            // Evaluate the submitted code snippet and return a result
                            myShell.eval(code).forEach(event -> {
                                if (Snippet.Status.VALID.equals(event.status())) {
                                    result.append(event.snippet().source().trim());
                                    result.append(EOL).append(EOL).append("-> " + myOutputStream.toString());
                                } else {
                                    final JShellException details = event.exception();

                                    if (details != null) {
                                        result.append(EOL).append("ERROR: ").append(EOL).append(details.getMessage());
                                    } else {
                                        final Snippet failedSnippet = event.snippet();

                                        // Output some useful error information so the issue can be fixed
                                        result.append(EOL).append(event.status()).append(EOL);
                                        result.append(failedSnippet.source()).append(EOL);

                                        myShell.diagnostics(failedSnippet).forEach(diag -> {
                                            if (diag.isError()) {
                                                result.append(diag.getMessage(null)).append(EOL);
                                            }
                                        });
                                    }
                                }
                            });
                        } catch (final Throwable details) {
                            result.append(details);
                        }

                        response = getResponse(OK, TEXT_CONTENT_TYPE, result.toString().getBytes(UTF_8));
                    } else {
                        response = getResponse(NOT_FOUND, TEXT_CONTENT_TYPE, EMPTY_BODY);
                    }
                }
                case "GET" -> {
                    final String uri = aRequest.uri();

                    if (uri.endsWith("editor") || uri.endsWith("editor/")) {
                        response = getResponse(OK, HTML_CONTENT_TYPE, myHTML);
                    } else {
                        response = getResponse(NOT_FOUND, TEXT_CONTENT_TYPE, EMPTY_BODY);
                    }
                }
                default -> {
                    response = getResponse(METHOD_NOT_ALLOWED, TEXT_CONTENT_TYPE, EMPTY_BODY);
                }
            }

            aCallback.accept(response);
        }

        /**
         * Decodes the code submission so that it can be evaluated.
         *
         * @param aSubmission An encoded code submission
         * @return A decoded code submission
         */
        private String decode(final byte[] aSubmission) {
            final String data = new String(aSubmission, UTF_8);

            if (data.startsWith(CODE_DELIM)) {
                return URLDecoder.decode(data.substring(CODE_DELIM.length()), UTF_8);
            }

            // If submission wasn't valid, just return an empty string which will evaluate to nothing
            return EMPTY;
        }

        /**
         * Gets a list of Java imports.
         *
         * @return A list of Java imports
         * @throws IOException If there is trouble reading the imports file
         */
        private final List<String> listImports() throws IOException {
            File importsFile = new File("/etc/jshell/imports.jsh");

            // Check to see if we're running from the Docker container or the Maven build
            if (!importsFile.exists()) {
                importsFile = new File("src/main/docker/imports.jsh");
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(importsFile))) {
                return reader.lines().filter(line -> !line.isBlank()).map(line -> line + EOL).toList();
            }
        }

        /**
         * Creates a server response from the supplied parameters.
         *
         * @param aEnum An HTTP response enum
         * @param aHeaderList A list of response headers
         * @param aBody A response body
         * @return The newly constructed response
         */
        private Response getResponse(final HttpResponse aEnum, final List<Header> aHeaderList, final byte[] aBody) {
            return new Response(aEnum.getCode(), aEnum.getMessage(), aHeaderList, aBody);
        }
    }

    /**
     * An output stream for JShell.
     */
    static final class JShellOutputStream extends OutputStream {

        /** A wrapped output byte stream. */
        private final ByteArrayOutputStream myByteStream;

        /**
         * Creates a new output stream.
         */
        JShellOutputStream() {
            myByteStream = new ByteArrayOutputStream();
        }

        @Override
        public void write(final int aByte) throws IOException {
            myByteStream.write(aByte);
        }

        @Override
        public void write(final byte[] aByte) throws IOException {
            myByteStream.write(aByte);
        }

        @Override
        public void write(final byte[] aByteArray, final int aOffset, final int aLength) {
            myByteStream.write(aByteArray, aOffset, aLength);
        }

        @Override
        public String toString() {
            return myByteStream.toString(UTF_8);
        }
    }
}
