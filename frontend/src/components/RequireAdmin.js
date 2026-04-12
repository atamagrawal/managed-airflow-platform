import React from 'react';
import { Result } from 'antd';
import { useAuth } from '../context/AuthContext';

export default function RequireAdmin({ children }) {
  const { isAdmin, authReady, token } = useAuth();

  if (!authReady || !token) {
    return null;
  }

  if (!isAdmin) {
    return <Result status="403" title="Admin only" subTitle="Tenant management is restricted to administrators." />;
  }

  return children;
}
