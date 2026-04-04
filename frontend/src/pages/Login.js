import React, { useState } from 'react';
import { Form, Input, Button, Card, Typography, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getApiErrorMessage } from '../utils/apiError';

const { Title, Paragraph } = Typography;

const Login = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values) => {
    try {
      setLoading(true);
      await login(values.username, values.password);
      message.success('Signed in');
      const redirectTo = location.state?.from && location.state.from !== '/login' ? location.state.from : '/dashboard';
      navigate(redirectTo, { replace: true });
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Sign-in failed');
      if (msg) message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #1f1f1f 0%, #434343 100%)',
        padding: 24,
      }}
    >
      <Card style={{ width: '100%', maxWidth: 400, borderRadius: 8 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>
          Managed Airflow
        </Title>
        <Paragraph type="secondary" style={{ textAlign: 'center', marginBottom: 24 }}>
          Sign in to the control plane
        </Paragraph>
        <Form name="login" onFinish={onFinish} layout="vertical" requiredMark={false}>
          <Form.Item name="username" label="Username" rules={[{ required: true, message: 'Enter username' }]}>
            <Input prefix={<UserOutlined />} placeholder="Username" autoComplete="username" size="large" />
          </Form.Item>
          <Form.Item name="password" label="Password" rules={[{ required: true, message: 'Enter password' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="Password" autoComplete="current-password" size="large" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block size="large">
              Sign in
            </Button>
          </Form.Item>
        </Form>
        <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0, textAlign: 'center' }}>
          Default dev users: <code>admin</code> / <code>admin</code> or <code>user</code> / <code>user</code>
        </Paragraph>
      </Card>
    </div>
  );
};

export default Login;
