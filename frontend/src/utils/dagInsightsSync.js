/**
 * Compare sync-status snapshots so the UI can detect when a queued sync has finished.
 */

export function snapshotLastSyncCompletedByDeployment(statuses) {
  const m = {};
  for (const s of statuses || []) {
    m[s.deploymentId] = s.lastSyncCompletedAt ?? null;
  }
  return m;
}

/**
 * True when every deployment that existed in the prior snapshot has a new lastSyncCompletedAt
 * (or disappeared from the list). Used after POST /dag-insights/sync for the same scope.
 */
export function allDeploymentsAdvanced(priorMap, currentStatuses) {
  const ids = Object.keys(priorMap);
  if (ids.length === 0) {
    return false;
  }
  const cur = Object.fromEntries(
    (currentStatuses || []).map((s) => [s.deploymentId, s.lastSyncCompletedAt ?? null])
  );
  return ids.every((id) => (cur[id] ?? null) !== (priorMap[id] ?? null));
}
