import { FolderPlus } from "lucide-react";
import { useState } from "react";
import type {
  AccountListFacetView,
  BulkAssignListRequest,
  ListAssignMode,
  ListKind,
} from "../../api/client";

interface ListAssignActionBarProps {
  selectedPlaceIds: string[];
  accountLists: readonly AccountListFacetView[];
  defaultSourceList?: string;
  isSubmitting: boolean;
  onAssign: (request: BulkAssignListRequest) => void;
}

export const CREATE_NEW_LIST_SENTINEL = "__CREATE_NEW__";
const FACET_SEPARATOR = "\u0000";

function encodeListFacet(facet: AccountListFacetView): string {
  return `${facet.accountLabel}${FACET_SEPARATOR}${facet.listName}${FACET_SEPARATOR}${facet.listKind}`;
}

export function resolveSelectedListTarget(
  selectedOption: string,
  newListName: string,
): { listName: string; listKind: ListKind; accountLabel?: string } {
  if (selectedOption === CREATE_NEW_LIST_SENTINEL) {
    return { listName: newListName.trim(), listKind: "CUSTOM" };
  }
  const [accountLabel, listName = "", rawKind] = selectedOption.split(FACET_SEPARATOR);
  return {
    listName,
    listKind: (rawKind as ListKind | undefined) ?? "CUSTOM",
    accountLabel,
  };
}

export function useEditableLists(accountLists: readonly AccountListFacetView[]) {
  const editableLists = accountLists.filter((facet) => facet.listKind !== "REVIEW");
  const showAccountPrefix = new Set(editableLists.map((facet) => facet.accountLabel)).size >= 2;
  return { editableLists, showAccountPrefix };
}

interface ListTargetSelectProps {
  editableLists: readonly AccountListFacetView[];
  showAccountPrefix: boolean;
  showCounts?: boolean;
  value: string;
  disabled: boolean;
  testId: string;
  ariaLabel: string;
  createLabel: string;
  className: string;
  onChange: (value: string) => void;
}

export function ListTargetSelect({
  editableLists,
  showAccountPrefix,
  showCounts = false,
  value,
  disabled,
  testId,
  ariaLabel,
  createLabel,
  className,
  onChange,
}: ListTargetSelectProps) {
  return (
    <select
      data-testid={testId}
      aria-label={ariaLabel}
      disabled={disabled}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      className={className}
    >
      <option value={CREATE_NEW_LIST_SENTINEL}>{createLabel}</option>
      {editableLists.map((facet) => (
        <option key={`${facet.accountLabel}:${facet.listName}`} value={encodeListFacet(facet)}>
          {showAccountPrefix ? `${facet.accountLabel} · ` : ""}
          {facet.listName}
          {showCounts ? ` (${facet.placeCount})` : ""}
        </option>
      ))}
    </select>
  );
}

export function ListAssignActionBar({
  selectedPlaceIds,
  accountLists,
  defaultSourceList,
  isSubmitting,
  onAssign,
}: ListAssignActionBarProps) {
  const { editableLists, showAccountPrefix } = useEditableLists(accountLists);
  const [selectedOption, setSelectedOption] = useState(CREATE_NEW_LIST_SENTINEL);
  const [newListName, setNewListName] = useState("");
  const [mode, setMode] = useState<ListAssignMode>("ADD");

  const creatingNew = selectedOption === CREATE_NEW_LIST_SENTINEL;
  const target = resolveSelectedListTarget(selectedOption, newListName);
  const canSubmit = !isSubmitting && selectedPlaceIds.length > 0 && target.listName.length > 0;

  const handleApply = () => {
    if (!canSubmit) {
      return;
    }
    onAssign({
      placeIds: selectedPlaceIds,
      targetListName: target.listName,
      listKind: target.listKind,
      mode,
      sourceListName:
        mode === "MOVE" && defaultSourceList && defaultSourceList.length > 0
          ? defaultSourceList
          : undefined,
      accountLabel: target.accountLabel,
    });
  };

  return (
    <div data-testid="bulk-list-action-bar" className="space-y-2 text-[12px]">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1.5 font-semibold text-atlas-ink">
          <FolderPlus className="h-3.5 w-3.5 text-atlas-accent" aria-hidden="true" />
          Organize in list
        </span>
        <fieldset
          aria-label="List assignment mode"
          className="inline-flex rounded-full border border-atlas-border bg-white/80 p-0.5"
        >
          {(["ADD", "MOVE"] as const).map((option) => (
            <button
              key={option}
              type="button"
              data-testid={option === "ADD" ? "bulk-list-mode-add" : "bulk-list-mode-move"}
              aria-pressed={mode === option}
              onClick={() => setMode(option)}
              className={`rounded-full px-2.5 py-0.5 font-medium transition-colors ${
                mode === option
                  ? "bg-atlas-accent text-white"
                  : "text-atlas-muted hover:text-atlas-ink"
              }`}
            >
              {option === "ADD" ? "Add" : "Move"}
            </button>
          ))}
        </fieldset>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <ListTargetSelect
          editableLists={editableLists}
          showAccountPrefix={showAccountPrefix}
          showCounts={true}
          value={selectedOption}
          disabled={isSubmitting}
          testId="bulk-list-select"
          ariaLabel="Target list"
          createLabel="+ Create new list..."
          className="h-8 min-w-36 flex-1 cursor-pointer rounded-full border border-atlas-border bg-white/90 px-3 text-[12px] text-atlas-ink outline-none focus:border-atlas-accent"
          onChange={setSelectedOption}
        />

        {creatingNew && (
          <input
            type="text"
            data-testid="bulk-list-new-name"
            aria-label="New list name"
            placeholder="New list name..."
            disabled={isSubmitting}
            value={newListName}
            onChange={(event) => setNewListName(event.target.value)}
            className="h-8 min-w-36 flex-1 rounded-full border border-atlas-border bg-white/90 px-3 text-[12px] text-atlas-ink outline-none focus:border-atlas-accent"
          />
        )}

        <button
          type="button"
          data-testid="bulk-list-apply-button"
          disabled={!canSubmit}
          onClick={handleApply}
          className="atlas-pill bg-white/90 px-3 py-1 font-medium text-atlas-ink"
        >
          {mode === "MOVE" ? "Move to list" : "Add to list"}
        </button>
      </div>
    </div>
  );
}
