"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import type { Order, OrderType, PaymentMethod, RestaurantDetail } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/auth-context";
import { itemCount, subtotalPaise, type Cart } from "@/lib/cart/cart";
import { useCart } from "@/lib/cart/use-cart";
import { ORDER_TYPE_LABEL, formatPaise, isValidPhone, normalizePhone } from "@/lib/format";
import { distanceKm } from "@/lib/geo";
import { saveGuestOrder } from "@/lib/guest-orders";
import { restaurantQuery } from "@/lib/queries";
import { useTableSession, type TableSession } from "@/lib/table-session";
import { useHydrated } from "@/lib/storage";
import { QuantityStepper } from "@/components/menu-item-row";
import { Button, Card, EmptyState, ErrorState, Field, InlineError, PageLoading, TextLink, VegMark, inputClass } from "@/components/ui";

export default function CheckoutPage() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Checkout />
    </Suspense>
  );
}

function Checkout() {
  const slug = useSearchParams().get("r") ?? "";
  const hydrated = useHydrated();
  const { cart } = useCart(slug);
  const restaurant = useQuery({ ...restaurantQuery(slug), enabled: !!slug });

  if (!hydrated || (slug && restaurant.isPending)) return <PageLoading />;
  if (!slug || !cart) {
    return <EmptyState title="Your cart is empty" message="Add some dishes from a restaurant's menu first." action={<TextLink href="/">Browse restaurants</TextLink>} />;
  }
  if (restaurant.isError) return <ErrorState message={errorMessage(restaurant.error)} onRetry={() => restaurant.refetch()} />;
  return <CheckoutForm restaurant={restaurant.data!} cart={cart} />;
}

type Address = { address: string; latitude: string; longitude: string };

function CheckoutForm({ restaurant, cart }: { restaurant: RestaurantDetail; cart: Cart }) {
  const router = useRouter();
  const auth = useAuth();
  const { increment, decrement, clear } = useCart(restaurant.slug);
  const table = useTableSession(restaurant.slug);

  // At a scanned table the order is dine-in for that table, full stop. Otherwise dine-in isn't
  // offered: the table can only come from scanning its QR code.
  const dineIn = !!table && restaurant.orderTypes.includes("DINE_IN");
  const types: OrderType[] = dineIn ? ["DINE_IN"] : restaurant.orderTypes.filter((t) => t !== "DINE_IN");
  const [orderType, setOrderType] = useState<OrderType | undefined>(types[0]);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("ONLINE");
  const [address, setAddress] = useState<Address>({ address: "", latitude: "", longitude: "" });
  const [guest, setGuest] = useState({ name: "", phone: "" });
  const [notes, setNotes] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const isGuest = orderType === "DINE_IN" && auth.status === "anonymous";
  const needsLogin = orderType !== undefined && orderType !== "DINE_IN" && auth.status === "anonymous";

  const place = useMutation({
    mutationFn: () => placeOrder({ restaurant, cart, orderType: orderType!, table, paymentMethod, address, guest, notes, isGuest }),
    onSuccess: (order) => {
      clear();
      // Prepaid orders go straight into payment on the order page.
      router.push(order.paymentStatus === "PENDING" ? `/orders/${order.id}?pay=1` : `/orders/${order.id}`);
    },
  });

  const submit = () => {
    setFormError(null);
    const problem = validate({ orderType, address, guest, isGuest, restaurant });
    if (problem) return setFormError(problem);
    place.mutate();
  };

  const subtotal = subtotalPaise(cart);

  // Staff accounts can't place customer orders (the API refuses them); say so up front.
  if (auth.status === "authenticated" && auth.user.role !== "CUSTOMER") {
    return (
      <EmptyState
        title="Ordering needs a customer account"
        message={`You're signed in as ${auth.user.name} (${auth.user.role.replace("_", " ").toLowerCase()}). Log out to order as a guest or customer.`}
        action={<Button variant="secondary" onClick={() => auth.logout()}>Log out</Button>}
      />
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Checkout</h1>

      <Card>
        <h2 className="font-semibold">{restaurant.name}</h2>
        <ul className="mt-3 divide-y divide-stone-100">
          {cart.lines.map((line) => (
            <li key={line.menuItemId} className="flex items-center gap-3 py-2.5">
              <VegMark veg={line.veg} />
              <span className="min-w-0 flex-1 text-sm">{line.name}</span>
              <QuantityStepper quantity={line.quantity} label={line.name} onIncrement={() => increment(line.menuItemId)} onDecrement={() => decrement(line.menuItemId)} />
              <span className="w-16 text-right text-sm font-medium">{formatPaise(line.unitPricePaise * line.quantity)}</span>
            </li>
          ))}
        </ul>
        <div className="mt-3 flex justify-between border-t border-stone-200 pt-3 font-semibold">
          <span>Subtotal ({itemCount(cart)} items)</span>
          <span>{formatPaise(subtotal)}</span>
        </div>
        <p className="mt-1 text-xs text-stone-500">GST is added to the final bill. Prices are confirmed when you place the order.</p>
      </Card>

      <Card className="space-y-3">
        <h2 className="font-semibold">How would you like it?</h2>
        {dineIn ? (
          <p className="rounded-xl bg-brand-50 px-3 py-2 text-sm text-brand-700">
            Dine-in at <strong>Table {table!.tableLabel}</strong> (from the QR code you scanned)
          </p>
        ) : types.length === 0 ? (
          <p className="text-sm text-stone-600">This restaurant only takes dine-in orders. Scan the QR code on your table to order.</p>
        ) : (
          <div className="grid grid-cols-2 gap-2">
            {types.map((t) => (
              <button
                key={t}
                type="button"
                aria-pressed={orderType === t}
                onClick={() => setOrderType(t)}
                className={`min-h-11 rounded-xl border text-sm font-semibold ${orderType === t ? "border-brand-600 bg-brand-50 text-brand-700" : "border-stone-300 bg-white"}`}
              >
                {ORDER_TYPE_LABEL[t]}
              </button>
            ))}
          </div>
        )}
        {!dineIn && restaurant.orderTypes.includes("DINE_IN") && (
          <p className="text-xs text-stone-500">Eating in? Scan the QR code on your table to order for it.</p>
        )}

        {orderType === "DELIVERY" && <DeliveryAddressFields value={address} onChange={setAddress} restaurant={restaurant} />}

        {orderType === "DINE_IN" && (
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-stone-700">Payment</legend>
            <div className="grid grid-cols-2 gap-2">
              {(["ONLINE", "PAY_LATER"] as const).map((m) => (
                <button
                  key={m}
                  type="button"
                  aria-pressed={paymentMethod === m}
                  onClick={() => setPaymentMethod(m)}
                  className={`min-h-11 rounded-xl border text-sm font-semibold ${paymentMethod === m ? "border-brand-600 bg-brand-50 text-brand-700" : "border-stone-300 bg-white"}`}
                >
                  {m === "ONLINE" ? "Pay now" : "Pay at the table"}
                </button>
              ))}
            </div>
          </fieldset>
        )}
      </Card>

      {orderType !== undefined && (
        <Card className="space-y-3">
          <h2 className="font-semibold">Your details</h2>
          {auth.status === "loading" ? (
            <PageLoading />
          ) : auth.status === "authenticated" ? (
            <p className="text-sm text-stone-600">
              Ordering as <strong>{auth.user.name}</strong> ({auth.user.phone})
            </p>
          ) : needsLogin ? (
            <p className="text-sm text-stone-600">
              Please <TextLink href={`/login?next=${encodeURIComponent(`/checkout?r=${restaurant.slug}`)}`}>log in or create an account</TextLink> for{" "}
              {ORDER_TYPE_LABEL[orderType].toLowerCase()} orders.
            </p>
          ) : (
            <>
              <Field label="Name">
                <input className={inputClass} value={guest.name} onChange={(e) => setGuest({ ...guest, name: e.target.value })} autoComplete="name" />
              </Field>
              <Field label="Mobile number" hint="We'll use this to find your orders. No account needed.">
                <input className={inputClass} value={guest.phone} onChange={(e) => setGuest({ ...guest, phone: e.target.value })} inputMode="tel" autoComplete="tel" placeholder="98765 43210" />
              </Field>
              <p className="text-xs text-stone-500">
                Have an account? <TextLink href={`/login?next=${encodeURIComponent(`/checkout?r=${restaurant.slug}`)}`}>Log in</TextLink>
              </p>
            </>
          )}
          <Field label="Notes for the kitchen (optional)">
            <textarea className={inputClass} rows={2} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Less spicy, no onions…" />
          </Field>
        </Card>
      )}

      <InlineError message={formError ?? (place.isError ? errorMessage(place.error) : null)} />

      <div className="pb-safe fixed inset-x-0 bottom-0 z-30 border-t border-stone-200 bg-white px-4 pt-3">
        <div className="mx-auto max-w-2xl">
          <Button className="w-full" onClick={submit} busy={place.isPending} disabled={!orderType || needsLogin || auth.status === "loading" || !restaurant.openNow}>
            {!restaurant.openNow ? "Restaurant is closed" : paymentMethod === "ONLINE" || orderType !== "DINE_IN" ? `Place order · ${formatPaise(subtotal)} + GST` : "Place order"}
          </Button>
        </div>
      </div>
    </div>
  );
}

function DeliveryAddressFields({ value, onChange, restaurant }: { value: Address; onChange: (a: Address) => void; restaurant: RestaurantDetail }) {
  const radiusKm = restaurant.deliveryRadiusKm;
  const away = deliveryDistance(value, restaurant);
  const [locating, setLocating] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);

  const useMyLocation = () => {
    if (!("geolocation" in navigator)) return setGeoError("Location isn't available in this browser; enter coordinates below.");
    setLocating(true);
    setGeoError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        onChange({ ...value, latitude: pos.coords.latitude.toFixed(6), longitude: pos.coords.longitude.toFixed(6) });
      },
      () => {
        setLocating(false);
        setGeoError("Couldn't get your location. Allow location access or enter coordinates below.");
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  };

  return (
    <div className="space-y-3">
      <Field label="Delivery address">
        <textarea className={inputClass} rows={2} value={value.address} onChange={(e) => onChange({ ...value, address: e.target.value })} autoComplete="street-address" placeholder="Flat, building, street, area" />
      </Field>
      <div className="space-y-2">
        <Button type="button" variant="secondary" className="w-full" busy={locating} onClick={useMyLocation}>
          📍 Use my current location
        </Button>
        <InlineError message={geoError} />
        <div className="grid grid-cols-2 gap-2">
          <Field label="Latitude">
            <input className={inputClass} inputMode="decimal" value={value.latitude} onChange={(e) => onChange({ ...value, latitude: e.target.value })} />
          </Field>
          <Field label="Longitude">
            <input className={inputClass} inputMode="decimal" value={value.longitude} onChange={(e) => onChange({ ...value, longitude: e.target.value })} />
          </Field>
        </div>
        {radiusKm !== null && (
          <p className={`text-xs ${away !== null && away > radiusKm ? "font-semibold text-red-700" : "text-stone-500"}`}>
            Delivers within {radiusKm} km
            {away !== null && `; you're ${away.toFixed(1)} km away${away > radiusKm ? ", which is too far" : ""}`}.
          </p>
        )}
      </div>
    </div>
  );
}

/** Distance from the restaurant to the entered location, once both coordinates are valid. */
function deliveryDistance(address: Address, restaurant: RestaurantDetail): number | null {
  const lat = Number(address.latitude);
  const lng = Number(address.longitude);
  if (!address.latitude || !address.longitude || Number.isNaN(lat) || Number.isNaN(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;
  return distanceKm(restaurant.latitude, restaurant.longitude, lat, lng);
}

function validate({
  orderType,
  address,
  guest,
  isGuest,
  restaurant,
}: {
  orderType?: OrderType;
  address: Address;
  guest: { name: string; phone: string };
  isGuest: boolean;
  restaurant: RestaurantDetail;
}): string | null {
  if (!orderType) return "Choose how you'd like your order.";
  if (orderType === "DELIVERY") {
    if (!address.address.trim()) return "Enter your delivery address.";
    const lat = Number(address.latitude);
    const lng = Number(address.longitude);
    if (!address.latitude || !address.longitude || Number.isNaN(lat) || Number.isNaN(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
      return "Add your location (use the button, or enter latitude and longitude).";
    }
    const away = deliveryDistance(address, restaurant);
    if (restaurant.deliveryRadiusKm !== null && away !== null && away > restaurant.deliveryRadiusKm) {
      return `You're ${away.toFixed(1)} km away; ${restaurant.name} delivers within ${restaurant.deliveryRadiusKm} km. Try takeaway instead.`;
    }
  }
  if (isGuest) {
    if (!guest.name.trim()) return "Enter your name.";
    if (!isValidPhone(normalizePhone(guest.phone))) return "Enter a valid mobile number, e.g. 98765 43210.";
  }
  return null;
}

async function placeOrder(input: {
  restaurant: RestaurantDetail;
  cart: Cart;
  orderType: OrderType;
  table: TableSession | null;
  paymentMethod: PaymentMethod;
  address: Address;
  guest: { name: string; phone: string };
  notes: string;
  isGuest: boolean;
}): Promise<Order> {
  const items = input.cart.lines.map((l) => ({ menuItemId: l.menuItemId, quantity: l.quantity }));
  const notes = input.notes.trim() || undefined;
  const paymentMethod = input.orderType === "DINE_IN" ? input.paymentMethod : "ONLINE";

  if (input.isGuest) {
    const result = await unwrap(
      api.POST("/api/guest/orders", {
        body: {
          name: input.guest.name.trim(),
          phone: normalizePhone(input.guest.phone),
          tableSessionToken: input.table!.token,
          items,
          paymentMethod,
          notes,
        },
      }),
    );
    saveGuestOrder({
      orderId: result.order.id,
      token: result.guestToken,
      expiresAt: result.guestTokenExpiresAt,
      displayNumber: result.order.displayNumber,
      restaurantName: result.order.restaurantName,
      placedAt: result.order.timeline.placedAt,
    });
    return result.order;
  }

  return unwrap(
    api.POST("/api/orders", {
      body: {
        restaurantId: input.restaurant.id,
        orderType: input.orderType,
        items,
        paymentMethod,
        notes,
        tableSessionToken: input.orderType === "DINE_IN" ? input.table!.token : undefined,
        deliveryAddress:
          input.orderType === "DELIVERY"
            ? { address: input.address.address.trim(), latitude: Number(input.address.latitude), longitude: Number(input.address.longitude) }
            : undefined,
      },
    }),
  );
}
