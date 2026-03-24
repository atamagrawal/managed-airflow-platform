import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, message, Typography, Space, Tag, Popconfirm, Alert } from 'antd';
import { PlusOutlined, DeleteOutlined, LinkOutlined } from '@ant-design/icons';
import { deploymentAPI, tenantAPI } from '../services/api';
import dayjs from 'dayjs';

const { Title } = Typography;
const { Option } = Select;
const { TextArea } = Input;

const Deployments = () => {
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
        <Alert
          message="Deployment Provider"
          description="The deployment will be created using the active provider (Local, Kubernetes, ECS, or EC2) configured in the control plane. For local testing, ensure Docker is running and the control plane is started with the 'local' profile."
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreateDeployment}
          initialValues={{
            airflowVersion: '3.1.8',
            executorType: 'LOCAL',
            minWorkers: 1,
            maxWorkers: 3,
            schedulerCpu: '500',
            schedulerMemory: '1024',
            workerCpu: '500',
            workerMemory: '1024',
            webserverCpu: '500',
            webserverMemory: '1024',
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
            tooltip="Apache Airflow version to deploy (e.g., 3.1.8, 2.8.1)"
          >
            <Input placeholder="e.g., 3.1.8" />
          </Form.Item>

          <Form.Item
            name="executorType"
            label="Executor Type"
            rules={[{ required: true, message: 'Please select executor type' }]}
            tooltip="LOCAL: Simple, single-process execution. CELERY: Distributed task execution (requires Redis). KUBERNETES: Each task runs in separate pod (K8s only)"
          >
            <Select placeholder="Select executor type">
              <Option value="LOCAL">Local Executor (recommended for dev/test)</Option>
              <Option value="CELERY">Celery Executor (distributed execution)</Option>
              <Option value="KUBERNETES">Kubernetes Executor (K8s only)</Option>
              <Option value="CELERY_KUBERNETES">Celery Kubernetes Executor (hybrid)</Option>
            </Select>
          </Form.Item>

          <Form.Item
            label="Worker Autoscaling"
            tooltip="For Local/EC2: Manual scaling. For ECS/K8s: Auto-scaling based on metrics"
          >
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

          <Form.Item
            name="schedulerCpu"
            label="Scheduler CPU (millicores)"
            tooltip="CPU allocation in millicores. 500 = 0.5 CPU, 1000 = 1 CPU"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="500" />
          </Form.Item>

          <Form.Item
            name="schedulerMemory"
            label="Scheduler Memory (MB)"
            tooltip="Memory allocation in megabytes"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="1024" />
          </Form.Item>

          <Form.Item
            name="webserverCpu"
            label="Webserver CPU (millicores)"
            tooltip="CPU allocation in millicores. 500 = 0.5 CPU"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="500" />
          </Form.Item>

          <Form.Item
            name="webserverMemory"
            label="Webserver Memory (MB)"
            tooltip="Memory allocation in megabytes"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="1024" />
          </Form.Item>

          <Form.Item
            name="workerCpu"
            label="Worker CPU (millicores)"
            tooltip="CPU allocation per worker in millicores"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="500" />
          </Form.Item>

          <Form.Item
            name="workerMemory"
            label="Worker Memory (MB)"
            tooltip="Memory allocation per worker in megabytes"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input placeholder="1024" />
          </Form.Item>

          <Form.Item
            name="ingressHost"
            label="Ingress Host (Optional - Kubernetes only)"
            tooltip="Custom domain for Airflow UI. Only applicable for Kubernetes deployments with ingress configured"
          >
            <Input placeholder="e.g., airflow.example.com" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Deployments;
