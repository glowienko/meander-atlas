import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { AtlasHealthPill } from "./features/atlas/AtlasHealthPill";
import { AtlasMapView } from "./features/atlas/AtlasMapView";
import { useAtlasHealth } from "./features/atlas/useAtlasHealth";
import {
  type AtlasProjection,
  readAtlasViewState,
  writeAtlasViewState,
} from "./features/atlas/viewState";

function AtlasShell() {
  const [viewState, setViewState] = useState(() => readAtlasViewState(window.location.search));
  const { data, isLoading, isError } = useAtlasHealth();

  useEffect(() => {
    const nextSearch = writeAtlasViewState(viewState, window.location.search);
    if (window.location.search !== nextSearch) {
      window.history.replaceState(
        null,
        "",
        `${window.location.pathname}${nextSearch}${window.location.hash}`,
      );
    }
  }, [viewState]);

  const handleProjectionChange = (projection: AtlasProjection) => {
    setViewState({ projection });
  };

  return (
    <main className="relative h-full w-full">
      <AtlasMapView projection={viewState.projection} />
      <AtlasHealthPill
        health={data}
        isLoading={isLoading}
        isError={isError}
        projection={viewState.projection}
        onProjectionChange={handleProjectionChange}
      />
    </main>
  );
}

export function App() {
  const [queryClient] = useState(() => new QueryClient());

  return (
    <QueryClientProvider client={queryClient}>
      <AtlasShell />
    </QueryClientProvider>
  );
}
