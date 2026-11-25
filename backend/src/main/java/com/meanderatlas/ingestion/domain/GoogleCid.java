package com.meanderatlas.ingestion.domain;

import java.util.Objects;

public record GoogleCid(long unsignedBits) {

    private static final long ZERO_CID = 0L;
    private static final int HEX_RADIX = 16;

    public GoogleCid {
        if (unsignedBits == ZERO_CID) {
            throw new IllegalArgumentException("GoogleCid must be non-zero");
        }
    }

    public String decimal() {
        return Long.toUnsignedString(unsignedBits);
    }

    public static GoogleCid fromUnsignedHex(String hex) {
        Objects.requireNonNull(hex, "hex must not be null");
        return new GoogleCid(Long.parseUnsignedLong(hex, HEX_RADIX));
    }

    public static GoogleCid fromUnsignedDecimal(String decimal) {
        Objects.requireNonNull(decimal, "decimal must not be null");
        return new GoogleCid(Long.parseUnsignedLong(decimal));
    }
}
