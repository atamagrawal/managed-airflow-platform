import axios from 'axios';
import { getStoredToken, setStoredToken } from '../authStorage';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';

/** Origin of the control plane (no /api/v1), for public handoff URLs etc. */
export function getControlPlaneOrigin() {
  const trimmed = API_BASE_URL.replace(/\/api\/v1\/?$/i, '').replace(/\/$/, '');
  if (/^https?:\/\//i.test(trimmed)) {
    return trimmed;
  }
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin.replace(/\/$/, '');
  }
  return 'http://localhost:8080';
}

/**
 * Call from a click handler only: opens one empty tab synchronously (keeps user activation), mints a handoff ticket,
 * then navigates that tab to the handoff URL.
 *
 * Do not pass `noopener` here: with noopener many browsers return a window handle that the opener cannot navigate,
 * so the tab stays on about:blank while the code falls back to location.assign in the current tab.
 * The handoff page clears window.opener after load.
 */
export async function openAirflowHandoffInNewTab(getHandoffId) {
  if (typeof window === 'undefined') {
    return;
  }
  const tab = window.open('about:blank', '_blank');
  try {
    const handoffId = await getHandoffId();
    const base = getControlPlaneOrigin();
    const url = `${base}/api/v1/public/airflow-handoff/${encodeURIComponent(handoffId)}`;
    if (tab) {
      try {
        tab.location.replace(url);
      } catch {
        tab.location.href = url;
      }
    } else {
      window.location.assign(url);
    }
  } catch (e) {
    try {
      if (tab && !tab.closed) {
        tab.close();
      }
    } catch (_) {
      /* ignore */
    }
    throw e;
  }
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config?.skipAuthRedirect) {
      setStoredToken(null);
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        window.location.assign('/login');
      }
    }
    return Promise.reject(error);
  }
);

// Authentication
export const authAPI = {
  login: (username, password) =>
    api.post('/auth/login', { username, password }, { skipAuthRedirect: true }),
  me: () => api.get('/auth/me'),
};

// Admin-only: control-plane users (database + configuration); no passwords on list
export const adminUserAPI = {
  list: () => api.get('/admin/users'),
  create: (data) => api.post('/admin/users', data),
  delete: (id) => api.delete(`/admin/users/${id}`),
};

// Tenant APIs
export const tenantAPI = {
  getAll: () => api.get('/tenants'),
  getById: (tenantId) => api.get(`/tenants/${tenantId}`),
  create: (data) => api.post('/tenants', data),
  delete: (tenantId) => api.delete(`/tenants/${tenantId}`),
};

// Environment (Airflow metadata on deployments: connections, etc.)
export const environmentAPI = {
  syncConnection: (data) => api.post('/environment/connections/sync', data),
};

// Deployment APIs
export const deploymentAPI = {
  getAll: () => api.get('/deployments'),
  getById: (deploymentId) => api.get(`/deployments/${deploymentId}`),
  getByTenant: (tenantId) => api.get(`/deployments/tenant/${tenantId}`),
  getConfig: () => api.get('/deployments/config'),
  create: (data) => api.post('/deployments', data),
  update: (deploymentId, data) => api.put(`/deployments/${deploymentId}`, data),
  delete: (deploymentId) => api.delete(`/deployments/${deploymentId}`),
  /** Single-use browser handoff for Airflow 3 FAB UI login (uses signed-in session; no body). */
  airflowUiHandoff: (deploymentId) => api.post(`/deployments/${deploymentId}/airflow-ui-handoff`),
};

// Deployed DAGs (project DAG files with a successful deploy to a deployment)
export const deployedDagsAPI = {
  getAll: (deploymentId) =>
    api.get('/deployed-dags', {
      params: deploymentId && deploymentId !== 'all' ? { deploymentId } : {},
    }),
};

// Project APIs
export const projectAPI = {
  getAll: () => api.get('/projects'),
  getById: (projectId) => api.get(`/projects/${projectId}`),
  getByDeployment: (deploymentId) => api.get(`/projects/deployment/${deploymentId}`),
  create: (data) => api.post('/projects', data),
  update: (projectId, data) => api.put(`/projects/${projectId}`, data),
  delete: (projectId) => api.delete(`/projects/${projectId}`),
  linkDeployment: (projectId, deploymentId) =>
    api.post(`/projects/${projectId}/deployments/${deploymentId}`),
  unlinkDeployment: (projectId, deploymentId) =>
    api.delete(`/projects/${projectId}/deployments/${deploymentId}`),
  deploy: (projectId, deploymentId) =>
    api.post(`/projects/${projectId}/deploy`, null, { params: { deploymentId } }),
  trigger: (projectId, deploymentId, fileName) =>
    api.post(`/projects/${projectId}/trigger`, null, {
      params: fileName ? { deploymentId, fileName } : { deploymentId },
    }),
  getFiles: (projectId) => api.get(`/projects/${projectId}/files`),
  addFile: (projectId, data) => api.post(`/projects/${projectId}/files`, data),
  updateFile: (projectId, fileId, data) => api.put(`/projects/${projectId}/files/${fileId}`, data),
};

export default api;
