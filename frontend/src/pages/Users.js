import React, { useState, useEffect } from 'react';
import { Table, Typography, Alert, Tag, message } from 'antd';
import { adminUserAPI } from '../services/api';
import { getApiErrorMessage } from '../utils/apiError';

const { Title, Paragraph } = Typography;

const Users = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        const { data } = await adminUserAPI.list();
        setUsers(data || []);
      } catch (error) {
        const msg = getApiErrorMessage(error, 'Failed to load users');
        if (msg) message.error(msg);
        console.error('Error loading users:', error);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const columns = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      width: 160,
    },
    {
      title: 'Roles',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles) =>
        (roles || []).map((r) => (
          <Tag key={r} color={r === 'ADMIN' ? 'red' : 'blue'}>
            {r}
          </Tag>
        )),
    },
    {
      title: 'Home tenant',
      dataIndex: 'tenantScope',
      key: 'tenantScope',
      width: 180,
      ellipsis: true,
      render: (v, row) => {
        if (row.admin) return '— (admin, not scoped)';
        return v || '—';
      },
    },
    {
      title: 'Tenant assignment',
      key: 'tenantAssignment',
      width: 160,
      render: (_, row) => {
        if (row.admin) return '—';
        return row.usesPlatformDefaultTenant ? (
          <Tag color="default">Platform default</Tag>
        ) : (
          <Tag color="blue">Per user (config)</Tag>
        );
      },
    },
    {
      title: 'Admin',
      dataIndex: 'admin',
      key: 'admin',
      width: 90,
      render: (v) => (v ? 'Yes' : 'No'),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Title level={2}>Users</Title>
      <Paragraph type="secondary" style={{ marginBottom: 16 }}>
        Each <strong>non-admin</strong> user has a <strong>home tenant</strong> (<code>tenant-id</code> under their
        entry, or <code>default-tenant-id-for-users</code> when omitted). That tenant must exist under{' '}
        <strong>Tenants</strong>. <strong>Admins</strong> are not limited to one tenant in the JWT. Edit{' '}
        <code>platform.security.users</code> and restart the control plane to change accounts.
      </Paragraph>
      <Alert
        type="info"
        showIcon
        message="This list is read-only"
        description="Future versions may add database-backed users and self-service management."
        style={{ marginBottom: 16 }}
      />
      <Table
        rowKey={(r) => r.username}
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={false}
      />
    </div>
  );
};

export default Users;
