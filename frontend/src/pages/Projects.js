import React, { useState, useEffect, useMemo } from 'react';
import { Table, Button, Space, Tag, Popconfirm, message, Typography, Input, Modal } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  RocketOutlined,
  PlayCircleOutlined,
  EyeOutlined,
  FolderOpenOutlined,
  CodeOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { projectAPI, deploymentAPI } from '../services/api';
import ProjectForm from '../components/ProjectForm';
import { triggerProjectWithDagSelection } from '../utils/triggerProjectDag';
import { resolveDeploymentForDeploy, resolveDeploymentForTrigger } from '../utils/projectDeployments';
import { pickDeploymentId } from '../utils/pickDeploymentModal';
import { getApiErrorMessage } from '../utils/apiError';
import dayjs from 'dayjs';

const { Title, Paragraph } = Typography;
const { Search } = Input;

const Projects = () => {
  const [projects, setProjects] = useState([]);
  const [deployments, setDeployments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [editingProject, setEditingProject] = useState(null);
  const [triggeringProjectId, setTriggeringProjectId] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchDeployments();
    fetchProjects();
  }, []);

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

  const filteredProjects = useMemo(() => {
    const q = searchText.trim().toLowerCase();
    if (!q) return projects;
    return projects.filter((p) => {
      const name = (p.name || '').toLowerCase();
      const desc = (p.description || '').toLowerCase();
      return name.includes(q) || desc.includes(q);
    });
  }, [projects, searchText]);

  const handleDeleteProject = async (projectId) => {
    try {
      await projectAPI.delete(projectId);
      message.success('Project deleted successfully');
      fetchProjects();
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to delete project');
      if (msg) message.error(msg);
      console.error('Error deleting project:', error);
    }
  };

  const handleDeployProject = async (projectId, projectName, record) => {
    try {
      const resolved = resolveDeploymentForDeploy(record, deployments);
      if (!resolved.ok) {
        message.error('No deployments available. Create an Airflow deployment first.');
        return;
      }
      let deploymentId = resolved.deploymentId;
      if (resolved.needsPicker) {
        deploymentId = await pickDeploymentId(`Deploy — ${projectName}`, resolved.options);
      }
      await projectAPI.deploy(projectId, deploymentId);
      message.success(`Project "${projectName}" deployed successfully`);
      fetchProjects();
    } catch (error) {
      if (error?.message === 'no deployments') return;
      const msg = getApiErrorMessage(error, 'Failed to deploy project');
      if (msg) message.error(msg);
      console.error('Error deploying project:', error);
    }
  };

  const handleTriggerProject = async (projectId, projectName, record) => {
    try {
      const resolved = resolveDeploymentForTrigger(record, deployments);
      if (!resolved.ok) {
        message.warning('Deploy this project to a deployment before triggering DAGs.');
        return;
      }
      let deploymentId = resolved.deploymentId;
      if (resolved.needsPicker) {
        deploymentId = await pickDeploymentId(`Trigger DAGs — ${projectName}`, resolved.options);
      }
      setTriggeringProjectId(projectId);
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

  const showCreateModal = () => {
    setEditingProject(null);
    setIsModalVisible(true);
  };

  const showEditModal = (project) => {
    setEditingProject(project);
    setIsModalVisible(true);
  };

  const handleModalCancel = () => {
    setIsModalVisible(false);
    setEditingProject(null);
  };

  const handleFormSubmit = () => {
    setIsModalVisible(false);
    setEditingProject(null);
    fetchProjects();
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
      width: 250,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status) => <Tag color={getStatusColor(status)}>{status}</Tag>,
    },
    {
      title: 'Airflow version',
      dataIndex: 'airflowVersion',
      key: 'airflowVersion',
      width: 120,
    },
    {
      title: 'DAGs',
      dataIndex: 'dagCount',
      key: 'dagCount',
      width: 80,
      render: (count) => <span>{count || 0}</span>,
    },
    {
      title: 'Plugins',
      dataIndex: 'pluginCount',
      key: 'pluginCount',
      width: 80,
      render: (count) => <span>{count || 0}</span>,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (date) => dayjs(date).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: 'Last deployed',
      dataIndex: 'lastDeployedAt',
      key: 'lastDeployedAt',
      width: 150,
      render: (date) => (date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      fixed: 'right',
      width: 320,
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
            icon={<EditOutlined />}
            onClick={() => showEditModal(record)}
            size="small"
          >
            Edit
          </Button>
          <Button
            type="link"
            icon={<RocketOutlined />}
            onClick={() => handleDeployProject(record.projectId, record.name, record)}
            disabled={record.status === 'DEPLOYING'}
            size="small"
          >
            Deploy
          </Button>
          {record.linkedDeploymentIds?.length > 0 && (
            <Button
              type="link"
              icon={<PlayCircleOutlined />}
              onClick={() => handleTriggerProject(record.projectId, record.name, record)}
              loading={triggeringProjectId === record.projectId}
              size="small"
              style={{ color: '#52c41a' }}
            >
              Trigger
            </Button>
          )}
          <Popconfirm
            title="Are you sure you want to delete this project?"
            onConfirm={() => handleDeleteProject(record.projectId)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" danger icon={<DeleteOutlined />} size="small">
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <div style={{ marginBottom: '16px' }}>
        <Title level={2}>
          <FolderOpenOutlined /> Project browser
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 12 }}>
          Search and manage projects. Deployment targeting is chosen when you deploy; see{' '}
          <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => navigate('/deployed-projects')}>
            deployed projects
          </Button>{' '}
          for what is live on each environment.
        </Paragraph>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <Search
            placeholder="Search by name or description"
            allowClear
            style={{ width: 360, maxWidth: '100%' }}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={showCreateModal}>
            Create project
          </Button>
        </div>
      </div>

      <Table
        columns={columns}
        dataSource={filteredProjects}
        rowKey={(r) => r.id ?? r.projectId}
        loading={loading}
        scroll={{ x: 1400 }}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} project(s)`,
        }}
      />

      <Modal
        title={editingProject ? 'Edit project' : 'Create project'}
        open={isModalVisible}
        onCancel={handleModalCancel}
        footer={null}
        width={800}
      >
        <ProjectForm
          project={editingProject}
          deployments={deployments}
          onSuccess={handleFormSubmit}
          onCancel={handleModalCancel}
        />
      </Modal>
    </div>
  );
};

export default Projects;
