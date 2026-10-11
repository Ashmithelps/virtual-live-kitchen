import { KITCHEN_POLICY, sessionHandlers } from "@/lib/auth/session-server";

const handlers = sessionHandlers(KITCHEN_POLICY);

/** Kitchen display login for chefs and owners (own cookie, separate from the customer session). */
export const POST = handlers.login;
export const DELETE = handlers.logout;
