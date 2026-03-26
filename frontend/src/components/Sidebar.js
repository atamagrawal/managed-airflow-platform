import React from 'react';
import { Layout, Menu } from 'antd';
import { DashboardOutlined, TeamOutlined, CloudServerOutlined, CodeOutlined, EditOutlined } from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';

const { Sider } = Layout;

const Sidebar = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: 'Dashboard',
    },
    {
      key: '/tenants',
      icon: <TeamOutlined />,
      label: 'Tenants',
    },
    {
      key: '/deployments',
      icon: <CloudServerOutlined />,
      label: 'Deployments',
    },
    {
      key: '/dags',
      icon: <CodeOutlined />,
      label: 'DAGs',
    },
    {
      key: '/code-editor',
      icon: <EditOutlined />,
      label: 'Code Editor',
    },
  ];

  const handleMenuClick = ({ key }) => {
    navigate(key);
  };

  // Determine the selected menu key based on current path
  const getSelectedKey = () => {
    const path = location.pathname;
    if (path.startsWith('/code-editor')) return '/code-editor';
    if (path.startsWith('/dags')) return '/dags';
    if (path.startsWith('/deployments')) return '/deployments';
    if (path.startsWith('/tenants')) return '/tenants';
    return path;
  };

  return (
    <Sider collapsible>
      <div className="logo">Airflow Platform</div>
      <Menu
        theme="dark"
        selectedKeys={[getSelectedKey()]}
        mode="inline"
        items={menuItems}
        onClick={handleMenuClick}
      />
    </Sider>
  );
};

export default Sidebar;
