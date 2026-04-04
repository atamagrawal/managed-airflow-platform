import React from 'react';
import { Alert, Typography } from 'antd';

const { Paragraph, Title } = Typography;

/** Placeholder: workspace-level Airflow variables with per-deployment push. */
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
            You will be able to define variables here and push them to selected deployments, similar to Connections.
            This is not implemented yet; until then use the Airflow UI or project-level settings where supported.
          </Paragraph>
        }
      />
    </div>
  );
};

export default EnvironmentVariables;
