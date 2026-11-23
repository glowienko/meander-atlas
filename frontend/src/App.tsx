import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { AtlasHealthPill } from "./features/atlas/AtlasHealthPill";
import { AtlasMapView } from "./features/atlas/AtlasMapView";
import { useAtlasHealth } from "./features/atlas/useAtlasHealth";
import {
  type AtlasProjection,
  type AtlasTheme,
  readAtlasViewState,
  writeAtlasViewState,
} from "./features/atlas/viewState";

const queryClient = new QueryClient();

function AtlasShell() {
  const [viewState, setViewState] = useState(() => readAtlasViewState(window.location.search));
  const { data, isLoading, isError } = useAtlasHealth();

  useEffect(() => {
    document.documentElement.dataset.theme = viewState.theme;
    const nextSearch = writeAtlasViewState(viewState, window.location.search);
    if (window.location.search !== nextSearch) {
      window.history.replaceState(null, "", `${window.location.pathname}${nextSearch}`);
    }
  }, [viewState]);

  const handleProjectionChange = (projection: AtlasProjection) => {
    setViewState((prev) => ({ ...prev, projection }));
  };

  const handleThemeChange = (theme: AtlasTheme) => {
    setViewState((prev) => ({ ...prev, theme }));
  };

  return (
    <main className="relative h-full w-full">
      <AtlasMapView projection={viewState.projection} theme={viewState.theme} />
      <AtlasHealthPill
        health={data}
        isLoading={isLoading}
        isError={isError}
        projection={viewState.projection}
        theme={viewState.theme}
        onProjectionChange={handleProjectionChange}
        onThemeChange={handleThemeChange}
      />
    </main>
  );
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AtlasShell />
    </QueryClientProvider>
  );
}
