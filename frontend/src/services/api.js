import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Tenant APIs
export const tenantAPI = {
  getAll: () => api.get('/tenants'),
  getById: (tenantId) => api.get(`/tenants/${tenantId}`),
  create: (data) => api.post('/tenants', data),
  delete: (tenantId) => api.delete(`/tenants/${tenantId}`),
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
