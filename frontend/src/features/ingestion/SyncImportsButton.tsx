import { RefreshCw } from "lucide-react";

interface SyncImportsButtonProps {
  isPending: boolean;
  onClick: () => void;
}

export function SyncImportsButton({ isPending, onClick }: SyncImportsButtonProps) {
  return (
    <button
      type="button"
      data-testid="sync-local-folder-button"
      disabled={isPending}
      onClick={onClick}
      className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors hover:bg-black/5 disabled:cursor-not-allowed disabled:opacity-60"
      style={{
        borderColor: "var(--atlas-border)",
        color: "var(--atlas-ink)",
      }}
    >
      <RefreshCw className={`h-3.5 w-3.5 ${isPending ? "animate-spin" : ""}`} aria-hidden="true" />
      {isPending ? "Syncing..." : "Sync local folder"}
    </button>
  );
}
