import { X } from "lucide-react";
import { useState } from "react";
import type { AccountListFacetView, AtlasPlaceView, PlaceListEntryRequest } from "../../api/client";
import {
  CREATE_NEW_LIST_SENTINEL,
  ListTargetSelect,
  resolveSelectedListTarget,
  useEditableLists,
} from "./ListAssignActionBar";

interface PlaceListEditorProps {
  place: AtlasPlaceView;
  accountLists: readonly AccountListFacetView[];
  isMutating: boolean;
  onUpdateLists: (place: AtlasPlaceView, lists: PlaceListEntryRequest[]) => void;
}

function toEntry(membership: AtlasPlaceView["memberships"][number]): PlaceListEntryRequest {
  return {
    listName: membership.listName,
    listKind: membership.listKind,
    accountLabel: membership.accountLabel,
  };
}

export function PlaceListEditor({
  place,
  accountLists,
  isMutating,
  onUpdateLists,
}: PlaceListEditorProps) {
  const { editableLists, showAccountPrefix } = useEditableLists(accountLists);
  const [selectedOption, setSelectedOption] = useState(CREATE_NEW_LIST_SENTINEL);
  const [newListName, setNewListName] = useState("");

  const creatingNew = selectedOption === CREATE_NEW_LIST_SENTINEL;
  const target = resolveSelectedListTarget(selectedOption, newListName);
  const canApply = !isMutating && target.listName.length > 0;
  const currentEditable = place.memberships.filter((m) => m.listKind !== "REVIEW");

  const applyTarget = (replaceAll: boolean) => {
    if (!canApply) {
      return;
    }
    const nextLists = replaceAll ? [target] : [...currentEditable.map(toEntry), target];
    onUpdateLists(place, nextLists);
    if (creatingNew) {
      setNewListName("");
    }
  };

  const handleRemove = (accountLabel: string, listName: string) => {
    const remaining = currentEditable
      .filter((m) => !(m.accountLabel === accountLabel && m.listName === listName))
      .map(toEntry);
    onUpdateLists(place, remaining);
  };

  return (
    <div className="mt-3 border-t border-atlas-border pt-2.5">
      {place.memberships.length > 0 && (
        <ul className="divide-y divide-atlas-border">
          {place.memberships.map((m) => (
            <li
              key={`${m.accountLabel}:${m.listName}`}
              className="flex items-start justify-between gap-2 py-1.5 text-sm"
            >
              <div className="min-w-0">
                <div className="truncate font-medium">
                  {showAccountPrefix ? `${m.accountLabel} · ` : ""}
                  {m.listName}
                </div>
                {m.note && (
                  <div className="mt-0.5 text-[13px] italic text-atlas-muted">{m.note}</div>
                )}
              </div>
              {m.listKind !== "REVIEW" && (
                <button
                  type="button"
                  data-testid="remove-place-list-button"
                  aria-label={`Remove from ${m.listName}`}
                  disabled={isMutating}
                  onClick={() => handleRemove(m.accountLabel, m.listName)}
                  className="atlas-icon-button h-6 w-6 shrink-0"
                >
                  <X className="h-3 w-3" aria-hidden="true" />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-2 space-y-1.5 text-[12px]">
        <div className="flex items-center gap-1.5">
          <ListTargetSelect
            editableLists={editableLists}
            showAccountPrefix={showAccountPrefix}
            value={selectedOption}
            disabled={isMutating}
            testId="place-list-select"
            ariaLabel="Choose list for place"
            createLabel="+ New list..."
            className="h-7 min-w-0 flex-1 cursor-pointer rounded-full border border-atlas-border bg-white/80 px-2.5 text-[12px] text-atlas-ink outline-none focus:border-atlas-accent"
            onChange={setSelectedOption}
          />
          <button
            type="button"
            data-testid="place-list-add-button"
            disabled={!canApply}
            onClick={() => applyTarget(false)}
            className="atlas-pill h-7 bg-white/80 px-2.5 text-[12px]"
          >
            Add
          </button>
          <button
            type="button"
            data-testid="place-list-move-button"
            disabled={!canApply}
            onClick={() => applyTarget(true)}
            className="atlas-pill h-7 bg-white/80 px-2.5 text-[12px]"
          >
            Move
          </button>
        </div>

        {creatingNew && (
          <input
            type="text"
            data-testid="place-list-new-name"
            aria-label="New list name for place"
            placeholder="New list name..."
            disabled={isMutating}
            value={newListName}
            onChange={(event) => setNewListName(event.target.value)}
            className="h-7 w-full rounded-full border border-atlas-border bg-white/80 px-2.5 text-[12px] text-atlas-ink outline-none focus:border-atlas-accent"
          />
        )}
      </div>
    </div>
  );
}
