import type { AtlasPlaceView } from "../../api/client";
import { PlaceSelectionDialog } from "./PlaceSelectionDialog";

interface BulkVisitDialogProps {
  title: string;
  places: AtlasPlaceView[];
  isSubmitting: boolean;
  onClose: () => void;
  onConfirm: (selectedPlaceIds: string[]) => void;
}

export function BulkVisitDialog({
  title,
  places,
  isSubmitting,
  onClose,
  onConfirm,
}: BulkVisitDialogProps) {
  return (
    <PlaceSelectionDialog
      title={title}
      subtitle="Uncheck any places you have not visited yet."
      places={places}
      isSubmitting={isSubmitting}
      onClose={onClose}
      onMarkVisited={onConfirm}
    />
  );
}
