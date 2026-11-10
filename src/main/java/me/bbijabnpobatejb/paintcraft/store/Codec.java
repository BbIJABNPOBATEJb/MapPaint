package me.bbijabnpobatejb.paintcraft.store;

import me.bbijabnpobatejb.paintcraft.palette.Palette;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class Codec {

    public static final String PREFIX = "pc1:";
    private static final String PART_PREFIX = "pc1p";
    private static final int FORMAT = 1;

    public record Image(int blocksW, int blocksH, int res, byte[] pixels) {
        public int width() {
            return blocksW * res;
        }

        public int height() {
            return blocksH * res;
        }
    }

    public record Part(int number, int total, String data) {
    }

    public static final class FormatException extends Exception {
        public FormatException(String message) {
            super(message);
        }
    }

    private Codec() {
    }

    public static String encode(int blocksW, int blocksH, int res, byte[] pixels) {
        byte[] raw = new byte[4 + pixels.length];
        raw[0] = FORMAT;
        raw[1] = (byte) blocksW;
        raw[2] = (byte) blocksH;
        raw[3] = (byte) Integer.numberOfTrailingZeros(res);
        System.arraycopy(pixels, 0, raw, 4, pixels.length);

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 8));
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    public static Image decode(String text, int maxBlocks) throws FormatException {
        String s = clean(text);
        if (!s.startsWith(PREFIX)) {
            throw new FormatException("prefix");
        }
        byte[] packed;
        try {
            packed = Base64.getUrlDecoder().decode(s.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new FormatException("base64");
        }

        Inflater inflater = new Inflater(true);
        inflater.setInput(packed);
        try {
            byte[] head = new byte[4];
            if (inflater.inflate(head) != 4 || head[0] != FORMAT) {
                throw new FormatException("header");
            }
            int blocksW = head[1], blocksH = head[2], res = 1 << head[3];
            if (blocksW < 1 || blocksH < 1 || blocksW > maxBlocks || blocksH > maxBlocks) {
                throw new FormatException("size");
            }
            if (res != 16 && res != 32 && res != 64 && res != 128) {
                throw new FormatException("resolution");
            }
            byte[] pixels = new byte[blocksW * res * blocksH * res];
            int read = 0;
            while (read < pixels.length) {
                int n = inflater.inflate(pixels, read, pixels.length - read);
                if (n == 0) {
                    break;
                }
                read += n;
            }
            if (read != pixels.length) {
                throw new FormatException("length");
            }
            for (int i = 0; i < pixels.length; i++) {
                if (!Palette.isValid(pixels[i])) {
                    pixels[i] = Palette.TRANSPARENT;
                }
            }
            return new Image(blocksW, blocksH, res, pixels);
        } catch (DataFormatException e) {
            throw new FormatException("deflate");
        } finally {
            inflater.end();
        }
    }

    public static List<String> split(String code, int limit) {
        if (code.length() <= limit) {
            return List.of(code);
        }
        String body = code.substring(PREFIX.length());
        int total = (body.length() + limit - 1) / limit;
        List<String> parts = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            String chunk = body.substring(i * limit, Math.min(body.length(), (i + 1) * limit));
            parts.add(PART_PREFIX + (i + 1) + "of" + total + ":" + chunk);
        }
        return parts;
    }

    public static Part parsePart(String text) {
        String s = clean(text);
        if (!s.startsWith(PART_PREFIX)) {
            return null;
        }
        int colon = s.indexOf(':');
        int of = s.indexOf("of");
        if (colon < 0 || of < 0 || of > colon) {
            return null;
        }
        try {
            int number = Integer.parseInt(s.substring(PART_PREFIX.length(), of));
            int total = Integer.parseInt(s.substring(of + 2, colon));
            if (total < 2 || total > 64 || number < 1 || number > total) {
                return null;
            }
            return new Part(number, total, s.substring(colon + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > ' ') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
