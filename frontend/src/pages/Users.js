import React, { useState, useEffect, useCallback } from 'react';
import { Table, Alert, Tag, message, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { adminUserAPI } from '../services/api';
import { getApiErrorMessage } from '../utils/apiError';
import PageHeader from '../components/PageHeader';

const Users = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);

  const loadUsers = useCallback(async () => {
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
  }, []);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

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
    <div>
      <PageHeader
        title="Users"
        description={
          <>
            Each <strong>non-admin</strong> has a <strong>home tenant</strong> (<code>tenant-id</code> in config or{' '}
            <code>default-tenant-id-for-users</code>). That tenant must exist under Tenants. Admins are not JWT-scoped
            to one tenant. Accounts are defined in <code>platform.security.users</code>; restart the control plane after
            changes.
          </>
        }
        extra={
          <Button icon={<ReloadOutlined />} onClick={loadUsers} loading={loading}>
            Refresh
          </Button>
        }
      />
      <Alert
        type="info"
        showIcon
        message="Read-only directory"
        description="This list reflects configuration, not a live user database."
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
