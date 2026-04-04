import React from 'react';
import { Layout, Menu } from 'antd';
import { DashboardOutlined, TeamOutlined, CloudServerOutlined, FolderOpenOutlined, RocketOutlined } from '@ant-design/icons';
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
      key: '/projects',
      icon: <FolderOpenOutlined />,
      label: 'Project browser',
    },
    {
      key: '/deployed-projects',
      icon: <RocketOutlined />,
      label: 'Deployed projects',
    },
  ];

  const handleMenuClick = ({ key }) => {
    navigate(key);
  };

  // Determine the selected menu key based on current path
  const getSelectedKey = () => {
    const path = location.pathname;
    if (path.startsWith('/deployed-projects')) return '/deployed-projects';
    if (path.startsWith('/projects')) return '/projects';
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
