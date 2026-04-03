/** Options when deploying: choose any Airflow deployment (deploy creates the project↔deployment link if needed). */
export function getDeploymentSelectOptionsForDeploy(allDeployments) {
  return (allDeployments || []).map((d) => ({
    label: d.name ? `${d.name} (${d.deploymentId})` : d.deploymentId,
    value: d.deploymentId,
  }));
}

/** Options when triggering: only deployments the project is linked to (deploy there first). */
export function getDeploymentSelectOptionsForTrigger(project, allDeployments) {
  const linked = project?.linkedDeploymentIds;
  if (!Array.isArray(linked) || linked.length === 0) {
    return [];
  }
  return allDeployments
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
    return { ok: true, deploymentId: linked[0], needsPicker: false };
  }
  return {
    ok: true,
    needsPicker: true,
    options: getDeploymentSelectOptionsForTrigger(project, allDeployments),
  };
}

/**
 * Deploy: show a picker only when:
 * - the project is linked to multiple deployments (choose which to sync to), or
 * - the project has no links yet and there is more than one Airflow deployment (first-time target).
 * If the project is linked to exactly one deployment, deploy there without a modal.
 */
export function resolveDeploymentForDeploy(project, allDeployments) {
  const allOpts = getDeploymentSelectOptionsForDeploy(allDeployments);
  if (allOpts.length === 0) {
    return { ok: false, reason: 'none' };
  }
  const linked = project?.linkedDeploymentIds || [];

  if (linked.length === 1) {
    return { ok: true, deploymentId: linked[0], needsPicker: false };
  }

  if (linked.length > 1) {
    const options = allOpts.filter((o) => linked.includes(o.value));
    return { ok: true, needsPicker: true, options };
  }

  // No links yet — first deploy
  if (allOpts.length === 1) {
    return { ok: true, deploymentId: allOpts[0].value, needsPicker: false };
  }
  return { ok: true, needsPicker: true, options: allOpts };
}
