import axios from 'axios';
import { getStoredToken, setStoredToken } from '../authStorage';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';

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
