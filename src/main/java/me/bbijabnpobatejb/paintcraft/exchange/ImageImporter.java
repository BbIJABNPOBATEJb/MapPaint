package me.bbijabnpobatejb.paintcraft.exchange;

import me.bbijabnpobatejb.paintcraft.palette.Palette;

import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ImageImporter {

    private static final int MAX_REDIRECTS = 5;

    private ImageImporter() {
    }

    public static BufferedImage fetch(String url, int maxBytes, boolean allowPrivate) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            URI uri = URI.create(url.trim());
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                check(uri, allowPrivate);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "PaintCraft")
                        .header("Accept", "image/*")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400) {
                        String location = response.headers().firstValue("Location").orElseThrow(() -> new IOException("redirect"));
                        uri = uri.resolve(location);
                        continue;
                    }
                    if (status != 200) {
                        throw new IOException("HTTP " + status);
                    }
                    byte[] data = body.readNBytes(maxBytes + 1);
                    if (data.length > maxBytes) {
                        throw new IOException("too large");
                    }
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                    if (image == null) {
                        throw new IOException("not an image");
                    }
                    return image;
                }
            }
            throw new IOException("too many redirects");
        } catch (IllegalArgumentException e) {
            throw new IOException("bad url");
        }
    }

    private static void check(URI uri, boolean allowPrivate) throws IOException {
        String scheme = uri.getScheme();
        if (scheme == null || uri.getHost() == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IOException("bad url");
        }
        if (allowPrivate) {
            return;
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            boolean uniqueLocal = address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC;
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress() || uniqueLocal) {
                throw new IOException("private address");
            }
        }
    }

    public static byte[] convert(BufferedImage image, int width, int height, ImageOptions options, float shade) {
        float[][] rgba = scale(image, width, height, options, shade);
        float[] r = rgba[0], g = rgba[1], b = rgba[2], a = rgba[3];
        boolean pixelArt = image.getWidth() * 2 <= width && image.getHeight() * 2 <= height;
        boolean dither = options.dither() && !pixelArt;
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (a[i] < 128f) {
                    pixels[i] = Palette.TRANSPARENT;
                    continue;
                }
                int cr = clamp(r[i]), cg = clamp(g[i]), cb = clamp(b[i]);
                byte color = Palette.nearest(cr << 16 | cg << 8 | cb);
                pixels[i] = color;
                if (!dither) {
                    continue;
                }
                int actual = Palette.rgb(color);
                float er = cr - ((actual >> 16) & 255), eg = cg - ((actual >> 8) & 255), eb = cb - (actual & 255);
                spread(r, g, b, width, height, x + 1, y, er, eg, eb, 7 / 16f);
                spread(r, g, b, width, height, x - 1, y + 1, er, eg, eb, 3 / 16f);
                spread(r, g, b, width, height, x, y + 1, er, eg, eb, 5 / 16f);
                spread(r, g, b, width, height, x + 1, y + 1, er, eg, eb, 1 / 16f);
            }
        }
        return pixels;
    }

    private static void spread(float[] r, float[] g, float[] b, int width, int height, int x, int y,
                               float er, float eg, float eb, float share) {
        if (x < 0 || x >= width || y >= height) {
            return;
        }
        int i = y * width + x;
        r[i] += er * share;
        g[i] += eg * share;
        b[i] += eb * share;
    }

    private static int clamp(float value) {
        return value < 0 ? 0 : value > 255 ? 255 : Math.round(value);
    }

    private static float[][] scale(BufferedImage image, int width, int height, ImageOptions options, float shade) {
        int iw = image.getWidth(), ih = image.getHeight();
        double zoom = Math.max(0.1, options.zoom());
        double ratioX, ratioY;
        switch (options.fit()) {
            case CONTAIN -> ratioX = ratioY = Math.max((double) iw / width, (double) ih / height) / zoom;
            case STRETCH -> {
                ratioX = iw / (double) width / zoom;
                ratioY = ih / (double) height / zoom;
            }
            default -> ratioX = ratioY = Math.min((double) iw / width, (double) ih / height) / zoom;
        }
        double slackX = iw - width * ratioX, slackY = ih - height * ratioY;
        double left = slackX / 2 - Math.abs(slackX) / 2 * options.offsetX();
        double top = slackY / 2 - Math.abs(slackY) / 2 * options.offsetY();
        float fillAlpha = options.clearFill() ? 0f : 255f;
        int[] source = pixels(image);

        float[][] out = new float[4][width * height];
        for (int y = 0; y < height; y++) {
            int y0 = (int) Math.floor(top + y * ratioY);
            int y1 = Math.max(y0 + 1, (int) Math.ceil(top + (y + 1) * ratioY));
            for (int x = 0; x < width; x++) {
                int x0 = (int) Math.floor(left + x * ratioX);
                int x1 = Math.max(x0 + 1, (int) Math.ceil(left + (x + 1) * ratioX));
                long sr = 0, sg = 0, sb = 0, sa = 0;
                int count = 0;
                for (int sy = Math.max(0, y0); sy < Math.min(ih, y1); sy++) {
                    for (int sx = Math.max(0, x0); sx < Math.min(iw, x1); sx++) {
                        int argb = source[sy * iw + sx];
                        int alpha = argb >>> 24;
                        sa += alpha;
                        sr += ((argb >> 16) & 255) * alpha;
                        sg += ((argb >> 8) & 255) * alpha;
                        sb += (argb & 255) * alpha;
                        count++;
                    }
                }
                int i = y * width + x;
                if (count == 0) {
                    out[0][i] = out[1][i] = out[2][i] = 255f;
                    out[3][i] = fillAlpha;
                } else if (sa > 0) {
                    out[0][i] = (float) sr / sa;
                    out[1][i] = (float) sg / sa;
                    out[2][i] = (float) sb / sa;
                    out[3][i] = (float) sa / count;
                    adjust(out, i, options, shade);
                }
            }
        }
        return out;
    }

    private static void adjust(float[][] out, int i, ImageOptions options, float shade) {
        float brightness = options.brightness(), contrast = options.contrast(), saturation = options.saturation();
        if (brightness == 0 && contrast == 0 && saturation == 0 && shade == 1f) {
            return;
        }
        float r = out[0][i], g = out[1][i], b = out[2][i];
        if (saturation != 0) {
            float gray = 0.299f * r + 0.587f * g + 0.114f * b;
            float k = 1 + saturation;
            r = gray + (r - gray) * k;
            g = gray + (g - gray) * k;
            b = gray + (b - gray) * k;
        }
        if (contrast != 0) {
            float k = contrast < 0 ? 1 + contrast : 1 + contrast * 2;
            r = (r - 127.5f) * k + 127.5f;
            g = (g - 127.5f) * k + 127.5f;
            b = (b - 127.5f) * k + 127.5f;
        }
        if (brightness != 0) {
            float shift = brightness * 255f;
            r += shift;
            g += shift;
            b += shift;
        }
        out[0][i] = Math.clamp(r, 0f, 255f) * shade;
        out[1][i] = Math.clamp(g, 0f, 255f) * shade;
        out[2][i] = Math.clamp(b, 0f, 255f) * shade;
    }

    private static int[] pixels(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        if (image.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY
                || image.getColorModel() instanceof java.awt.image.IndexColorModel) {
            return image.getRGB(0, 0, width, height, null, 0, width);
        }
        Raster raster = image.getRaster();
        boolean alpha = image.getColorModel().hasAlpha() && raster.getNumBands() > 1;
        int shift = Math.max(0, image.getColorModel().getComponentSize(0) - 8);
        int alphaShift = alpha ? Math.max(0, image.getColorModel().getComponentSize(1) - 8) : 0;
        int[] result = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = (raster.getSample(x, y, 0) >> shift) & 255;
                int a = alpha ? (raster.getSample(x, y, 1) >> alphaShift) & 255 : 255;
                result[y * width + x] = a << 24 | gray << 16 | gray << 8 | gray;
            }
        }
        return result;
    }
}
