import React, { useState, useEffect, useCallback } from 'react';
import { Table, Alert, Tag, message, Button, Space, Modal, Form, Input, Select, Popconfirm } from 'antd';
import { ReloadOutlined, UserAddOutlined, DeleteOutlined } from '@ant-design/icons';
import { adminUserAPI, tenantAPI } from '../services/api';
import { getApiErrorMessage } from '../utils/apiError';
import PageHeader from '../components/PageHeader';

const SOURCE_DB = 'database';

const Users = () => {
  const [users, setUsers] = useState([]);
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();
  const rolesWatch = Form.useWatch('roles', form);

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

  const loadTenants = useCallback(async () => {
    try {
      const { data } = await tenantAPI.getAll();
      setTenants(data || []);
    } catch (e) {
      console.error('Failed to load tenants for user form', e);
    }
  }, []);

  useEffect(() => {
    loadUsers();
    loadTenants();
  }, [loadUsers, loadTenants]);

  const openModal = () => {
    form.resetFields();
    form.setFieldsValue({ roles: ['USER'] });
    setModalOpen(true);
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);
      const roles = values.roles?.length ? values.roles : ['USER'];
      const payload = {
        username: values.username.trim(),
        password: values.password,
        roles,
      };
      const isAdmin = roles.some((r) => r === 'ADMIN');
      if (!isAdmin && values.tenantId) {
        payload.tenantId = values.tenantId;
      }
      await adminUserAPI.create(payload);
      message.success('User created');
      setModalOpen(false);
      form.resetFields();
      loadUsers();
    } catch (error) {
      if (error?.errorFields) return;
      const msg = getApiErrorMessage(error, 'Failed to create user');
      if (msg) message.error(msg);
      console.error(error);
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id) => {
    try {
      await adminUserAPI.delete(id);
      message.success('User removed');
      loadUsers();
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to delete user');
      if (msg) message.error(msg);
      console.error(error);
    }
  };

  const showTenantField = !rolesWatch?.includes('ADMIN');

  const columns = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      width: 160,
    },
    {
      title: 'Source',
      dataIndex: 'source',
      key: 'source',
      width: 130,
      render: (src) =>
        src === SOURCE_DB ? (
          <Tag color="green">Database</Tag>
        ) : (
          <Tag color="default">Configuration</Tag>
        ),
    },
    {
      title: 'Roles',
      dataIndex: 'roles',
      key: 'roles',
      render: (roleList) =>
        (roleList || []).map((r) => (
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
          <Tag color="blue">Explicit tenant</Tag>
        );
      },
    },
    {
      title: 'Admin',
      dataIndex: 'admin',
      key: 'admin',
      width: 80,
      render: (v) => (v ? 'Yes' : 'No'),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (_, row) =>
        row.source === SOURCE_DB && row.id != null ? (
          <Popconfirm title="Remove this user?" onConfirm={() => handleDelete(row.id)} okText="Remove" cancelText="Cancel">
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              Remove
            </Button>
          </Popconfirm>
        ) : (
          <span style={{ color: 'rgba(0,0,0,0.25)' }}>—</span>
        ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Users"
        description={
          <>
            <strong>Database</strong> users are managed here (sign-in stored in the control plane DB).{' '}
            <strong>Configuration</strong> users come from <code>platform.security.users</code> and require a restart to
            change. Non-admins need a home tenant (explicit or platform default). Database login overrides the same
            username in YAML if both exist.
          </>
        }
        extra={
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={loadUsers} loading={loading}>
              Refresh
            </Button>
            <Button type="primary" icon={<UserAddOutlined />} onClick={openModal}>
              Add user
            </Button>
          </Space>
        }
      />
      <Alert
        type="info"
        showIcon
        message="Removing the last administrator is blocked"
        description="Keep at least one ADMIN in the database or in configuration."
        style={{ marginBottom: 16 }}
      />
      <Table
        rowKey={(r) => (r.id != null ? `db-${r.id}` : `cfg-${r.username}`)}
        columns={columns}
        dataSource={users}
        loading={loading}
        scroll={{ x: 900 }}
        pagination={false}
      />

      <Modal
        title="Add database user"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => setModalOpen(false)}
        confirmLoading={creating}
        destroyOnClose
        width={480}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item name="username" label="Username" rules={[{ required: true, message: 'Required' }]}>
            <Input autoComplete="off" placeholder="Unique login name" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[
              { required: true, message: 'Required' },
              { min: 8, message: 'At least 8 characters' },
            ]}
          >
            <Input.Password autoComplete="new-password" placeholder="Min. 8 characters" />
          </Form.Item>
          <Form.Item name="roles" label="Roles" rules={[{ required: true, message: 'Select at least one role' }]}>
            <Select
              mode="multiple"
              placeholder="Roles"
              options={[
                { value: 'USER', label: 'USER' },
                { value: 'ADMIN', label: 'ADMIN' },
              ]}
            />
          </Form.Item>
          {showTenantField ? (
            <Form.Item
              name="tenantId"
              label="Home tenant"
              tooltip="Leave empty to use platform default-tenant-id-for-users for non-admins."
            >
              <Select
                allowClear
                placeholder="Platform default if empty"
                showSearch
                optionFilterProp="label"
                options={tenants.map((t) => ({
                  value: t.tenantId,
                  label: `${t.name} (${t.tenantId})`,
                }))}
              />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </div>
  );
};

export default Users;
