import React, { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Button,
  Space,
  Select,
  message,
  Tag,
  Tabs,
  Switch,
  Typography,
  Alert,
} from 'antd';
import { ReloadOutlined, CloudSyncOutlined } from '@ant-design/icons';
import { dagInsightsAPI, deploymentAPI } from '../services/api';
import { getApiErrorMessage } from '../utils/apiError';
import PageHeader from '../components/PageHeader';
import dayjs from 'dayjs';

const { Option } = Select;
const { Text, Paragraph } = Typography;

const fmtTime = (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—');

const DagDebug = () => {
  const [deployments, setDeployments] = useState([]);
  const [selectedDeployment, setSelectedDeployment] = useState('all');
  const [errorsOnly, setErrorsOnly] = useState(false);
  const [pageMeta, setPageMeta] = useState(0);
  const [sizeMeta, setSizeMeta] = useState(20);
  const [totalMeta, setTotalMeta] = useState(0);
  const [metaRows, setMetaRows] = useState([]);
  const [pageImp, setPageImp] = useState(0);
  const [sizeImp, setSizeImp] = useState(20);
  const [totalImp, setTotalImp] = useState(0);
  const [impRows, setImpRows] = useState([]);
  const [loadingMeta, setLoadingMeta] = useState(false);
  const [loadingImp, setLoadingImp] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [activeTab, setActiveTab] = useState('dags');

  const fetchDeployments = useCallback(async () => {
    try {
      const response = await deploymentAPI.getAll();
      setDeployments(response.data || []);
    } catch (error) {
      console.error('Error fetching deployments:', error);
    }
  }, []);

  const depParam =
    selectedDeployment && selectedDeployment !== 'all' ? selectedDeployment : undefined;

  const fetchMeta = useCallback(async () => {
    try {
      setLoadingMeta(true);
      const params = { page: pageMeta, size: sizeMeta, errorsOnly: errorsOnly || undefined };
      if (depParam) params.deploymentId = depParam;
      const { data } = await dagInsightsAPI.listDebug(params);
      setMetaRows(data?.content || []);
      setTotalMeta(data?.totalElements ?? 0);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to load DAG debug metadata');
      if (msg) message.error(msg);
      console.error(error);
    } finally {
      setLoadingMeta(false);
    }
  }, [depParam, errorsOnly, pageMeta, sizeMeta]);

  const fetchImp = useCallback(async () => {
    try {
      setLoadingImp(true);
      const params = { page: pageImp, size: sizeImp };
      if (depParam) params.deploymentId = depParam;
      const { data } = await dagInsightsAPI.listImportErrors(params);
      setImpRows(data?.content || []);
      setTotalImp(data?.totalElements ?? 0);
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to load import errors');
      if (msg) message.error(msg);
      console.error(error);
    } finally {
      setLoadingImp(false);
    }
  }, [depParam, pageImp, sizeImp]);

  useEffect(() => {
    fetchDeployments();
  }, [fetchDeployments]);

  useEffect(() => {
    setPageMeta(0);
    setPageImp(0);
  }, [selectedDeployment, errorsOnly]);

  useEffect(() => {
    fetchMeta();
  }, [fetchMeta]);

  useEffect(() => {
    fetchImp();
  }, [fetchImp]);

  const handleSync = async () => {
    try {
      setSyncing(true);
      await dagInsightsAPI.sync(depParam);
      message.success('Sync queued. Refresh after a few seconds.');
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to queue sync');
      if (msg) message.error(msg);
    } finally {
      setSyncing(false);
    }
  };

  const dagColumns = [
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
    { title: 'DAG id', dataIndex: 'dagId', key: 'dagId', width: 180, ellipsis: true },
    {
      title: 'Paused',
      dataIndex: 'paused',
      key: 'paused',
      width: 90,
      render: (p) => (p ? <Tag>Yes</Tag> : <Tag color="green">No</Tag>),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 90,
      render: (a) =>
        a === true ? (
          <Tag color="green">Yes</Tag>
        ) : a === false ? (
          <Tag color="orange">No</Tag>
        ) : (
          '—'
        ),
    },
    {
      title: 'Import error',
      dataIndex: 'importError',
      key: 'importError',
      width: 110,
      render: (e) =>
        e ? <Tag color="red">Yes</Tag> : <Tag color="green">No</Tag>,
    },
    { title: 'File', dataIndex: 'fileloc', key: 'fileloc', ellipsis: true },
    { title: 'Owners', dataIndex: 'owners', key: 'owners', width: 120, ellipsis: true },
    { title: 'Cached at', dataIndex: 'syncedAt', key: 'syncedAt', width: 170, render: fmtTime },
    {
      title: 'Error detail',
      dataIndex: 'importErrorStackTrace',
      key: 'importErrorStackTrace',
      width: 280,
      ellipsis: true,
      render: (t) =>
        t ? (
          <Paragraph ellipsis={{ rows: 2, expandable: true, symbol: 'more' }} style={{ marginBottom: 0 }}>
            {t}
          </Paragraph>
        ) : (
          '—'
        ),
    },
  ];

  const impColumns = [
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
    { title: 'File', dataIndex: 'filename', key: 'filename', ellipsis: true },
    {
      title: 'Source time',
      dataIndex: 'sourceTimestamp',
      key: 'sourceTimestamp',
      width: 170,
      render: fmtTime,
    },
    { title: 'Cached at', dataIndex: 'syncedAt', key: 'syncedAt', width: 170, render: fmtTime },
    {
      title: 'Stack trace',
      dataIndex: 'stackTrace',
      key: 'stackTrace',
      ellipsis: true,
      render: (t) =>
        t ? (
          <Paragraph ellipsis={{ rows: 2, expandable: true, symbol: 'more' }} style={{ marginBottom: 0 }}>
            {t}
          </Paragraph>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="DAG debug"
        description="Pause/active flags, file locations, and import errors cached from Airflow — similar in spirit to managed-platform DAG overview pages."
        extra={
          <Space wrap>
            <Select
              style={{ width: 280, maxWidth: '100%' }}
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
            <Button icon={<ReloadOutlined />} onClick={() => { fetchMeta(); fetchImp(); }} loading={loadingMeta || loadingImp}>
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

      <Alert
        type="info"
        showIcon
        message="Import errors appear both on the DAGs tab (matched to a DAG file path) and in raw form on the Import errors tab."
        style={{ marginBottom: 16 }}
      />

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'dags',
            label: 'DAGs',
            children: (
              <div>
                <Space style={{ marginBottom: 12 }}>
                  <Text>Errors only</Text>
                  <Switch checked={errorsOnly} onChange={setErrorsOnly} />
                </Space>
                <Table
                  rowKey={(r) => `${r.deploymentId}-${r.dagId}`}
                  loading={loadingMeta}
                  columns={dagColumns}
                  dataSource={metaRows}
                  scroll={{ x: 1400 }}
                  pagination={{
                    current: pageMeta + 1,
                    pageSize: sizeMeta,
                    total: totalMeta,
                    showSizeChanger: true,
                    showTotal: (t) => `Total ${t} DAG(s)`,
                    onChange: (p, ps) => {
                      setPageMeta(p - 1);
                      setSizeMeta(ps);
                    },
                  }}
                />
              </div>
            ),
          },
          {
            key: 'import',
            label: 'Import errors',
            children: (
              <Table
                rowKey={(r) => `${r.deploymentId}-${r.filename}`}
                loading={loadingImp}
                columns={impColumns}
                dataSource={impRows}
                scroll={{ x: 1200 }}
                pagination={{
                  current: pageImp + 1,
                  pageSize: sizeImp,
                  total: totalImp,
                  showSizeChanger: true,
                  showTotal: (t) => `Total ${t} file(s)`,
                  onChange: (p, ps) => {
                    setPageImp(p - 1);
                    setSizeImp(ps);
                  },
                }}
              />
            ),
          },
        ]}
      />
    </div>
  );
};

export default DagDebug;
