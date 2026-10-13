"use client";

import { useEffect, useState } from "react";

type BatteryManager = EventTarget & { level: number; charging: boolean };

/** Battery level/charging where supported (Chrome on Android); null elsewhere. */
export function useBattery(): { level: number; charging: boolean } | null {
  const [battery, setBattery] = useState<{ level: number; charging: boolean } | null>(null);
  useEffect(() => {
    const nav = navigator as Navigator & { getBattery?: () => Promise<BatteryManager> };
    if (!nav.getBattery) return;
    let manager: BatteryManager | null = null;
    const read = () => manager && setBattery({ level: manager.level, charging: manager.charging });
    nav.getBattery().then((m) => {
      manager = m;
      read();
      m.addEventListener("levelchange", read);
      m.addEventListener("chargingchange", read);
    });
    return () => {
      manager?.removeEventListener("levelchange", read);
      manager?.removeEventListener("chargingchange", read);
    };
  }, []);
  return battery;
}
