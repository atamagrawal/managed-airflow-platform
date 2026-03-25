import React, { useState, useEffect } from 'react';
import { Card, Descriptions, Button, Space, Tag, message, Typography, Spin } from 'antd';
import { ArrowLeftOutlined, EditOutlined, RocketOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { dagAPI } from '../services/api';
import dayjs from 'dayjs';

const { Title } = Typography;

const DagDetails = () => {
  const { dagId } = useParams();
  const navigate = useNavigate();
  const [dag, setDag] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchDag();
  }, [dagId]);

  const fetchDag = async () => {
    try {
      setLoading(true);
      const response = await dagAPI.getById(dagId);
      setDag(response.data);
    } catch (error) {
      message.error('Failed to fetch DAG details');
      console.error('Error fetching DAG:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeploy = async () => {
    try {
      await dagAPI.deploy(dagId);
      message.success('DAG deployed successfully');
      fetchDag();
    } catch (error) {
      message.error('Failed to deploy DAG');
      console.error('Error deploying DAG:', error);
    }
  };

  const handleTrigger = async () => {
    try {
      const response = await dagAPI.trigger(dagId);
      if (response.data.success) {
        message.success('DAG run triggered successfully');
      } else {
        message.error(response.data.message || 'Failed to trigger DAG');
      }
    } catch (error) {
      message.error('Failed to trigger DAG run');
      console.error('Error triggering DAG:', error);
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

  if (loading || !dag) {
    return (
      <div style={{ textAlign: 'center', padding: '100px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/dags')}>
              Back to DAGs
            </Button>
            <Title level={2} style={{ margin: 0 }}>
              DAG Details
            </Title>
            <Tag color={getStatusColor(dag.status)}>{dag.status}</Tag>
          </Space>
          <Space>
            <Button icon={<EditOutlined />} onClick={() => navigate(`/dags/${dagId}/edit`)}>
              Edit
            </Button>
            {dag.status === 'VALID' && (
              <Button type="primary" icon={<RocketOutlined />} onClick={handleDeploy}>
                Deploy
              </Button>
            )}
            {dag.status === 'DEPLOYED' && (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={handleTrigger}
                style={{ background: '#52c41a', borderColor: '#52c41a' }}
              >
                Run DAG
              </Button>
            )}
          </Space>
        </Space>
      </div>

      <Card title="Basic Information" style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2}>
          <Descriptions.Item label="DAG ID">{dag.dagId}</Descriptions.Item>
          <Descriptions.Item label="Name">{dag.name}</Descriptions.Item>
          <Descriptions.Item label="Deployment">{dag.deploymentName}</Descriptions.Item>
          <Descriptions.Item label="File Name">{dag.fileName}</Descriptions.Item>
          <Descriptions.Item label="Status">
            <Tag color={getStatusColor(dag.status)}>{dag.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Owner">{dag.owner || '-'}</Descriptions.Item>
          <Descriptions.Item label="Tags">{dag.tags || '-'}</Descriptions.Item>
          <Descriptions.Item label="Paused">{dag.isPaused ? 'Yes' : 'No'}</Descriptions.Item>
          <Descriptions.Item label="Description" span={2}>
            {dag.description || '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {dag.gitRepository && (
        <Card title="Git Configuration" style={{ marginBottom: 16 }}>
          <Descriptions bordered column={2}>
            <Descriptions.Item label="Repository" span={2}>
              {dag.gitRepository}
            </Descriptions.Item>
            <Descriptions.Item label="Branch">{dag.gitBranch || '-'}</Descriptions.Item>
            <Descriptions.Item label="Path">{dag.gitPath || '-'}</Descriptions.Item>
            <Descriptions.Item label="Commit Hash" span={2}>
              {dag.gitCommitHash || '-'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      <Card title="Timestamps" style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2}>
          <Descriptions.Item label="Created At">
            {dayjs(dag.createdAt).format('YYYY-MM-DD HH:mm:ss')}
          </Descriptions.Item>
          <Descriptions.Item label="Updated At">
            {dayjs(dag.updatedAt).format('YYYY-MM-DD HH:mm:ss')}
          </Descriptions.Item>
          <Descriptions.Item label="Last Deployed">
            {dag.lastDeployedAt ? dayjs(dag.lastDeployedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Last Synced">
            {dag.lastSyncedAt ? dayjs(dag.lastSyncedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {dag.validationErrors && (
        <Card title="Validation Errors" style={{ marginBottom: 16 }}>
          <div style={{ color: '#ff4d4f' }}>{dag.validationErrors}</div>
        </Card>
      )}

      <Card title="DAG Code">
        <div style={{ border: '1px solid #d9d9d9', borderRadius: '2px' }}>
          <Editor
            height="600px"
            defaultLanguage="python"
            theme="vs-dark"
            value={dag.dagCode}
            options={{
              readOnly: true,
              minimap: { enabled: true },
              scrollBeyondLastLine: false,
              fontSize: 14,
              lineNumbers: 'on',
            }}
          />
        </div>
      </Card>
    </div>
  );
};

export default DagDetails;
