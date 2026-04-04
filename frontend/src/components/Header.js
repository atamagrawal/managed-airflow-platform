import React from 'react';
import { Layout, Typography, Space, Button } from 'antd';
import { LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import BrandMark from './BrandMark';
import FlowDeckWordmark from './FlowDeckWordmark';
import { BRAND } from '../brand';

const { Header: AntHeader } = Layout;
const { Text } = Typography;

const Header = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <AntHeader className="app-top-header" style={{ background: '#fff' }}>
      <div className="app-top-header-brand">
        <BrandMark size="md" />
        <div className="app-top-header-brand-row" style={{ color: 'rgba(0, 0, 0, 0.88)' }}>
          <FlowDeckWordmark size="md" />
          <span className="app-top-header-tagline">{BRAND.taglineShort}</span>
        </div>
      </div>
      <div className="app-top-header-actions">
        <Text type="secondary" style={{ margin: 0 }}>
          <UserOutlined /> {user?.username || '—'}
          {user?.admin ? ' (admin)' : user?.tenantScope ? ` · ${user.tenantScope}` : ''}
        </Text>
        <Button type="default" icon={<LogoutOutlined />} onClick={handleLogout}>
          Sign out
        </Button>
      </div>
    </AntHeader>
  );
};

export default Header;
