import React, { useState, useEffect, useMemo } from 'react';
import { Table, Button, Space, Tag, message, Input, Modal, Empty, Dropdown } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  RocketOutlined,
  PlayCircleOutlined,
  EyeOutlined,
  FolderOpenOutlined,
  ReloadOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { projectAPI, deploymentAPI } from '../services/api';
import ProjectForm from '../components/ProjectForm';
import { triggerProjectWithDagSelection } from '../utils/triggerProjectDag';
import { resolveDeploymentForDeploy, resolveDeploymentForTrigger } from '../utils/projectDeployments';
import { pickDeploymentId } from '../utils/pickDeploymentModal';
import { getApiErrorMessage } from '../utils/apiError';
import { useAuth } from '../context/AuthContext';
import PageHeader from '../components/PageHeader';
import { BRAND } from '../brand';
import dayjs from 'dayjs';

const { Search } = Input;

const Projects = () => {
  const { isAdmin } = useAuth();
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

  const tenantColumn = isAdmin
    ? [
        {
          title: 'Tenant',
          dataIndex: 'tenantId',
          key: 'tenantId',
          width: 160,
          ellipsis: true,
        },
      ]
    : [];

  const columns = [
    ...tenantColumn,
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
      width: 72,
      align: 'center',
      render: (_, record) => {
        const items = [
          {
            key: 'view',
            label: 'View details',
            icon: <EyeOutlined />,
            onClick: () => navigate(`/projects/${record.projectId}`),
          },
          {
            key: 'edit',
            label: 'Edit',
            icon: <EditOutlined />,
            onClick: () => showEditModal(record),
          },
          {
            key: 'deploy',
            label: 'Deploy to environment',
            icon: <RocketOutlined />,
            disabled: record.status === 'DEPLOYING',
            onClick: () => handleDeployProject(record.projectId, record.name, record),
          },
        ];
        if (record.linkedDeploymentIds?.length > 0) {
          items.push({
            key: 'trigger',
            label: 'Trigger DAGs',
            icon: <PlayCircleOutlined />,
            disabled: triggeringProjectId === record.projectId,
            onClick: () => handleTriggerProject(record.projectId, record.name, record),
          });
        }
        items.push({ type: 'divider' });
        items.push({
          key: 'delete',
          label: 'Delete project',
          icon: <DeleteOutlined />,
          danger: true,
          onClick: () => {
            Modal.confirm({
              title: 'Delete this project?',
              okText: 'Delete',
              okType: 'danger',
              cancelText: 'Cancel',
              onOk: () => handleDeleteProject(record.projectId),
            });
          },
        });
        return (
          <span onClick={(e) => e.stopPropagation()} role="presentation">
            <Dropdown menu={{ items }} trigger={['click']} placement="bottomRight">
              <Button type="text" icon={<MoreOutlined style={{ fontSize: 18 }} />} aria-label="More actions" />
            </Dropdown>
          </span>
        );
      },
    },
  ];

  return (
    <div>
      <PageHeader
        title={
          <span>
            <FolderOpenOutlined style={{ marginRight: 10 }} />
            {BRAND.navProjects}
          </span>
        }
        description={
          <span>
            Click a row to open the project in {BRAND.ideName}. Use the ⋯ menu for metadata, deploy, and more. See{' '}
            <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={() => navigate('/deployed-projects')}>
              deployed projects
            </Button>{' '}
            for what is live on each environment.
          </span>
        }
        extra={
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={fetchProjects} loading={loading}>
              Refresh
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={showCreateModal}>
              Create project
            </Button>
          </Space>
        }
      />

      <div style={{ marginBottom: 16 }}>
        <Search
          placeholder="Search by name or description"
          allowClear
          style={{ width: 360, maxWidth: '100%' }}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
      </div>

      <Table
        columns={columns}
        dataSource={filteredProjects}
        rowKey={(r) => r.id ?? r.projectId}
        loading={loading}
        scroll={{ x: 1400 }}
        onRow={(record) => ({
          onClick: () => navigate(`/projects/${record.projectId}/editor`),
          style: { cursor: 'pointer' },
        })}
        locale={{
          emptyText: (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No projects match your search (or none exist yet).">
              <Button type="primary" icon={<PlusOutlined />} onClick={showCreateModal}>
                Create project
              </Button>
            </Empty>
          ),
        }}
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
