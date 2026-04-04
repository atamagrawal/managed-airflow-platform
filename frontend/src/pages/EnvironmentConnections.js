import React, { useState, useEffect, useCallback } from 'react';
import {
  Alert,
  AutoComplete,
  Button,
  Card,
  Divider,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { CloudUploadOutlined } from '@ant-design/icons';
import { deploymentAPI, environmentAPI } from '../services/api';
import { getApiErrorMessage } from '../utils/apiError';

const { TextArea } = Input;
const { Text, Paragraph, Title } = Typography;

const SYNC_ALL = 'ALL';
const SYNC_SELECTED = 'SELECTED';

const CONN_TYPES = [
  { value: 'postgres', label: 'postgres' },
  { value: 'mysql', label: 'mysql' },
  { value: 'http', label: 'http' },
  { value: 'https', label: 'https' },
  { value: 'generic', label: 'generic' },
  { value: 'sqlite', label: 'sqlite' },
  { value: 'mssql', label: 'mssql' },
  { value: 'oracle', label: 'oracle' },
  { value: 'snowflake', label: 'snowflake' },
  { value: 'mongo', label: 'mongo' },
  { value: 'redis', label: 'redis' },
  { value: 's3', label: 's3' },
  { value: 'aws', label: 'aws' },
  { value: 'google_cloud_platform', label: 'google_cloud_platform' },
  { value: 'slack', label: 'slack' },
  { value: 'ssh', label: 'ssh' },
  { value: 'ftp', label: 'ftp' },
];

const EnvironmentConnections = () => {
  const [form] = Form.useForm();
  const syncScope = Form.useWatch('syncScope', form);
  const [deployments, setDeployments] = useState([]);
  const [loadingDeployments, setLoadingDeployments] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [results, setResults] = useState(null);

  const loadDeployments = useCallback(async () => {
    try {
      setLoadingDeployments(true);
      const { data } = await deploymentAPI.getAll();
      setDeployments(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error(error);
      const msg = getApiErrorMessage(error, 'Failed to load deployments');
      if (msg) message.error(msg);
    } finally {
      setLoadingDeployments(false);
    }
  }, []);

  useEffect(() => {
    loadDeployments();
  }, [loadDeployments]);

  const handleSync = async (values) => {
    try {
      setSyncing(true);
      setResults(null);
      const payload = {
        syncScope: values.syncScope,
        targetDeploymentIds:
          values.syncScope === SYNC_SELECTED ? values.targetDeploymentIds : undefined,
        connectionId: values.connectionId.trim(),
        connType: String(values.connType ?? '').trim(),
        description: values.description?.trim() || undefined,
        host: values.host?.trim() || undefined,
        login: values.login?.trim() || undefined,
        port: values.port != null ? values.port : undefined,
        schema: values.schema?.trim() || undefined,
        extra: values.extra?.trim() || undefined,
      };
      if (values.password) {
        payload.password = values.password;
      }
      const { data } = await environmentAPI.syncConnection(payload);
      setResults(data);
      if (data?.allSucceeded) {
        message.success('Connection saved and pushed to every selected deployment.');
      } else {
        message.warning('Finished with one or more failures. Review the results below.');
      }
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Push failed');
      if (msg) message.error(msg);
      console.error(error);
    } finally {
      setSyncing(false);
    }
  };

  const resultColumns = [
    { title: 'Deployment', dataIndex: 'deploymentId', key: 'deploymentId', width: 200 },
    { title: 'Name', dataIndex: 'deploymentName', key: 'deploymentName' },
    { title: 'Tenant', dataIndex: 'tenantId', key: 'tenantId', width: 160 },
    {
      title: 'Status',
      key: 'success',
      width: 110,
      render: (_, row) =>
        row.success ? <Tag color="success">OK</Tag> : <Tag color="error">Failed</Tag>,
    },
    { title: 'Message', dataIndex: 'message', key: 'message', ellipsis: true },
  ];

  return (
    <div>
      <Title level={4} style={{ marginTop: 0, marginBottom: 8 }}>
        Connections
      </Title>
      <Paragraph type="secondary" style={{ marginBottom: 20, maxWidth: 800 }}>
        Define or update an Airflow connection here, then push it to the deployments you choose—either every running
        deployment you can reach, or only the ones you select.
      </Paragraph>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 20, maxWidth: 900 }}
        message="How deployment targeting works"
        description={
          <Paragraph style={{ marginBottom: 0 }} type="secondary">
            <strong>All running deployments</strong> pushes the connection to each deployment you can access that is{' '}
            <Text code>RUNNING</Text> and has a stored API URL.{' '}
            <strong>Selected deployments</strong> limits the push to the deployments you pick in the list below. The
            control plane calls each Airflow instance using <Text code>airflow.api.username</Text> /{' '}
            <Text code>airflow.api.password</Text>.
          </Paragraph>
        }
      />

      <Card title="Connection" bordered style={{ maxWidth: 720 }}>
        <Form
          form={form}
          layout="vertical"
          initialValues={{ syncScope: SYNC_ALL }}
          onFinish={handleSync}
        >
          <Title level={5}>Link to deployments</Title>
          <Paragraph type="secondary" style={{ marginTop: -8, marginBottom: 12 }}>
            Choose whether this connection is created on every eligible running deployment or only on the deployments you
            select.
          </Paragraph>
          <Form.Item
            name="syncScope"
            rules={[{ required: true, message: 'Choose how to link deployments' }]}
          >
            <Radio.Group>
              <Space direction="vertical" size={8}>
                <Radio value={SYNC_ALL}>All running deployments I can access</Radio>
                <Radio value={SYNC_SELECTED}>Selected deployments…</Radio>
              </Space>
            </Radio.Group>
          </Form.Item>

          {syncScope === SYNC_SELECTED ? (
            <Form.Item
              name="targetDeploymentIds"
              label="Deployments"
              rules={[
                { required: true, type: 'array', min: 1, message: 'Select at least one deployment' },
              ]}
            >
              <Select
                mode="multiple"
                allowClear
                showSearch
                placeholder="Select one or more deployments"
                loading={loadingDeployments}
                optionFilterProp="label"
                options={deployments.map((d) => ({
                  value: d.deploymentId,
                  label: `${d.name} (${d.deploymentId})`,
                }))}
              />
            </Form.Item>
          ) : null}

          <Divider orientationMargin={0} />

          <Title level={5}>Connection details</Title>
          <Form.Item
            name="connectionId"
            label="Connection ID"
            rules={[{ required: true, message: 'Connection ID is required' }]}
          >
            <Input placeholder="e.g. warehouse_prod" autoComplete="off" />
          </Form.Item>

          <Form.Item
            name="connType"
            label="Connection type"
            rules={[{ required: true, message: 'Connection type is required' }]}
          >
            <AutoComplete
              options={CONN_TYPES}
              placeholder="e.g. postgres, snowflake, http"
              filterOption={(input, option) =>
                (option?.value ?? '').toLowerCase().includes((input ?? '').toLowerCase())
              }
            />
          </Form.Item>

          <Form.Item name="description" label="Description">
            <Input placeholder="Optional" />
          </Form.Item>

          <Form.Item name="host" label="Host">
            <Input placeholder="Optional" />
          </Form.Item>

          <Form.Item name="login" label="Login">
            <Input placeholder="Optional" autoComplete="off" />
          </Form.Item>

          <Form.Item name="password" label="Password">
            <Input.Password
              placeholder="Optional; omit on update to leave the current secret unchanged"
              autoComplete="new-password"
            />
          </Form.Item>

          <Form.Item name="port" label="Port">
            <InputNumber min={1} max={65535} placeholder="Optional" style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="schema" label="Schema">
            <Input placeholder="Optional" />
          </Form.Item>

          <Form.Item
            name="extra"
            label="Extra (JSON)"
            rules={[
              {
                validator: async (_, value) => {
                  if (!value || !String(value).trim()) return;
                  try {
                    JSON.parse(String(value).trim());
                  } catch {
                    throw new Error('Must be valid JSON');
                  }
                },
              },
            ]}
          >
            <TextArea rows={4} placeholder='Optional, e.g. {"region_name": "us-east-1"}' />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" icon={<CloudUploadOutlined />} loading={syncing}>
                Save & push to Airflow
              </Button>
              <Button onClick={() => form.resetFields()} disabled={syncing}>
                Reset
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {results?.results?.length ? (
        <div style={{ marginTop: 28 }}>
          <Title level={5} style={{ marginBottom: 12 }}>
            Push results
          </Title>
          <Table
            rowKey={(r) => r.deploymentId}
            size="small"
            pagination={false}
            columns={resultColumns}
            dataSource={results.results}
          />
        </div>
      ) : null}
    </div>
  );
};

export default EnvironmentConnections;
