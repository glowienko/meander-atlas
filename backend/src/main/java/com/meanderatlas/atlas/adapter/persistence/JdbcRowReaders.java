package com.meanderatlas.atlas.adapter.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.SqlTypeValue;

final class JdbcRowReaders {

    private JdbcRowReaders() {}

    static UUID requiredUuid(ResultSet rs, String column) throws SQLException {
        return Objects.requireNonNull(
                rs.getObject(column, UUID.class), "Expected non-null UUID column: " + column);
    }

    static String requiredString(ResultSet rs, String column) throws SQLException {
        return Objects.requireNonNull(
                rs.getString(column), "Expected non-null string column: " + column);
    }

    static @Nullable Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
        return odt == null ? null : odt.toInstant();
    }

    static @Nullable OffsetDateTime toOffsetDateTime(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static SqlTypeValue uuidArray(List<UUID> ids) {
        UUID[] values = ids.toArray(UUID[]::new);
        return (ps, paramIndex, sqlType, typeName) -> {
            Array sqlArray = ps.getConnection().createArrayOf("uuid", values);
            ps.setArray(paramIndex, sqlArray);
        };
    }
}
