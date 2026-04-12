import React from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Tabs } from 'antd';
import PageHeader from '../components/PageHeader';

const TAB_ITEMS = [
  { key: '/environment/connections', label: 'Connections' },
  { key: '/environment/variables', label: 'Variables' },
];

/** Environment area: connections and variables pushed to Airflow deployments. */
const EnvironmentLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const activeKey = location.pathname.startsWith('/environment/variables')
    ? '/environment/variables'
    : '/environment/connections';

  return (
    <div>
      <PageHeader
        title="Environment"
        description="Configure Airflow connections and variables in one place, then push them to the deployments you choose."
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
