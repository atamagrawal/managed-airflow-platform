import React, { useState, useEffect, useMemo } from 'react';
import { Table, Button, Space, Tag, message, Typography, Select } from 'antd';
import { RocketOutlined, PlayCircleOutlined, EyeOutlined, CodeOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { projectAPI, deploymentAPI } from '../services/api';
import { triggerProjectWithDagSelection } from '../utils/triggerProjectDag';
import { resolveDeploymentForTrigger } from '../utils/projectDeployments';
import { pickDeploymentId } from '../utils/pickDeploymentModal';
import { getApiErrorMessage } from '../utils/apiError';
import dayjs from 'dayjs';

const { Title, Paragraph } = Typography;
const { Option } = Select;

const DeployedProjects = () => {
  const [projects, setProjects] = useState([]);
  const [deployments, setDeployments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedDeployment, setSelectedDeployment] = useState('all');
  const [searchParams] = useSearchParams();
  const [triggeringProjectId, setTriggeringProjectId] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchDeployments();
    fetchProjects();
  }, []);

  useEffect(() => {
    const deploymentId = searchParams.get('deploymentId');
    if (deploymentId) {
      setSelectedDeployment(deploymentId);
    }
  }, [searchParams]);

  const fetchProjects = async () => {
    try {
      setLoading(true);
      const response = await projectAPI.getAll();
      setProjects(response.data);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to fetch projects');
      if (msg) message.error(msg);
      console.error('Error fetching projects:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchDeployments = async () => {
    try {
      const response = await deploymentAPI.getAll();
      setDeployments(response.data);
    } catch (error) {
      console.error('Error fetching deployments:', error);
    }
  };

  const deployedList = useMemo(
    () =>
      projects.filter(
        (p) =>
          p.status === 'DEPLOYED' &&
          Array.isArray(p.linkedDeploymentIds) &&
          p.linkedDeploymentIds.length > 0
      ),
    [projects]
  );

  const tableData = useMemo(() => {
    if (selectedDeployment === 'all') return deployedList;
    return deployedList.filter((p) =>
      (p.linkedDeploymentIds || []).includes(selectedDeployment)
    );
  }, [deployedList, selectedDeployment]);

  const handleTriggerProject = async (record) => {
    const { projectId, name: projectName } = record;
    try {
      setTriggeringProjectId(projectId);
      let deploymentId =
        selectedDeployment !== 'all' ? selectedDeployment : null;
      if (deploymentId) {
        if (!(record.linkedDeploymentIds || []).includes(deploymentId)) {
          message.error('This project is not linked to the selected deployment.');
          return;
        }
      } else {
        const resolved = resolveDeploymentForTrigger(record, deployments);
        if (!resolved.ok) {
          message.warning('No linked deployment to trigger against.');
          return;
        }
        deploymentId = resolved.deploymentId;
        if (resolved.needsPicker) {
          deploymentId = await pickDeploymentId(`Trigger — ${projectName}`, resolved.options);
        }
      }
      await triggerProjectWithDagSelection({
        projectId,
        projectName,
        deploymentId,
        onAwaitingUserChoice: () => setTriggeringProjectId(null),
        onTriggerStart: () => setTriggeringProjectId(projectId),
      });
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to trigger DAG runs');
      if (msg) message.error(msg);
      console.error('Error triggering project:', error);
    } finally {
      setTriggeringProjectId(null);
    }
  };

  const getStatusColor = (status) => {
    const colors = {
      DRAFT: 'default',
      VALIDATING: 'processing',
      VALID: 'success',
      INVALID: 'error',
      DEPLOYING: 'processing',
      DEPLOYED: 'success',
      FAILED: 'error',
      UPDATING: 'processing',
      DELETING: 'warning',
      DELETED: 'default',
    };
    return colors[status] || 'default';
  };

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      width: 220,
    },
    {
      title: 'Deployments',
      key: 'linkedDeploymentIds',
      width: 220,
      render: (_, record) =>
        record.linkedDeploymentIds?.length
          ? record.linkedDeploymentIds.join(', ')
          : '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status) => <Tag color={getStatusColor(status)}>{status}</Tag>,
    },
    {
      title: 'DAGs',
      dataIndex: 'dagCount',
      key: 'dagCount',
      width: 72,
      render: (count) => <span>{count || 0}</span>,
    },
    {
      title: 'Last deployed',
      dataIndex: 'lastDeployedAt',
      key: 'lastDeployedAt',
      width: 160,
      render: (date) => (date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      fixed: 'right',
      width: 260,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="primary"
            icon={<CodeOutlined />}
            onClick={() => navigate(`/projects/${record.projectId}/editor`)}
            size="small"
          >
            Project Editor
          </Button>
          <Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/projects/${record.projectId}`)}
            size="small"
          >
            View
          </Button>
          <Button
            type="link"
            icon={<PlayCircleOutlined />}
            onClick={() => handleTriggerProject(record)}
            loading={triggeringProjectId === record.projectId}
            size="small"
            style={{ color: '#52c41a' }}
          >
            Trigger
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <div style={{ marginBottom: '16px' }}>
        <Title level={2}>
          <RocketOutlined /> Deployed projects
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Projects currently deployed to an Airflow environment. Assign or deploy projects from the{' '}
          <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => navigate('/projects')}>
            project browser
          </Button>
          .
        </Paragraph>
      </div>

      <div style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 280 }}
          placeholder="Filter by deployment"
          value={selectedDeployment}
          onChange={setSelectedDeployment}
        >
          <Option value="all">All deployments</Option>
          {deployments.map((deployment) => (
            <Option key={deployment.deploymentId} value={deployment.deploymentId}>
              {deployment.name}
            </Option>
          ))}
        </Select>
      </div>

      <Table
        columns={columns}
        dataSource={tableData}
        rowKey={(r) => r.id ?? r.projectId}
        loading={loading}
        scroll={{ x: 1100 }}
        locale={{
          emptyText: 'No deployed projects yet. Deploy a project from the project browser.',
        }}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showTotal: (total) => `${total} deployed project(s)`,
        }}
      />
    </div>
  );
};

export default DeployedProjects;
