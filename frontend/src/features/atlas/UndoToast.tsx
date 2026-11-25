import { RotateCcw, X } from "lucide-react";

interface UndoToastProps {
  message: string;
  canUndo: boolean;
  isRestoring: boolean;
  onUndo: () => void;
  onDismiss: () => void;
}

export function UndoToast({ message, canUndo, isRestoring, onUndo, onDismiss }: UndoToastProps) {
  return (
    <div
      role="status"
      data-testid="atlas-undo-toast"
      className="pointer-events-auto fixed right-4 bottom-4 z-50 flex items-center gap-3 rounded-full border px-4 py-2 text-xs shadow-lg backdrop-blur-md"
      style={{
        backgroundColor: "rgba(250, 249, 246, 0.96)",
        borderColor: "var(--atlas-border)",
        color: "var(--atlas-ink)",
      }}
    >
      <span className="font-medium">{message}</span>
      {canUndo && (
        <button
          type="button"
          data-testid="atlas-undo-button"
          disabled={isRestoring}
          onClick={onUndo}
          className="inline-flex cursor-pointer items-center gap-1 rounded-full px-2.5 py-1 font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          style={{ backgroundColor: "var(--atlas-accent)" }}
        >
          <RotateCcw className="h-3 w-3" aria-hidden="true" />
          Undo
        </button>
      )}
      <button
        type="button"
        aria-label="Dismiss notification"
        onClick={onDismiss}
        className="cursor-pointer rounded-full p-0.5 hover:bg-black/5"
        style={{ color: "var(--atlas-muted)" }}
      >
        <X className="h-3.5 w-3.5" aria-hidden="true" />
      </button>
    </div>
  );
}
