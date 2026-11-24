package com.meanderatlas.ingestion.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class GoogleCidDecoder {

    private static final int CAPTURE_GROUP = 1;
    private static final Pattern FTID_PATTERN =
            Pattern.compile(
                    "(?:1s|[?&]ftid=)0[xX][0-9a-fA-F]{1,16}(?::|%3[aA])0[xX]([0-9a-fA-F]{1,16})(?![0-9a-fA-F])");
    private static final Pattern QUERY_CID_PATTERN =
            Pattern.compile("[?&]cid=([0-9]{1,20})(?![0-9])");

    private GoogleCidDecoder() {}

    public static Optional<GoogleCid> decodeFromUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Matcher ftidMatcher = FTID_PATTERN.matcher(url);
        if (ftidMatcher.find()) {
            return parseHex(ftidMatcher.group(CAPTURE_GROUP));
        }
        Matcher queryMatcher = QUERY_CID_PATTERN.matcher(url);
        if (queryMatcher.find()) {
            return parseDecimal(queryMatcher.group(CAPTURE_GROUP));
        }
        return Optional.empty();
    }

    private static Optional<GoogleCid> parseHex(String hex) {
        try {
            return Optional.of(GoogleCid.fromUnsignedHex(hex));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<GoogleCid> parseDecimal(String decimal) {
        try {
            return Optional.of(GoogleCid.fromUnsignedDecimal(decimal));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
