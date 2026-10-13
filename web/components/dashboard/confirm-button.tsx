"use client";

import { useState } from "react";
import { Button } from "@/components/ui";

/** Two-step delete: the first click arms it, the second confirms (no browser dialogs). */
export function ConfirmButton({ label, confirmLabel = "Really delete?", onConfirm }: { label: string; confirmLabel?: string; onConfirm: () => void }) {
  const [armed, setArmed] = useState(false);
  return armed ? (
    <span className="flex gap-1">
      <Button variant="danger" onClick={() => { setArmed(false); onConfirm(); }}>
        {confirmLabel}
      </Button>
      <Button variant="ghost" onClick={() => setArmed(false)}>
        Cancel
      </Button>
    </span>
  ) : (
    <Button variant="danger" onClick={() => setArmed(true)}>
      {label}
    </Button>
  );
}

