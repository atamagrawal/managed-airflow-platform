import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Statistic, Typography, Spin, Alert } from 'antd';
import { TeamOutlined, CloudServerOutlined, CheckCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { tenantAPI, deploymentAPI } from '../services/api';

const { Title } = Typography;

const Dashboard = () => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [stats, setStats] = useState({
    totalTenants: 0,
    totalDeployments: 0,
    runningDeployments: 0,
    failedDeployments: 0,
  });

  useEffect(() => {
    fetchDashboardData();
  }, []);

  const fetchDashboardData = async () => {
    try {
      setLoading(true);
      const [tenantsResponse, deploymentsResponse] = await Promise.all([
        tenantAPI.getAll(),
        deploymentAPI.getAll(),
      ]);

      const tenants = tenantsResponse.data;
      const deployments = deploymentsResponse.data;

      const runningDeployments = deployments.filter((d) => d.status === 'RUNNING').length;
      const failedDeployments = deployments.filter((d) => d.status === 'FAILED').length;

      setStats({
        totalTenants: tenants.length,
        totalDeployments: deployments.length,
        runningDeployments,
        failedDeployments,
      });

      setError(null);
    } catch (err) {
      console.error('Error fetching dashboard data:', err);
      setError('Failed to load dashboard data. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '50px' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error) {
    return <Alert message="Error" description={error} type="error" showIcon />;
  }

  return (
    <div>
      <Title level={2} className="page-header">
        Dashboard
      </Title>

      <Row gutter={16}>
        <Col span={6}>
          <Card className="stats-card">
            <Statistic
              title="Total Tenants"
              value={stats.totalTenants}
              prefix={<TeamOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card className="stats-card">
            <Statistic
              title="Total Deployments"
              value={stats.totalDeployments}
              prefix={<CloudServerOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card className="stats-card">
            <Statistic
              title="Running Deployments"
              value={stats.runningDeployments}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card className="stats-card">
            <Statistic
              title="Failed Deployments"
              value={stats.failedDeployments}
              prefix={<WarningOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
