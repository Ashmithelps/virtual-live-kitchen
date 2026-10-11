import { describe, expect, it } from "vitest";
import { MAX_QUANTITY, addItem, changeQuantity, isCart, itemCount, quantityOf, subtotalPaise, type Cart } from "./cart";

const restaurant = { restaurantId: "r1", restaurantSlug: "spice", restaurantName: "Spice" };
const tikka = { id: "i1", name: "Tikka", pricePaise: 24900, veg: false };
const dal = { id: "i2", name: "Dal", pricePaise: 9900, veg: true };

describe("cart", () => {
  it("adds new items and increments existing ones", () => {
    let cart = addItem(null, restaurant, tikka);
    cart = addItem(cart, restaurant, tikka);
    cart = addItem(cart, restaurant, dal);
    expect(quantityOf(cart, "i1")).toBe(2);
    expect(itemCount(cart)).toBe(3);
    expect(subtotalPaise(cart)).toBe(2 * 24900 + 9900);
  });

  it("removes a line when it reaches zero and caps quantity", () => {
    let cart: Cart = addItem(null, restaurant, dal);
    cart = changeQuantity(cart, "i2", -1);
    expect(cart.lines).toHaveLength(0);

    cart = addItem(null, restaurant, dal);
    for (let i = 0; i < 100; i++) cart = changeQuantity(cart, "i2", +1);
    expect(quantityOf(cart, "i2")).toBe(MAX_QUANTITY);
  });

  it("does not mutate its input", () => {
    const before = addItem(null, restaurant, tikka);
    const after = changeQuantity(before, "i1", +1);
    expect(quantityOf(before, "i1")).toBe(1);
    expect(quantityOf(after, "i1")).toBe(2);
  });

  it("rejects corrupted storage", () => {
    expect(isCart(null)).toBe(false);
    expect(isCart({ restaurantId: "r", restaurantSlug: "s", lines: [{ menuItemId: "x", quantity: -1 }] })).toBe(false);
    expect(isCart(addItem(null, restaurant, tikka))).toBe(true);
  });
});
