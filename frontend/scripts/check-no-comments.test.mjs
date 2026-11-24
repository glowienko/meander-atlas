import assert from "node:assert/strict";
import test from "node:test";
import { scanSource } from "./check-no-comments.mjs";

test("allows URLs, template expressions, regex literals, and JSX apostrophes/slashes", () => {
  const source = [
    'const url = "https://tiles.openfreemap.org/styles/positron";',
    `const path = \`\${url}//tiles/\${1 + 2}'s\`;`,
    "const regex = /\\/\\//;",
    "export function Sample() {",
    "  return <p>It's open 24/7 // http://example.com /* literal */</p>;",
    "}",
  ].join("\n");

  assert.deepEqual(scanSource(source, "sample.tsx"), []);
});

test("allows explicit tool directives only", () => {
  const source = [
    "// biome-ignore lint/suspicious/noConsole: debug",
    "const a = 1;",
    "// @ts-expect-error intentional check",
    "const b: string = 1;",
    'const mod = import(/* @vite-ignore */ "x");',
    "export { a, b, mod };",
  ].join("\n");

  assert.deepEqual(scanSource(source, "directives.ts"), []);
});

test("rejects line, block, JSX, empty-block, and post-template comments", () => {
  assert.equal(scanSource("// comment only\n", "only.ts").length, 1);
  assert.equal(scanSource(`const x = \`\${a}'s\`;\n// real comment\n`, "after-tpl.ts").length, 1);
  assert.equal(scanSource("function f(/* param */) { /* empty */ }\n", "empty.ts").length, 2);
  assert.equal(
    scanSource("export const C = () => <div>{/* jsx comment */}</div>;\n", "jsx.tsx").length,
    1,
  );
  assert.equal(scanSource("export const x = 1; // trailing\n", "trailing.ts").length, 1);
});
