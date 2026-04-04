import { resolveDeploymentForDeploy } from './projectDeployments';
import { pickDeploymentId } from './pickDeploymentModal';

/**
 * Picks the deployment used for local Docker test cluster actions (same rules as project deploy).
 */
export async function pickDeploymentForLocalStack(project, deployments, modalTitle) {
  const resolved = resolveDeploymentForDeploy(project, deployments);
  if (!resolved.ok) {
    return { ok: false };
  }
  let deploymentId = resolved.deploymentId;
  if (resolved.needsPicker) {
    deploymentId = await pickDeploymentId(modalTitle, resolved.options);
  }
  return { ok: true, deploymentId };
}
