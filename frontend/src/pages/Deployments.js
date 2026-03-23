import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, message, Typography, Space, Tag, Popconfirm } from 'antd';
import { PlusOutlined, DeleteOutlined, LinkOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { deploymentAPI, tenantAPI } from '../services/api';
import dayjs from 'dayjs';

const { Title } = Typography;
const { Option } = Select;
const { TextArea } = Input;

const Deployments = () => {
  const navigate = useNavigate();
  const [deployments, setDeployments] = useState([]);
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchDeployments();
    fetchTenants();
  }, []);

  const fetchDeployments = async () => {
    try {
      setLoading(true);
      const response = await deploymentAPI.getAll();
      setDeployments(response.data);
    } catch (error) {
      message.error('Failed to fetch deployments');
      console.error('Error fetching deployments:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchTenants = async () => {
    try {
      const response = await tenantAPI.getAll();
      setTenants(response.data);
    } catch (error) {
      console.error('Error fetching tenants:', error);
    }
  };

  const handleCreateDeployment = async (values) => {
    try {
      await deploymentAPI.create(values);
      message.success('Deployment created successfully');
      setModalVisible(false);
      form.resetFields();
      fetchDeployments();
    } catch (error) {
      message.error('Failed to create deployment');
      console.error('Error creating deployment:', error);
    }
  };

  const handleDeleteDeployment = async (deploymentId) => {
    try {
      await deploymentAPI.delete(deploymentId);
      message.success('Deployment deleted successfully');
      fetchDeployments();
    } catch (error) {
      message.error('Failed to delete deployment');
      console.error('Error deleting deployment:', error);
    }
  };

  const getStatusColor = (status) => {
    const colors = {
      RUNNING: 'green',
      PENDING: 'orange',
      DEPLOYING: 'blue',
      UPDATING: 'cyan',
      FAILED: 'red',
      STOPPED: 'default',
      DELETED: 'default',
    };
    return colors[status] || 'default';
  };

  const columns = [
    {
      title: 'Deployment ID',
      dataIndex: 'deploymentId',
      key: 'deploymentId',
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Tenant ID',
      dataIndex: 'tenantId',
      key: 'tenantId',
    },
    {
      title: 'Airflow Version',
      dataIndex: 'airflowVersion',
      key: 'airflowVersion',
    },
    {
      title: 'Executor',
      dataIndex: 'executorType',
      key: 'executorType',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => <Tag color={getStatusColor(status)}>{status}</Tag>,
    },
    {
      title: 'Workers',
      key: 'workers',
      render: (_, record) => `${record.minWorkers} - ${record.maxWorkers}`,
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
          {record.webserverUrl && (
            <Button
              type="link"
              icon={<LinkOutlined />}
              onClick={() => window.open(record.webserverUrl, '_blank')}
            >
              Open
            </Button>
          )}
          <Popconfirm
            title="Are you sure you want to delete this deployment?"
            onConfirm={() => handleDeleteDeployment(record.deploymentId)}
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
        <Title level={2}>Deployments</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalVisible(true)}>
          Create Deployment
        </Button>
      </div>

      <Table columns={columns} dataSource={deployments} loading={loading} rowKey="id" />

      <Modal
        title="Create New Deployment"
        open={modalVisible}
        onOk={() => form.submit()}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        width={700}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateDeployment}
          initialValues={{
            airflowVersion: '1.13.0',
            executorType: 'CELERY',
            minWorkers: 1,
            maxWorkers: 5,
            schedulerCpu: '1000m',
            schedulerMemory: '2Gi',
            workerCpu: '1000m',
            workerMemory: '2Gi',
            webserverCpu: '500m',
            webserverMemory: '1Gi',
          }}
        >
          <Form.Item
            name="tenantId"
            label="Tenant"
            rules={[{ required: true, message: 'Please select a tenant' }]}
          >
            <Select placeholder="Select tenant">
              {tenants.map((tenant) => (
                <Option key={tenant.tenantId} value={tenant.tenantId}>
                  {tenant.name} ({tenant.tenantId})
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="name"
            label="Deployment Name"
            rules={[{ required: true, message: 'Please enter deployment name' }]}
          >
            <Input placeholder="Enter deployment name" />
          </Form.Item>

          <Form.Item name="description" label="Description">
            <TextArea rows={3} placeholder="Enter deployment description" />
          </Form.Item>

          <Form.Item
            name="airflowVersion"
            label="Airflow Version"
            rules={[{ required: true, message: 'Please enter Airflow version' }]}
          >
            <Input placeholder="e.g., 1.13.0" />
          </Form.Item>

          <Form.Item
            name="executorType"
            label="Executor Type"
            rules={[{ required: true, message: 'Please select executor type' }]}
          >
            <Select placeholder="Select executor type">
              <Option value="LOCAL">Local Executor</Option>
              <Option value="CELERY">Celery Executor</Option>
              <Option value="KUBERNETES">Kubernetes Executor</Option>
              <Option value="CELERY_KUBERNETES">Celery Kubernetes Executor</Option>
            </Select>
          </Form.Item>

          <Form.Item label="Worker Autoscaling">
            <Space>
              <Form.Item
                name="minWorkers"
                noStyle
                rules={[{ required: true, message: 'Required' }]}
              >
                <InputNumber min={1} placeholder="Min" style={{ width: 100 }} />
              </Form.Item>
              <span>to</span>
              <Form.Item
                name="maxWorkers"
                noStyle
                rules={[{ required: true, message: 'Required' }]}
              >
                <InputNumber min={1} placeholder="Max" style={{ width: 100 }} />
              </Form.Item>
            </Space>
          </Form.Item>

          <Form.Item name="ingressHost" label="Ingress Host (Optional)">
            <Input placeholder="e.g., airflow.example.com" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Deployments;
