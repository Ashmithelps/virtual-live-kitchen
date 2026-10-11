import type { components } from "./schema";

type Schemas = components["schemas"];

export type OrderType = Schemas["PlaceOrderRequest"]["orderType"];
export type PaymentMethod = NonNullable<Schemas["PlaceOrderRequest"]["paymentMethod"]>;
export type OrderStatus = Schemas["OrderResponse"]["status"];
export type Order = Schemas["OrderResponse"];
export type OrderPage = Schemas["PageResponseOrderResponse"];
export type RestaurantSummary = Schemas["RestaurantSummaryResponse"];
export type RestaurantDetail = Schemas["RestaurantDetailResponse"];
export type MenuCategory = Schemas["MenuCategoryResponse"];
export type MenuItem = Schemas["MenuItemResponse"];
export type TableLookup = Schemas["TableLookupResponse"];
export type User = Schemas["UserResponse"];
