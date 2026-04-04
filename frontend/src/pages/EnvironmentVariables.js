import React from 'react';
import { Alert, Typography } from 'antd';

const { Paragraph, Title } = Typography;

/**
 * Placeholder for Astro-style Airflow Variables (workspace-level define → link deployments).
 */
const EnvironmentVariables = () => {
  return (
    <div style={{ maxWidth: 720 }}>
      <Title level={4} style={{ marginTop: 0, marginBottom: 8 }}>
        Variables
      </Title>
      <Paragraph type="secondary" style={{ marginBottom: 16 }}>
        Workspace-level Airflow variables with per-deployment linking will follow the same pattern as Connections.
      </Paragraph>
      <Alert
        type="info"
        showIcon
        message="Airflow variables"
        description={
          <Paragraph style={{ marginBottom: 0 }} type="secondary">
            This will mirror how Astro lets you manage variables outside the Airflow UI and apply them across
            deployments. Implementation is planned next; until then you can use the Airflow UI or project-level
            settings where supported.
          </Paragraph>
        }
      />
    </div>
  );
};

export default EnvironmentVariables;
