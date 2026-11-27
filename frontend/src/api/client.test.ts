import { afterEach, describe, expect, it, vi } from "vitest";
import {
  apiClient,
  BULK_BATCH_SIZE,
  chunkItems,
  patchBulkVisitStatus,
  postRestoreVisitStates,
  type VisitStateSnapshotView,
} from "./client";

describe("api client bulk chunking", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("splits arrays into bounded chunks", () => {
    expect(chunkItems([], 500)).toEqual([]);
    expect(chunkItems([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
    expect(chunkItems(["a", "b"], 0)).toEqual([["a"], ["b"]]);
  });

  it("chunks patchBulkVisitStatus sequentially in batches of 500 and aggregates results", async () => {
    const requestBodies: Array<{ placeIds: string[]; targetStatus: string }> = [];
    const patchSpy = vi.spyOn(apiClient, "PATCH").mockImplementation((async (
      _url: string,
      options?: { body?: { placeIds: string[]; targetStatus: string } },
    ) => {
      const body = options?.body ?? { placeIds: [], targetStatus: "VISITED" };
      requestBodies.push(body);
      return {
        data: {
          updatedCount: body.placeIds.length,
          previousStates: body.placeIds.map((placeId) => ({
            placeId,
            status: "UNVISITED",
            source: "MANUAL_BULK",
            visitCount: 0,
          })),
        },
        response: new Response(null, { status: 200 }),
      };
    }) as typeof apiClient.PATCH);

    expect(await patchBulkVisitStatus([], "VISITED")).toEqual({
      updatedCount: 0,
      previousStates: [],
    });
    expect(patchSpy).not.toHaveBeenCalled();

    const ids = Array.from({ length: BULK_BATCH_SIZE * 2 + 205 }, (_, i) => `id-${i}`);
    const result = await patchBulkVisitStatus(ids, "VISITED");

    expect(patchSpy).toHaveBeenCalledTimes(3);
    expect(requestBodies.map((b) => b.placeIds.length)).toEqual([500, 500, 205]);
    expect(result.updatedCount).toBe(1205);
    expect(result.previousStates).toHaveLength(1205);
    expect(result.previousStates[0]?.placeId).toBe("id-0");
    expect(result.previousStates[1204]?.placeId).toBe("id-1204");
  });

  it("chunks postRestoreVisitStates sequentially in batches of 500 and sums restoredCount", async () => {
    const batchLengths: number[] = [];
    const postSpy = vi.spyOn(apiClient, "POST").mockImplementation((async (
      _url: string,
      options?: { body?: { snapshots: VisitStateSnapshotView[] } },
    ) => {
      const snapshots = options?.body?.snapshots ?? [];
      batchLengths.push(snapshots.length);
      return {
        data: { restoredCount: snapshots.length },
        response: new Response(null, { status: 200 }),
      };
    }) as typeof apiClient.POST);

    expect(await postRestoreVisitStates([])).toEqual({ restoredCount: 0 });
    expect(postSpy).not.toHaveBeenCalled();

    const snapshots: VisitStateSnapshotView[] = Array.from({ length: 601 }, (_, i) => ({
      placeId: `id-${i}`,
      status: "UNVISITED",
      source: "MANUAL_BULK",
      visitCount: 0,
    }));

    const result = await postRestoreVisitStates(snapshots);
    expect(postSpy).toHaveBeenCalledTimes(2);
    expect(batchLengths).toEqual([500, 101]);
    expect(result).toEqual({ restoredCount: 601 });
  });
});
