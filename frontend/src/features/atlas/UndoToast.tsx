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
      className="atlas-panel pointer-events-auto fixed right-4 bottom-4 z-50 flex items-center gap-3 rounded-full px-4 py-2 text-[13px] text-atlas-ink"
    >
      <span className="font-medium">{message}</span>
      {canUndo && (
        <button
          type="button"
          data-testid="atlas-undo-button"
          disabled={isRestoring}
          onClick={onUndo}
          className="atlas-pill-solid gap-1 px-2.5 font-semibold"
        >
          <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
          Undo
        </button>
      )}
      <button
        type="button"
        aria-label="Dismiss notification"
        onClick={onDismiss}
        className="atlas-icon-button p-0.5"
      >
        <X className="h-3.5 w-3.5" aria-hidden="true" />
      </button>
    </div>
  );
}
