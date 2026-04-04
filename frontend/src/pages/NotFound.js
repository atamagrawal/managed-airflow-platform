import React from 'react';
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';

export default function NotFound() {
  const navigate = useNavigate();

  return (
    <Result
      status="404"
      title="Page not found"
      subTitle="That route does not exist in FlowDeck."
      extra={
        <Button type="primary" onClick={() => navigate('/dashboard', { replace: true })}>
          Back to dashboard
        </Button>
      }
    />
  );
}
