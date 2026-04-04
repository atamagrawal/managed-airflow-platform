import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Card, Descriptions, Button, Space, Tag, Tabs, Table, Modal, Form, Input, Select, message, Breadcrumb } from 'antd';
import { getBreadcrumbItems } from '../utils/breadcrumbs';
import { ArrowLeftOutlined, EditOutlined, RocketOutlined, PlayCircleOutlined, PlusOutlined, FolderOutlined, CodeOutlined } from '@ant-design/icons';
import { projectAPI, deploymentAPI } from '../services/api';
import { triggerProjectWithDagSelection } from '../utils/triggerProjectDag';
import { resolveDeploymentForDeploy, resolveDeploymentForTrigger } from '../utils/projectDeployments';
import { pickDeploymentId } from '../utils/pickDeploymentModal';
import { getApiErrorMessage } from '../utils/apiError';
import dayjs from 'dayjs';

const { TabPane } = Tabs;
const { TextArea } = Input;
const { Option } = Select;

const ProjectDetails = () => {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [project, setProject] = useState(null);
  const [files, setFiles] = useState([]);
  const [deployments, setDeployments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isFileModalVisible, setIsFileModalVisible] = useState(false);
  const [triggerLoading, setTriggerLoading] = useState(false);
  const [form] = Form.useForm();

  const fetchDeployments = useCallback(async () => {
    try {
      const response = await deploymentAPI.getAll();
      setDeployments(response.data);
    } catch (error) {
      console.error('Error fetching deployments:', error);
    }
  }, []);

  const fetchProjectDetails = useCallback(async () => {
    try {
      setLoading(true);
      const response = await projectAPI.getById(projectId);
      setProject(response.data);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to fetch project details');
      if (msg) message.error(msg);
      console.error('Error fetching project:', error);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  const fetchProjectFiles = useCallback(async () => {
    try {
      const response = await projectAPI.getFiles(projectId);
      setFiles(response.data);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to fetch project files');
      if (msg) message.error(msg);
      console.error('Error fetching files:', error);
    }
  }, [projectId]);

  useEffect(() => {
    fetchProjectDetails();
    fetchProjectFiles();
    fetchDeployments();
  }, [fetchProjectDetails, fetchProjectFiles, fetchDeployments]);

  const handleDeploy = async () => {
    try {
      const resolved = resolveDeploymentForDeploy(project, deployments);
      if (!resolved.ok) {
        message.error('No deployments available. Create an Airflow deployment first.');
        return;
      }
      let deploymentId = resolved.deploymentId;
      if (resolved.needsPicker) {
        deploymentId = await pickDeploymentId(`Deploy — ${project?.name}`, resolved.options);
      }
      await projectAPI.deploy(projectId, deploymentId);
      message.success('Project deployed successfully');
      fetchProjectDetails();
    } catch (error) {
      if (error?.message === 'no deployments') return;
      const msg = getApiErrorMessage(error, 'Failed to deploy project');
      if (msg) message.error(msg);
      console.error('Error deploying project:', error);
    }
  };

  const handleTrigger = async () => {
    try {
      const resolved = resolveDeploymentForTrigger(project, deployments);
      if (!resolved.ok) {
        message.warning('Deploy this project to a deployment before triggering DAGs.');
        return;
      }
      let deploymentId = resolved.deploymentId;
      if (resolved.needsPicker) {
        deploymentId = await pickDeploymentId(`Trigger DAGs — ${project?.name}`, resolved.options);
      }
      setTriggerLoading(true);
      await triggerProjectWithDagSelection({
        projectId,
        projectName: project?.name,
        deploymentId,
        files,
        onAwaitingUserChoice: () => setTriggerLoading(false),
        onTriggerStart: () => setTriggerLoading(true),
      });
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to trigger DAG runs');
      if (msg) message.error(msg);
      console.error('Error triggering project:', error);
    } finally {
      setTriggerLoading(false);
    }
  };

  const showAddFileModal = () => {
    form.resetFields();
    setIsFileModalVisible(true);
  };

  const handleAddFile = async (values) => {
    try {
      await projectAPI.addFile(projectId, values);
      message.success('File added successfully');
      setIsFileModalVisible(false);
      fetchProjectFiles();
      fetchProjectDetails(); // Refresh to update DAG/plugin counts
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to add file');
      if (msg) message.error(msg);
      console.error('Error adding file:', error);
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
    };
    return colors[status] || 'default';
  };

  const fileColumns = [
    {
      title: 'File Path',
      dataIndex: 'filePath',
      key: 'filePath',
    },
    {
      title: 'File Name',
      dataIndex: 'fileName',
      key: 'fileName',
    },
    {
      title: 'Type',
      dataIndex: 'fileType',
      key: 'fileType',
      render: (type) => <Tag>{type}</Tag>,
    },
    {
      title: 'Size',
      dataIndex: 'fileSize',
      key: 'fileSize',
      render: (size) => `${(size / 1024).toFixed(2)} KB`,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date) => dayjs(date).format('YYYY-MM-DD HH:mm'),
    },
  ];

  if (!project) {
    return <div>Loading...</div>;
  }

  return (
    <div>
      <Breadcrumb items={getBreadcrumbItems(location.pathname)} style={{ marginBottom: 12 }} />
      <Space style={{ marginBottom: 16 }} wrap>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/projects')}>
          Back to projects
        </Button>
        <Button
          type="primary"
          icon={<CodeOutlined />}
          onClick={() => navigate(`/projects/${projectId}/editor`)}
        >
          Open in Project Editor
        </Button>
        <Button
          icon={<RocketOutlined />}
          onClick={handleDeploy}
          disabled={project.status === 'DEPLOYING'}
        >
          Deploy
        </Button>
        {project.linkedDeploymentIds?.length > 0 && (
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={handleTrigger}
            loading={triggerLoading}
          >
            Trigger DAGs
          </Button>
        )}
        <Button icon={<EditOutlined />} onClick={() => navigate(`/projects/${projectId}/editor`)}>
          Edit in editor
        </Button>
      </Space>

      <Card title={<><FolderOutlined /> {project.name}</>} loading={loading}>
        <Descriptions bordered column={2}>
          <Descriptions.Item label="Linked deployments">
            {(project.linkedDeploymentIds && project.linkedDeploymentIds.length > 0)
              ? project.linkedDeploymentIds.join(', ')
              : 'None (deploy to link)'}
          </Descriptions.Item>
          <Descriptions.Item label="Status">
            <Tag color={getStatusColor(project.status)}>{project.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Airflow Version">{project.airflowVersion}</Descriptions.Item>
          <Descriptions.Item label="Owner">{project.owner || '-'}</Descriptions.Item>
          <Descriptions.Item label="DAG Count">{project.dagCount || 0}</Descriptions.Item>
          <Descriptions.Item label="Plugin Count">{project.pluginCount || 0}</Descriptions.Item>
          <Descriptions.Item label="Created">{dayjs(project.createdAt).format('YYYY-MM-DD HH:mm')}</Descriptions.Item>
          <Descriptions.Item label="Last Deployed">
            {project.lastDeployedAt ? dayjs(project.lastDeployedAt).format('YYYY-MM-DD HH:mm') : 'Never'}
          </Descriptions.Item>
          <Descriptions.Item label="Tags" span={2}>{project.tags || '-'}</Descriptions.Item>
          <Descriptions.Item label="Description" span={2}>{project.description || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card style={{ marginTop: 16 }}>
        <Tabs defaultActiveKey="files">
          <TabPane tab="Files" key="files">
            <Space style={{ marginBottom: 16 }}>
              <Button type="primary" icon={<PlusOutlined />} onClick={showAddFileModal}>
                Add File
              </Button>
            </Space>
            <Table
              columns={fileColumns}
              dataSource={files}
              rowKey="id"
              pagination={{ pageSize: 10 }}
            />
          </TabPane>

          <TabPane tab="Configuration" key="config">
            <Descriptions bordered column={1}>
              <Descriptions.Item label="requirements.txt">
                <pre style={{ whiteSpace: 'pre-wrap' }}>{project.requirementsTxt || 'Not configured'}</pre>
              </Descriptions.Item>
              <Descriptions.Item label="packages.txt">
                <pre style={{ whiteSpace: 'pre-wrap' }}>{project.packagesTxt || 'Not configured'}</pre>
              </Descriptions.Item>
              <Descriptions.Item label="Dockerfile">
                <pre style={{ whiteSpace: 'pre-wrap' }}>{project.dockerfile || 'Not configured'}</pre>
              </Descriptions.Item>
              <Descriptions.Item label="airflow_settings.yaml">
                <pre style={{ whiteSpace: 'pre-wrap' }}>{project.airflowSettingsYaml || 'Not configured'}</pre>
              </Descriptions.Item>
              <Descriptions.Item label=".airflowignore">
                <pre style={{ whiteSpace: 'pre-wrap' }}>{project.airflowIgnore || 'Not configured'}</pre>
              </Descriptions.Item>
              <Descriptions.Item label=".env">
                <pre style={{ whiteSpace: 'pre-wrap' }}>{project.envFile || 'Not configured'}</pre>
              </Descriptions.Item>
            </Descriptions>
          </TabPane>

          <TabPane tab="Git Integration" key="git">
            <Descriptions bordered>
              <Descriptions.Item label="Repository">{project.gitRepository || '-'}</Descriptions.Item>
              <Descriptions.Item label="Branch">{project.gitBranch || '-'}</Descriptions.Item>
              <Descriptions.Item label="Commit Hash">{project.gitCommitHash || '-'}</Descriptions.Item>
            </Descriptions>
          </TabPane>
        </Tabs>
      </Card>

      <Modal
        title="Add File to Project"
        open={isFileModalVisible}
        onCancel={() => setIsFileModalVisible(false)}
        footer={null}
      >
        <Form form={form} layout="vertical" onFinish={handleAddFile}>
          <Form.Item
            label="File Path"
            name="filePath"
            rules={[{ required: true, message: 'Please enter file path' }]}
            tooltip="e.g., dags/my_dag.py, contracts/my_dataset.yaml, or plugins/my_plugin.py"
          >
            <Input placeholder="dags/my_dag.py" />
          </Form.Item>

          <Form.Item
            label="File Name"
            name="fileName"
            rules={[{ required: true, message: 'Please enter file name' }]}
          >
            <Input placeholder="my_dag.py" />
          </Form.Item>

          <Form.Item
            label="File Type"
            name="fileType"
            rules={[{ required: true, message: 'Please select file type' }]}
          >
            <Select placeholder="Select file type">
              <Option value="DAG">DAG</Option>
              <Option value="CONTRACT">Data contract (YAML)</Option>
              <Option value="PLUGIN">Plugin</Option>
              <Option value="INCLUDE">Include</Option>
              <Option value="TEST">Test</Option>
              <Option value="UTIL">Utility</Option>
              <Option value="OTHER">Other</Option>
            </Select>
          </Form.Item>

          <Form.Item
            label="Content"
            name="content"
            rules={[{ required: true, message: 'Please enter file content' }]}
          >
            <TextArea rows={10} placeholder="Enter file content" />
          </Form.Item>

          <Form.Item label="Description" name="description">
            <TextArea rows={3} placeholder="Optional description" />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Add File
              </Button>
              <Button onClick={() => setIsFileModalVisible(false)}>
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ProjectDetails;
