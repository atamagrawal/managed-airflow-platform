import React from 'react';
import { Layout, Menu } from 'antd';
import { DashboardOutlined, TeamOutlined, CloudServerOutlined } from '@ant-design/icons';
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
  ];

  const handleMenuClick = ({ key }) => {
    navigate(key);
  };

  return (
    <Sider collapsible>
      <div className="logo">Airflow Platform</div>
      <Menu
        theme="dark"
        selectedKeys={[location.pathname]}
        mode="inline"
        items={menuItems}
        onClick={handleMenuClick}
      />
    </Sider>
  );
};

export default Sidebar;
