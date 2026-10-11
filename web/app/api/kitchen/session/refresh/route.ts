import { KITCHEN_POLICY, sessionHandlers } from "@/lib/auth/session-server";

export const POST = sessionHandlers(KITCHEN_POLICY).refresh;
