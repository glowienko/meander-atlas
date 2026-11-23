import fs from "node:fs";
import path from "node:path";
import ts from "typescript";

const SRC_DIR = path.resolve(process.cwd(), "src");
const IGNORED_FILES = new Set([path.resolve(SRC_DIR, "api/schema.d.ts")]);
const ALLOWED_DIRECTIVES = [
  /^\/\/\s*biome-ignore(?:-all)?\b/,
  /^\/\/\s*@ts-expect-error\b/,
  /^\/\*\s*@vite-ignore\s*\*\/$/,
];
const SOURCE_EXTENSIONS = new Set([".ts", ".tsx", ".mts", ".cts", ".js", ".jsx", ".mjs"]);

function collectFiles(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectFiles(fullPath));
    } else if (
      entry.isFile() &&
      SOURCE_EXTENSIONS.has(path.extname(entry.name)) &&
      !IGNORED_FILES.has(fullPath)
    ) {
      files.push(fullPath);
    }
  }
  return files;
}

function isAllowedDirective(text) {
  const trimmed = text.trim();
  return ALLOWED_DIRECTIVES.some((pattern) => pattern.test(trimmed));
}

function scanFile(filePath) {
  const content = fs.readFileSync(filePath, "utf8");
  const variant =
    filePath.endsWith(".tsx") || filePath.endsWith(".jsx")
      ? ts.LanguageVariant.JSX
      : ts.LanguageVariant.Standard;
  const scanner = ts.createScanner(ts.ScriptTarget.Latest, false, variant, content);
  const violations = [];

  let token = scanner.scan();
  while (token !== ts.SyntaxKind.EndOfFileToken) {
    if (
      token === ts.SyntaxKind.SingleLineCommentTrivia ||
      token === ts.SyntaxKind.MultiLineCommentTrivia
    ) {
      const text = scanner.getTokenText();
      if (!isAllowedDirective(text)) {
        const pos = scanner.getTokenPos();
        const line = content.slice(0, pos).split("\n").length;
        violations.push(`${path.relative(process.cwd(), filePath)}:${line}: forbidden comment`);
      }
    }
    token = scanner.scan();
  }
  return violations;
}

const violations = collectFiles(SRC_DIR).flatMap(scanFile);
if (violations.length > 0) {
  process.stderr.write(
    `Code comments are prohibited in frontend/src/. Put design rationale in docs/adr/:\n${violations.map((v) => `  - ${v}`).join("\n")}\n`,
  );
  process.exit(1);
}
