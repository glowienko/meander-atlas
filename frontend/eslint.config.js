import reactHooks from "eslint-plugin-react-hooks";
import tseslint from "typescript-eslint";

const reactCompilerRules = Object.fromEntries(
  Object.keys(reactHooks.configs["recommended-latest"].rules).map((rule) => [rule, "error"]),
);

export default [
  {
    files: ["src/**/*.{ts,tsx}"],
    ignores: ["src/api/schema.d.ts"],
    languageOptions: { parser: tseslint.parser },
    plugins: { "react-hooks": reactHooks },
    rules: reactCompilerRules,
  },
];
