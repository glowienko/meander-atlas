import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const TARGET_DIRS = ["src", "e2e"];
const IGNORED_FILES = new Set([path.resolve(process.cwd(), "src/api/schema.d.ts")]);
const ALLOWED_DIRECTIVES = [
  /^\/\/\s*biome-ignore(?:-all)?\b/,
  /^\/\/\s*@ts-expect-error\b/,
  /^\/\*\s*@vite-ignore\s*\*\/$/,
];
const SOURCE_EXTENSIONS = new Set([".ts", ".tsx", ".mts", ".cts", ".js", ".jsx", ".mjs"]);

function collectFiles(dir) {
  if (!fs.existsSync(dir)) {
    return [];
  }
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

function scriptKindFor(filePath) {
  switch (path.extname(filePath)) {
    case ".tsx":
      return ts.ScriptKind.TSX;
    case ".jsx":
      return ts.ScriptKind.JSX;
    case ".js":
    case ".mjs":
    case ".cjs":
      return ts.ScriptKind.JS;
    default:
      return ts.ScriptKind.TS;
  }
}

export function scanSource(content, filePath, displayPath = filePath) {
  const sourceFile = ts.createSourceFile(
    filePath,
    content,
    ts.ScriptTarget.Latest,
    true,
    scriptKindFor(filePath),
  );
  const seenPositions = new Set();
  const violations = [];

  const checkRanges = (ranges) => {
    if (!ranges) {
      return;
    }
    for (const range of ranges) {
      if (seenPositions.has(range.pos)) {
        continue;
      }
      seenPositions.add(range.pos);
      const text = content.slice(range.pos, range.end);
      if (!isAllowedDirective(text)) {
        const { line } = sourceFile.getLineAndCharacterOfPosition(range.pos);
        violations.push(`${displayPath}:${line + 1}: forbidden comment`);
      }
    }
  };

  const visit = (node) => {
    const children = node.getChildren(sourceFile);
    if (children.length === 0) {
      checkRanges(ts.getLeadingCommentRanges(content, node.getFullStart()));
      checkRanges(ts.getTrailingCommentRanges(content, node.getEnd()));
      return;
    }
    for (const child of children) {
      visit(child);
    }
  };

  visit(sourceFile);
  return violations;
}

export function scanFile(filePath) {
  const content = fs.readFileSync(filePath, "utf8");
  return scanSource(content, filePath, path.relative(process.cwd(), filePath));
}

const currentFile = fileURLToPath(import.meta.url);
if (process.argv[1] && path.resolve(process.argv[1]) === currentFile) {
  const files = TARGET_DIRS.flatMap((dir) => collectFiles(path.resolve(process.cwd(), dir)));
  const violations = files.flatMap(scanFile);
  if (violations.length > 0) {
    process.stderr.write(
      `Code comments are prohibited in frontend/src/ and frontend/e2e/. Put design rationale in docs/adr/:\n${violations.map((v) => `  - ${v}`).join("\n")}\n`,
    );
    process.exit(1);
  }
}
