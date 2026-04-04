import React from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Tabs } from 'antd';
import PageHeader from '../components/PageHeader';

const TAB_ITEMS = [
  { key: '/environment/connections', label: 'Connections' },
  { key: '/environment/variables', label: 'Variables' },
];

/**
 * Workspace-style environment surface (connections, variables) similar to Astro Environment Manager.
 */
const EnvironmentLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const activeKey = location.pathname.startsWith('/environment/variables')
    ? '/environment/variables'
    : '/environment/connections';

  return (
    <div>
      <PageHeader
        title="Environment manager"
        description="Manage Airflow connections and variables from one place, then push them to the deployments you choose—similar to Astronomer's Environment Manager (workspace-level resources linked to Deployments)."
      />
      <Tabs
        activeKey={activeKey}
        onChange={(key) => navigate(key)}
        items={TAB_ITEMS}
        style={{ marginBottom: 20 }}
      />
      <Outlet />
    </div>
  );
};

export default EnvironmentLayout;
