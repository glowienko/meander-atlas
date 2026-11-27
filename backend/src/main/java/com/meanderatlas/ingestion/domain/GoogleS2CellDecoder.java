package com.meanderatlas.ingestion.domain;

import com.meanderatlas.shared.GeoPoint;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class GoogleS2CellDecoder {

    private static final Pattern FTID_S2_PATTERN =
            Pattern.compile(
                    "(?:1s|[?&]ftid=)0[xX]([0-9a-fA-F]{1,16})(?::|%3[aA])0[xX][0-9a-fA-F]{1,16}(?![0-9a-fA-F])");
    private static final int HEX_RADIX = 16;
    private static final int MAX_FACE = 5;
    private static final int MAX_LEVEL = 30;
    private static final int MAX_TRAILING_ZEROS = 60;
    private static final int FACE_SHIFT = 61;
    private static final int UPPER_WORD_SHIFT = 32;
    private static final double MAX_COORDINATE_RANGE = 1 << MAX_LEVEL;
    private static final int[][] POS_TO_IJ = {
        {0, 1, 3, 2},
        {0, 2, 3, 1},
        {3, 2, 0, 1},
        {3, 1, 0, 2}
    };
    private static final int[] POS_TO_ORIENTATION = {1, 0, 0, 3};

    private GoogleS2CellDecoder() {}

    public static Optional<GeoPoint> decodeFromUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = FTID_S2_PATTERN.matcher(url);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return parseHex(matcher.group(1));
    }

    public static Optional<GeoPoint> decodeCellId(long cellId) {
        if (cellId == 0L || (cellId >>> UPPER_WORD_SHIFT) == 0L) {
            return Optional.empty();
        }
        int face = (int) (cellId >>> FACE_SHIFT);
        if (face < 0 || face > MAX_FACE) {
            return Optional.empty();
        }
        int trailingZeros = Long.numberOfTrailingZeros(cellId);
        if (trailingZeros > MAX_TRAILING_ZEROS || (trailingZeros & 1) != 0) {
            return Optional.empty();
        }
        int level = MAX_LEVEL - (trailingZeros >>> 1);
        int orientation = face & 1;
        int i = 0;
        int j = 0;
        for (int step = MAX_LEVEL - 1; step >= 0; step--) {
            int pos = (int) ((cellId >>> (2 * step + 1)) & 3L);
            int ij = POS_TO_IJ[orientation][pos];
            i = (i << 1) | ((ij >>> 1) & 1);
            j = (j << 1) | (ij & 1);
            orientation ^= POS_TO_ORIENTATION[pos];
        }
        int size = 1 << (MAX_LEVEL - level);
        int iBase = i & ~(size - 1);
        int jBase = j & ~(size - 1);
        double s = (iBase + 0.5 * size) / MAX_COORDINATE_RANGE;
        double t = (jBase + 0.5 * size) / MAX_COORDINATE_RANGE;
        double u = stToUv(s);
        double v = stToUv(t);
        return Optional.of(faceUvToGeoPoint(face, u, v));
    }

    private static Optional<GeoPoint> parseHex(String hex) {
        try {
            long cellId = Long.parseUnsignedLong(hex, HEX_RADIX);
            return decodeCellId(cellId);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static double stToUv(double s) {
        if (s >= 0.5) {
            return (1.0 / 3.0) * (4.0 * s * s - 1.0);
        }
        return (1.0 / 3.0) * (1.0 - 4.0 * (1.0 - s) * (1.0 - s));
    }

    private static GeoPoint faceUvToGeoPoint(int face, double u, double v) {
        double x;
        double y;
        double z;
        switch (face) {
            case 0 -> {
                x = 1.0;
                y = u;
                z = v;
            }
            case 1 -> {
                x = -u;
                y = 1.0;
                z = v;
            }
            case 2 -> {
                x = -u;
                y = -v;
                z = 1.0;
            }
            case 3 -> {
                x = -1.0;
                y = -v;
                z = -u;
            }
            case 4 -> {
                x = v;
                y = -1.0;
                z = -u;
            }
            default -> {
                x = v;
                y = u;
                z = -1.0;
            }
        }
        double lat = Math.toDegrees(Math.atan2(z, Math.hypot(x, y)));
        double lng = Math.toDegrees(Math.atan2(y, x));
        return new GeoPoint(lat == 0.0 ? 0.0 : lat, lng == 0.0 ? 0.0 : lng);
    }
}
