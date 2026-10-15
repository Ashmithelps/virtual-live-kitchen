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

// Owner dashboard and admin
export type Restaurant = Schemas["RestaurantResponse"];
export type RestaurantRequest = Schemas["RestaurantRequest"];
export type OpeningSlot = Schemas["OpeningSlotDto"];
export type MenuItemRequest = Schemas["MenuItemRequest"];
export type DiningTable = Schemas["DiningTableResponse"];
export type Station = Schemas["StationResponse"];
export type Chef = Schemas["ChefResponse"];
export type OwnerClip = Schemas["OwnerClipResponse"];
export type ClipState = OwnerClip["status"];
export type UploadPurpose = Schemas["UploadRequest"]["purpose"];
export type UserPage = Schemas["PageResponseUserResponse"];

// Clips (M11)
export type ClipPlayback = Schemas["ClipPlaybackResponse"];
export type ClipShare = Schemas["ClipShareResponse"];
export type PublicClip = Schemas["PublicClipResponse"];
export type PublicChef = Schemas["PublicChefResponse"];
