import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Descriptions, Button, Tag, Spin, Alert, Typography, Space } from 'antd';
import { ArrowLeftOutlined, LinkOutlined } from '@ant-design/icons';
import { deploymentAPI } from '../services/api';
import dayjs from 'dayjs';

const { Title } = Typography;

const DeploymentDetails = () => {
  const { deploymentId } = useParams();
  const navigate = useNavigate();
  const [deployment, setDeployment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchDeploymentDetails();
  }, [deploymentId]);

  const fetchDeploymentDetails = async () => {
    try {
      setLoading(true);
      const response = await deploymentAPI.getById(deploymentId);
      setDeployment(response.data);
      setError(null);
    } catch (err) {
      console.error('Error fetching deployment details:', err);
      setError('Failed to load deployment details');
    } finally {
      setLoading(false);
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

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '50px' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error) {
    return <Alert message="Error" description={error} type="error" showIcon />;
  }

  if (!deployment) {
    return <Alert message="Deployment not found" type="warning" showIcon />;
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/deployments')}>
          Back
        </Button>
      </Space>

      <Title level={2}>Deployment Details</Title>

      <Card title="Basic Information" style={{ marginBottom: 16 }}>
        <Descriptions column={2} bordered>
          <Descriptions.Item label="Deployment ID">{deployment.deploymentId}</Descriptions.Item>
          <Descriptions.Item label="Name">{deployment.name || deployment.deploymentId}</Descriptions.Item>
          <Descriptions.Item label="Tenant ID">{deployment.tenantId}</Descriptions.Item>
          <Descriptions.Item label="Status">
            <Tag color={getStatusColor(deployment.status)}>{deployment.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Airflow Version">{deployment.airflowVersion}</Descriptions.Item>
          <Descriptions.Item label="Executor Type">{deployment.executorType}</Descriptions.Item>
          {deployment.namespace && (
            <Descriptions.Item label="Namespace (Kubernetes)">
              {deployment.namespace}
            </Descriptions.Item>
          )}
          {deployment.helmReleaseName && (
            <Descriptions.Item label="Helm Release (Kubernetes)">
              {deployment.helmReleaseName}
            </Descriptions.Item>
          )}
          <Descriptions.Item label="Description" span={2}>
            {deployment.description || 'N/A'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Resource Configuration" style={{ marginBottom: 16 }}>
        <Descriptions column={2} bordered>
          <Descriptions.Item label="Min Workers">{deployment.minWorkers}</Descriptions.Item>
          <Descriptions.Item label="Max Workers">{deployment.maxWorkers}</Descriptions.Item>
          <Descriptions.Item label="Scheduler CPU">
            {deployment.schedulerCpu} {deployment.schedulerCpu && !deployment.schedulerCpu.includes('m') ? 'millicores' : ''}
          </Descriptions.Item>
          <Descriptions.Item label="Scheduler Memory">
            {deployment.schedulerMemory} {deployment.schedulerMemory && !deployment.schedulerMemory.includes('Gi') && !deployment.schedulerMemory.includes('Mi') ? 'MB' : ''}
          </Descriptions.Item>
          <Descriptions.Item label="Worker CPU">
            {deployment.workerCpu} {deployment.workerCpu && !deployment.workerCpu.includes('m') ? 'millicores' : ''}
          </Descriptions.Item>
          <Descriptions.Item label="Worker Memory">
            {deployment.workerMemory} {deployment.workerMemory && !deployment.workerMemory.includes('Gi') && !deployment.workerMemory.includes('Mi') ? 'MB' : ''}
          </Descriptions.Item>
          <Descriptions.Item label="Webserver CPU">
            {deployment.webserverCpu} {deployment.webserverCpu && !deployment.webserverCpu.includes('m') ? 'millicores' : ''}
          </Descriptions.Item>
          <Descriptions.Item label="Webserver Memory">
            {deployment.webserverMemory} {deployment.webserverMemory && !deployment.webserverMemory.includes('Gi') && !deployment.webserverMemory.includes('Mi') ? 'MB' : ''}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Network Configuration" style={{ marginBottom: 16 }}>
        <Descriptions column={1} bordered>
          <Descriptions.Item label="Webserver URL">
            {deployment.webserverUrl ? (
              <Space>
                <a href={deployment.webserverUrl} target="_blank" rel="noopener noreferrer">
                  {deployment.webserverUrl}
                </a>
                <Button
                  type="link"
                  icon={<LinkOutlined />}
                  onClick={() => window.open(deployment.webserverUrl, '_blank')}
                >
                  Open
                </Button>
              </Space>
            ) : (
              'N/A'
            )}
          </Descriptions.Item>
          <Descriptions.Item label="Ingress Host">
            {deployment.ingressHost || 'N/A'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Timestamps">
        <Descriptions column={2} bordered>
          <Descriptions.Item label="Created At">
            {dayjs(deployment.createdAt).format('YYYY-MM-DD HH:mm:ss')}
          </Descriptions.Item>
          <Descriptions.Item label="Updated At">
            {dayjs(deployment.updatedAt).format('YYYY-MM-DD HH:mm:ss')}
          </Descriptions.Item>
          <Descriptions.Item label="Deployed At">
            {deployment.deployedAt
              ? dayjs(deployment.deployedAt).format('YYYY-MM-DD HH:mm:ss')
              : 'N/A'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </div>
  );
};

export default DeploymentDetails;
