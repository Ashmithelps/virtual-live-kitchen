/** Error thrown for any non-2xx API response; `message` comes from the backend's ErrorResponse. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof TypeError) return "Can't reach the server. Check your connection and try again.";
  return "Something went wrong. Please try again.";
}

function messageFrom(body: unknown, status: number): string {
  if (body && typeof body === "object" && "message" in body && typeof body.message === "string" && body.message) {
    return body.message;
  }
  return `Request failed (${status})`;
}

/**
 * Turns an openapi-fetch result into data-or-throw, which is what TanStack Query expects.
 * Typed loosely on purpose: openapi-fetch's result union is precise but awkward to name.
 */
export async function unwrap<T>(
  call: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await call;
  if (error !== undefined || !response.ok) {
    throw new ApiError(response.status, messageFrom(error, response.status));
  }
  return data as T;
}
