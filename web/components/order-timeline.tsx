import type { Order } from "@/lib/api/types";
import { formatTime } from "@/lib/format";
import { timeline } from "@/lib/order-status";

export function OrderTimeline({ order }: { order: Order }) {
  const steps = timeline(order);
  return (
    <ol className="space-y-0">
      {steps.map((step, i) => {
        const dot = {
          done: "bg-green-600 border-green-600",
          current: "bg-brand-600 border-brand-600 ring-4 ring-brand-100 animate-pulse",
          upcoming: "bg-white border-stone-300",
          stopped: "bg-red-600 border-red-600",
        }[step.state];
        return (
          <li key={step.status} className="relative flex gap-3 pb-5 last:pb-0">
            {i < steps.length - 1 && <span aria-hidden className="absolute left-[7px] top-4 h-full w-0.5 bg-stone-200" />}
            <span aria-hidden className={`relative mt-1 size-4 shrink-0 rounded-full border-2 ${dot}`} />
            <div className="flex flex-1 justify-between gap-2">
              <span className={`text-sm ${step.state === "upcoming" ? "text-stone-400" : "font-medium"} ${step.state === "stopped" ? "text-red-700" : ""}`}>
                {step.label}
                {step.state === "current" && <span className="sr-only"> (current)</span>}
              </span>
              {step.at && <time dateTime={step.at} className="text-xs text-stone-500">{formatTime(step.at)}</time>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
