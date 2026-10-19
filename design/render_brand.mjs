// Renders the PNG brand assets for design/brand.py: reads jobs as JSON from stdin,
// each {html, w, h, scale?, out}, and screenshots them with Chromium.
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";

const require = createRequire(import.meta.url);
let playwright;
for (const p of ["playwright", "/opt/node-tools/node_modules/playwright"]) {
  try { playwright = require(p); break; } catch (e) { /* try next */ }
}
if (!playwright) throw new Error("Playwright not found (npm i playwright)");

const jobs = JSON.parse(readFileSync(0, "utf8"));
const browser = await playwright.chromium.launch();
for (const job of jobs) {
  const page = await browser.newPage({ viewport: { width: job.w, height: job.h }, deviceScaleFactor: job.scale || 1 });
  const html = job.fit ? job.html.replace("<svg ", `<svg width="${job.w}" height="${job.h}" `) : job.html;
  await page.setContent(html, { waitUntil: "load" });
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: job.out, clip: { x: 0, y: 0, width: job.w, height: job.h } });
  console.log("wrote", job.out);
  await page.close();
}
await browser.close();
