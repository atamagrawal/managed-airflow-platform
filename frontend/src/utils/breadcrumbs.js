import React from 'react';
import { Link } from 'react-router-dom';
import { BRAND } from '../brand';

/**
 * Ant Design {@code Breadcrumb} items for the current path.
 *
 * @param {string} pathname
 * @param {{ projectName?: string }} [options] For `/projects/:projectId` and `/projects/:projectId/editor`, pass
 *   `projectName` so the crumb shows the human-readable name; the URL still uses the stable `projectId`.
 */
export function getBreadcrumbItems(pathname, options = {}) {
  const { projectName } = options;
  const p = pathname || '/';
  const home = { title: <Link to="/dashboard">Home</Link> };

  if (p === '/' || p === '/dashboard') {
    return [home, { title: 'Dashboard' }];
  }
  if (p === '/tenants') {
    return [home, { title: 'Tenants' }];
  }
  if (p === '/users') {
    return [home, { title: 'Users' }];
  }
  if (p === '/deployments') {
    return [home, { title: 'Deployments' }];
  }
  const dm = p.match(/^\/deployments\/([^/]+)$/);
  if (dm) {
    return [
      home,
      { title: <Link to="/deployments">Deployments</Link> },
      { title: dm[1] },
    ];
  }
  if (p === '/dags') {
    return [home, { title: 'DAGs' }];
  }
  if (p === '/projects') {
    return [home, { title: BRAND.navProjects }];
  }
  const pm = p.match(/^\/projects\/([^/]+)$/);
  if (pm) {
    const id = pm[1];
    const label = projectName?.trim() ? projectName.trim() : id;
    return [
      home,
      { title: <Link to="/projects">{BRAND.navProjects}</Link> },
      { title: label },
    ];
  }
  const pem = p.match(/^\/projects\/([^/]+)\/editor$/);
  if (pem) {
    const id = pem[1];
    const label = projectName?.trim() ? projectName.trim() : id;
    return [
      home,
      { title: <Link to="/projects">{BRAND.navProjects}</Link> },
      { title: <Link to={`/projects/${id}`}>{label}</Link> },
      { title: BRAND.ideName },
    ];
  }
  if (p === '/deployed-projects') {
    return [home, { title: 'Deployed projects' }];
  }
  if (p === '/environment' || p === '/environment/connections') {
    return [
      home,
      { title: 'Environment' },
      { title: 'Connections' },
    ];
  }
  if (p === '/environment/variables') {
    return [
      home,
      { title: 'Environment' },
      { title: 'Variables' },
    ];
  }
  return [home, { title: 'Page' }];
}
