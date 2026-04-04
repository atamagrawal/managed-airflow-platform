import React from 'react';
import { Link } from 'react-router-dom';

/**
 * Ant Design {@code Breadcrumb} items for the current path.
 */
export function getBreadcrumbItems(pathname) {
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
    return [home, { title: 'Projects' }];
  }
  const pm = p.match(/^\/projects\/([^/]+)$/);
  if (pm) {
    return [
      home,
      { title: <Link to="/projects">Projects</Link> },
      { title: pm[1] },
    ];
  }
  const pem = p.match(/^\/projects\/([^/]+)\/editor$/);
  if (pem) {
    const id = pem[1];
    return [
      home,
      { title: <Link to="/projects">Projects</Link> },
      { title: <Link to={`/projects/${id}`}>{id}</Link> },
      { title: 'Editor' },
    ];
  }
  if (p === '/deployed-projects') {
    return [home, { title: 'Deployed projects' }];
  }
  return [home, { title: 'Page' }];
}
