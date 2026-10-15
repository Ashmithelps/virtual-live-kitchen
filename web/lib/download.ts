/** Saves a fetched file (e.g. `api.GET(..., { parseAs: "blob" })`) under the given name. */
export function saveBlob(result: { data?: unknown; response: Response }, filename: string) {
  if (!result.response.ok || !(result.data instanceof Blob)) throw new Error(`Couldn't download the file (HTTP ${result.response.status})`);
  const url = URL.createObjectURL(result.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
