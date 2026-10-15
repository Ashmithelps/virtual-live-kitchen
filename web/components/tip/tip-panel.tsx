"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { errorMessage } from "@/lib/api/errors";
import { formatPaise, paiseFromRupees } from "@/lib/format";
import type { OrderAccess } from "@/lib/orders";
import { openRazorpayCheckout, type Checkout } from "@/lib/razorpay";
import { confirmTip, fetchOrderTips, startTip, type TipCheckout, type TipSource } from "@/lib/tips";
import { useToast } from "@/components/toast";
import { Button, Card, InlineError, inputBaseClass } from "@/components/ui";

/**
 * "Loved watching Chef X make this? Tip ₹20 / ₹50 / ₹100 / custom". Only chefs whose payouts are ACTIVE are
 * offered (the server decides); the whole tip goes to the chef's own account through Razorpay Route.
 */
export function TipPanel({
  orderId,
  access,
  source,
  preselect,
}: {
  orderId: string;
  access: OrderAccess;
  source: TipSource;
  /** From checkout (?tipChef=&tipAmount=): the chef and amount the customer already picked. */
  preselect?: { chefId: string; amountPaise: number };
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = ["order-tips", orderId, access.kind];
  const options = useQuery({ queryKey: key, queryFn: () => fetchOrderTips(orderId, access) });
  const [chefId, setChefId] = useState<string | null>(preselect?.chefId ?? null);
  const [amount, setAmount] = useState<number | null>(preselect?.amountPaise ?? null);
  const [custom, setCustom] = useState("");
  const [mock, setMock] = useState<TipCheckout | null>(null);

  const thank = (chefName: string, paise: number) => toast.success(`Thank you! ${formatPaise(paise)} goes straight to ${chefName}.`);

  const finish = useMutation({
    mutationFn: ({ checkout, paymentId, signature }: { checkout: TipCheckout; paymentId: string; signature: string }) =>
      confirmTip(orderId, access, checkout.tip.id, { providerOrderId: checkout.checkout.providerOrderId, providerPaymentId: paymentId, signature }),
    onSuccess: (tip) => {
      setMock(null);
      if (tip.status === "FAILED") {
        toast.error("The tip payment didn't go through. No money was taken.");
      } else {
        thank(tip.chefName, tip.amountPaise);
        setAmount(null);
        setCustom("");
      }
      void queryClient.invalidateQueries({ queryKey: key });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const pay = useMutation({
    mutationFn: async ({ chef, paise }: { chef: string; paise: number }) => {
      const started = await startTip(orderId, access, { chefId: chef, amountPaise: paise, source });
      if (started.checkout.provider !== "razorpay") {
        setMock(started);
        return;
      }
      const outcome = await openRazorpayCheckout(started.checkout as Checkout);
      if (outcome.kind === "dismissed") {
        toast.info(outcome.lastError ? `${outcome.lastError} No money was taken.` : "Tip cancelled. No money was taken.");
        return;
      }
      finish.mutate({ checkout: started, paymentId: outcome.result.providerPaymentId, signature: outcome.result.signature });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (options.isPending || options.isError) return null;
  const { chefs, tips, presetsPaise, minPaise, maxPaise } = options.data;
  const paid = tips.filter((t) => t.status === "PAID" || t.status === "TRANSFERRED");
  if (chefs.length === 0 && paid.length === 0) return null;

  const chef = chefs.find((c) => c.chefId === chefId) ?? (chefs.length === 1 ? chefs[0] : undefined);
  const customPaise = custom ? paiseFromRupees(custom) : null;
  const chosen = customPaise ?? amount;
  const amountProblem =
    custom && customPaise === null
      ? "Enter an amount in rupees."
      : chosen !== null && (chosen < minPaise || chosen > maxPaise)
        ? `Tips can be ${formatPaise(minPaise)} to ${formatPaise(maxPaise)}.`
        : null;
  const busy = pay.isPending || finish.isPending;

  return (
    <Card className="space-y-3 border-amber-200 bg-amber-50">
      {paid.map((t) => (
        <p key={t.id} className="text-sm text-green-800">
          ✓ You tipped {t.chefName} {formatPaise(t.amountPaise)}. Thank you!
        </p>
      ))}
      {chefs.length > 0 && (
        <>
          <p className="font-semibold">
            {chef ? (chef.cookedThisOrder ? `Loved watching ${chef.displayName} make this?` : `Tip ${chef.displayName}?`) : "Tip a chef?"}{" "}
            <span className="font-normal text-stone-600">The whole tip goes to them.</span>
          </p>
          {chefs.length > 1 && (
            <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Chef to tip">
              {chefs.map((c) => (
                <button
                  key={c.chefId}
                  role="radio"
                  aria-checked={chef?.chefId === c.chefId}
                  onClick={() => setChefId(c.chefId)}
                  className={`rounded-full border px-3 py-1.5 text-sm ${chef?.chefId === c.chefId ? "border-brand-600 bg-white font-semibold text-brand-700" : "border-stone-300 bg-white"}`}
                >
                  {c.displayName}
                  {c.cookedThisOrder ? " (cooked this)" : ""}
                </button>
              ))}
            </div>
          )}
          <div className="flex flex-wrap items-center gap-2" role="radiogroup" aria-label="Tip amount">
            {presetsPaise.map((p) => (
              <button
                key={p}
                role="radio"
                aria-checked={!custom && amount === p}
                onClick={() => {
                  setAmount(p);
                  setCustom("");
                }}
                className={`min-h-11 rounded-xl border px-4 text-sm font-semibold ${!custom && amount === p ? "border-brand-600 bg-brand-600 text-white" : "border-stone-300 bg-white"}`}
              >
                {formatPaise(p)}
              </button>
            ))}
            <input
              className={`${inputBaseClass} w-28`}
              inputMode="decimal"
              placeholder="Custom ₹"
              aria-label="Custom tip in rupees"
              value={custom}
              onChange={(e) => setCustom(e.target.value)}
            />
          </div>
          <InlineError message={amountProblem} />
          {mock ? (
            <div className="space-y-2 rounded-xl border border-dashed border-amber-400 bg-white p-3">
              <p className="text-sm text-amber-800">Test mode (mock gateway): no real money moves.</p>
              <Button className="w-full" busy={finish.isPending} onClick={() => finish.mutate({ checkout: mock, paymentId: `pay_mock_${crypto.randomUUID().slice(0, 8)}`, signature: "mock" })}>
                Approve {formatPaise(mock.checkout.amountPaise)} tip
              </Button>
              <Button variant="ghost" className="w-full" disabled={finish.isPending} onClick={() => finish.mutate({ checkout: mock, paymentId: "fail_mock", signature: "mock" })}>
                Simulate a failed payment
              </Button>
            </div>
          ) : (
            <Button className="w-full" busy={busy} disabled={!chef || chosen === null || !!amountProblem} onClick={() => chef && chosen && pay.mutate({ chef: chef.chefId, paise: chosen })}>
              {chef && chosen && !amountProblem ? `Tip ${chef.displayName} ${formatPaise(chosen)}` : "Choose an amount"}
            </Button>
          )}
        </>
      )}
    </Card>
  );
}
