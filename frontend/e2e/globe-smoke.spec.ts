import fs from "node:fs";
import path from "node:path";
import { expect, test } from "@playwright/test";

test("renders MapLibre globe with live PostGIS health and zero runtime errors", async ({
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
    failedRequests.push(`${req.method()} ${req.url()} (${req.failure()?.errorText ?? "failed"})`);
  });
  page.on("response", (res) => {
    if (res.status() >= 400) {
      errorResponses.push(`${res.status()} ${res.url()}`);
    }
  });

  await page.goto("/", { waitUntil: "domcontentloaded" });

  const healthStatus = page.getByTestId("atlas-health-status");
  await expect(healthStatus).toContainText("UP · PostGIS 3.", { timeout: 15_000 });

  const mapContainer = page.getByTestId("atlas-map-canvas");
  await expect(mapContainer).toHaveAttribute("data-map-loaded", "true", { timeout: 25_000 });

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

  await page.getByRole("button", { name: "2D Map" }).click();
  await expect(page).toHaveURL(/view=map/);

  await page.getByRole("button", { name: "Switch to dark theme" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expect(page).toHaveURL(/theme=dark/);

  expect(consoleErrors, `Console errors: ${consoleErrors.join("; ")}`).toEqual([]);
  expect(pageErrors, `Page errors: ${pageErrors.join("; ")}`).toEqual([]);
  expect(failedRequests, `Failed requests: ${failedRequests.join("; ")}`).toEqual([]);
  expect(errorResponses, `HTTP >=400 responses: ${errorResponses.join("; ")}`).toEqual([]);
});
