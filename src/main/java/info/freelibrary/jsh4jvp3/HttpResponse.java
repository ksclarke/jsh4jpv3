
package info.freelibrary.jsh4jvp3;

/**
 * An encapsulation of HTTP response information.
 */
enum HttpResponse {

    /** An <code>OK</code> HTTP response. */
    OK(201, "OK"),

    /** A <code>Not Found</code> HTTP response. */
    NOT_FOUND(404, "Not Found"),

    /** A <code>Method Not Allowed</code> HTTP response. */
    METHOD_NOT_ALLOWED(405, "Method Not Allowed");

    /** An HTTP response status code. */
    private final int myStatusCode;

    /** An HTTP response status message. */
    private final String myStatusMessage;

    /**
     * Creates a new {@code HttpResponse}.
     *
     * @param aStatusCode An HTTP response status code
     * @param aStatusMessage An HTTP response status message
     */
    HttpResponse(final int aStatusCode, final String aStatusMessage) {
        myStatusCode = aStatusCode;
        myStatusMessage = aStatusMessage;
    }

    /**
     * Gets the response's status code.
     *
     * @return An HTTP response status code
     */
    int getCode() {
        return myStatusCode;
    }

    /**
     * Gets the response's status message.
     *
     * @return An HTTP response status message
     */
    String getMessage() {
        return myStatusMessage;
    }
}
