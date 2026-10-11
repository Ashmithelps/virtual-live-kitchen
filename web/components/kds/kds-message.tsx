export function KdsMessage({ title, body, action }: { title: string; body?: string; action?: { label: string; onClick: () => void } }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 p-6 text-center">
      <p className="text-2xl font-bold">{title}</p>
      {body && <p className="max-w-lg text-lg text-stone-400">{body}</p>}
      {action && (
        <button onClick={action.onClick} className="mt-2 min-h-14 rounded-2xl bg-stone-800 px-8 text-lg font-semibold active:bg-stone-700">
          {action.label}
        </button>
      )}
    </div>
  );
}
