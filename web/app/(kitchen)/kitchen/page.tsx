"use client";

import { useQuery } from "@tanstack/react-query";
import { kitchenApi } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { useAuth } from "@/lib/auth/auth-context";
import { useStoredJson, writeJson } from "@/lib/storage";
import { Board } from "@/components/kds/board";
import { ConsentGate } from "@/components/kds/consent-gate";
import { KdsMessage } from "@/components/kds/kds-message";
import { KitchenLogin } from "@/components/kds/kitchen-login";
import { Picker } from "@/components/kds/picker";

/** Log in → (chef: accept consent, pick station | owner: pick restaurant) → live board. */
export default function KitchenPage() {
  const auth = useAuth();
  if (auth.status === "loading") return <KdsMessage title="Loading…" />;
  if (auth.status === "anonymous") return <KitchenLogin />;
  return auth.user.role === "CHEF" ? <ChefKitchen userId={auth.user.id} onLogout={auth.logout} /> : <OwnerKitchen userId={auth.user.id} userName={auth.user.name} onLogout={auth.logout} />;
}

function ChefKitchen({ userId, onLogout }: { userId: string; onLogout: () => void }) {
  const stationKey = `vk.kds.station.${userId}`;
  const chosenStationId = useStoredJson<string>(stationKey);
  const me = useQuery({ queryKey: ["kds-chef-me"], queryFn: () => unwrap(kitchenApi.GET("/api/chef/me")) });

  if (me.isPending) return <KdsMessage title="Loading…" />;
  if (me.isError) return <KdsMessage title="Couldn't load your profile" body={errorMessage(me.error)} action={{ label: "Try again", onClick: () => me.refetch() }} />;
  if (me.data.consentRequired) return <ConsentGate onAccepted={() => me.refetch()} onLogout={onLogout} />;
  if (me.data.stations.length === 0) {
    return <KdsMessage title="You're not on a station yet" body="Ask your manager to assign you to a kitchen station, then refresh." action={{ label: "Refresh", onClick: () => me.refetch() }} />;
  }

  const station = me.data.stations.find((s) => s.id === chosenStationId);
  if (!station) {
    return (
      <Picker
        title={`Hi ${me.data.displayName}, where are you working today?`}
        subtitle={me.data.restaurantName}
        options={me.data.stations.map((s) => ({ id: s.id, label: s.name }))}
        onPick={(id) => writeJson(stationKey, id)}
        onLogout={onLogout}
      />
    );
  }
  return (
    <Board
      role="CHEF"
      restaurantId={me.data.restaurantId}
      restaurantName={me.data.restaurantName}
      station={station}
      userName={me.data.displayName}
      onSwitch={() => writeJson(stationKey, null)}
      onLogout={onLogout}
    />
  );
}

function OwnerKitchen({ userId, userName, onLogout }: { userId: string; userName: string; onLogout: () => void }) {
  const restaurantKey = `vk.kds.restaurant.${userId}`;
  const chosenRestaurantId = useStoredJson<string>(restaurantKey);
  const restaurants = useQuery({ queryKey: ["kds-owner-restaurants"], queryFn: () => unwrap(kitchenApi.GET("/api/owner/restaurants")) });

  if (restaurants.isPending) return <KdsMessage title="Loading…" />;
  if (restaurants.isError) {
    return <KdsMessage title="Couldn't load your restaurants" body={errorMessage(restaurants.error)} action={{ label: "Try again", onClick: () => restaurants.refetch() }} />;
  }
  if (restaurants.data.length === 0) return <KdsMessage title="No restaurants yet" body="Create a restaurant first." action={{ label: "Log out", onClick: onLogout }} />;

  const restaurant = restaurants.data.find((r) => r.id === chosenRestaurantId) ?? (restaurants.data.length === 1 ? restaurants.data[0] : undefined);
  if (!restaurant) {
    return (
      <Picker
        title="Which restaurant?"
        options={restaurants.data.map((r) => ({ id: r.id, label: r.name, detail: r.status === "ACTIVE" ? r.city : `${r.city} · ${r.status.replace("_", " ").toLowerCase()}` }))}
        onPick={(id) => writeJson(restaurantKey, id)}
        onLogout={onLogout}
      />
    );
  }
  return (
    <Board
      role="OWNER"
      restaurantId={restaurant.id}
      restaurantName={restaurant.name}
      station={null}
      userName={userName}
      onSwitch={() => writeJson(restaurantKey, null)}
      onLogout={onLogout}
    />
  );
}
