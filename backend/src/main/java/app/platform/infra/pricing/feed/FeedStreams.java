package app.platform.infra.pricing.feed;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

/** Opens feeds safely: HTTPS only, bounded size, transparent gzip. */
public final class FeedStreams {

    /** Feeds are CSV text; anything larger than this is not a product feed we should be reading. */
    static final long MAX_BYTES = 300L * 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private FeedStreams() {
    }

    /** Downloads a feed. The URL often carries an API key, so it never appears in exceptions or logs. */
    public static InputStream download(URI url) throws IOException {
        if (url == null || !"https".equalsIgnoreCase(url.getScheme())) {
            throw new IOException("Feed URL must use HTTPS");
        }
        HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(5)).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Feed download interrupted");
        } catch (IOException e) {
            throw new IOException("Feed download failed (" + e.getClass().getSimpleName() + ")");
        }
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Feed download returned HTTP " + response.statusCode());
        }
        return decode(response.body());
    }

    /** Size-limited, gunzipped when the content starts with the gzip magic number. */
    public static InputStream decode(InputStream raw) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(new Limited(raw, MAX_BYTES));
        buffered.mark(2);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();
        return first == 0x1f && second == 0x8b ? new Limited(new GZIPInputStream(buffered), MAX_BYTES * 10) : buffered;
    }

    private static final class Limited extends FilterInputStream {
        private final long max;
        private long count;

        Limited(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                advance(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                advance(read);
            }
            return read;
        }

        private void advance(long bytes) throws IOException {
            count += bytes;
            if (count > max) {
                throw new IOException("Feed exceeds the size limit");
            }
        }
    }
}
