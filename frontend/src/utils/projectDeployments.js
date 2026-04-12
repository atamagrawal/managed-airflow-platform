function deploymentsForProjectTenant(project, allDeployments) {
  const tid = project?.tenantId;
  if (!tid) return allDeployments || [];
  return (allDeployments || []).filter((d) => d.tenantId === tid);
}

function deployOptionSortKey(status) {
  if (status === 'RUNNING') return 0;
  if (status === 'DEPLOYING' || status === 'PENDING') return 1;
  return 2;
}

/**
 * Options when deploying: any tenant deployment counts (including STOPPED — you can sync files before starting).
 * Labels include status so a running test env and a stopped second deployment are easy to tell apart.
 */
export function getDeploymentSelectOptionsForDeploy(allDeployments) {
  const list = [...(allDeployments || [])];
  list.sort(
    (a, b) =>
      deployOptionSortKey(a.status) - deployOptionSortKey(b.status) ||
      String(a.name || '').localeCompare(String(b.name || '')) ||
      String(a.deploymentId || '').localeCompare(String(b.deploymentId || ''))
  );
  return list.map((d) => {
    const rawTag = d.tag != null && String(d.tag).trim() !== '' ? String(d.tag).trim() : null;
    const base = d.name ? `${d.name} (${d.deploymentId})` : d.deploymentId;
    const titled = rawTag ? `${rawTag} · ${base}` : base;
    const st = d.status != null && String(d.status).trim() !== '' ? String(d.status).trim() : null;
    const label = st ? `${titled} — ${st}` : titled;
    return { label, value: d.deploymentId };
  });
}

/** Options when triggering: only deployments the project is linked to (deploy there first). */
export function getDeploymentSelectOptionsForTrigger(project, allDeployments) {
  const linked = project?.linkedDeploymentIds;
  if (!Array.isArray(linked) || linked.length === 0) {
    return [];
  }
  return deploymentsForProjectTenant(project, allDeployments)
    .filter((d) => linked.includes(d.deploymentId))
    .map((d) => ({
      label: d.name ? `${d.name} (${d.deploymentId})` : d.deploymentId,
      value: d.deploymentId,
    }));
}

/**
 * Trigger DAGs: show a deployment picker only when the project is linked to more than one deployment.
 * Single linked deployment → use it with no modal.
 */
export function resolveDeploymentForTrigger(project, allDeployments) {
  const linked = project?.linkedDeploymentIds || [];
  if (linked.length === 0) {
    return { ok: false, reason: 'none' };
  }
  if (linked.length === 1) {
    const opts = getDeploymentSelectOptionsForTrigger(project, allDeployments);
    if (opts.length !== 1) {
      return { ok: false, reason: 'none' };
    }
    return { ok: true, deploymentId: linked[0], needsPicker: false };
  }
  const options = getDeploymentSelectOptionsForTrigger(project, allDeployments);
  if (options.length === 0) {
    return { ok: false, reason: 'none' };
  }
  return {
    ok: true,
    needsPicker: true,
    options,
  };
}

/**
 * Deploy: show a picker whenever this tenant has more than one Airflow deployment row (including STOPPED),
 * so e.g. test env (RUNNING) + second env (STOPPED) still opens "Choose deployment…" instead of syncing only
 * to the linked test deployment. Single deployment row for the tenant → deploy there with no modal.
 * {@link getDeploymentSelectOptionsForDeploy} — deploy may create the project↔deployment link if needed.
 */
export function resolveDeploymentForDeploy(project, allDeployments) {
  const scoped = deploymentsForProjectTenant(project, allDeployments);
  const allOpts = getDeploymentSelectOptionsForDeploy(scoped);
  if (allOpts.length === 0) {
    return { ok: false, reason: 'none' };
  }
  if (allOpts.length === 1) {
    return { ok: true, deploymentId: allOpts[0].value, needsPicker: false };
  }
  return { ok: true, needsPicker: true, options: allOpts };
}
