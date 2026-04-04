import React from 'react';
import { Layout, Typography, Space, Button } from 'antd';
import { LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const { Header: AntHeader } = Layout;
const { Title, Text } = Typography;

const Header = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <AntHeader
      style={{
        background: '#fff',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
    >
      <Title level={3} style={{ margin: 0 }}>
        Managed Airflow Control Plane
      </Title>
      <Space size="middle">
        <Text type="secondary">
          <UserOutlined /> {user?.username || '—'}
          {user?.admin ? ' (admin)' : user?.tenantScope ? ` · ${user.tenantScope}` : ''}
        </Text>
        <Button type="default" icon={<LogoutOutlined />} onClick={handleLogout}>
          Sign out
        </Button>
      </Space>
    </AntHeader>
  );
};

export default Header;
