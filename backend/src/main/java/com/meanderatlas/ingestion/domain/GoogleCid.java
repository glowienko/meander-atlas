package com.meanderatlas.ingestion.domain;

import java.util.Objects;

public record GoogleCid(long unsignedBits) {

    private static final long ZERO_CID = 0L;
    private static final int HEX_RADIX = 16;
    private static final String ZERO_CID_MESSAGE = "GoogleCid must be non-zero";
    private static final String NULL_INPUT_MESSAGE = "GoogleCid input must not be null";

    public GoogleCid {
        if (unsignedBits == ZERO_CID) {
            throw new IllegalArgumentException(ZERO_CID_MESSAGE);
        }
    }

    public String decimal() {
        return Long.toUnsignedString(unsignedBits);
    }

    public static GoogleCid fromUnsignedHex(String hex) {
        Objects.requireNonNull(hex, NULL_INPUT_MESSAGE);
        return new GoogleCid(Long.parseUnsignedLong(hex, HEX_RADIX));
    }

    public static GoogleCid fromUnsignedDecimal(String decimal) {
        Objects.requireNonNull(decimal, NULL_INPUT_MESSAGE);
        return new GoogleCid(Long.parseUnsignedLong(decimal));
    }
}
