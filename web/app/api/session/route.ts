import { CUSTOMER_POLICY, sessionHandlers } from "@/lib/auth/session-server";

const handlers = sessionHandlers(CUSTOMER_POLICY);

/** POST = log in with phone + password. DELETE = log out (revoke + drop cookie). */
export const POST = handlers.login;
export const DELETE = handlers.logout;
