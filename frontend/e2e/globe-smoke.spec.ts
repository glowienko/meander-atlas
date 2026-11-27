import fs from "node:fs";
import path from "node:path";
import { expect, test } from "@playwright/test";

test("syncs synthetic local folder, visualizes Live Atlas stats and pins, and supports region dossier bulk undo", async ({
  page,
}) => {
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
      (req.url().includes(".pbf") || req.url().includes(".mvt"));
    if (!isAbortedTile) {
      failedRequests.push(`${req.method()} ${req.url()} (${errorText})`);
    }
  });
  page.on("response", (res) => {
    if (res.status() >= 400) {
      errorResponses.push(`${res.status()} ${res.url()}`);
    }
  });

  await page.goto("/#slice4-proof", { waitUntil: "domcontentloaded" });

  const healthStatus = page.getByTestId("atlas-health-status");
  await expect(healthStatus).toContainText("UP · PostGIS 3.", { timeout: 15_000 });

  const mapContainer = page.getByTestId("atlas-map-canvas");
  await expect(mapContainer).toHaveAttribute("data-map-loaded", "true", { timeout: 25_000 });
  await expect(mapContainer).toHaveAttribute("data-projection", "globe");

  await expect(page.getByTestId("atlas-filter-bar")).toBeVisible();
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

  await page.getByTestId("filter-status").selectOption("VISITED");
  await expect(page).toHaveURL(/status=VISITED/);
  await expect(page.getByTestId("places-count-badge")).toContainText("5 pins");

  const canvas = page.locator(".maplibregl-canvas");
  await expect(canvas).toBeVisible();
  const bounds = await canvas.boundingBox();
  expect(bounds).not.toBeNull();
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

  await page.getByRole("button", { name: "Globe" }).click();
  await expect(page).not.toHaveURL(/view=map/);
  await expect(mapContainer).toHaveAttribute("data-projection", "globe");

  await page.goto("/?region=PL&regionLevel=COUNTRY&status=UNVISITED", {
    waitUntil: "domcontentloaded",
  });
  const dossier = page.getByTestId("region-dossier-drawer");
  await expect(dossier).toContainText("Poland", { timeout: 15_000 });
  await expect(dossier).toContainText("Wawel Royal Castle");
  await expect(dossier.getByTestId("dossier-filter-notice")).toContainText("Map filter active");

  await dossier.getByRole("button", { name: "Wawel Royal Castle" }).click();
  const detailCard = page.getByTestId("place-detail-card");
  await expect(detailCard).toBeVisible();
  await expect(detailCard).toContainText("Wawel Royal Castle");
  await detailCard.getByRole("button", { name: "Mark Unvisited" }).click();
  await expect(detailCard).toBeVisible();
  await expect(detailCard.getByRole("button", { name: "Mark Visited" })).toBeVisible();
  await expect(page.getByTestId("atlas-undo-toast")).toBeVisible();

  const bulkButton = page.getByTestId("dossier-bulk-visit-button");
  await expect(bulkButton).toBeVisible();

  await bulkButton.click();
  await expect(page.getByTestId("bulk-visit-dialog")).toBeVisible();
  await page.getByTestId("confirm-bulk-visit-button").click();

  await expect(page.getByTestId("atlas-undo-toast")).toContainText("Marked 1 place as visited");
  await page.getByTestId("atlas-undo-button").click();
  await expect(bulkButton).toBeVisible({ timeout: 10_000 });

  await page.getByTestId("reset-view-button").click();
  await expect(dossier).not.toBeVisible();
  await expect(detailCard).not.toBeVisible();
  await expect(page).not.toHaveURL(/region=/);

  expect(consoleErrors, `Console errors: ${consoleErrors.join("; ")}`).toEqual([]);
  expect(pageErrors, `Page errors: ${pageErrors.join("; ")}`).toEqual([]);
  expect(failedRequests, `Failed requests: ${failedRequests.join("; ")}`).toEqual([]);
  expect(errorResponses, `HTTP >=400 responses: ${errorResponses.join("; ")}`).toEqual([]);
});
