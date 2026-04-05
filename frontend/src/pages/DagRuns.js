import React, { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Button,
  Space,
  Select,
  message,
  Tag,
  Input,
  Alert,
  Typography,
} from 'antd';
import { ReloadOutlined, CloudSyncOutlined } from '@ant-design/icons';
import { dagInsightsAPI, deploymentAPI } from '../services/api';
import { getApiErrorMessage } from '../utils/apiError';
import PageHeader from '../components/PageHeader';
import dayjs from 'dayjs';

const { Option } = Select;
const { Text } = Typography;

const fmtTime = (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—');

const DagRuns = () => {
  const [deployments, setDeployments] = useState([]);
  const [selectedDeployment, setSelectedDeployment] = useState('all');
  const [dagIdFilter, setDagIdFilter] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [syncStatuses, setSyncStatuses] = useState([]);
  const [syncing, setSyncing] = useState(false);

  const fetchDeployments = useCallback(async () => {
    try {
      const response = await deploymentAPI.getAll();
      setDeployments(response.data || []);
    } catch (error) {
      console.error('Error fetching deployments:', error);
    }
  }, []);

  const fetchSyncStatus = useCallback(async () => {
    try {
      const dep =
        selectedDeployment && selectedDeployment !== 'all' ? selectedDeployment : undefined;
      const { data } = await dagInsightsAPI.syncStatus(
        dep ? { deploymentId: dep } : {}
      );
      setSyncStatuses(data || []);
    } catch (error) {
      console.error('sync-status', error);
    }
  }, [selectedDeployment]);

  const fetchRuns = useCallback(async () => {
    try {
      setLoading(true);
      const dep =
        selectedDeployment && selectedDeployment !== 'all' ? selectedDeployment : undefined;
      const params = { page, size };
      if (dep) params.deploymentId = dep;
      if (dagIdFilter.trim()) params.dagId = dagIdFilter.trim();
      const { data } = await dagInsightsAPI.listRuns(params);
      setRows(data?.content || []);
      setTotal(data?.totalElements ?? 0);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to load cached DAG runs');
      if (msg) message.error(msg);
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [selectedDeployment, dagIdFilter, page, size]);

  useEffect(() => {
    fetchDeployments();
  }, [fetchDeployments]);

  useEffect(() => {
    setPage(0);
  }, [selectedDeployment, dagIdFilter]);

  useEffect(() => {
    fetchRuns();
    fetchSyncStatus();
  }, [fetchRuns, fetchSyncStatus]);

  const handleSync = async () => {
    try {
      setSyncing(true);
      await dagInsightsAPI.sync(
        selectedDeployment && selectedDeployment !== 'all' ? selectedDeployment : undefined
      );
      message.success('Sync queued. Data updates after Airflow responds.');
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to queue sync');
      if (msg) message.error(msg);
    } finally {
      setSyncing(false);
    }
  };

  const columns = [
    {
      title: 'Deployment',
      key: 'deployment',
      width: 200,
      ellipsis: true,
      render: (_, r) => (
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 500 }}>{r.deploymentName || r.deploymentId}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {r.deploymentId}
          </Text>
        </div>
      ),
    },
    { title: 'DAG', dataIndex: 'dagId', key: 'dagId', width: 180, ellipsis: true },
    { title: 'Run ID', dataIndex: 'dagRunId', key: 'dagRunId', width: 200, ellipsis: true },
    {
      title: 'State',
      dataIndex: 'state',
      key: 'state',
      width: 100,
      render: (s) => {
        const color =
          s === 'success'
            ? 'green'
            : s === 'failed'
              ? 'red'
              : s === 'running'
                ? 'processing'
                : 'default';
        return s ? <Tag color={color}>{s}</Tag> : '—';
      },
    },
    { title: 'Logical date', dataIndex: 'logicalDate', key: 'logicalDate', width: 170, render: fmtTime },
    { title: 'Start', dataIndex: 'startDate', key: 'startDate', width: 170, render: fmtTime },
    { title: 'End', dataIndex: 'endDate', key: 'endDate', width: 170, render: fmtTime },
    { title: 'Run type', dataIndex: 'runType', key: 'runType', width: 100 },
    { title: 'Cached at', dataIndex: 'syncedAt', key: 'syncedAt', width: 170, render: fmtTime },
  ];

  const statusSummary =
    syncStatuses.length === 1 ? (
      <Alert
        type={syncStatuses[0].lastSyncSuccess === false ? 'warning' : 'info'}
        showIcon
        message={
          <Space direction="vertical" size={0}>
            <span>
              Last sync: {fmtTime(syncStatuses[0].lastSyncCompletedAt)}
              {syncStatuses[0].lastSyncSuccess === true && ' · OK'}
              {syncStatuses[0].lastSyncSuccess === false && ' · Failed'}
            </span>
            {syncStatuses[0].lastErrorMessage && (
              <Text type="danger" style={{ fontSize: 12 }}>
                {syncStatuses[0].lastErrorMessage}
              </Text>
            )}
          </Space>
        }
        style={{ marginBottom: 16 }}
      />
    ) : syncStatuses.length > 1 ? (
      <Alert
        type="info"
        showIcon
        message={`${syncStatuses.length} deployment(s) in scope — open sync details below.`}
        style={{ marginBottom: 16 }}
      />
    ) : null;

  return (
    <div>
      <PageHeader
        title="DAG runs"
        description="Recent DAG runs cached from each deployment's Airflow API. Use sync to refresh; the control plane also refreshes on a schedule."
        extra={
          <Space wrap>
            <Select
              style={{ width: 280, maxWidth: '100%' }}
              placeholder="Deployment"
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
            <Input
              placeholder="Filter by DAG id"
              value={dagIdFilter}
              onChange={(e) => setDagIdFilter(e.target.value)}
              style={{ width: 200 }}
              allowClear
            />
            <Button icon={<ReloadOutlined />} onClick={() => fetchRuns()} loading={loading}>
              Reload
            </Button>
            <Button
              type="primary"
              icon={<CloudSyncOutlined />}
              onClick={handleSync}
              loading={syncing}
            >
              Sync from Airflow
            </Button>
          </Space>
        }
      />

      {statusSummary}

      {syncStatuses.length > 1 && (
        <Table
          size="small"
          style={{ marginBottom: 16 }}
          pagination={false}
          rowKey={(r) => r.deploymentId}
          dataSource={syncStatuses}
          columns={[
            { title: 'Deployment', dataIndex: 'deploymentName', key: 'n' },
            { title: 'Last sync', dataIndex: 'lastSyncCompletedAt', key: 't', render: fmtTime },
            {
              title: 'Status',
              key: 's',
              render: (_, r) =>
                r.lastSyncSuccess === true ? (
                  <Tag color="green">OK</Tag>
                ) : r.lastSyncSuccess === false ? (
                  <Tag color="red">Failed</Tag>
                ) : (
                  <Tag>—</Tag>
                ),
            },
            {
              title: 'Error',
              dataIndex: 'lastErrorMessage',
              key: 'e',
              ellipsis: true,
              render: (t) => t || '—',
            },
          ]}
        />
      )}

      <Table
        rowKey={(r) => `${r.deploymentId}-${r.dagId}-${r.dagRunId}`}
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1200 }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} run(s)`,
          onChange: (p, ps) => {
            setPage(p - 1);
            setSize(ps);
          },
        }}
      />
    </div>
  );
};

export default DagRuns;
