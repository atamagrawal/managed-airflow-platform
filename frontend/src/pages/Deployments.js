import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  message,
  Space,
  Tag,
  Alert,
  Empty,
  Dropdown,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  LinkOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  MoreOutlined,
  ExperimentOutlined,
  CopyOutlined,
  FolderOpenOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { deploymentAPI, tenantAPI, openAirflowHandoffInNewTab } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { DEFAULT_AIRFLOW_VERSION, getAirflowVersionSelectOptions } from '../constants/airflowVersions';
import PageHeader from '../components/PageHeader';
import { getApiErrorMessage } from '../utils/apiError';
import { isFlowDeckTestDeploymentName } from '../constants/localTestDeployment';
import { BRAND } from '../brand';
import dayjs from 'dayjs';
import './Deployments.css';

const { Option } = Select;
const { TextArea } = Input;

const Deployments = () => {
  const navigate = useNavigate();
  const { isAdmin, tenantScope } = useAuth();
  const [deployments, setDeployments] = useState([]);
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(false);
  const [creatingDeployment, setCreatingDeployment] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [deploymentProvider, setDeploymentProvider] = useState('kubernetes');
  const [localIdleTimeoutMinutes, setLocalIdleTimeoutMinutes] = useState(60);
  const [now, setNow] = useState(() => Date.now());
  const [form] = Form.useForm();
  const [openingAirflowDeploymentId, setOpeningAirflowDeploymentId] = useState(null);
  const [localStackBusyId, setLocalStackBusyId] = useState(null);
  const [keepAliveBusyId, setKeepAliveBusyId] = useState(null);
  const openAirflowHandoffLockRef = useRef(false);

  useEffect(() => {
    fetchDeployments();
    fetchDeploymentConfig();
    if (isAdmin) {
      fetchTenants();
    }
  }, [isAdmin]);

  // Local deploy can take minutes; poll while any row is still provisioning so status self-heals from the API.
  useEffect(() => {
    const busy = deployments.some((d) => d.status === 'DEPLOYING' || d.status === 'PENDING');
    if (!busy) {
      return undefined;
    }
    const id = setInterval(() => {
      fetchDeployments();
    }, 8000);
    return () => clearInterval(id);
  }, [deployments]);

  // Tick every 30 s so idle countdown labels stay fresh without refetching.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(id);
  }, []);

  const fetchDeployments = async () => {
    try {
      setLoading(true);
      const response = await deploymentAPI.getAll();
      setDeployments(response.data);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to fetch deployments');
      if (msg) message.error(msg);
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
      if (response.data.localIdleTimeoutMinutes != null) {
        setLocalIdleTimeoutMinutes(Number(response.data.localIdleTimeoutMinutes));
      }
    } catch (error) {
      console.error('Error fetching deployment config:', error);
    }
  };

  /**
   * Returns a human-readable string describing how long until the local stack idles out,
   * or null when the feature is off / not applicable.
   */
  const getIdleShutdownLabel = useCallback(
    (record) => {
      if (deploymentProvider !== 'local') return null;
      if (record.status !== 'RUNNING') return null;
      if (!localIdleTimeoutMinutes || localIdleTimeoutMinutes <= 0) return null;

      const lastActivity =
        record.localStackLastActivityAt ||
        record.deployedAt ||
        record.createdAt;
      if (!lastActivity) return null;

      const shutdownAt = dayjs(lastActivity).add(localIdleTimeoutMinutes, 'minute');
      const diffMs = shutdownAt.valueOf() - now;

      if (diffMs <= 0) return { label: 'Stopping soon', urgent: true, warning: false };

      const diffMin = Math.ceil(diffMs / 60_000);
      if (diffMin < 60) return { label: `Stops in ${diffMin}m`, urgent: diffMin <= 10, warning: diffMin <= 20 };
      const h = Math.floor(diffMin / 60);
      const m = diffMin % 60;
      return { label: m > 0 ? `Stops in ${h}h ${m}m` : `Stops in ${h}h`, urgent: false, warning: false };
    },
    [deploymentProvider, localIdleTimeoutMinutes, now]
  );

  const handleOpenAirflow = async (deploymentId) => {
    if (openAirflowHandoffLockRef.current) return;
    openAirflowHandoffLockRef.current = true;
    try {
      setOpeningAirflowDeploymentId(deploymentId);
      await openAirflowHandoffInNewTab(async () => {
        const { data } = await deploymentAPI.airflowUiHandoff(deploymentId);
        return data.handoffId;
      });
      openAirflowHandoffLockRef.current = false;
    } catch (error) {
      openAirflowHandoffLockRef.current = false;
      const msg = getApiErrorMessage(error, 'Could not open Airflow');
      if (msg) message.error(msg);
    } finally {
      setOpeningAirflowDeploymentId(null);
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
          tag: values.tag,
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
      message.success('Deployment removed from the platform');
      fetchDeployments();
    } catch (error) {
      message.error('Failed to remove deployment');
      console.error('Error deleting deployment:', error);
    }
  };

  const confirmRemoveDeployment = (record) => {
    const flowDeck = isFlowDeckTestDeploymentName(record.name);
    Modal.confirm({
      title: 'Remove this deployment from the platform?',
      width: 480,
      content: (
        <div style={{ marginTop: 8 }}>
          <p style={{ marginBottom: 8 }}>
            <strong>Stop Airflow</strong> only shuts down running containers. The deployment stays in this list and you
            can start it again.
          </p>
          <p style={{ marginBottom: flowDeck ? 8 : 0 }}>
            <strong>Remove</strong> deletes the deployment record and local workspace files. This cannot be undone.
          </p>
          {flowDeck && (
            <p style={{ marginBottom: 0, color: 'rgba(0,0,0,0.65)' }}>
              This row is the {BRAND.name} test environment for your tenant. The IDE can create it again the next time you
              use <strong>Sync → Test environment</strong>.
            </p>
          )}
        </div>
      ),
      okText: 'Remove',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk: () => handleDeleteDeployment(record.deploymentId),
    });
  };

  const handleStartLocalStack = async (deploymentId) => {
    try {
      setLocalStackBusyId(deploymentId);
      await deploymentAPI.startLocalStack(deploymentId);
      message.success('Stack is starting. This may take a few minutes.');
      fetchDeployments();
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to start stack');
      if (msg) message.error(msg);
      console.error(error);
    } finally {
      setLocalStackBusyId(null);
    }
  };

  const handleStopLocalStack = async (deploymentId) => {
    try {
      setLocalStackBusyId(deploymentId);
      await deploymentAPI.stopLocalStack(deploymentId);
      message.success('Stack stopped.');
      fetchDeployments();
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to stop stack');
      if (msg) message.error(msg);
      console.error(error);
    } finally {
      setLocalStackBusyId(null);
    }
  };

  const handleKeepAlive = async (deploymentId) => {
    try {
      setKeepAliveBusyId(deploymentId);
      await deploymentAPI.keepAliveLocalStack(deploymentId);
      await fetchDeployments();
      message.success('Timer reset — idle clock restarted.');
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to reset timer');
      if (msg) message.error(msg);
    } finally {
      setKeepAliveBusyId(null);
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

  const copyDeploymentId = (id) => {
    if (!id || typeof navigator?.clipboard?.writeText !== 'function') return;
    navigator.clipboard.writeText(id).then(
      () => message.success('Deployment ID copied'),
      () => message.error('Could not copy')
    );
  };

  const tenantColumn = {
    title: 'Tenant',
    dataIndex: 'tenantId',
    key: 'tenantId',
    width: 120,
    ellipsis: true,
  };

  const deploymentColumn = {
    title: 'Deployment',
    key: 'deployment',
    width: 240,
    fixed: 'left',
    render: (_, record) => {
      const id = record.deploymentId;
      return (
        <div className="deployments-deployment-cell deployments-deployment-cell--id-only">
          <Tooltip title={id}>
            <span className="deployments-deployment-id-wrap">
              <code className="deployments-deployment-id-code">{id}</code>
            </span>
          </Tooltip>
          <Tooltip title="Copy deployment ID">
            <Button
              type="text"
              size="small"
              icon={<CopyOutlined />}
              onClick={() => copyDeploymentId(id)}
              aria-label="Copy deployment ID"
            />
          </Tooltip>
        </div>
      );
    },
  };

  const tagColumn = {
    title: 'Tag',
    key: 'tag',
    width: 118,
    align: 'center',
    render: (_, record) => {
      const custom = record.tag && String(record.tag).trim() !== '' ? String(record.tag).trim() : null;
      if (custom) {
        return (
          <Tag color="geekblue" style={{ margin: 0 }}>
            {custom}
          </Tag>
        );
      }
      if (isFlowDeckTestDeploymentName(record.name)) {
        return (
          <Tag icon={<ExperimentOutlined />} color="processing" style={{ margin: 0 }}>
            Test env
          </Tag>
        );
      }
      return <span className="deployments-tag-empty">—</span>;
    },
  };

  const actionsColumn = {
    title: 'Actions',
    key: 'actions',
    width: 200,
    fixed: 'right',
    align: 'right',
    render: (_, record) => (
      <div className="deployments-row-actions">
        {deploymentProvider === 'local' &&
          (record.status === 'STOPPED' || record.status === 'FAILED') && (
            <Tooltip title="Start Airflow (containers only; row stays)">
              <Button
                type="text"
                size="small"
                icon={<PlayCircleOutlined />}
                loading={localStackBusyId === record.deploymentId}
                onClick={() => handleStartLocalStack(record.deploymentId)}
                aria-label="Start Airflow"
              />
            </Tooltip>
          )}
        {deploymentProvider === 'local' && record.status === 'RUNNING' && (
          <Tooltip title="Stop Airflow (containers only)">
            <Button
              type="text"
              size="small"
              icon={<PauseCircleOutlined />}
              loading={localStackBusyId === record.deploymentId}
              onClick={() => handleStopLocalStack(record.deploymentId)}
              aria-label="Stop Airflow"
            />
          </Tooltip>
        )}
        {record.webserverUrl && (
          <Tooltip title="Open Airflow UI">
            <Button
              type="text"
              size="small"
              icon={<LinkOutlined />}
              loading={openingAirflowDeploymentId === record.deploymentId}
              onClick={() => handleOpenAirflow(record.deploymentId)}
              aria-label="Open Airflow"
            />
          </Tooltip>
        )}
        <Tooltip title="Deployed projects">
          <Button
            type="text"
            size="small"
            icon={<FolderOpenOutlined />}
            onClick={() => navigate(`/deployed-projects?deploymentId=${record.deploymentId}`)}
            aria-label="Deployed projects"
          />
        </Tooltip>
        <Dropdown
          trigger={['click']}
          placement="bottomRight"
          menu={{
            items: [
              ...(deploymentProvider === 'local' && record.status === 'RUNNING'
                ? [
                    {
                      key: 'keep-alive',
                      icon: keepAliveBusyId === record.deploymentId
                        ? <ClockCircleOutlined spin />
                        : <ClockCircleOutlined />,
                      label: 'Reset idle timer',
                    },
                    { type: 'divider' },
                  ]
                : []),
              {
                key: 'remove',
                danger: true,
                icon: <DeleteOutlined />,
                label: 'Remove from platform…',
              },
            ],
            onClick: ({ key, domEvent }) => {
              domEvent?.stopPropagation();
              if (key === 'keep-alive') {
                handleKeepAlive(record.deploymentId);
              } else if (key === 'remove') {
                confirmRemoveDeployment(record);
              }
            },
          }}
        >
          <Tooltip title="More">
            <Button
              type="text"
              size="small"
              icon={<MoreOutlined />}
              aria-label="More actions"
            />
          </Tooltip>
        </Dropdown>
      </div>
    ),
  };

  const columns = [
    deploymentColumn,
    tagColumn,
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 108,
      render: (status) => <Tag color={getStatusColor(status)}>{status}</Tag>,
    },
    ...(deploymentProvider === 'local'
      ? [
          {
            title: 'Idle',
            key: 'autoStop',
            width: 130,
            render: (_, record) => {
              const idleInfo = getIdleShutdownLabel(record);
              if (!idleInfo) {
                return <span className="deployments-autostop-na">—</span>;
              }

              const lastActivity =
                record.localStackLastActivityAt || record.deployedAt || record.createdAt;
              const elapsedMs = lastActivity ? now - dayjs(lastActivity).valueOf() : 0;
              const totalMs = localIdleTimeoutMinutes * 60 * 1000;
              const pct = Math.min(100, Math.max(0, (elapsedMs / totalMs) * 100));

              const barColor = idleInfo.urgent
                ? '#cf1322'
                : idleInfo.warning
                ? '#d46b08'
                : '#52c41a';
              const textColor = idleInfo.urgent
                ? '#cf1322'
                : idleInfo.warning
                ? '#d46b08'
                : 'rgba(0,0,0,0.55)';

              return (
                <Tooltip
                  title={`Auto-stops after ${localIdleTimeoutMinutes} min of inactivity — activity: deploy, trigger, or Open Airflow`}
                >
                  <div className="deployments-autostop-cell">
                    <span className="deployments-autostop-label" style={{ color: textColor }}>
                      <ClockCircleOutlined />
                      {idleInfo.label}
                    </span>
                    <div className="deployments-autostop-bar-track">
                      <div
                        className="deployments-autostop-bar-fill"
                        style={{ width: `${pct}%`, background: barColor }}
                      />
                    </div>
                  </div>
                </Tooltip>
              );
            },
          },
        ]
      : []),
    {
      title: 'Version',
      dataIndex: 'airflowVersion',
      key: 'airflowVersion',
      width: 96,
      ellipsis: true,
    },
    {
      title: 'Executor',
      dataIndex: 'executorType',
      key: 'executorType',
      width: 96,
      ellipsis: true,
    },
    {
      title: 'Workers',
      key: 'workers',
      width: 84,
      render: (_, record) => {
        const min = record.minWorkers;
        const max = record.maxWorkers;
        if (min == null && max == null) return <span style={{ color: 'rgba(0,0,0,0.25)' }}>—</span>;
        return `${min ?? '?'}–${max ?? '?'}`;
      },
    },
    ...(isAdmin ? [tenantColumn] : []),
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 128,
      render: (date) => dayjs(date).format('MM-DD HH:mm'),
    },
    actionsColumn,
  ];

  return (
    <div className="deployments-page">
      <PageHeader
        title="Deployments"
        description="Airflow environments in your tenant (or all tenants as admin). Create a deployment before linking and syncing projects."
        extra={
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={fetchDeployments} loading={loading}>
              Refresh
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setModalVisible(true);
                if (!isAdmin && tenantScope) {
                  form.setFieldsValue({ tenantId: tenantScope });
                }
              }}
            >
              Create deployment
            </Button>
          </Space>
        }
      />

      <Table
        className="deployments-table"
        columns={columns}
        dataSource={deployments}
        loading={loading}
        rowKey="id"
        scroll={{ x: deploymentProvider === 'local' ? 1200 : 1060 }}
        tableLayout="fixed"
        locale={{
          emptyText: (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No deployments yet">
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setModalVisible(true);
                  if (!isAdmin && tenantScope) {
                    form.setFieldsValue({ tenantId: tenantScope });
                  }
                }}
              >
                Create your first deployment
              </Button>
            </Empty>
          ),
        }}
      />

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
            airflowVersion: DEFAULT_AIRFLOW_VERSION,
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
          {isAdmin ? (
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
          ) : (
            <Form.Item
              name="tenantId"
              label="Tenant"
              rules={[{ required: true, message: 'Tenant is required' }]}
              extra="Deployments are created in your assigned tenant. Contact an administrator to manage tenants."
            >
              <Input readOnly placeholder={tenantScope || '—'} />
            </Form.Item>
          )}

          <Form.Item
            name="name"
            label="Deployment Name"
            rules={[{ required: true, message: 'Please enter deployment name' }]}
          >
            <Input placeholder="Enter deployment name" />
          </Form.Item>

          <Form.Item
            name="tag"
            label="Tag"
            rules={[{ max: 100, message: 'Tag must be 100 characters or less' }]}
            extra="Short label for this row in the deployments table (e.g. Prod, Staging, Dev). Not a Docker image tag."
          >
            <Input placeholder="e.g. Staging, Prod" allowClear />
          </Form.Item>

          <Form.Item name="description" label="Description">
            <TextArea rows={3} placeholder="Enter deployment description" />
          </Form.Item>

          <Form.Item
            name="airflowVersion"
            label="Airflow Version"
            rules={[{ required: true, message: 'Please select Airflow version' }]}
            tooltip="Supported Airflow releases for new deployments. Additional versions will be added as the platform validates them."
          >
            <Select
              placeholder="Select Airflow version"
              options={getAirflowVersionSelectOptions()}
            />
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
