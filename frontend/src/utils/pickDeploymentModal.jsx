import React from 'react';
import { Modal, Select, message } from 'antd';
import { DeploymentPickCancelledError, DeploymentPickValidationError } from './apiError';

/**
 * Resolves a deployment id: single option returns immediately; otherwise shows a modal.
 * @param {string} title
 * @param {{ label: string, value: string }[]} options
 * @returns {Promise<string>}
 */
export function pickDeploymentId(title, options) {
  if (!options || options.length === 0) {
    message.error('No deployments available. Create an Airflow deployment first.');
    return Promise.reject(new Error('no deployments'));
  }
  if (options.length === 1) {
    return Promise.resolve(options[0].value);
  }
  let selectedDeploymentId = null;
  return new Promise((resolve, reject) => {
    Modal.confirm({
      title,
      content: (
        <div>
          <p style={{ marginBottom: 12 }}>Choose the Airflow deployment:</p>
          <Select
            style={{ width: '100%' }}
            placeholder="Select deployment"
            onChange={(value) => {
              selectedDeploymentId = value;
            }}
            options={options}
          />
        </div>
      ),
      okText: 'Continue',
      cancelText: 'Cancel',
      onOk: () => {
        if (!selectedDeploymentId) {
          message.error('Please select a deployment');
          return Promise.reject(new DeploymentPickValidationError());
        }
        resolve(selectedDeploymentId);
      },
      onCancel: () => reject(new DeploymentPickCancelledError()),
    });
  });
}
