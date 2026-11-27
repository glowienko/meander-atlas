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
      className="atlas-pill"
    >
      <RefreshCw
        className={`h-3.5 w-3.5 ${isPending ? "animate-spin motion-reduce:animate-none" : ""}`}
        aria-hidden="true"
      />
      {isPending ? "Syncing..." : "Sync local folder"}
    </button>
  );
}
