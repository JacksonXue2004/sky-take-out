// One key per checkout attempt: if the request is retried, the backend returns the order it already
// created instead of placing a second one. crypto.randomUUID only exists in secure contexts (HTTPS or
// localhost), so the key is built from crypto.getRandomValues, which also works over plain HTTP.
export function newIdempotencyKey(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}
