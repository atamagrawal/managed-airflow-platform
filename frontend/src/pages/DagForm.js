import React, { useState, useEffect, useCallback } from 'react';
import { Form, Input, Select, Button, Card, message, Space, Typography, Alert, Tag } from 'antd';
import { SaveOutlined, ArrowLeftOutlined, RocketOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { dagAPI, deploymentAPI } from '../services/api';

const { Title, Text } = Typography;
const { Option } = Select;
const { TextArea } = Input;

const DagForm = () => {
  const { dagId } = useParams();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [deployments, setDeployments] = useState([]);
  const [dagCode, setDagCode] = useState('');
  const [validationErrors, setValidationErrors] = useState(null);
  const [dagStatus, setDagStatus] = useState(null);
  const isEditMode = !!dagId;

  const fetchDeployments = useCallback(async () => {
    try {
      const response = await deploymentAPI.getAll();
      setDeployments(response.data.filter(d => d.status === 'RUNNING'));
    } catch (error) {
      message.error('Failed to fetch deployments');
      console.error('Error fetching deployments:', error);
    }
  }, []);

  const fetchDag = useCallback(async () => {
    try {
      setLoading(true);
      const response = await dagAPI.getById(dagId);
      const dag = response.data;

      form.setFieldsValue({
        deploymentId: dag.deploymentId,
        name: dag.name,
        description: dag.description,
        fileName: dag.fileName,
        gitRepository: dag.gitRepository,
        gitBranch: dag.gitBranch,
        gitPath: dag.gitPath,
        owner: dag.owner,
        tags: dag.tags,
        isPaused: dag.isPaused,
      });

      setDagCode(dag.dagCode);
      setValidationErrors(dag.validationErrors);
      setDagStatus(dag.status);
    } catch (error) {
      message.error('Failed to fetch DAG');
      console.error('Error fetching DAG:', error);
    } finally {
      setLoading(false);
    }
  }, [dagId, form]);

  useEffect(() => {
    fetchDeployments();
    if (dagId) {
      fetchDag();
    } else {
      // Set default DAG code template for new DAGs
      setDagCode(getDefaultDagTemplate());
    }
  }, [dagId, fetchDeployments, fetchDag]);

  const handleSubmit = async (values) => {
    try {
      setLoading(true);
      const payload = {
        ...values,
        dagCode,
      };

      if (isEditMode) {
        await dagAPI.update(dagId, payload);
        message.success('DAG updated successfully');
      } else {
        const response = await dagAPI.create(payload);
        message.success('DAG created successfully');

        // Navigate to the newly created DAG's edit page
        const newDagId = response.data.dagId;
        navigate(`/dags/${newDagId}`, { replace: true });
      }
    } catch (error) {
      message.error(`Failed to ${isEditMode ? 'update' : 'create'} DAG`);
      console.error(`Error ${isEditMode ? 'updating' : 'creating'} DAG:`, error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeploy = async () => {
    try {
      setLoading(true);
      await dagAPI.deploy(dagId);
      message.success('DAG deployed successfully');
      fetchDag(); // Refresh to get updated status
    } catch (error) {
      message.error('Failed to deploy DAG');
      console.error('Error deploying DAG:', error);
    } finally {
      setLoading(false);
    }
  };

  const getDefaultDagTemplate = () => {
    return `from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.bash import BashOperator

# Default arguments for the DAG
default_args = {
    'owner': 'airflow',
    'depends_on_past': False,
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

# Define the DAG
dag = DAG(
    'my_sample_dag',
    default_args=default_args,
    description='A simple sample DAG',
    catchup=False,
    tags=['example'],
)

def print_hello():
    print('Hello from Airflow!')
    return 'Hello task completed'

# Define tasks
hello_task = PythonOperator(
    task_id='hello_task',
    python_callable=print_hello,
    dag=dag,
)

bash_task = BashOperator(
    task_id='bash_task',
    bash_command='echo "Running bash task"',
    dag=dag,
)

# Set task dependencies
hello_task >> bash_task
`;
  };

  const getStatusColor = (status) => {
    const colors = {
      DRAFT: 'default',
      VALID: 'success',
      INVALID: 'error',
      DEPLOYED: 'success',
      FAILED: 'error',
    };
    return colors[status] || 'default';
  };

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/dags')}>
              Back to DAGs
            </Button>
            <Title level={2} style={{ margin: 0 }}>
              {isEditMode ? 'Edit DAG' : 'Create New DAG'}
            </Title>
            {isEditMode && dagStatus && (
              <Tag color={getStatusColor(dagStatus)}>{dagStatus}</Tag>
            )}
          </Space>
          {isEditMode && dagStatus === 'VALID' && (
            <Button
              type="primary"
              icon={<RocketOutlined />}
              onClick={handleDeploy}
              loading={loading}
            >
              Deploy to Airflow
            </Button>
          )}
        </Space>
      </div>

      {validationErrors && (
        <Alert
          message="Validation Errors"
          description={validationErrors}
          type="error"
          closable
          style={{ marginBottom: 16 }}
        />
      )}

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            isPaused: false,
            gitBranch: 'main',
          }}
        >
          <Form.Item
            name="deploymentId"
            label="Deployment"
            rules={[{ required: true, message: 'Please select a deployment' }]}
            extra="Select the Airflow deployment where this DAG will be deployed"
          >
            <Select
              placeholder="Select deployment"
              disabled={isEditMode}
              showSearch
              filterOption={(input, option) =>
                option.children.toLowerCase().includes(input.toLowerCase())
              }
            >
              {deployments.map((deployment) => (
                <Option key={deployment.deploymentId} value={deployment.deploymentId}>
                  {deployment.name} ({deployment.deploymentId})
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="name"
            label="DAG Name"
            rules={[{ required: true, message: 'Please enter DAG name' }]}
            extra="Human-readable name for this DAG"
          >
            <Input placeholder="e.g., My ETL Pipeline" />
          </Form.Item>

          <Form.Item
            name="description"
            label="Description"
            extra="Brief description of what this DAG does"
          >
            <TextArea rows={2} placeholder="Enter DAG description" />
          </Form.Item>

          <Form.Item
            name="fileName"
            label="File Name"
            rules={[
              { required: true, message: 'Please enter file name' },
              { pattern: /^[\w-]+\.py$/, message: 'File name must end with .py' },
            ]}
            extra="Python file name (must end with .py)"
          >
            <Input placeholder="e.g., my_etl_dag.py" />
          </Form.Item>

          <Form.Item
            label="DAG Code"
            required
            extra="Write your Airflow DAG Python code here"
          >
            <div style={{ border: '1px solid #d9d9d9', borderRadius: '2px' }}>
              <Editor
                height="500px"
                defaultLanguage="python"
                theme="vs-dark"
                value={dagCode}
                onChange={(value) => setDagCode(value || '')}
                options={{
                  minimap: { enabled: true },
                  scrollBeyondLastLine: false,
                  fontSize: 14,
                  lineNumbers: 'on',
                  automaticLayout: true,
                }}
              />
            </div>
          </Form.Item>

          <Title level={4}>Git Configuration (Optional)</Title>
          <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
            Configure Git repository settings for version control and deployment
          </Text>

          <Form.Item
            name="gitRepository"
            label="Git Repository URL"
            extra="URL of the Git repository (e.g., https://github.com/user/repo.git)"
          >
            <Input placeholder="https://github.com/user/repo.git" />
          </Form.Item>

          <Form.Item
            name="gitBranch"
            label="Git Branch"
            extra="Branch name to sync from"
          >
            <Input placeholder="main" />
          </Form.Item>

          <Form.Item
            name="gitPath"
            label="Git Path"
            extra="Path within the repository where the DAG file is located"
          >
            <Input placeholder="dags/" />
          </Form.Item>

          <Title level={4}>Additional Metadata</Title>

          <Form.Item
            name="owner"
            label="Owner"
            extra="Owner or team responsible for this DAG"
          >
            <Input placeholder="e.g., data-engineering-team" />
          </Form.Item>

          <Form.Item
            name="tags"
            label="Tags"
            extra="Comma-separated tags for categorization"
          >
            <Input placeholder="e.g., etl, production, daily" />
          </Form.Item>

          <Form.Item
            name="isPaused"
            label="Initial State"
            extra="Whether the DAG should be paused when first deployed"
          >
            <Select>
              <Option value={false}>Active (Unpaused)</Option>
              <Option value={true}>Paused</Option>
            </Select>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={loading}>
                {isEditMode ? 'Update DAG' : 'Create DAG'}
              </Button>
              <Button onClick={() => navigate('/dags')}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default DagForm;
