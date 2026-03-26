import React, { useEffect, useState } from 'react';
import { Modal, Form, Input, List, Button, Space, Popconfirm, message } from 'antd';
import { FolderOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';

const { TextArea } = Input;

const ProjectModal = ({ visible, onCancel, projects, onCreateProject, onDeleteProject }) => {
  const [form] = Form.useForm();
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!visible) {
      form.resetFields();
      setCreating(false);
    }
  }, [visible, form]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();

      // Check if project name already exists
      if (projects.some(p => p.name.toLowerCase() === values.name.toLowerCase())) {
        message.error('A project with this name already exists');
        return;
      }

      const newProject = {
        projectId: `project-${Date.now()}`,
        name: values.name,
        description: values.description || '',
        createdAt: new Date().toISOString(),
      };

      onCreateProject(newProject);
      form.resetFields();
      setCreating(false);
      message.success(`Project "${values.name}" created successfully`);
    } catch (error) {
      console.error('Validation failed:', error);
    }
  };

  const handleDelete = (project) => {
    onDeleteProject(project.projectId);
    message.success(`Project "${project.name}" deleted`);
  };

  return (
    <Modal
      title="Manage Projects"
      open={visible}
      onCancel={onCancel}
      footer={[
        <Button key="close" onClick={onCancel}>
          Close
        </Button>,
      ]}
      width={600}
    >
      <div style={{ marginBottom: 16 }}>
        {!creating ? (
          <Button
            type="dashed"
            icon={<PlusOutlined />}
            onClick={() => setCreating(true)}
            block
          >
            Create New Project
          </Button>
        ) : (
          <div style={{ padding: 16, background: '#f5f5f5', borderRadius: 4 }}>
            <Form form={form} layout="vertical">
              <Form.Item
                name="name"
                label="Project Name"
                rules={[
                  { required: true, message: 'Please enter project name' },
                  { max: 50, message: 'Project name must be less than 50 characters' },
                ]}
              >
                <Input placeholder="e.g., Data Engineering" autoFocus />
              </Form.Item>

              <Form.Item
                name="description"
                label="Description (Optional)"
              >
                <TextArea
                  rows={2}
                  placeholder="Brief description of this project"
                />
              </Form.Item>

              <Space>
                <Button type="primary" onClick={handleCreate}>
                  Create
                </Button>
                <Button onClick={() => setCreating(false)}>
                  Cancel
                </Button>
              </Space>
            </Form>
          </div>
        )}
      </div>

      <div style={{ marginTop: 24 }}>
        <h4 style={{ marginBottom: 12 }}>Your Projects ({projects.length})</h4>
        {projects.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 32, color: '#999' }}>
            No projects yet. Create one to organize your DAG files.
          </div>
        ) : (
          <List
            dataSource={projects}
            renderItem={(project) => (
              <List.Item
                actions={[
                  <Popconfirm
                    title="Delete project?"
                    description="This won't delete the DAG files, only the project grouping."
                    onConfirm={() => handleDelete(project)}
                    okText="Delete"
                    cancelText="Cancel"
                  >
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      size="small"
                    />
                  </Popconfirm>,
                ]}
              >
                <List.Item.Meta
                  avatar={<FolderOutlined style={{ fontSize: 20, color: '#1890ff' }} />}
                  title={project.name}
                  description={project.description || 'No description'}
                />
              </List.Item>
            )}
          />
        )}
      </div>
    </Modal>
  );
};

export default ProjectModal;
