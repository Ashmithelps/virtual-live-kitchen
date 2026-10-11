import type { components } from "@/lib/api/schema";

export type Checkout = components["schemas"]["PaymentCheckoutResponse"];

/** What Razorpay Checkout returns after a successful payment; sent to the backend to verify. */
export type CheckoutSuccess = { providerOrderId: string; providerPaymentId: string; signature: string };

export type CheckoutOutcome = { kind: "paid"; result: CheckoutSuccess } | { kind: "dismissed"; lastError: string | null };

type RazorpayResponse = { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string };
type RazorpayFailure = { error?: { description?: string } };
type RazorpayInstance = { open(): void; on(event: "payment.failed", handler: (r: RazorpayFailure) => void): void };
type RazorpayConstructor = new (options: Record<string, unknown>) => RazorpayInstance;

const SCRIPT_URL = "https://checkout.razorpay.com/v1/checkout.js";
let loading: Promise<RazorpayConstructor> | null = null;

function loadRazorpay(): Promise<RazorpayConstructor> {
  const existing = (window as unknown as { Razorpay?: RazorpayConstructor }).Razorpay;
  if (existing) return Promise.resolve(existing);
  loading ??= new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () => {
      const ctor = (window as unknown as { Razorpay?: RazorpayConstructor }).Razorpay;
      if (ctor) resolve(ctor);
      else reject(new Error("Razorpay checkout didn't load"));
    };
    script.onerror = () => {
      loading = null;
      reject(new Error("Couldn't load Razorpay checkout. Check your connection and try again."));
    };
    document.head.appendChild(script);
  });
  return loading;
}

/** Options for Razorpay Checkout, built only from what our backend returned (amount included). */
export function checkoutOptions(checkout: Checkout) {
  return {
    key: checkout.keyId,
    order_id: checkout.providerOrderId,
    amount: checkout.amountPaise,
    currency: checkout.currency,
    name: checkout.merchantName,
    description: checkout.description,
    prefill: { name: checkout.prefillName ?? undefined, contact: checkout.prefillContact ?? undefined },
    theme: { color: "#ea580c" },
  };
}

/**
 * Opens Razorpay Checkout. Resolves "paid" with the ids + signature to verify server-side, or
 * "dismissed" if the customer closes it (with the last failure message, if a try failed).
 * Razorpay keeps the dialog open after a failed attempt so the customer can retry.
 */
export async function openRazorpayCheckout(checkout: Checkout): Promise<CheckoutOutcome> {
  const Razorpay = await loadRazorpay();
  return new Promise((resolve) => {
    let lastError: string | null = null;
    const instance = new Razorpay({
      ...checkoutOptions(checkout),
      handler: (r: RazorpayResponse) =>
        resolve({ kind: "paid", result: { providerOrderId: r.razorpay_order_id, providerPaymentId: r.razorpay_payment_id, signature: r.razorpay_signature } }),
      modal: { ondismiss: () => resolve({ kind: "dismissed", lastError }), confirm_close: true },
    });
    instance.on("payment.failed", (r) => {
      lastError = r.error?.description ?? "The payment failed.";
    });
    instance.open();
  });
}
