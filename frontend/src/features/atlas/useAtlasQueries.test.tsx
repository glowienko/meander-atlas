import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import * as client from "../../api/client";
import { useBulkUpdateVisitStatus, useRestoreVisitStates } from "./useAtlasQueries";

function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("useAtlasQueries bulk mutation cache invalidation", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("invalidates atlas queries onSettled when bulk visit update fails mid-batch", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");
    vi.spyOn(client, "patchBulkVisitStatus").mockRejectedValueOnce(new Error("Chunk 2 failed"));

    const { result } = renderHook(() => useBulkUpdateVisitStatus(), {
      wrapper: createWrapper(queryClient),
    });

    await act(async () => {
      await expect(
        result.current.mutateAsync({ placeIds: ["p1", "p2"], targetStatus: "VISITED" }),
      ).rejects.toThrow("Chunk 2 failed");
    });

    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["atlas"] });
    });
  });

  it("invalidates atlas queries onSettled when restore visit states fails mid-batch", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");
    vi.spyOn(client, "postRestoreVisitStates").mockRejectedValueOnce(new Error("Restore failed"));

    const { result } = renderHook(() => useRestoreVisitStates(), {
      wrapper: createWrapper(queryClient),
    });

    await act(async () => {
      await expect(
        result.current.mutateAsync([
          {
            placeId: "p1",
            status: "UNVISITED",
            visitCount: 0,
          },
        ]),
      ).rejects.toThrow("Restore failed");
    });

    await waitFor(() => {
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["atlas"] });
    });
  });
});
