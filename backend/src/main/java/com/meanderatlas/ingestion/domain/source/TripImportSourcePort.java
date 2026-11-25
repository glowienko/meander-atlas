package com.meanderatlas.ingestion.domain.source;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface TripImportSourcePort {

    List<AccountImportSource> discoverAccounts(@Nullable String accountFilter);
}
