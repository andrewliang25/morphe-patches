package app.andrewliang.extension;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Fetches one address and hands the bytes to a {@link Sink}.
 *
 * <p>Holds no Android type, for the same reason as {@link RenditionPicker}: everything here can be
 * run against a local web server with {@code javac} alone. {@link Sink} is what keeps it that way.
 * The real sink writes through MediaStore. A test sink writes to an array.
 *
 * <p>The shape of this class comes from one measured fact. An address of Facebook carries an
 * {@code oh} and an {@code oe} parameter, the server signs them, and they stop working after a few
 * hours. So there is no queue, no scheduler and no retry anywhere in here. The fetch starts when
 * the user taps and either finishes or reports why not. That is also why this feature does not use
 * {@code DownloadManager}, which is otherwise the obvious choice. That class queues, and a queued
 * fetch of an address with hours to live is the one thing that cannot be allowed.
 */
final class Downloader {

    private Downloader() {}

    /** What happened, in the terms that the message to the user needs. */
    enum Status {
        /** The file is written and published. */
        OK,
        /** The server refused the signature. The address is too old to use. */
        EXPIRED,
        /** The server answered, and not with the file. */
        HTTP_ERROR,
        /** The fetch never completed. */
        NETWORK_ERROR,
        /** The bytes arrived incomplete, or they cannot be stored. */
        WRITE_ERROR
    }

    /**
     * Where the bytes go.
     *
     * <p>{@link #open} is called once, on the worker thread, and only after the server has
     * answered with a success and has said what the bytes are. Nothing is created before that, so
     * a refused fetch leaves nothing behind.
     */
    interface Sink {
        OutputStream open(String mimeFromServer) throws IOException;

        void commit() throws IOException;

        void abandon();
    }

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int BUFFER = 64 * 1024;
    private static final long MAX_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_REDIRECTS = 5;

    /**
     * Fetch [url] into [sink]. Blocking. Never throws.
     *
     * <p>No header is set on the request. A captured address was fetched from an unrelated machine
     * with none at all and answered 200, so a guessed {@code User-Agent} or {@code Referer} can
     * only make a refusal more likely.
     */
    static Status fetch(String url, Sink sink) {
        HttpURLConnection connection = null;
        boolean opened = false;
        boolean committed = false;

        try {
            connection = connect(url);
            if (connection == null) return Status.NETWORK_ERROR;

            int code = connection.getResponseCode();
            if (code == 401 || code == 403 || code == 410) {
                drain(connection);
                return Status.EXPIRED;
            }
            if (code < 200 || code > 299) {
                drain(connection);
                return Status.HTTP_ERROR;
            }

            String mime = mimeOf(connection.getContentType());
            long expected = connection.getContentLengthLong();

            OutputStream out = sink.open(mime);
            opened = true;

            long total = copy(connection.getInputStream(), out, expected);

            sink.commit();
            committed = true;
            return Status.OK;
        } catch (Truncated t) {
            return Status.WRITE_ERROR;
        } catch (IOException e) {
            return opened ? Status.WRITE_ERROR : Status.NETWORK_ERROR;
        } catch (Throwable t) {
            return Status.NETWORK_ERROR;
        } finally {
            // A sink that was opened and not committed has a half written entry behind it.
            // Removing it here, rather than at each failure, is what makes it impossible to
            // leave one behind through a path nobody thought of.
            if (opened && !committed) {
                try {
                    sink.abandon();
                } catch (Throwable ignored) {
                    // Cleaning up must never replace the real failure.
                }
            }
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }

    /**
     * Open [url], following redirects by hand.
     *
     * <p>{@code HttpURLConnection} follows a redirect only while the protocol stays the same, and
     * quietly hands back the 3xx when it changes. Facebook redirects between hosts, so leaving
     * this to the default means an occasional unexplained failure.
     */
    private static HttpURLConnection connect(String url) throws IOException {
        String current = url;

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);

            int code = connection.getResponseCode();
            if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) {
                return connection;
            }

            String location = connection.getHeaderField("Location");
            drain(connection);
            connection.disconnect();

            if (location == null || location.isEmpty()) return null;

            URL resolved = new URL(new URL(current), location);

            // A redirect from https to http means something is sitting in the middle. The
            // original address is always https, so this is never a legitimate hop.
            if ("http".equalsIgnoreCase(resolved.getProtocol())
                && current.toLowerCase(Locale.US).startsWith("https://")) {
                return null;
            }

            current = resolved.toString();
        }

        return null;
    }

    /**
     * Copy the body across, and refuse a short one.
     *
     * <p>Without the length check a connection dropped near the end publishes a truncated file
     * that reports success and will not play. That failure is worse than no file, because the user
     * has no reason to try again.
     */
    private static long copy(InputStream in, OutputStream out, long expected) throws IOException {
        byte[] buffer = new byte[BUFFER];
        long total = 0;

        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            total += read;
            if (total > MAX_BYTES) throw new Truncated();
        }

        out.flush();

        if (total == 0) throw new Truncated();
        if (expected >= 0 && total != expected) throw new Truncated();

        return total;
    }

    /** The type that the server gave, lowercased and without its parameters. */
    private static String mimeOf(String contentType) {
        if (contentType == null) return null;

        String mime = contentType;
        int cut = mime.indexOf(';');
        if (cut >= 0) mime = mime.substring(0, cut);

        mime = mime.trim().toLowerCase(Locale.US);
        return mime.isEmpty() ? null : mime;
    }

    /** Read and close the error body, so the connection can go back to the pool. */
    private static void drain(HttpURLConnection connection) {
        InputStream stream = null;
        try {
            stream = connection.getErrorStream();
            if (stream == null) return;

            byte[] buffer = new byte[4096];
            while (stream.read(buffer) > 0) {
                // Discarded on purpose. Reading it is what releases the connection.
            }
        } catch (Throwable ignored) {
            // Nothing useful to do.
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Throwable ignored) {
                    // Nothing useful to do.
                }
            }
        }
    }

    /** The body was not the size that the server promised. */
    private static final class Truncated extends IOException {}
}
