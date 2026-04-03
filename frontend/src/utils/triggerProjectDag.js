import React from 'react';
import { Modal, Select, message } from 'antd';
import { projectAPI } from '../services/api';

export function getDagFilesFromList(files) {
  return (files || []).filter((f) => f.fileType === 'DAG');
}

async function resolveDagFiles(projectId, existingFiles) {
  if (existingFiles != null) {
    return getDagFilesFromList(existingFiles);
  }
  const response = await projectAPI.getFiles(projectId);
  return getDagFilesFromList(response.data);
}

function showTriggerResult(response, projectName) {
  const { triggeredCount, failedCount, totalDagFiles, results } = response.data;
  if (triggeredCount > 0) {
    message.success(
      projectName
        ? `"${projectName}": triggered ${triggeredCount}/${totalDagFiles} DAG run(s)`
        : `Triggered ${triggeredCount}/${totalDagFiles} DAG run(s) successfully`
    );
  }
  if (failedCount > 0) {
    const failedDetails = results
      .filter((r) => !r.success)
      .map((r) => `• ${r.fileName || r.airflowDagId}: ${r.message}`)
      .join('\n');
    message.warning({
      content: projectName
        ? `"${projectName}": ${failedCount} DAG(s) failed:\n${failedDetails}`
        : `${failedCount} DAG(s) failed:\n${failedDetails}`,
      duration: 8,
    });
  }
  if (totalDagFiles === 0) {
    message.warning(
      projectName ? `"${projectName}": no DAG files found in project` : 'No DAG files found in project'
    );
  }
}

/**
 * If the project has multiple DAG files, prompts for which file to trigger.
 * Pass `files` when already loaded to avoid an extra request.
 * When a choice modal opens, `onAwaitingUserChoice` runs so callers can clear loading state; `onTriggerStart` runs when the user confirms before the API call.
 */
export async function triggerProjectWithDagSelection({
  projectId,
  projectName,
  files: existingFiles,
  onAwaitingUserChoice,
  onTriggerStart,
}) {
  let dagFiles;
  try {
    dagFiles = await resolveDagFiles(projectId, existingFiles);
  } catch (error) {
    message.error('Failed to load project files');
    console.error(error);
    throw error;
  }

  const label = projectName || projectId;

  if (dagFiles.length === 0) {
    message.warning(`"${label}": no DAG files found in project`);
    return;
  }

  const execTrigger = async (fileName) => {
    const response = await projectAPI.trigger(projectId, fileName);
    showTriggerResult(response, projectName);
    return response;
  };

  if (dagFiles.length === 1) {
    await execTrigger(dagFiles[0].fileName);
    return;
  }

  onAwaitingUserChoice?.();

  let selectedFileName = null;
  return new Promise((resolve, reject) => {
    Modal.confirm({
      title: `Trigger DAG — ${label}`,
      width: 480,
      content: (
        <div>
          <p style={{ marginBottom: 12 }}>This project has multiple DAG files. Choose which one to trigger:</p>
          <Select
            style={{ width: '100%' }}
            placeholder="Select DAG file"
            onChange={(value) => {
              selectedFileName = value;
            }}
            options={dagFiles.map((f) => ({
              label: f.filePath || f.fileName,
              value: f.fileName,
            }))}
          />
        </div>
      ),
      okText: 'Trigger',
      cancelText: 'Cancel',
      onOk: async () => {
        if (!selectedFileName) {
          message.error('Please select a DAG file');
          return Promise.reject();
        }
        try {
          onTriggerStart?.();
          await execTrigger(selectedFileName);
          resolve();
        } catch (error) {
          console.error('Error triggering project:', error);
          reject(error);
        }
      },
      onCancel: () => resolve(),
    });
  });
}
