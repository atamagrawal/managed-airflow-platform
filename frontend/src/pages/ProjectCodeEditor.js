import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { message, Modal, Input, Select, Form } from 'antd';
import { Allotment } from 'allotment';
import 'allotment/dist/style.css';
import ProjectFileTree from '../components/ProjectCodeEditor/ProjectFileTree';
import EditorTabs from '../components/CodeEditor/EditorTabs';
import ProjectToolbar from '../components/ProjectCodeEditor/ProjectToolbar';
import CodeEditorPane from '../components/CodeEditor/CodeEditorPane';
import { projectAPI, deploymentAPI } from '../services/api';
import { triggerProjectWithDagSelection } from '../utils/triggerProjectDag';
import { resolveDeploymentForDeploy, resolveDeploymentForTrigger } from '../utils/projectDeployments';
import { pickDeploymentId } from '../utils/pickDeploymentModal';
import { getApiErrorMessage } from '../utils/apiError';
import './CodeEditor.css';

const { Option } = Select;

const ProjectCodeEditor = () => {
  const { projectId } = useParams();
  const navigate = useNavigate();

  const [project, setProject] = useState(null);
  const [files, setFiles] = useState([]);
  const [deployments, setDeployments] = useState([]);
  const [openFiles, setOpenFiles] = useState([]);
  const [activeFileId, setActiveFileId] = useState(null);
  const [fileContents, setFileContents] = useState({});
  const [modifiedFiles, setModifiedFiles] = useState(new Set());
  const [editorSettings, setEditorSettings] = useState({
    theme: 'vs-dark',
    fontSize: 14,
    minimap: true,
    lineNumbers: 'on',
    wordWrap: 'off',
  });
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deploying, setDeploying] = useState(false);
  const [triggering, setTriggering] = useState(false);
  const [showNewFileModal, setShowNewFileModal] = useState(false);
  const [newFileForm] = Form.useForm();

  const currentFile = openFiles.find((f) => f.fileId === activeFileId);
  const isCurrentFileModified = modifiedFiles.has(activeFileId);

  useEffect(() => {
    fetchProject();
    fetchProjectFiles();
    fetchDeployments();
  }, [projectId]);

  const fetchDeployments = async () => {
    try {
      const response = await deploymentAPI.getAll();
      setDeployments(response.data);
    } catch (error) {
      console.error('Error fetching deployments:', error);
    }
  };

  const fetchProject = async () => {
    try {
      const response = await projectAPI.getById(projectId);
      setProject(response.data);
    } catch (error) {
      message.error('Failed to fetch project');
      console.error('Error:', error);
    }
  };

  const fetchProjectFiles = async () => {
    try {
      const response = await projectAPI.getFiles(projectId);
      const filesData = response.data || [];
      setFiles(filesData);

      // Load file contents
      const contents = {};
      filesData.forEach(file => {
        contents[file.id] = file.content;
      });
      setFileContents(prev => ({ ...prev, ...contents }));
    } catch (error) {
      message.error('Failed to fetch project files');
      console.error('Error:', error);
    }
  };

  const handleFileSelect = (file) => {
    const fileId = file.isConfig ? file.fileId : file.id.toString();

    // Check if file is already open
    const isOpen = openFiles.some((f) => f.fileId === fileId);

    if (!isOpen) {
      const fileData = {
        ...file,
        fileId: fileId,
        name: file.fileName,
      };
      setOpenFiles((prev) => [...prev, fileData]);

      // Load config file content
      if (file.isConfig) {
        setFileContents(prev => ({ ...prev, [fileId]: file.content }));
      }
    }

    setActiveFileId(fileId);
  };

  const handleTabChange = (fileId) => {
    setActiveFileId(fileId);
  };

  const handleTabClose = (fileId) => {
    if (modifiedFiles.has(fileId)) {
      Modal.confirm({
        title: 'Unsaved Changes',
        content: 'You have unsaved changes. Do you want to discard them?',
        okText: 'Discard',
        cancelText: 'Cancel',
        onOk: () => {
          closeTab(fileId);
        },
      });
    } else {
      closeTab(fileId);
    }
  };

  const closeTab = (fileId) => {
    setOpenFiles((prev) => prev.filter((f) => f.fileId !== fileId));
    setFileContents((prev) => {
      const newContents = { ...prev };
      delete newContents[fileId];
      return newContents;
    });
    setModifiedFiles((prev) => {
      const newSet = new Set(prev);
      newSet.delete(fileId);
      return newSet;
    });

    // Set active tab to the next available one
    if (activeFileId === fileId) {
      const currentIndex = openFiles.findIndex((f) => f.fileId === fileId);
      const nextFile = openFiles[currentIndex + 1] || openFiles[currentIndex - 1];
      setActiveFileId(nextFile?.fileId || null);
    }
  };

  const handleCodeChange = (newCode) => {
    if (activeFileId) {
      setFileContents((prev) => ({
        ...prev,
        [activeFileId]: newCode,
      }));
      setModifiedFiles((prev) => new Set(prev).add(activeFileId));
    }
  };

  const handleSave = useCallback(async () => {
    if (!activeFileId || !modifiedFiles.has(activeFileId)) {
      message.info('No changes to save');
      return;
    }

    setSaving(true);
    try {
      const activeFile = openFiles.find(f => f.fileId === activeFileId);
      const content = fileContents[activeFileId];

      if (activeFile.isConfig) {
        await saveConfigFile(activeFile, content);
      } else {
        const numericId = typeof activeFile.id === 'number' ? activeFile.id : Number(activeFile.id);
        if (!numericId || Number.isNaN(numericId)) {
          throw new Error('Invalid file id');
        }
        await projectAPI.updateFile(projectId, numericId, { content });
        await fetchProjectFiles();
      }

      setModifiedFiles(prev => {
        const newSet = new Set(prev);
        newSet.delete(activeFileId);
        return newSet;
      });

      message.success('File saved successfully');
    } catch (error) {
      message.error('Failed to save file');
      console.error('Error:', error);
    } finally {
      setSaving(false);
    }
  }, [activeFileId, modifiedFiles, openFiles, fileContents, projectId]);

  const saveConfigFile = async (configFile, content) => {
    const updates = {};

    switch (configFile.configKey) {
      case 'requirements':
        updates.requirementsTxt = content;
        break;
      case 'packages':
        updates.packagesTxt = content;
        break;
      case 'dockerfile':
        updates.dockerfile = content;
        break;
      case 'settings':
        updates.airflowSettingsYaml = content;
        break;
      case 'ignore':
        updates.airflowIgnore = content;
        break;
      case 'env':
        updates.envFile = content;
        break;
    }

    await projectAPI.update(projectId, updates);
    await fetchProject();
  };

  const handleFormat = () => {
    if (!activeFileId || !fileContents[activeFileId]) return;

    try {
      // Simple Python formatting
      const code = fileContents[activeFileId];
      const lines = code.split('\n');
      let indentLevel = 0;
      const formatted = [];

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmed = line.trim();

        if (trimmed === '') {
          formatted.push('');
          continue;
        }

        // Decrease indent for lines starting with dedent keywords
        if (/^(return|break|continue|pass|raise|except|finally|elif|else)/.test(trimmed)) {
          indentLevel = Math.max(0, indentLevel - 1);
        }

        // Add formatted line
        formatted.push('    '.repeat(indentLevel) + trimmed);

        // Increase indent after lines ending with ':'
        if (trimmed.endsWith(':')) {
          indentLevel++;
        }

        // Decrease indent for dedent keywords at line start
        if (/^(except|finally|elif|else):/.test(trimmed)) {
          indentLevel = Math.max(0, indentLevel - 1);
        }
      }

      const formattedCode = formatted.join('\n');
      setFileContents(prev => ({ ...prev, [activeFileId]: formattedCode }));
      setModifiedFiles(prev => new Set(prev).add(activeFileId));
      message.success('Code formatted');
    } catch (error) {
      message.error('Failed to format code');
      console.error('Error formatting:', error);
    }
  };

  const handleDeploy = async () => {
    try {
      const resolved = resolveDeploymentForDeploy(project, deployments);
      if (!resolved.ok) {
        message.error('No deployments available. Create an Airflow deployment first.');
        return;
      }
      let deploymentId = resolved.deploymentId;
      if (resolved.needsPicker) {
        deploymentId = await pickDeploymentId(`Deploy — ${project?.name}`, resolved.options);
      }
      setDeploying(true);
      await projectAPI.deploy(projectId, deploymentId);
      message.success('Project deployed successfully');
      await fetchProject();
    } catch (error) {
      if (error?.message === 'no deployments') return;
      const msg = getApiErrorMessage(error, 'Failed to deploy project');
      if (msg) message.error(msg);
      console.error('Error:', error);
    } finally {
      setDeploying(false);
    }
  };

  const handleTrigger = async () => {
    try {
      const resolved = resolveDeploymentForTrigger(project, deployments);
      if (!resolved.ok) {
        message.warning('Deploy this project to a deployment before triggering DAGs.');
        return;
      }
      let deploymentId = resolved.deploymentId;
      if (resolved.needsPicker) {
        deploymentId = await pickDeploymentId(`Trigger DAGs — ${project?.name}`, resolved.options);
      }
      setTriggering(true);
      await triggerProjectWithDagSelection({
        projectId,
        projectName: project?.name,
        deploymentId,
        files,
        onAwaitingUserChoice: () => setTriggering(false),
        onTriggerStart: () => setTriggering(true),
      });
    } catch (error) {
      const msg = getApiErrorMessage(error, 'Failed to trigger DAG runs');
      if (msg) message.error(msg);
      console.error('Error triggering project:', error);
    } finally {
      setTriggering(false);
    }
  };

  const handleNewFile = () => {
    newFileForm.resetFields();
    newFileForm.setFieldsValue({
      path: 'dags/',
      type: 'DAG',
    });
    setShowNewFileModal(true);
  };

  const handleCreateFile = async (values) => {
    try {
      const payload = {
        filePath: `${values.path}${values.name}`,
        fileName: values.name,
        fileType: values.type,
        content: getDefaultFileContent(values.type),
        description: '',
      };

      await projectAPI.addFile(projectId, payload);
      message.success('File created successfully');
      await fetchProjectFiles();
      setShowNewFileModal(false);
    } catch (error) {
      message.error('Failed to create file');
      console.error('Error:', error);
    }
  };

  const getDefaultFileContent = (fileType) => {
    switch (fileType) {
      case 'DAG':
        return `from datetime import datetime
import requests
from airflow import DAG
from airflow.operators.python import PythonOperator

dag = DAG(
    'my_new_dag',
    start_date=datetime(2024, 1, 1),
    schedule=None,
    catchup=False,
)

def my_task():
    # requests should be installed from requirements.txt
    response = requests.get('https://httpbin.org/get', timeout=10)
    response.raise_for_status()
    print('HTTP status:', response.status_code)

task = PythonOperator(
    task_id='my_task',
    python_callable=my_task,
    dag=dag,
)
`;
      case 'PLUGIN':
        return `from airflow.plugins_manager import AirflowPlugin

class MyCustomPlugin(AirflowPlugin):
    name = "my_custom_plugin"
`;
      case 'CONTRACT':
        return `contract_id: my-dataset-v1
dataset_urn: "urn:example:my_dataset"
dataset_name: my_dataset
version: 1
status: ACTIVE

schema:
  - name: id
    type: STRING
    nullable: false

min_row_count: 1

schema_compatibility: BACKWARD
`;
      default:
        return '';
    }
  };

  const handleSettingsChange = (updates) => {
    setEditorSettings((prev) => ({ ...prev, ...updates }));
  };

  const handleFullscreen = () => {
    setIsFullscreen((prev) => !prev);
  };

  const handleBack = () => {
    if (modifiedFiles.size > 0) {
      Modal.confirm({
        title: 'Unsaved Changes',
        content: 'You have unsaved changes. Are you sure you want to leave?',
        okText: 'Leave',
        cancelText: 'Stay',
        onOk: () => {
          navigate('/projects');
        },
      });
    } else {
      navigate('/projects');
    }
  };

  return (
    <div className={`code-editor-container ${isFullscreen ? 'fullscreen' : ''}`}>
      <ProjectToolbar
        project={project}
        currentFile={currentFile}
        isModified={isCurrentFileModified}
        onSave={handleSave}
        onFormat={handleFormat}
        onDeploy={handleDeploy}
        onTrigger={handleTrigger}
        onFullscreen={handleFullscreen}
        isFullscreen={isFullscreen}
        onNewFile={handleNewFile}
        onBack={handleBack}
        editorSettings={editorSettings}
        onSettingsChange={handleSettingsChange}
        saving={saving}
        deploying={deploying}
        triggering={triggering}
      />

      <div className="code-editor-content">
        <Allotment style={{ height: '100%' }}>
          <Allotment.Pane minSize={200} preferredSize={250} maxSize={400}>
            <ProjectFileTree
              project={project}
              files={files}
              activeFileId={activeFileId}
              onFileSelect={handleFileSelect}
              onRefresh={fetchProjectFiles}
            />
          </Allotment.Pane>

          <Allotment.Pane>
            <div className="editor-main-pane">
              <EditorTabs
                openFiles={openFiles}
                activeFileId={activeFileId}
                onTabChange={handleTabChange}
                onTabClose={handleTabClose}
                modifiedFiles={modifiedFiles}
              />

              {currentFile ? (
                <div className="editor-content-area">
                  <CodeEditorPane
                    key={activeFileId}
                    value={fileContents[activeFileId] || ''}
                    onChange={handleCodeChange}
                    readOnly={false}
                    settings={{
                      theme: editorSettings.theme,
                      fontSize: editorSettings.fontSize,
                      minimap: { enabled: editorSettings.minimap },
                      lineNumbers: editorSettings.lineNumbers,
                      wordWrap: editorSettings.wordWrap,
                      readOnly: false,
                    }}
                    onMount={{
                      onSave: () => handleSave(),
                      onFormat: handleFormat,
                    }}
                  />
                </div>
              ) : (
                <div className="editor-placeholder">
                  <p>Select a file from the tree to start editing</p>
                </div>
              )}
            </div>
          </Allotment.Pane>
        </Allotment>
      </div>

      <Modal
        title="Create New File"
        open={showNewFileModal}
        onOk={() => newFileForm.submit()}
        onCancel={() => setShowNewFileModal(false)}
      >
        <Form form={newFileForm} layout="vertical" onFinish={handleCreateFile}>
          <Form.Item
            label="Directory"
            name="path"
            rules={[{ required: true, message: 'Please select a directory' }]}
          >
            <Select>
              <Option value="dags/">dags/</Option>
              <Option value="contracts/">contracts/</Option>
              <Option value="plugins/">plugins/</Option>
              <Option value="include/">include/</Option>
              <Option value="tests/">tests/</Option>
            </Select>
          </Form.Item>
          <Form.Item
            label="File Name"
            name="name"
            rules={[{ required: true, message: 'Please enter a file name' }]}
          >
            <Input placeholder="my_dag.py" />
          </Form.Item>
          <Form.Item
            label="File Type"
            name="type"
            rules={[{ required: true, message: 'Please select a file type' }]}
          >
            <Select>
              <Option value="DAG">DAG</Option>
              <Option value="CONTRACT">Data contract (YAML)</Option>
              <Option value="PLUGIN">Plugin</Option>
              <Option value="INCLUDE">Include</Option>
              <Option value="TEST">Test</Option>
              <Option value="UTIL">Utility</Option>
              <Option value="OTHER">Other</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ProjectCodeEditor;
