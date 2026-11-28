import fs from "node:fs";
import path from "node:path";
import { expect, type Page, test } from "@playwright/test";

interface ErrorMonitor {
  assertClean: () => void;
}

function attachErrorMonitor(page: Page): ErrorMonitor {
  const consoleErrors: string[] = [];
  const pageErrors: string[] = [];
  const failedRequests: string[] = [];
  const errorResponses: string[] = [];

  page.on("console", (msg) => {
    if (msg.type() === "error") {
      consoleErrors.push(msg.text());
    }
  });
  page.on("pageerror", (err) => {
    pageErrors.push(err.message);
  });
  page.on("requestfailed", (req) => {
    const errorText = req.failure()?.errorText ?? "failed";
    const isAbortedTile =
      errorText === "net::ERR_ABORTED" &&
      (req.url().includes(".pbf") ||
        req.url().includes(".mvt") ||
        req.url().startsWith("https://tiles.openfreemap.org/"));
    if (!isAbortedTile) {
      failedRequests.push(`${req.method()} ${req.url()} (${errorText})`);
    }
  });
  page.on("response", (res) => {
    if (res.status() >= 400) {
      errorResponses.push(`${res.status()} ${res.url()}`);
    }
  });

  return {
    assertClean: () => {
      expect(consoleErrors, `Console errors: ${consoleErrors.join("; ")}`).toEqual([]);
      expect(pageErrors, `Page errors: ${pageErrors.join("; ")}`).toEqual([]);
      expect(failedRequests, `Failed requests: ${failedRequests.join("; ")}`).toEqual([]);
      expect(errorResponses, `HTTP >=400 responses: ${errorResponses.join("; ")}`).toEqual([]);
    },
  };
}

async function waitForMapReady(page: Page) {
  const mapContainer = page.getByTestId("atlas-map-canvas");
  await expect(mapContainer).toHaveAttribute("data-map-loaded", "true", { timeout: 25_000 });
  await expect(mapContainer).toHaveAttribute("data-style-loaded", "true", { timeout: 25_000 });
  return mapContainer;
}

test.describe
  .serial("MeanderAtlas deterministic E2E journeys", () => {
    test("syncs local folder, renders passive enrichment UX, toggles basemap and projection, and captures visual proof", async ({
      page,
    }) => {
      const monitor = attachErrorMonitor(page);

      await page.goto("/#slice4-proof", { waitUntil: "domcontentloaded" });
      await expect(page.getByTestId("atlas-health-status")).toContainText("UP · PostGIS 3.", {
        timeout: 15_000,
      });

      const mapContainer = await waitForMapReady(page);
      await expect(mapContainer).toHaveAttribute("data-projection", "globe");
      await expect(mapContainer).toHaveAttribute("data-basemap", "atlas");
      await expect(page.getByTestId("places-count-badge")).toContainText("0 pins");

      await page.getByTestId("sync-local-folder-button").click();
      await expect(page.getByTestId("atlas-undo-toast")).toContainText(
        "Synced 5 new, 2 merged · 20 awaiting coordinates",
        { timeout: 15_000 },
      );
      await expect(page.getByTestId("places-count-badge")).toContainText("5 pins", {
        timeout: 15_000,
      });

      await page.getByTestId("toggle-stats-button").click();
      await expect(page.getByTestId("travel-stats-panel")).toBeVisible();
      await expect(page).toHaveURL(/stats=1/);
      await expect(page).toHaveURL(/#slice4-proof$/);

      const importBadge = page.getByTestId("import-status-badge");
      await expect(importBadge).toContainText("5 mapped");
      await expect(importBadge).toContainText("20 awaiting coordinates");
      await expect(importBadge).not.toContainText("Accounts:");
      const indicator = page.getByTestId("enrichment-status-indicator");
      await expect(indicator).toContainText("left");
      await expect(indicator).toHaveAttribute("title", /OpenStreetMap and Wikimedia/);
      await expect(page.getByTestId("materialize-offline-button")).toHaveCount(0);
      await expect(page.getByTestId("enrich-batch-button")).toHaveCount(0);
      await expect(page.getByTestId("enrich-all-button")).toHaveCount(0);

      await page.getByTestId("filter-status").selectOption("VISITED");
      await expect(page).toHaveURL(/status=VISITED/);
      await expect(page.getByTestId("places-count-badge")).toContainText("5 pins");

      const canvas = page.locator(".maplibregl-canvas");
      await expect(canvas).toBeVisible();
      const bounds = await canvas.boundingBox();
      expect(bounds?.width ?? 0).toBeGreaterThan(400);
      expect(bounds?.height ?? 0).toBeGreaterThan(300);

      const proofDir = path.resolve(process.cwd(), "../artifacts/proof");
      fs.mkdirSync(proofDir, { recursive: true });
      const proofPath = path.join(proofDir, "phase0-globe.png");
      await page.screenshot({ path: proofPath, fullPage: false });
      expect(fs.statSync(proofPath).size).toBeGreaterThan(5_000);

      await page.getByTestId("clear-filters-button").click();
      await expect(page).not.toHaveURL(/status=/);

      await page.getByRole("button", { name: "2D Map" }).click();
      await expect(page).toHaveURL(/view=map/);
      await expect(mapContainer).toHaveAttribute("data-projection", "mercator");

      await page.getByRole("button", { name: "Street" }).click();
      await expect(page).toHaveURL(/basemap=street/);
      await expect(mapContainer).toHaveAttribute("data-basemap", "street");
      await expect(mapContainer).toHaveAttribute("data-style-loaded", "true", { timeout: 25_000 });

      await page.getByRole("button", { name: "Atlas", exact: true }).click();
      await expect(page).not.toHaveURL(/basemap=street/);
      await expect(mapContainer).toHaveAttribute("data-basemap", "atlas");
      await expect(mapContainer).toHaveAttribute("data-style-loaded", "true", { timeout: 25_000 });

      await page.getByRole("button", { name: "Globe" }).click();
      await expect(page).not.toHaveURL(/view=map/);
      await expect(mapContainer).toHaveAttribute("data-projection", "globe");

      await page.getByTestId("map-legend-toggle").click();
      await expect(page.getByTestId("map-legend-popover")).toBeVisible();
      await page.getByRole("button", { name: "Close map legend" }).click();
      await expect(page.getByTestId("map-legend-popover")).not.toBeVisible();

      monitor.assertClean();
    });

    test("handles Northern, Southern, polar Antarctica (-90), and multi-polygon overseas countries with 0 HTTP 500 tiles and working view resets", async ({
      page,
      request,
    }) => {
      const monitor = attachErrorMonitor(page);

      for (const tilePath of [
        "/api/v1/atlas/tiles/regions/COUNTRY/0/0/0.mvt",
        "/api/v1/atlas/tiles/regions/COUNTRY/1/0/0.mvt",
        "/api/v1/atlas/tiles/regions/COUNTRY/1/1/0.mvt",
        "/api/v1/atlas/tiles/regions/COUNTRY/1/0/1.mvt",
        "/api/v1/atlas/tiles/regions/COUNTRY/1/1/1.mvt",
      ]) {
        const response = await request.get(tilePath);
        expect(response.status(), `MVT tile ${tilePath}`).toBe(200);
      }

      const franceRes = await request.get("/api/v1/atlas/regions/COUNTRY/FR");
      expect(franceRes.status()).toBe(200);
      const franceDossier = (await franceRes.json()) as {
        region: { minLng: number; maxLng: number; minLat: number; maxLat: number };
      };
      expect(franceDossier.region.minLng).toBeGreaterThan(-10);
      expect(franceDossier.region.maxLng).toBeLessThan(15);

      await page.goto("/?region=PL&regionLevel=COUNTRY", { waitUntil: "domcontentloaded" });
      const mapContainer = await waitForMapReady(page);
      const dossier = page.getByTestId("region-dossier-drawer");
      await expect(dossier).toContainText("Poland", { timeout: 15_000 });
      await expect
        .poll(async () => Number((await mapContainer.getAttribute("data-zoom")) ?? "0"), {
          timeout: 10_000,
        })
        .toBeGreaterThan(3);

      await page.getByTestId("reset-view-button").click();
      await expect(dossier).not.toBeVisible();
      await expect(page).not.toHaveURL(/region=/);
      await expect
        .poll(async () => Number((await mapContainer.getAttribute("data-zoom")) ?? "99"), {
          timeout: 10_000,
        })
        .toBeLessThan(2.5);

      for (const [code, label] of [
        ["FR", "France"],
        ["AU", "Australia"],
        ["AQ", "Antarctica"],
      ] as const) {
        await page.goto(`/?region=${code}&regionLevel=COUNTRY`, { waitUntil: "domcontentloaded" });
        await waitForMapReady(page);
        await expect(page.getByTestId("region-dossier-drawer")).toContainText(label, {
          timeout: 15_000,
        });
      }

      await page.getByTestId("brand-reset-view-button").click();
      await expect(page.getByTestId("region-dossier-drawer")).not.toBeVisible();
      await expect(page).not.toHaveURL(/region=/);

      monitor.assertClean();
    });

    test("persists manual category editing across reload and filters dossier pins", async ({
      page,
    }) => {
      const monitor = attachErrorMonitor(page);

      await page.goto("/?region=PL&regionLevel=COUNTRY&status=UNVISITED", {
        waitUntil: "domcontentloaded",
      });
      await waitForMapReady(page);

      const dossier = page.getByTestId("region-dossier-drawer");
      await expect(dossier).toContainText("Poland", { timeout: 15_000 });
      await expect(dossier).toContainText("Wawel Royal Castle");
      await expect(dossier.getByTestId("dossier-filter-notice")).toContainText("Map filter active");

      await dossier.getByRole("button", { name: "Wawel Royal Castle" }).click();
      const detailCard = page.getByTestId("place-detail-card");
      await expect(detailCard).toBeVisible();

      const categorySelect = detailCard.getByTestId("place-category-select");
      await categorySelect.selectOption("GROCERIES");
      await expect(page.getByTestId("atlas-undo-toast")).toContainText("Groceries & Markets");
      await expect(detailCard.getByTestId("place-category-manual-badge")).toHaveText("Manual");

      await page.reload({ waitUntil: "domcontentloaded" });
      await waitForMapReady(page);

      await page.getByTestId("clear-filters-button").click();
      await page.getByTestId("filter-category").selectOption("GROCERIES");
      await expect(page).toHaveURL(/category=GROCERIES/);
      await expect(page.getByTestId("places-count-badge")).toContainText("1 pins");

      await page
        .getByTestId("region-dossier-drawer")
        .getByRole("button", { name: "Wawel Royal Castle" })
        .click();
      await expect(page.getByTestId("place-category-select")).toHaveValue("GROCERIES");
      await expect(page.getByTestId("place-category-manual-badge")).toHaveText("Manual");

      await page.getByTestId("place-category-select").selectOption("LANDMARK");
      await expect(page.getByTestId("atlas-undo-toast")).toContainText("Landmarks");

      monitor.assertClean();
    });

    test("supports drag-box area selection, bulk visit and undo, custom list creation, and single-place list move", async ({
      page,
    }) => {
      const monitor = attachErrorMonitor(page);

      await page.goto("/?view=map&region=PL&regionLevel=COUNTRY", {
        waitUntil: "domcontentloaded",
      });
      const mapContainer = await waitForMapReady(page);
      await expect(page.getByTestId("region-dossier-drawer")).toContainText("Poland", {
        timeout: 15_000,
      });
      await expect
        .poll(async () => Number((await mapContainer.getAttribute("data-zoom")) ?? "0"), {
          timeout: 10_000,
        })
        .toBeGreaterThan(3);

      await page
        .getByTestId("region-dossier-drawer")
        .getByRole("button", { name: "Wawel Royal Castle" })
        .click();
      const detailCard = page.getByTestId("place-detail-card");
      await expect(detailCard).toBeVisible();
      await detailCard.getByRole("button", { name: "Mark Unvisited" }).click();
      await expect(detailCard.getByRole("button", { name: "Mark Visited" })).toBeVisible();
      await detailCard.getByRole("button", { name: "Close place details" }).click();
      await page
        .getByTestId("region-dossier-drawer")
        .getByRole("button", { name: "Close region dossier" })
        .click();

      await page.getByTestId("select-area-button").click();
      await expect(page.getByTestId("select-area-button")).toHaveAttribute("aria-pressed", "true");

      const canvas = page.locator(".maplibregl-canvas");
      const box = await canvas.boundingBox();
      expect(box).not.toBeNull();
      const startX = (box?.x ?? 0) + 120;
      const startY = (box?.y ?? 0) + 160;
      const endX = (box?.x ?? 0) + (box?.width ?? 900) - 120;
      const endY = (box?.y ?? 0) + (box?.height ?? 600) - 120;

      await page.mouse.move(startX, startY);
      await page.mouse.down();
      await page.mouse.move(endX, endY, { steps: 6 });
      await expect(page.getByTestId("area-selection-box")).toBeVisible();
      await page.mouse.up();

      const bulkDialog = page.getByTestId("bulk-visit-dialog");
      await expect(bulkDialog).toBeVisible({ timeout: 10_000 });
      await bulkDialog.getByTestId("confirm-bulk-visit-button").click();
      await expect(page.getByTestId("atlas-undo-toast")).toContainText("Marked 1 place as visited");
      await page.getByTestId("atlas-undo-button").click();

      await page.getByTestId("select-area-button").click();
      await page.mouse.move(startX, startY);
      await page.mouse.down();
      await page.mouse.move(endX, endY, { steps: 6 });
      await page.mouse.up();
      await expect(bulkDialog).toBeVisible({ timeout: 10_000 });

      await bulkDialog.getByTestId("bulk-list-new-name").fill("E2E Roadtrip");
      await bulkDialog.getByTestId("bulk-list-apply-button").click();
      await expect(page.getByTestId("atlas-undo-toast")).toContainText("E2E Roadtrip");
      await expect(bulkDialog).not.toBeVisible();

      const listFilter = page.getByTestId("filter-account-list");
      await expect(listFilter).toContainText("E2E Roadtrip", { timeout: 10_000 });
      await listFilter.selectOption({ label: "E2E Roadtrip (1)" });
      await expect(page).toHaveURL(/list=E2E\+Roadtrip/);
      await expect(page.getByTestId("places-count-badge")).toContainText("1 pins");

      await page.goto("/?region=PL&regionLevel=COUNTRY", { waitUntil: "domcontentloaded" });
      await waitForMapReady(page);
      await page
        .getByTestId("region-dossier-drawer")
        .getByRole("button", { name: "Wawel Royal Castle" })
        .click();
      await page.getByTestId("place-list-new-name").fill("E2E Moved List");
      await page.getByTestId("place-list-move-button").click();
      await expect(page.getByTestId("atlas-undo-toast")).toContainText(
        "Updated lists for Wawel Royal Castle",
      );

      await page.reload({ waitUntil: "domcontentloaded" });
      await waitForMapReady(page);
      await expect(page.getByTestId("filter-account-list")).toContainText("E2E Moved List", {
        timeout: 10_000,
      });

      monitor.assertClean();
    });
  });
