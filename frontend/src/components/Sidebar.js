import React, { useMemo } from 'react';
import { Layout, Menu } from 'antd';
import {
  DashboardOutlined,
  TeamOutlined,
  UserOutlined,
  CloudServerOutlined,
  CodeOutlined,
  FolderOpenOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import BrandMark from './BrandMark';
import FlowDeckWordmark from './FlowDeckWordmark';
import { BRAND } from '../brand';

const { Sider } = Layout;

const Sidebar = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAdmin } = useAuth();

  const menuItems = useMemo(() => {
    const items = [
      {
        key: '/dashboard',
        icon: <DashboardOutlined />,
        label: 'Dashboard',
      },
    ];
    if (isAdmin) {
      items.push(
        {
          key: '/tenants',
          icon: <TeamOutlined />,
          label: 'Tenants',
        },
        {
          key: '/users',
          icon: <UserOutlined />,
          label: 'Users',
        }
      );
    }
    items.push(
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
      key: '/projects',
      icon: <FolderOpenOutlined />,
      label: 'Project browser',
    },
      {
        key: '/deployed-projects',
        icon: <RocketOutlined />,
        label: 'Deployed projects',
      }
    );
    return items;
  }, [isAdmin]);

  const handleMenuClick = ({ key }) => {
    navigate(key);
  };

  // Determine the selected menu key based on current path
  const getSelectedKey = () => {
    const path = location.pathname;
    if (path.startsWith('/deployed-projects')) return '/deployed-projects';
    if (path.startsWith('/dags')) return '/dags';
    if (path.startsWith('/projects')) return '/projects';
    if (path.startsWith('/deployments')) return '/deployments';
    if (path.startsWith('/tenants')) return '/tenants';
    if (path.startsWith('/users')) return '/users';
    return path;
  };

  return (
    <Sider collapsible>
      <div className="logo">
        <div className="logo-inner">
          <BrandMark size="sm" />
          <div>
            <div className="logo-wordmark">
              <FlowDeckWordmark size="sm" />
            </div>
            <div className="logo-tagline">{BRAND.taglineShort}</div>
          </div>
        </div>
      </div>
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
