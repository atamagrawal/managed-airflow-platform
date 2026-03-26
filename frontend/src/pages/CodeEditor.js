import React, { useState, useEffect, useCallback } from 'react';
import { message, Modal } from 'antd';
import { Allotment } from 'allotment';
import 'allotment/dist/style.css';
import FileTree from '../components/CodeEditor/FileTree';
import EditorTabs from '../components/CodeEditor/EditorTabs';
import Toolbar from '../components/CodeEditor/Toolbar';
import CodeEditorPane from '../components/CodeEditor/CodeEditorPane';
import NewDagModal from '../components/CodeEditor/NewDagModal';
import DeployModal from '../components/CodeEditor/DeployModal';
import ProjectModal from '../components/CodeEditor/ProjectModal';
import { dagAPI } from '../services/api';
import './CodeEditor.css';

// Project storage key for localStorage
const PROJECTS_STORAGE_KEY = 'airflow-dags-projects';

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

let unsavedFileCounter = 0;

const CodeEditor = () => {
  const [openFiles, setOpenFiles] = useState([]);
  const [activeFileId, setActiveFileId] = useState(null);
  const [fileContents, setFileContents] = useState({}); // fileId -> content
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
  const [showNewDagModal, setShowNewDagModal] = useState(false);
  const [showDeployModal, setShowDeployModal] = useState(false);
  const [showProjectModal, setShowProjectModal] = useState(false);
  const [unsavedFileIdToSave, setUnsavedFileIdToSave] = useState(null);
  const [projects, setProjects] = useState([]);

  const currentFile = openFiles.find((f) => f.fileId === activeFileId);
  const modalFile = openFiles.find((f) => f.fileId === (unsavedFileIdToSave || activeFileId));
  const isCurrentFileModified = modifiedFiles.has(activeFileId);
  const isUnsavedFile = currentFile && currentFile.isUnsaved;

  // Load projects from localStorage
  useEffect(() => {
    const storedProjects = localStorage.getItem(PROJECTS_STORAGE_KEY);
    if (storedProjects) {
      try {
        setProjects(JSON.parse(storedProjects));
      } catch (error) {
        console.error('Error loading projects:', error);
        setProjects([]);
      }
    }
  }, []);

  // Save projects to localStorage whenever they change
  useEffect(() => {
    if (projects.length > 0) {
      localStorage.setItem(PROJECTS_STORAGE_KEY, JSON.stringify(projects));
    }
  }, [projects]);

  // Debug logging
  useEffect(() => {
    console.log('[CodeEditor] State update:', {
      openFilesCount: openFiles.length,
      activeFileId,
      hasContent: activeFileId ? fileContents[activeFileId]?.length : 0,
      currentFileName: currentFile?.fileName,
    });
  }, [openFiles, activeFileId, fileContents, currentFile]);

  // Load file from API (for saved files)
  const loadFile = async (dag) => {
    try {
      if (!fileContents[dag.dagId]) {
        const response = await dagAPI.getById(dag.dagId);
        setFileContents((prev) => ({
          ...prev,
          [dag.dagId]: response.data.dagCode || '',
        }));
      }
    } catch (error) {
      message.error('Failed to load file content');
      console.error('Error loading file:', error);
    }
  };

  // Handle file selection from tree
  const handleFileSelect = async (dag) => {
    const fileId = dag.dagId;

    // Check if file is already open
    const isOpen = openFiles.some((f) => f.fileId === fileId);

    if (!isOpen) {
      const fileData = {
        ...dag,
        fileId: dag.dagId,
        isUnsaved: false,
      };
      setOpenFiles((prev) => [...prev, fileData]);
      await loadFile(dag);
    }

    setActiveFileId(fileId);
  };

  // Handle tab change
  const handleTabChange = (fileId) => {
    setActiveFileId(fileId);
  };

  // Handle tab close
  const handleTabClose = (fileId) => {
    if (modifiedFiles.has(fileId)) {
      Modal.confirm({
        title: 'Unsaved Changes',
        content: 'You have unsaved changes. Do you want to save before closing?',
        okText: 'Save',
        cancelText: 'Discard',
        onOk: async () => {
          await handleSave(fileId);
          closeTab(fileId);
        },
        onCancel: () => {
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

  // Handle code change
  const handleCodeChange = (newCode) => {
    console.log('[CodeEditor] handleCodeChange called', {
      activeFileId,
      newCodeLength: newCode?.length || 0,
      hasActiveFile: !!activeFileId,
    });

    if (activeFileId) {
      setFileContents((prev) => ({
        ...prev,
        [activeFileId]: newCode,
      }));

      // Mark as modified
      setModifiedFiles((prev) => new Set(prev).add(activeFileId));
    } else {
      console.error('[CodeEditor] No active file ID for code change!');
    }
  };

  // Handle new file
  const handleNewFile = () => {
    unsavedFileCounter++;
    const newFileId = `unsaved-${unsavedFileCounter}`;
    const defaultTemplate = getDefaultDagTemplate();

    const newFile = {
      fileId: newFileId,
      name: `Untitled-${unsavedFileCounter}`,
      fileName: `untitled_${unsavedFileCounter}.py`,
      isUnsaved: true,
      status: 'DRAFT',
    };

    // Use functional updates to ensure state consistency
    setFileContents((prev) => ({
      ...prev,
      [newFileId]: defaultTemplate,
    }));

    setOpenFiles((prev) => [...prev, newFile]);

    setActiveFileId(newFileId);

    // Mark as modified since it has content
    setModifiedFiles((prev) => new Set(prev).add(newFileId));

    // Show success message only if not the initial file
    if (unsavedFileCounter > 1) {
      message.success('New file created. Edit and save to deploy.');
    }
  };

  // Handle save for unsaved files - show modal
  const handleSaveUnsavedFile = async (dagData) => {
    const fileId = unsavedFileIdToSave || activeFileId;
    const code = fileContents[fileId];

    // Find project name
    const project = projects.find((p) => p.projectId === dagData.projectId);

    console.log('[CodeEditor] Saving DAG with data:', {
      fileName: dagData.fileName,
      name: dagData.name,
      projectId: dagData.projectId,
      projectName: project?.name,
      codeLength: code?.length,
    });

    try {
      setSaving(true);

      // Prepare payload - backend doesn't have projectId field yet, store in tags
      // Note: deploymentId is NOT included - it's only required when deploying
      const payload = {
        name: dagData.name,
        fileName: dagData.fileName,
        description: dagData.description || '',
        dagCode: code,
        // deploymentId is NOT sent on save - only on deploy
        tags: project ? project.name : 'Uncategorized', // Store project as tag for backend
        owner: 'user', // Default owner
      };

      console.log('[CodeEditor] Sending payload to API:', payload);

      const response = await dagAPI.create(payload);
      const newDag = response.data;

      console.log('[CodeEditor] DAG saved successfully:', newDag);
      message.success(`DAG saved successfully to project "${project?.name || 'Uncategorized'}".`);

      // Remove unsaved file from state
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

      // Add the newly saved file
      const savedFile = {
        ...newDag,
        fileId: newDag.dagId,
        isUnsaved: false,
        projectId: dagData.projectId, // Store project ID for client-side organization
      };

      setOpenFiles((prev) => [...prev, savedFile]);
      setFileContents((prev) => ({
        ...prev,
        [newDag.dagId]: code,
      }));
      setActiveFileId(newDag.dagId);

      setShowNewDagModal(false);
      setUnsavedFileIdToSave(null);
    } catch (error) {
      console.error('[CodeEditor] Error saving DAG:', error);
      console.error('[CodeEditor] Error response:', error.response?.data);

      const errorMessage = error.response?.data?.message || error.message || 'Failed to save DAG';
      message.error(`Failed to save DAG: ${errorMessage}`);
    } finally {
      setSaving(false);
    }
  };

  // Handle save
  const handleSave = useCallback(async (fileId = activeFileId) => {
    if (!fileId) return;

    const file = openFiles.find((f) => f.fileId === fileId);
    if (!file) return;

    // If unsaved file, show modal
    if (file.isUnsaved) {
      setUnsavedFileIdToSave(fileId);
      setShowNewDagModal(true);
      return;
    }

    // For saved files, update them
    try {
      setSaving(true);
      const payload = {
        ...file,
        dagCode: fileContents[fileId],
      };

      const response = await dagAPI.update(file.dagId, payload);
      message.success('File saved successfully');

      // Remove from modified files
      setModifiedFiles((prev) => {
        const newSet = new Set(prev);
        newSet.delete(fileId);
        return newSet;
      });

      // Update file data with response (to get updated status, etc.)
      if (response.data) {
        setOpenFiles((prev) =>
          prev.map((f) => (f.fileId === fileId ? { ...f, ...response.data, fileId } : f))
        );
      }
    } catch (error) {
      message.error('Failed to save file');
      console.error('Error saving file:', error);
    } finally {
      setSaving(false);
    }
  }, [activeFileId, openFiles, fileContents]);

  // Handle format
  const handleFormat = () => {
    if (!activeFileId || !fileContents[activeFileId]) return;

    try {
      // Python formatting with proper indentation rules
      const code = fileContents[activeFileId];
      const lines = code.split('\n');
      let indentLevel = 0;
      const formatted = [];

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmed = line.trim();

        // Skip empty lines but preserve them
        if (!trimmed) {
          formatted.push('');
          continue;
        }

        // Check if line starts with dedent keywords or closing brackets
        const isDedent = /^(return|pass|break|continue|raise|elif|else|except|finally)(\s|$)/.test(trimmed);
        const startsWithClosing = /^[}\])]/.test(trimmed);

        // Decrease indent for dedent keywords and closing brackets
        if (isDedent || startsWithClosing) {
          indentLevel = Math.max(0, indentLevel - 1);
        }

        // Apply indentation
        const indentedLine = '    '.repeat(indentLevel) + trimmed;
        formatted.push(indentedLine);

        // Increase indent after lines ending with colon (but not in comments)
        if (trimmed.endsWith(':') && !trimmed.startsWith('#')) {
          indentLevel++;
        }

        // Handle opening brackets that increase indent
        const openCount = (trimmed.match(/[[({]/g) || []).length;
        const closeCount = (trimmed.match(/[\])}]/g) || []).length;
        const netBrackets = openCount - closeCount;

        // Adjust indent based on unclosed brackets
        if (netBrackets > 0 && !trimmed.endsWith(':')) {
          indentLevel += netBrackets;
        } else if (netBrackets < 0) {
          indentLevel = Math.max(0, indentLevel + netBrackets);
        }

        // Reset indent to 0 if we're back at module level
        if (indentLevel < 0) indentLevel = 0;
      }

      const formattedCode = formatted.join('\n');

      setFileContents((prev) => ({
        ...prev,
        [activeFileId]: formattedCode,
      }));

      setModifiedFiles((prev) => new Set(prev).add(activeFileId));
      message.success('Code formatted');
    } catch (error) {
      message.error('Failed to format code');
      console.error('Format error:', error);
    }
  };

  // Handle deploy
  const handleDeploy = async () => {
    if (!activeFileId || isUnsavedFile) {
      message.warning('Please save the file before deploying');
      return;
    }

    // Check if file has unsaved changes
    if (modifiedFiles.has(activeFileId)) {
      Modal.confirm({
        title: 'Unsaved Changes',
        content: 'You have unsaved changes. Save before deploying?',
        okText: 'Save & Deploy',
        cancelText: 'Cancel',
        onOk: async () => {
          await handleSave();
          setShowDeployModal(true);
        },
      });
    } else {
      setShowDeployModal(true);
    }
  };

  const performDeploy = async (deploymentId) => {
    try {
      setDeploying(true);

      // First update the DAG with the deployment ID
      const updatePayload = {
        ...currentFile,
        deploymentId: deploymentId,
        dagCode: fileContents[activeFileId],
      };

      await dagAPI.update(currentFile.dagId, updatePayload);

      // Then deploy it
      await dagAPI.deploy(currentFile.dagId);

      message.success('DAG deployed successfully. Now you can trigger it.');

      // Reload file to get updated status
      const response = await dagAPI.getById(currentFile.dagId);
      setOpenFiles((prev) =>
        prev.map((f) =>
          f.fileId === activeFileId ? { ...f, ...response.data, fileId: f.fileId } : f
        )
      );

      setShowDeployModal(false);
    } catch (error) {
      message.error('Failed to deploy DAG');
      console.error('Deploy error:', error);
    } finally {
      setDeploying(false);
    }
  };

  // Handle trigger
  const handleTrigger = async () => {
    if (!activeFileId || isUnsavedFile) {
      message.warning('Please save and deploy the file before triggering');
      return;
    }

    try {
      await dagAPI.trigger(currentFile.dagId);
      message.success('DAG triggered successfully');
    } catch (error) {
      message.error('Failed to trigger DAG');
      console.error('Trigger error:', error);
    }
  };

  // Handle fullscreen
  const handleFullscreen = () => {
    setIsFullscreen(!isFullscreen);
  };

  // Handle settings change
  const handleSettingsChange = (newSettings) => {
    setEditorSettings((prev) => ({ ...prev, ...newSettings }));
  };

  // Handle project management
  const handleCreateProject = (newProject) => {
    setProjects((prev) => [...prev, newProject]);
  };

  const handleDeleteProject = (projectId) => {
    setProjects((prev) => prev.filter((p) => p.projectId !== projectId));
  };

  const handleManageProjects = () => {
    setShowProjectModal(true);
  };

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e) => {
      // Ctrl/Cmd + S to save
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        if (activeFileId && (modifiedFiles.has(activeFileId) || isUnsavedFile)) {
          handleSave();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [activeFileId, modifiedFiles, isUnsavedFile, handleSave]);

  return (
    <div className={`code-editor-container ${isFullscreen ? 'fullscreen' : ''}`}>
      <Toolbar
        currentFile={currentFile}
        isModified={isCurrentFileModified}
        onSave={() => handleSave()}
        onFormat={handleFormat}
        onDeploy={handleDeploy}
        onTrigger={handleTrigger}
        onFullscreen={handleFullscreen}
        isFullscreen={isFullscreen}
        onNewFile={handleNewFile}
        onManageProjects={handleManageProjects}
        editorSettings={editorSettings}
        onSettingsChange={handleSettingsChange}
        saving={saving}
        deploying={deploying}
      />

      <div className="code-editor-content">
        <Allotment style={{ height: '100%' }}>
          <Allotment.Pane minSize={200} preferredSize={250} maxSize={400}>
            <FileTree
              onFileSelect={handleFileSelect}
              activeFileId={activeFileId}
              onRefresh={() => {
                // Reload open saved files
                openFiles.forEach((file) => {
                  if (!file.isUnsaved) {
                    loadFile(file);
                  }
                });
              }}
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
                  <p>No file is currently open. Create a new file or select one from the file tree.</p>
                </div>
              )}
            </div>
          </Allotment.Pane>
        </Allotment>
      </div>

      <NewDagModal
        visible={showNewDagModal}
        onCancel={() => {
          setShowNewDagModal(false);
          setUnsavedFileIdToSave(null);
        }}
        onSubmit={handleSaveUnsavedFile}
        projects={projects}
        onManageProjects={() => {
          setShowNewDagModal(false);
          setShowProjectModal(true);
        }}
        initialValues={{
          fileName: modalFile?.fileName,
          name: modalFile?.name,
          description: modalFile?.description,
          projectId: modalFile?.projectId,
        }}
      />

      <ProjectModal
        visible={showProjectModal}
        onCancel={() => {
          setShowProjectModal(false);
          // Reopen save modal if we came from there
          if (unsavedFileIdToSave) {
            setShowNewDagModal(true);
          }
        }}
        projects={projects}
        onCreateProject={handleCreateProject}
        onDeleteProject={handleDeleteProject}
      />

      <DeployModal
        visible={showDeployModal}
        onCancel={() => setShowDeployModal(false)}
        onSubmit={performDeploy}
        dagName={currentFile?.name}
      />
    </div>
  );
};

export default CodeEditor;
