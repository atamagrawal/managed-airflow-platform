import React, { useEffect, useState } from 'react';
import { Modal, Form, Input, Select, Button, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';

const { TextArea } = Input;
const { Option } = Select;

const NewDagModal = ({ visible, onCancel, onSubmit, initialValues, projects, onManageProjects }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!visible) return;
    if (!initialValues) return;
    form.setFieldsValue(initialValues);
  }, [visible, initialValues, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      console.log('[NewDagModal] Form values:', values);

      setLoading(true);
      await onSubmit(values);
      form.resetFields();
    } catch (error) {
      console.error('[NewDagModal] Validation failed:', error);

      // Check if no projects exist
      if (error.errorFields?.some(f => f.name[0] === 'projectId')) {
        if (!projects || projects.length === 0) {
          message.warning('Please create a project first before saving');
        }
      }
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
      title="Save DAG File"
      open={visible}
      onOk={handleSubmit}
      onCancel={handleCancel}
      confirmLoading={loading}
      width={500}
      okText="Save"
      cancelText="Cancel"
    >
      <Form
        form={form}
        layout="vertical"
      >
        <Form.Item
          name="projectId"
          label="Project"
          rules={[{ required: true, message: 'Please select a project' }]}
          extra={
            <span>
              {projects && projects.length === 0 ? (
                <span style={{ color: '#ff4d4f' }}>
                  No projects yet.{' '}
                  <Button
                    type="link"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={onManageProjects}
                    style={{ padding: 0, height: 'auto', color: '#ff4d4f' }}
                  >
                    Create your first project
                  </Button>
                </span>
              ) : (
                <span>
                  Organize your DAG files by project.{' '}
                  <Button
                    type="link"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={onManageProjects}
                    style={{ padding: 0, height: 'auto' }}
                  >
                    Manage Projects
                  </Button>
                </span>
              )}
            </span>
          }
        >
          <Select
            placeholder={projects && projects.length === 0 ? "Create a project first" : "Select a project"}
            showSearch
            filterOption={(input, option) =>
              option.children.toLowerCase().includes(input.toLowerCase())
            }
            disabled={!projects || projects.length === 0}
          >
            {projects?.map((project) => (
              <Option key={project.projectId} value={project.projectId}>
                {project.name}
              </Option>
            ))}
          </Select>
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
          name="name"
          label="DAG Name"
          rules={[{ required: true, message: 'Please enter DAG name' }]}
          extra="Human-readable name for this DAG"
        >
          <Input placeholder="e.g., My ETL Pipeline" />
        </Form.Item>

        <Form.Item
          name="description"
          label="Description (Optional)"
        >
          <TextArea rows={2} placeholder="Brief description of what this DAG does" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default NewDagModal;
