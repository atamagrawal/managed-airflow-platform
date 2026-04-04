import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Space, Tag, Typography, Select, message, Tooltip } from 'antd';
import {
  PlayCircleOutlined,
  FolderOpenOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { deployedDagsAPI, deploymentAPI } from '../services/api';
import { triggerProjectDagFile } from '../utils/triggerProjectDag';
import { getApiErrorMessage } from '../utils/apiError';
import dayjs from 'dayjs';

const { Title } = Typography;
const { Option } = Select;

const Dags = () => {
  const [rows, setRows] = useState([]);
  const [deployments, setDeployments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedDeployment, setSelectedDeployment] = useState('all');
  const [triggeringKey, setTriggeringKey] = useState(null);
  const navigate = useNavigate();

  const fetchDeployments = useCallback(async () => {
    try {
      const response = await deploymentAPI.getAll();
      setDeployments(response.data);
    } catch (error) {
      console.error('Error fetching deployments:', error);
    }
  }, []);

  const fetchDags = useCallback(async () => {
    try {
      setLoading(true);
      const dep =
        selectedDeployment && selectedDeployment !== 'all' ? selectedDeployment : undefined;
      const response = await deployedDagsAPI.getAll(dep);
      setRows(response.data || []);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to fetch deployed DAGs');
      if (msg) message.error(msg);
      console.error('Error fetching deployed DAGs:', error);
    } finally {
      setLoading(false);
    }
  }, [selectedDeployment]);

  useEffect(() => {
    fetchDeployments();
  }, [fetchDeployments]);

  useEffect(() => {
    fetchDags();
  }, [fetchDags]);

  const handleTrigger = async (record) => {
    const key = `${record.deploymentId}-${record.projectId}-${record.fileId}`;
    try {
      setTriggeringKey(key);
      await triggerProjectDagFile(
        record.projectId,
        record.deploymentId,
        record.fileName,
        record.projectName
      );
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to trigger DAG');
      if (msg) message.error(msg);
      console.error('Trigger error:', error);
    } finally {
      setTriggeringKey(null);
    }
  };

  const columns = [
    {
      title: 'Airflow DAG ID',
      dataIndex: 'airflowDagId',
      key: 'airflowDagId',
      width: 200,
      ellipsis: true,
      render: (v) =>
        v ? (
          <div style={{ minWidth: 0, maxWidth: '100%', overflow: 'hidden' }}>
            <Tooltip title={v}>
              <Tag
                color="blue"
                style={{
                  margin: 0,
                  maxWidth: '100%',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  display: 'inline-block',
                  verticalAlign: 'middle',
                }}
              >
                {v}
              </Tag>
            </Tooltip>
          </div>
        ) : (
          <span style={{ color: '#999' }}>—</span>
        ),
    },
    {
      title: 'File',
      key: 'file',
      ellipsis: true,
      render: (_, record) => (
        <div style={{ minWidth: 0 }}>
          <div
            style={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={record.fileName}
          >
            {record.fileName}
          </div>
          <div
            style={{
              fontSize: 12,
              color: '#888',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={record.filePath}
          >
            {record.filePath}
          </div>
        </div>
      ),
    },
    {
      title: 'Project',
      key: 'project',
      ellipsis: true,
      render: (_, record) => (
        <Button
          type="link"
          style={{
            padding: 0,
            height: 'auto',
            maxWidth: '100%',
            display: 'block',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            textAlign: 'left',
          }}
          title={record.projectName}
          onClick={() => navigate(`/projects/${record.projectId}`)}
        >
          {record.projectName}
        </Button>
      ),
    },
    {
      title: 'Deployment',
      key: 'deployment',
      width: 220,
      ellipsis: true,
      render: (_, record) => (
        <div style={{ minWidth: 0 }}>
          <div
            style={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={record.deploymentName || record.deploymentId}
          >
            {record.deploymentName || record.deploymentId}
          </div>
          <div
            style={{
              fontSize: 12,
              color: '#888',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={record.deploymentId}
          >
            {record.deploymentId}
          </div>
        </div>
      ),
    },
    {
      title: 'Last deployed',
      dataIndex: 'lastDeployedAt',
      key: 'lastDeployedAt',
      width: 180,
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_, record) => {
        const key = `${record.deploymentId}-${record.projectId}-${record.fileId}`;
        return (
          <Space>
            <Button
              type="primary"
              size="small"
              icon={<PlayCircleOutlined />}
              loading={triggeringKey === key}
              onClick={() => handleTrigger(record)}
            >
              Trigger
            </Button>
            <Button
              size="small"
              icon={<FolderOpenOutlined />}
              onClick={() => navigate(`/projects/${record.projectId}`)}
            >
              Project
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginBottom: 16,
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <Title level={2} style={{ margin: 0 }}>
          DAGs
        </Title>
        <Space wrap>
          <Select
            style={{ width: 300 }}
            placeholder="Filter by deployment"
            value={selectedDeployment}
            onChange={setSelectedDeployment}
          >
            <Option value="all">All deployments</Option>
            {deployments.map((d) => (
              <Option key={d.deploymentId} value={d.deploymentId}>
                {d.name} ({d.deploymentId})
              </Option>
            ))}
          </Select>
          <Button icon={<ReloadOutlined />} onClick={fetchDags}>
            Refresh
          </Button>
        </Space>
      </div>

      <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
        DAG files from projects that have been successfully deployed to an Airflow deployment. Deploy or redeploy a
        project to appear here.
      </Typography.Paragraph>

      <Table
        tableLayout="fixed"
        columns={columns}
        dataSource={rows}
        loading={loading}
        rowKey={(r) => `${r.deploymentId}-${r.projectId}-${r.fileId}`}
        scroll={{ x: 1100 }}
        pagination={{
          pageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} DAG file(s)`,
        }}
      />
    </div>
  );
};

export default Dags;
