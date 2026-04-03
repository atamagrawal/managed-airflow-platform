import React, { useState, useEffect } from 'react';
import { Form, Input, Button, message, Tabs, Space, Select } from 'antd';
import { projectAPI } from '../services/api';
import { DEFAULT_AIRFLOW_VERSION, getAirflowVersionSelectOptions } from '../constants/airflowVersions';

const { TextArea } = Input;
const { TabPane } = Tabs;

const ProjectForm = ({ project, deployments, onSuccess, onCancel }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (project) {
      form.setFieldsValue({
        name: project.name,
        description: project.description,
        deploymentId: project.deploymentId,
        airflowVersion: project.airflowVersion,
        requirementsTxt: project.requirementsTxt,
        packagesTxt: project.packagesTxt,
        dockerfile: project.dockerfile,
        airflowSettingsYaml: project.airflowSettingsYaml,
        airflowIgnore: project.airflowIgnore,
        envFile: project.envFile,
        gitRepository: project.gitRepository,
        gitBranch: project.gitBranch,
        owner: project.owner,
        tags: project.tags,
      });
    } else {
      // Set default values for new project (Dockerfile left empty so the API applies server default FROM,
      // including deployment.compose.airflow-image when configured)
      form.setFieldsValue({
        airflowVersion: DEFAULT_AIRFLOW_VERSION,
        requirementsTxt: '# Default dependency used by sample DAG\nrequests==2.32.3',
        airflowIgnore: '# Ignore Python cache files\n__pycache__/\n*.py[cod]\n*$py.class\n\n# Ignore virtual environment\nvenv/\nenv/\n\n# Ignore IDE files\n.vscode/\n.idea/\n\n# Ignore test files\ntests/',
      });
    }
  }, [project, form]);

  const buildCreatePayload = (values) => {
    const payload = { ...values };
    const df = payload.dockerfile;
    if (df === undefined || df === null || String(df).trim() === '') {
      delete payload.dockerfile;
    }
    return payload;
  };

  const handleSubmit = async (values) => {
    try {
      setLoading(true);
      if (project) {
        await projectAPI.update(project.projectId, values);
        message.success('Project updated successfully');
      } else {
        await projectAPI.create(buildCreatePayload(values));
        message.success('Project created successfully');
      }
      onSuccess();
    } catch (error) {
      message.error(project ? 'Failed to update project' : 'Failed to create project');
      console.error('Error saving project:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={handleSubmit}
    >
      <Tabs defaultActiveKey="basic">
        <TabPane tab="Basic Info" key="basic">
          <Form.Item
            label="Project Name"
            name="name"
            rules={[{ required: true, message: 'Please enter project name' }]}
          >
            <Input placeholder="my-airflow-project" />
          </Form.Item>

          <Form.Item
            label="Description"
            name="description"
          >
            <TextArea rows={3} placeholder="Project description" />
          </Form.Item>

          <Form.Item
            label="Airflow Version"
            name="airflowVersion"
            rules={[{ required: true, message: 'Please select Airflow version' }]}
            tooltip="Versions offered here are what the platform supports today; more will appear over time."
          >
            <Select
              placeholder="Select Airflow version"
              options={getAirflowVersionSelectOptions(project?.airflowVersion)}
            />
          </Form.Item>

          <Form.Item
            label="Owner"
            name="owner"
          >
            <Input placeholder="Owner name" />
          </Form.Item>

          <Form.Item
            label="Tags"
            name="tags"
          >
            <Input placeholder="Comma-separated tags" />
          </Form.Item>
        </TabPane>

        <TabPane tab="Configuration Files" key="config">
          <Form.Item
            label="requirements.txt"
            name="requirementsTxt"
            tooltip="Python package dependencies"
          >
            <TextArea rows={6} placeholder="# Python dependencies" />
          </Form.Item>

          <Form.Item
            label="packages.txt"
            name="packagesTxt"
            tooltip="OS-level package dependencies"
          >
            <TextArea rows={4} placeholder="# OS-level packages" />
          </Form.Item>

          <Form.Item
            label="Dockerfile"
            name="dockerfile"
            tooltip="Leave empty to use the control plane default FROM (apache/airflow:version, or deployment.compose.airflow-image when set)"
          >
            <TextArea rows={8} placeholder="Leave empty for platform default base image, or set FROM …" />
          </Form.Item>

          <Form.Item
            label="airflow_settings.yaml"
            name="airflowSettingsYaml"
            tooltip="Airflow connections, variables, and pools configuration"
          >
            <TextArea rows={6} placeholder="# Airflow settings in YAML format" />
          </Form.Item>

          <Form.Item
            label=".airflowignore"
            name="airflowIgnore"
            tooltip="Files and directories to ignore"
          >
            <TextArea rows={6} placeholder="# Files to ignore" />
          </Form.Item>

          <Form.Item
            label=".env"
            name="envFile"
            tooltip="Local environment variables for project runtime"
          >
            <TextArea rows={4} placeholder="# KEY=value" />
          </Form.Item>

        </TabPane>

        <TabPane tab="Git Integration" key="git">
          <Form.Item
            label="Git Repository"
            name="gitRepository"
          >
            <Input placeholder="https://github.com/user/repo.git" />
          </Form.Item>

          <Form.Item
            label="Git Branch"
            name="gitBranch"
          >
            <Input placeholder="main" />
          </Form.Item>
        </TabPane>
      </Tabs>

      <Form.Item style={{ marginTop: 16, marginBottom: 0 }}>
        <Space>
          <Button type="primary" htmlType="submit" loading={loading}>
            {project ? 'Update' : 'Create'}
          </Button>
          <Button onClick={onCancel}>
            Cancel
          </Button>
        </Space>
      </Form.Item>
    </Form>
  );
};

export default ProjectForm;
