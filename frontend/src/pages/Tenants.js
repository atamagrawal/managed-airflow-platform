import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, message, Space, Tag, Popconfirm, Alert, Empty } from 'antd';
import { PlusOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { tenantAPI } from '../services/api';
import PageHeader from '../components/PageHeader';
import { getApiErrorMessage } from '../utils/apiError';
import dayjs from 'dayjs';
const { Option } = Select;

const Tenants = () => {
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchTenants();
  }, []);

  const fetchTenants = async () => {
    try {
      setLoading(true);
      const response = await tenantAPI.getAll();
      setTenants(response.data);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to fetch tenants');
      if (msg) message.error(msg);
      console.error('Error fetching tenants:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateTenant = async (values) => {
    try {
      await tenantAPI.create(values);
      message.success('Tenant created successfully');
      setModalVisible(false);
      form.resetFields();
      fetchTenants();
    } catch (error) {
      message.error('Failed to create tenant');
      console.error('Error creating tenant:', error);
    }
  };

  const handleDeleteTenant = async (tenantId) => {
    try {
      await tenantAPI.delete(tenantId);
      message.success('Tenant deleted successfully');
      fetchTenants();
    } catch (error) {
      message.error('Failed to delete tenant');
      console.error('Error deleting tenant:', error);
    }
  };

  const getStatusColor = (status) => {
    const colors = {
      ACTIVE: 'green',
      PENDING: 'orange',
      SUSPENDED: 'red',
      DELETED: 'default',
    };
    return colors[status] || 'default';
  };

  const columns = [
    {
      title: 'Tenant ID',
      dataIndex: 'tenantId',
      key: 'tenantId',
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: 'Organization',
      dataIndex: 'organization',
      key: 'organization',
    },
    {
      title: 'Cloud Provider',
      dataIndex: 'cloudProvider',
      key: 'cloudProvider',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => <Tag color={getStatusColor(status)}>{status}</Tag>,
    },
    {
      title: 'Created At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date) => dayjs(date).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Popconfirm
            title="Are you sure you want to delete this tenant?"
            onConfirm={() => handleDeleteTenant(record.tenantId)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Tenants"
        description="Customer / isolation boundaries. Non-admin users are scoped to a tenant; deployments and projects belong to tenants."
        extra={
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={fetchTenants} loading={loading}>
              Refresh
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalVisible(true)}>
              Create tenant
            </Button>
          </Space>
        }
      />

      <Table
        columns={columns}
        dataSource={tenants}
        loading={loading}
        rowKey="id"
        locale={{
          emptyText: (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No tenants yet">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalVisible(true)}>
                Create tenant
              </Button>
            </Empty>
          ),
        }}
      />

      <Modal
        title="Create New Tenant"
        open={modalVisible}
        onOk={() => form.submit()}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        width={600}
      >
        <Alert
          message="Tenant Information"
          description="Tenant details are for organizational purposes. The actual deployment mode (Local, EC2, ECS, Kubernetes) is configured in the control plane."
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />
        <Form form={form} layout="vertical" onFinish={handleCreateTenant}>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Please enter tenant name' }]}
          >
            <Input placeholder="Enter tenant name" />
          </Form.Item>

          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, message: 'Please enter email' },
              { type: 'email', message: 'Please enter a valid email' },
            ]}
          >
            <Input placeholder="Enter email address" />
          </Form.Item>

          <Form.Item name="organization" label="Organization">
            <Input placeholder="Enter organization name" />
          </Form.Item>

          <Form.Item
            name="cloudProvider"
            label="Cloud Provider"
            rules={[{ required: true, message: 'Please select a cloud provider' }]}
            tooltip="Primary cloud provider for this tenant (for organizational tracking)"
          >
            <Select placeholder="Select cloud provider">
              <Option value="AWS">AWS</Option>
              <Option value="GCP">GCP</Option>
              <Option value="AZURE">Azure</Option>
              <Option value="LOCAL">Local/On-Premises</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="clusterName"
            label="Cluster Name (Optional)"
            tooltip="Cluster identifier for Kubernetes deployments (for organizational tracking)"
          >
            <Input placeholder="e.g., prod-cluster-us-east" />
          </Form.Item>

          <Form.Item
            name="region"
            label="Region (Optional)"
            tooltip="Cloud region identifier (for organizational tracking)"
          >
            <Input placeholder="e.g., us-east-1, us-west-2" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Tenants;
