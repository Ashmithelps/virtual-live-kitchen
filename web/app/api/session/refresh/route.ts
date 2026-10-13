import { SESSION_POLICY, sessionHandlers } from "@/lib/auth/session-server";

/** Rotates the refresh cookie and returns a new access token; 401 when there is no valid session. */
export const POST = sessionHandlers(SESSION_POLICY).refresh;
