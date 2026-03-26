import React, { useState, useEffect } from 'react';
import { Modal, Form, Select, message } from 'antd';
import { deploymentAPI } from '../../services/api';

const { Option } = Select;

const DeployModal = ({ visible, onCancel, onSubmit, dagName }) => {
  const [form] = Form.useForm();
  const [deployments, setDeployments] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (visible) {
      fetchDeployments();
    }
  }, [visible]);

  const fetchDeployments = async () => {
    try {
      const response = await deploymentAPI.getAll();
      setDeployments(response.data.filter(d => d.status === 'RUNNING'));
    } catch (error) {
      message.error('Failed to fetch deployments');
      console.error('Error fetching deployments:', error);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      await onSubmit(values.deploymentId);
      form.resetFields();
    } catch (error) {
      console.error('Validation failed:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  return (
    <Modal
      title={`Deploy DAG: ${dagName || 'Untitled'}`}
      open={visible}
      onOk={handleSubmit}
      onCancel={handleCancel}
      confirmLoading={loading}
      width={500}
      okText="Deploy"
    >
      <Form
        form={form}
        layout="vertical"
      >
        <Form.Item
          name="deploymentId"
          label="Deployment"
          rules={[{ required: true, message: 'Please select a deployment' }]}
          extra="Select the Airflow deployment where this DAG will be deployed"
        >
          <Select
            placeholder="Select deployment"
            showSearch
            filterOption={(input, option) =>
              option.children.toLowerCase().includes(input.toLowerCase())
            }
            autoFocus
          >
            {deployments.map((deployment) => (
              <Option key={deployment.deploymentId} value={deployment.deploymentId}>
                {deployment.name} ({deployment.deploymentId})
              </Option>
            ))}
          </Select>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default DeployModal;
