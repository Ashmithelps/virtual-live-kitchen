// Refreshes openapi/openapi.json, the source of the generated API types (npm run api:gen).
// Prefers a running backend; falls back to the file written by the backend's OpenApiDocsIT.
import { copyFile, writeFile } from "node:fs/promises";

const target = new URL("../openapi/openapi.json", import.meta.url);
const backendUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

try {
  const response = await fetch(`${backendUrl}/v3/api-docs`);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const spec = await response.json();
  await writeFile(target, JSON.stringify(spec, null, 2) + "\n");
  console.log(`openapi.json updated from ${backendUrl}`);
} catch (error) {
  const fallback = new URL("../../backend/target/openapi.json", import.meta.url);
  console.warn(`Backend not reachable (${error.message}); copying ${fallback.pathname}`);
  await copyFile(fallback, target);
}
