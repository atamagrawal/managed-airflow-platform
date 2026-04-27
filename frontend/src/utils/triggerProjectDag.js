import React from 'react';
import { Modal, Select, message } from 'antd';
import { projectAPI } from '../services/api';
import { getApiErrorMessage } from './apiError';

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

export function showTriggerResult(response, projectName) {
  const { triggeredCount, failedCount, totalDagFiles, results, requestedWorkerQueue } = response.data;
  const queueSuffix = requestedWorkerQueue ? ` on queue "${requestedWorkerQueue}"` : '';
  if (triggeredCount > 0) {
    message.success(
      projectName
        ? `"${projectName}": triggered ${triggeredCount}/${totalDagFiles} DAG run(s)${queueSuffix}`
        : `Triggered ${triggeredCount}/${totalDagFiles} DAG run(s) successfully${queueSuffix}`
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
 *
 * @param {string} deploymentId - Airflow deployment to send DAG triggers to (required).
 */
export async function triggerProjectWithDagSelection({
  projectId,
  projectName,
  deploymentId,
  deployment,
  files: existingFiles,
  onAwaitingUserChoice,
  onTriggerStart,
}) {
  if (!deploymentId) {
    message.error('deploymentId is required to trigger DAGs');
    return;
  }
  let dagFiles;
  try {
    dagFiles = await resolveDagFiles(projectId, existingFiles);
  } catch (error) {
    const msg = getApiErrorMessage(error, 'Failed to load project files');
    if (msg) message.error(msg);
    console.error('Failed to load project files:', error);
    throw error;
  }

  const label = projectName || projectId;

  if (dagFiles.length === 0) {
    message.warning(`"${label}": no DAG files found in project`);
    return;
  }

  const queueOptions = Array.isArray(deployment?.workerQueues)
    ? deployment.workerQueues
        .map((queue) => ({
          value: (queue?.name || '').trim(),
          workers: queue?.workers ?? 1,
        }))
        .filter((queue) => queue.value)
    : [];

  const execTrigger = async (fileName, workerQueue) => {
    const response = await projectAPI.trigger(projectId, deploymentId, fileName, workerQueue);
    showTriggerResult(response, projectName);
    return response;
  };

  if (dagFiles.length === 1 && queueOptions.length === 0) {
    await execTrigger(dagFiles[0].fileName, null);
    return;
  }

  onAwaitingUserChoice?.();

  let selectedFileName = dagFiles.length === 1 ? dagFiles[0].fileName : null;
  let selectedQueueName = null;
  return new Promise((resolve, reject) => {
    Modal.confirm({
      title: `Trigger DAG — ${label}`,
      width: 480,
      content: (
        <div>
          {dagFiles.length > 1 ? (
            <>
              <p style={{ marginBottom: 12 }}>Choose which DAG file to trigger:</p>
              <Select
                style={{ width: '100%', marginBottom: queueOptions.length ? 12 : 0 }}
                placeholder="Select DAG file"
                onChange={(value) => {
                  selectedFileName = value;
                }}
                options={dagFiles.map((f) => ({
                  label: f.filePath || f.fileName,
                  value: f.fileName,
                }))}
              />
            </>
          ) : (
            <p style={{ marginBottom: queueOptions.length ? 12 : 0 }}>
              DAG file: <strong>{dagFiles[0].filePath || dagFiles[0].fileName}</strong>
            </p>
          )}
          {queueOptions.length > 0 && (
            <>
              <p style={{ marginBottom: 8 }}>
                Run using task queue (optional). Local deployments route to queue-specific workers; other providers may map
                the same queue name differently.
              </p>
              <Select
                style={{ width: '100%' }}
                placeholder="Default queue behavior"
                allowClear
                onChange={(value) => {
                  selectedQueueName = value || null;
                }}
                options={queueOptions.map((q) => ({
                  label: `${q.value} (${q.workers} worker${q.workers === 1 ? '' : 's'})`,
                  value: q.value,
                }))}
              />
            </>
          )}
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
          await execTrigger(selectedFileName, selectedQueueName);
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

/** Trigger a single DAG file when deployment and file are already known (e.g. deployed DAGs list). */
export async function triggerProjectDagFile(projectId, deploymentId, fileName, projectName) {
  if (!deploymentId || !fileName) {
    message.error('deploymentId and fileName are required');
    return;
  }
  const response = await projectAPI.trigger(projectId, deploymentId, fileName, null);
  showTriggerResult(response, projectName);
  return response;
}
