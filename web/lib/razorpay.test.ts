import { describe, expect, it } from "vitest";
import { checkoutOptions, type Checkout } from "./razorpay";

describe("razorpay checkout options", () => {
  it("passes through only what the backend computed", () => {
    const checkout: Checkout = {
      provider: "razorpay",
      providerOrderId: "order_abc",
      keyId: "rzp_test_123",
      amountPaise: 62685,
      currency: "INR",
      merchantName: "Virtual Live Kitchen",
      description: "Order #12 at Spice Garden",
      prefillName: "Asha",
      prefillContact: "+919811111111",
    };
    expect(checkoutOptions(checkout)).toMatchObject({
      key: "rzp_test_123",
      order_id: "order_abc",
      amount: 62685,
      currency: "INR",
      prefill: { name: "Asha", contact: "+919811111111" },
    });
  });

  it("omits missing prefill values instead of sending null", () => {
    const options = checkoutOptions({
      provider: "razorpay",
      providerOrderId: "o",
      keyId: "k",
      amountPaise: 1,
      currency: "INR",
      merchantName: "m",
      description: "d",
      prefillName: null,
      prefillContact: null,
    });
    expect(options.prefill).toEqual({ name: undefined, contact: undefined });
  });
});
