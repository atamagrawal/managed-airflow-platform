import React, { useState, useEffect, useCallback } from 'react';
import { Row, Col, Card, Statistic, Spin, Alert, Button, Space } from 'antd';
import {
  TeamOutlined,
  CloudServerOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  ReloadOutlined,
  NodeIndexOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { tenantAPI, deploymentAPI, deployedDagsAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import BrandMark from '../components/BrandMark';
import FlowDeckWordmark from '../components/FlowDeckWordmark';
import PageHeader from '../components/PageHeader';
import { BRAND } from '../brand';
import { getApiErrorMessage } from '../utils/apiError';

const Dashboard = () => {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [stats, setStats] = useState({
    totalTenants: 0,
    totalDeployments: 0,
    runningDeployments: 0,
    failedDeployments: 0,
    /** DAG-type project files with a successful deploy (same scope as DAGs page). */
    totalDeployedDags: 0,
  });

  const fetchDashboardData = useCallback(async () => {
    try {
      setLoading(true);
      let tenants = [];
      let deployments = [];
      let dagsList = [];
      if (isAdmin) {
        const [tenantsResponse, deploymentsResponse, dagsResponse] = await Promise.all([
          tenantAPI.getAll(),
          deploymentAPI.getAll(),
          deployedDagsAPI.getAll(),
        ]);
        tenants = tenantsResponse.data;
        deployments = deploymentsResponse.data;
        dagsList = dagsResponse.data || [];
      } else {
        const [deploymentsResponse, dagsResponse] = await Promise.all([
          deploymentAPI.getAll(),
          deployedDagsAPI.getAll(),
        ]);
        deployments = deploymentsResponse.data;
        dagsList = dagsResponse.data || [];
      }

      const runningDeployments = deployments.filter((d) => d.status === 'RUNNING').length;
      const failedDeployments = deployments.filter((d) => d.status === 'FAILED').length;

      setStats({
        totalTenants: tenants.length,
        totalDeployments: deployments.length,
        runningDeployments,
        failedDeployments,
        totalDeployedDags: dagsList.length,
      });

      setError(null);
    } catch (err) {
      console.error('Error fetching dashboard data:', err);
      setError(getApiErrorMessage(err, 'Failed to load dashboard data. Please try again.'));
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  const hero = (
    <div className="dashboard-hero" style={{ marginTop: 0 }}>
      <div className="dashboard-hero-mark">
        <BrandMark size="lg" />
      </div>
      <div className="dashboard-hero-title" style={{ color: 'rgba(0, 0, 0, 0.88)' }}>
        <FlowDeckWordmark size="lg" />
      </div>
      <p className="dashboard-hero-sub">{BRAND.tagline}</p>
    </div>
  );

  if (loading) {
    return (
      <div>
        {hero}
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <Spin size="large" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        {hero}
        <Alert
          message="Could not load overview"
          description={error}
          type="error"
          showIcon
          action={
            <Button size="small" onClick={fetchDashboardData}>
              Retry
            </Button>
          }
        />
      </div>
    );
  }

  const cardKeyDown = (e, path) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      navigate(path);
    }
  };

  return (
    <div>
      <PageHeader
        prepend={hero}
        title="Dashboard"
        description="Overview of tenants, deployments, and deployed DAG files in your scope. Click a card to open that area."
        extra={
          <Button icon={<ReloadOutlined />} onClick={fetchDashboardData}>
            Refresh
          </Button>
        }
      />

      <Row gutter={[16, 16]}>
        {isAdmin && (
          <Col xs={24} sm={12} lg={6}>
            <Card
              hoverable
              className="stats-card clickable-stat-card"
              onClick={() => navigate('/tenants')}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => cardKeyDown(e, '/tenants')}
            >
              <Statistic
                title="Total tenants"
                value={stats.totalTenants}
                prefix={<TeamOutlined />}
                valueStyle={{ color: '#3f8600' }}
              />
            </Card>
          </Col>
        )}
        <Col xs={24} sm={12} lg={6}>
          <Card
            hoverable
            className="stats-card clickable-stat-card"
            onClick={() => navigate('/deployments')}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => cardKeyDown(e, '/deployments')}
          >
            <Statistic
              title="Total deployments"
              value={stats.totalDeployments}
              prefix={<CloudServerOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card
            hoverable
            className="stats-card clickable-stat-card"
            onClick={() => navigate('/deployments')}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => cardKeyDown(e, '/deployments')}
          >
            <Statistic
              title="Running"
              value={stats.runningDeployments}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card
            hoverable
            className="stats-card clickable-stat-card"
            onClick={() => navigate('/deployments')}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => cardKeyDown(e, '/deployments')}
          >
            <Statistic
              title="Failed"
              value={stats.failedDeployments}
              prefix={<WarningOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card
            hoverable
            className="stats-card clickable-stat-card"
            onClick={() => navigate('/dags')}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => cardKeyDown(e, '/dags')}
          >
            <Statistic
              title="Deployed DAG files"
              value={stats.totalDeployedDags}
              prefix={<NodeIndexOutlined />}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
      </Row>

      {stats.totalDeployments === 0 && (
        <Alert
          style={{ marginTop: 24 }}
          type="info"
          showIcon
          message="Get started"
          description={
            <Space direction="vertical" size="small">
              <span>Create an Airflow deployment, then add projects and deploy them from the project browser.</span>
              <Button type="primary" onClick={() => navigate('/deployments')}>
                Go to deployments
              </Button>
            </Space>
          }
        />
      )}

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} md={12}>
          <Card size="small" title="Quick links">
            <Space wrap>
              <Button type="link" onClick={() => navigate('/projects')}>
                Project browser
              </Button>
              <Button type="link" onClick={() => navigate('/deployed-projects')}>
                Deployed projects
              </Button>
              <Button type="link" onClick={() => navigate('/dags')}>
                DAGs
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
