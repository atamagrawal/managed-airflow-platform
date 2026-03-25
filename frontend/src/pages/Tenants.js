import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, message, Typography, Space, Tag, Popconfirm, Alert } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { tenantAPI } from '../services/api';
import dayjs from 'dayjs';

const { Title } = Typography;
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
      message.error('Failed to fetch tenants');
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
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={2}>Tenants</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalVisible(true)}>
          Create Tenant
        </Button>
      </div>

      <Table columns={columns} dataSource={tenants} loading={loading} rowKey="id" />

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
