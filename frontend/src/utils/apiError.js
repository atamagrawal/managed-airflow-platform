/**
 * Thrown when the user closes/cancels the deployment picker modal (not an API failure).
 */
export class DeploymentPickCancelledError extends Error {
  constructor() {
    super('Deployment pick cancelled');
    this.name = 'DeploymentPickCancelledError';
  }
}

/**
 * Thrown when the user confirms the picker without selecting a deployment (validation only).
 */
export class DeploymentPickValidationError extends Error {
  constructor() {
    super('Deployment pick validation');
    this.name = 'DeploymentPickValidationError';
  }
}

export function isDeploymentPickNoise(error) {
  return (
    error?.name === 'DeploymentPickCancelledError' ||
    error?.name === 'DeploymentPickValidationError'
  );
}

/**
 * Spring {@link GlobalExceptionHandler.ErrorResponse} uses `message`.
 * Validation errors use `errors` map.
 */
export function getApiErrorMessage(error, fallback = 'Request failed') {
  if (error == null) return fallback;
  if (isDeploymentPickNoise(error)) return null;

  const data = error.response?.data;
  if (data && typeof data.error === 'string' && data.error.trim()) {
    return data.error;
  }
  if (data && typeof data.message === 'string' && data.message.trim()) {
    return data.message;
  }
  if (data?.errors && typeof data.errors === 'object') {
    const parts = Object.entries(data.errors).map(([k, v]) => `${k}: ${v}`);
    if (parts.length) return parts.join('; ');
  }

  if (error.message === 'Network Error') {
    return 'Network error — check that the control plane API is reachable.';
  }
  if (!error.response && error.message) {
    return error.message;
  }

  return fallback;
}
