import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, message, Typography, Space, Tag, Popconfirm, Alert } from 'antd';
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
  const [creatingDeployment, setCreatingDeployment] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [deploymentProvider, setDeploymentProvider] = useState('kubernetes');
  const [form] = Form.useForm();

  useEffect(() => {
    fetchDeployments();
    fetchTenants();
    fetchDeploymentConfig();
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

  const fetchDeploymentConfig = async () => {
    try {
      const response = await deploymentAPI.getConfig();
      setDeploymentProvider(response.data.provider || 'kubernetes');
    } catch (error) {
      console.error('Error fetching deployment config:', error);
    }
  };

  const handleCreateDeployment = async (values) => {
    const tempId = -Date.now();
    try {
      if (creatingDeployment) return;
      setCreatingDeployment(true);

      // UX: close the form immediately, while we create the deployment in the background.
      // Also show an in-progress row in the deployments table.
      setModalVisible(false);
      form.resetFields();

      const tempDeploymentId = 'pending...';
      const tempCreatedAt = new Date().toISOString();

      setDeployments((prev) => [
        ...prev,
        {
          id: tempId,
          deploymentId: tempDeploymentId,
          tenantId: values.tenantId,
          name: values.name,
          description: values.description,
          airflowVersion: values.airflowVersion,
          executorType: values.executorType,
          status: 'DEPLOYING',
          namespace: '',
          helmReleaseName: '',
          minWorkers: values.minWorkers,
          maxWorkers: values.maxWorkers,
          schedulerCpu: values.schedulerCpu,
          schedulerMemory: values.schedulerMemory,
          workerCpu: values.workerCpu,
          workerMemory: values.workerMemory,
          webserverCpu: values.webserverCpu,
          webserverMemory: values.webserverMemory,
          webserverUrl: undefined,
          ingressHost: '',
          createdAt: tempCreatedAt,
          updatedAt: tempCreatedAt,
          deployedAt: tempCreatedAt,
        },
      ]);

      await deploymentAPI.create(values);
      // Replace the optimistic row with server truth.
      message.success('Deployment created successfully');
      fetchDeployments();
    } catch (error) {
      // Remove optimistic row on error
      setDeployments((prev) => prev.filter((d) => d.id !== tempId));
      message.error('Failed to create deployment');
      console.error('Error creating deployment:', error);
    } finally {
      setCreatingDeployment(false);
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
          <Button
            type="link"
            onClick={() => navigate(`/deployed-projects?deploymentId=${record.deploymentId}`)}
          >
            Deployed projects
          </Button>
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
          message={`Deployment Provider: ${deploymentProvider.toUpperCase()}`}
          description={
            deploymentProvider === 'local'
              ? 'Deployments will be created using Docker Compose on localhost. Ensure Docker is running.'
              : deploymentProvider === 'ec2'
              ? 'Deployments will be created using Docker Compose on EC2 instances.'
              : deploymentProvider === 'ecs'
              ? 'Deployments will be created using AWS ECS Fargate.'
              : 'Deployments will be created using Kubernetes with Helm charts.'
          }
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
            ...(deploymentProvider === 'ecs' && {
              schedulerCpu: '512',
              schedulerMemory: '1024',
              workerCpu: '512',
              workerMemory: '1024',
              webserverCpu: '512',
              webserverMemory: '1024',
            }),
            ...(deploymentProvider === 'kubernetes' && {
              schedulerCpu: '1000m',
              schedulerMemory: '2Gi',
              workerCpu: '1000m',
              workerMemory: '2Gi',
              webserverCpu: '500m',
              webserverMemory: '1Gi',
            }),
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
            tooltip={
              deploymentProvider === 'kubernetes'
                ? 'LOCAL: Single-process execution. CELERY: Distributed with Redis. KUBERNETES: Each task in separate pod. CELERY_KUBERNETES: Hybrid approach.'
                : 'LOCAL: Single-process execution (recommended for dev/test). CELERY: Distributed task execution with Redis.'
            }
          >
            <Select placeholder="Select executor type">
              <Option value="LOCAL">Local Executor</Option>
              <Option value="CELERY">Celery Executor (requires Redis)</Option>
              {deploymentProvider === 'kubernetes' && (
                <>
                  <Option value="KUBERNETES">Kubernetes Executor</Option>
                  <Option value="CELERY_KUBERNETES">Celery Kubernetes Executor</Option>
                </>
              )}
            </Select>
          </Form.Item>

          <Form.Item
            label="Worker Scaling"
            tooltip={
              deploymentProvider === 'local' || deploymentProvider === 'ec2'
                ? 'Number of worker replicas for Celery executor. Docker Compose will maintain this count.'
                : deploymentProvider === 'ecs'
                ? 'Min and desired worker count. ECS auto-scaling can adjust based on metrics.'
                : 'Min and max workers. KEDA auto-scaling adjusts based on queue depth.'
            }
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

          {(deploymentProvider === 'ecs' || deploymentProvider === 'kubernetes') && (
            <>
              <Form.Item
                name="schedulerCpu"
                label={deploymentProvider === 'kubernetes' ? 'Scheduler CPU (e.g., 1000m)' : 'Scheduler CPU (CPU units)'}
                tooltip={
                  deploymentProvider === 'kubernetes'
                    ? 'CPU allocation in millicores with m suffix. Examples: 500m = 0.5 CPU, 1000m = 1 CPU'
                    : 'CPU allocation in CPU units. Examples: 256, 512, 1024, 2048'
                }
                rules={[{ required: true, message: 'Required' }]}
              >
                <Input placeholder={deploymentProvider === 'kubernetes' ? '1000m' : '512'} />
              </Form.Item>

              <Form.Item
                name="schedulerMemory"
                label={deploymentProvider === 'kubernetes' ? 'Scheduler Memory (e.g., 2Gi)' : 'Scheduler Memory (MB)'}
                tooltip={
                  deploymentProvider === 'kubernetes'
                    ? 'Memory allocation with Mi/Gi suffix. Examples: 512Mi, 1Gi, 2Gi'
                    : 'Memory allocation in megabytes. Examples: 512, 1024, 2048'
                }
                rules={[{ required: true, message: 'Required' }]}
              >
                <Input placeholder={deploymentProvider === 'kubernetes' ? '2Gi' : '1024'} />
              </Form.Item>

              <Form.Item
                name="webserverCpu"
                label={deploymentProvider === 'kubernetes' ? 'Webserver CPU (e.g., 500m)' : 'Webserver CPU (CPU units)'}
                tooltip={
                  deploymentProvider === 'kubernetes'
                    ? 'CPU allocation in millicores with m suffix'
                    : 'CPU allocation in CPU units'
                }
                rules={[{ required: true, message: 'Required' }]}
              >
                <Input placeholder={deploymentProvider === 'kubernetes' ? '500m' : '512'} />
              </Form.Item>

              <Form.Item
                name="webserverMemory"
                label={deploymentProvider === 'kubernetes' ? 'Webserver Memory (e.g., 1Gi)' : 'Webserver Memory (MB)'}
                tooltip={
                  deploymentProvider === 'kubernetes'
                    ? 'Memory allocation with Mi/Gi suffix'
                    : 'Memory allocation in megabytes'
                }
                rules={[{ required: true, message: 'Required' }]}
              >
                <Input placeholder={deploymentProvider === 'kubernetes' ? '1Gi' : '1024'} />
              </Form.Item>

              <Form.Item
                name="workerCpu"
                label={deploymentProvider === 'kubernetes' ? 'Worker CPU (e.g., 1000m)' : 'Worker CPU (CPU units)'}
                tooltip={
                  deploymentProvider === 'kubernetes'
                    ? 'CPU allocation per worker in millicores with m suffix'
                    : 'CPU allocation per worker in CPU units'
                }
                rules={[{ required: true, message: 'Required' }]}
              >
                <Input placeholder={deploymentProvider === 'kubernetes' ? '1000m' : '512'} />
              </Form.Item>

              <Form.Item
                name="workerMemory"
                label={deploymentProvider === 'kubernetes' ? 'Worker Memory (e.g., 2Gi)' : 'Worker Memory (MB)'}
                tooltip={
                  deploymentProvider === 'kubernetes'
                    ? 'Memory allocation per worker with Mi/Gi suffix'
                    : 'Memory allocation per worker in megabytes'
                }
                rules={[{ required: true, message: 'Required' }]}
              >
                <Input placeholder={deploymentProvider === 'kubernetes' ? '2Gi' : '1024'} />
              </Form.Item>
            </>
          )}

          {(deploymentProvider === 'kubernetes' || deploymentProvider === 'ecs') && (
            <Form.Item
              name="ingressHost"
              label="Custom Domain (Optional)"
              tooltip={
                deploymentProvider === 'kubernetes'
                  ? 'Custom domain for Airflow UI. Used for Kubernetes ingress configuration.'
                  : 'Custom domain for Airflow UI. Used for Application Load Balancer configuration.'
              }
            >
              <Input placeholder="e.g., airflow.example.com" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default Deployments;
