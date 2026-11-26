import { Search, X } from "lucide-react";
import type { AccountListFacetView } from "../../api/client";
import { CATEGORY_OPTIONS, DERIVED_ROLE_OPTIONS, VISIT_STATUS_OPTIONS } from "./atlasLabels";
import {
  type AtlasViewState,
  type DerivedRoleFilter,
  hasActiveFilters,
  type PlaceCategoryFilter,
  type VisitStatusFilter,
} from "./viewState";

interface AtlasFilterBarProps {
  viewState: AtlasViewState;
  accountLists: AccountListFacetView[];
  totalCount: number | undefined;
  onChange: (next: AtlasViewState) => void;
  onClear: () => void;
}

interface FilterSelectProps {
  testId: string;
  ariaLabel: string;
  emptyLabel: string;
  value: string;
  options: readonly { value: string; label: string }[];
  onChange: (value: string) => void;
}

const FACET_SEPARATOR = "\u0000";

function FilterSelect({
  testId,
  ariaLabel,
  emptyLabel,
  value,
  options,
  onChange,
}: FilterSelectProps) {
  return (
    <select
      data-testid={testId}
      aria-label={ariaLabel}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="h-8 cursor-pointer rounded-full border bg-white/75 px-3 text-[13px] outline-none focus:border-[var(--atlas-accent)]"
      style={{ borderColor: "var(--atlas-border)", color: "var(--atlas-ink)" }}
    >
      <option value="">{emptyLabel}</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  );
}

export function AtlasFilterBar({
  viewState,
  accountLists,
  totalCount,
  onChange,
  onClear,
}: AtlasFilterBarProps) {
  const activeAccountListValue =
    viewState.account.length > 0 && viewState.list.length > 0
      ? `${viewState.account}${FACET_SEPARATOR}${viewState.list}`
      : "";
  const filtersActive = hasActiveFilters(viewState);

  const handleAccountListChange = (raw: string) => {
    if (raw.length === 0) {
      onChange({ ...viewState, account: "", list: "" });
      return;
    }
    const [account = "", list = ""] = raw.split(FACET_SEPARATOR);
    onChange({ ...viewState, account, list });
  };

  return (
    <div
      data-testid="atlas-filter-bar"
      className="pointer-events-auto flex flex-wrap items-center justify-center gap-2 rounded-2xl border px-3 py-1.5 text-[13px] shadow-sm backdrop-blur-md"
      style={{
        backgroundColor: "var(--atlas-surface)",
        borderColor: "var(--atlas-border)",
      }}
    >
      <label className="relative flex items-center">
        <Search
          className="pointer-events-none absolute left-2.5 h-3.5 w-3.5"
          style={{ color: "var(--atlas-muted)" }}
          aria-hidden="true"
        />
        <input
          type="search"
          data-testid="filter-search"
          aria-label="Search places"
          placeholder="Search places, cities, notes..."
          value={viewState.q}
          onChange={(e) => onChange({ ...viewState, q: e.target.value })}
          className="h-8 w-44 rounded-full border bg-white/75 pl-8 pr-3 text-[13px] outline-none focus:border-[var(--atlas-accent)] sm:w-52"
          style={{ borderColor: "var(--atlas-border)", color: "var(--atlas-ink)" }}
        />
      </label>

      <FilterSelect
        testId="filter-status"
        ariaLabel="Filter by visit status"
        emptyLabel="All statuses"
        value={viewState.visitStatus}
        options={VISIT_STATUS_OPTIONS}
        onChange={(value) => onChange({ ...viewState, visitStatus: value as VisitStatusFilter })}
      />

      <FilterSelect
        testId="filter-role"
        ariaLabel="Filter by derived role"
        emptyLabel="All roles"
        value={viewState.derivedRole}
        options={DERIVED_ROLE_OPTIONS}
        onChange={(value) => onChange({ ...viewState, derivedRole: value as DerivedRoleFilter })}
      />

      <FilterSelect
        testId="filter-category"
        ariaLabel="Filter by category"
        emptyLabel="All categories"
        value={viewState.category}
        options={CATEGORY_OPTIONS}
        onChange={(value) => onChange({ ...viewState, category: value as PlaceCategoryFilter })}
      />

      <select
        data-testid="filter-account-list"
        aria-label="Filter by account or list"
        value={activeAccountListValue}
        onChange={(e) => handleAccountListChange(e.target.value)}
        className="h-8 max-w-52 cursor-pointer rounded-full border bg-white/75 px-3 text-[13px] outline-none focus:border-[var(--atlas-accent)]"
        style={{ borderColor: "var(--atlas-border)", color: "var(--atlas-ink)" }}
      >
        <option value="">All lists</option>
        {accountLists.map((facet) => (
          <option
            key={`${facet.accountLabel}:${facet.listName}`}
            value={`${facet.accountLabel}${FACET_SEPARATOR}${facet.listName}`}
          >
            {facet.accountLabel} · {facet.listName} ({facet.placeCount})
          </option>
        ))}
      </select>

      {typeof totalCount === "number" && (
        <span
          data-testid="places-count-badge"
          className="px-1.5 font-medium tabular-nums"
          style={{ color: "var(--atlas-muted)" }}
        >
          {totalCount} pins
        </span>
      )}

      {filtersActive && (
        <button
          type="button"
          data-testid="clear-filters-button"
          onClick={onClear}
          className="inline-flex h-8 cursor-pointer items-center gap-1 rounded-full border px-3 font-medium transition-colors hover:bg-black/5"
          style={{ borderColor: "var(--atlas-border)", color: "var(--atlas-ink)" }}
        >
          <X className="h-3.5 w-3.5" aria-hidden="true" />
          Clear
        </button>
      )}
    </div>
  );
}
