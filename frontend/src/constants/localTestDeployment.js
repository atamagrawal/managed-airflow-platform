/**
 * Lazy-created test deployment names for the IDE (see frontend/src/brand.js PRODUCT_NAME). Canonical value matches control-plane
 * `local.test-deployment.name`. Older DB rows may still be "Shared test" or "Local test".
 */
export const FLOW_DECK_TEST_DEPLOYMENT_NAMES = [
  'Test environment',
  'Shared test',
  'Local test',
];

export function isFlowDeckTestDeploymentName(name) {
  return name != null && FLOW_DECK_TEST_DEPLOYMENT_NAMES.includes(name);
}
