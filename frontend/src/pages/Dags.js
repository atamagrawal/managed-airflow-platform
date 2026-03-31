import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Tag, Popconfirm, message, Typography, Select } from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined, RocketOutlined, EyeOutlined, PlayCircleOutlined, CodeOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { dagAPI, deploymentAPI } from '../services/api';
import dayjs from 'dayjs';

const { Title } = Typography;
const { Option } = Select;

const Dags = () => {
  const [dags, setDags] = useState([]);
  const [deployments, setDeployments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedDeployment, setSelectedDeployment] = useState('all');
  const navigate = useNavigate();

  useEffect(() => {
    fetchDeployments();
    fetchDags();
  }, []);

  useEffect(() => {
    if (selectedDeployment && selectedDeployment !== 'all') {
      fetchDagsByDeployment(selectedDeployment);
    } else {
      fetchDags();
    }
  }, [selectedDeployment]);

  const fetchDags = async () => {
    try {
      setLoading(true);
      const response = await dagAPI.getAll();
      setDags(response.data);
    } catch (error) {
      message.error('Failed to fetch DAGs');
      console.error('Error fetching DAGs:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchDagsByDeployment = async (deploymentId) => {
    try {
      setLoading(true);
      const response = await dagAPI.getByDeployment(deploymentId);
      setDags(response.data);
    } catch (error) {
      message.error('Failed to fetch DAGs');
      console.error('Error fetching DAGs:', error);
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

  const handleDeleteDag = async (dagId) => {
    try {
      await dagAPI.delete(dagId);
      message.success('DAG deleted successfully');
      if (selectedDeployment && selectedDeployment !== 'all') {
        fetchDagsByDeployment(selectedDeployment);
      } else {
        fetchDags();
      }
    } catch (error) {
      message.error('Failed to delete DAG');
      console.error('Error deleting DAG:', error);
    }
  };

  const handleDeployDag = async (dagId, dagName) => {
    try {
      await dagAPI.deploy(dagId);
      message.success(`DAG "${dagName}" deployed successfully`);
      if (selectedDeployment && selectedDeployment !== 'all') {
        fetchDagsByDeployment(selectedDeployment);
      } else {
        fetchDags();
      }
    } catch (error) {
      message.error('Failed to deploy DAG');
      console.error('Error deploying DAG:', error);
    }
  };

  const handleTriggerDag = async (dagId, dagName) => {
    try {
      const response = await dagAPI.trigger(dagId);
      if (response.data.success) {
        message.success(`DAG "${dagName}" triggered successfully`);
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
      DELETING: 'warning',
      DELETED: 'default',
    };
    return colors[status] || 'default';
  };

  const columns = [
    {
      title: 'DAG ID',
      dataIndex: 'dagId',
      key: 'dagId',
      width: 200,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      width: 200,
    },
    {
      title: 'Deployment',
      dataIndex: 'deploymentName',
      key: 'deploymentName',
      width: 150,
    },
    {
      title: 'File Name',
      dataIndex: 'fileName',
      key: 'fileName',
      width: 150,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status) => <Tag color={getStatusColor(status)}>{status}</Tag>,
    },
    {
      title: 'Owner',
      dataIndex: 'owner',
      key: 'owner',
      width: 120,
    },
    {
      title: 'Git Repository',
      dataIndex: 'gitRepository',
      key: 'gitRepository',
      width: 200,
      ellipsis: true,
    },
    {
      title: 'Git Branch',
      dataIndex: 'gitBranch',
      key: 'gitBranch',
      width: 120,
    },
    {
      title: 'Last Deployed',
      dataIndex: 'lastDeployedAt',
      key: 'lastDeployedAt',
      width: 160,
      render: (date) => (date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: 'Created At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (date) => dayjs(date).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: 'Actions',
      key: 'actions',
      fixed: 'right',
      width: 280,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/dags/${record.dagId}`)}
          >
            View
          </Button>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/dags/${record.dagId}/edit`)}
          >
            Edit
          </Button>
          {record.status === 'VALID' && (
            <Popconfirm
              title="Deploy this DAG to Airflow?"
              onConfirm={() => handleDeployDag(record.dagId, record.name)}
              okText="Yes"
              cancelText="No"
            >
              <Button type="link" size="small" icon={<RocketOutlined />}>
                Deploy
              </Button>
            </Popconfirm>
          )}
          {record.status === 'DEPLOYED' && (
            <Popconfirm
              title="Trigger this DAG to run now?"
              onConfirm={() => handleTriggerDag(record.dagId, record.name)}
              okText="Yes"
              cancelText="No"
            >
              <Button type="link" size="small" icon={<PlayCircleOutlined />} style={{ color: '#52c41a' }}>
                Run
              </Button>
            </Popconfirm>
          )}
          <Popconfirm
            title="Are you sure you want to delete this DAG?"
            onConfirm={() => handleDeleteDag(record.dagId)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16, alignItems: 'center' }}>
        <Title level={2}>DAGs</Title>
        <Space>
          <Select
            style={{ width: 300 }}
            placeholder="Filter by deployment"
            value={selectedDeployment}
            onChange={setSelectedDeployment}
          >
            <Option value="all">All Deployments</Option>
            {deployments.map((deployment) => (
              <Option key={deployment.deploymentId} value={deployment.deploymentId}>
                {deployment.name}
              </Option>
            ))}
          </Select>
          <Button icon={<CodeOutlined />} onClick={() => navigate('/dag-editor')}>
            DAG Editor
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/dags/create')}>
            Create DAG
          </Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={dags}
        loading={loading}
        rowKey="id"
        scroll={{ x: 1500 }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} DAGs`,
        }}
      />
    </div>
  );
};

export default Dags;
