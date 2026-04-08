import React, { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation, Outlet } from 'react-router-dom';
import { ConfigProvider, Layout, Spin } from 'antd';
import './App.css';
import { AuthProvider } from './context/AuthContext';
import RequireAuth from './components/RequireAuth';
import RequireAdmin from './components/RequireAdmin';
import Sidebar from './components/Sidebar';
import Header from './components/Header';

const Login = lazy(() => import('./pages/Login'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Tenants = lazy(() => import('./pages/Tenants'));
const Users = lazy(() => import('./pages/Users'));
const Deployments = lazy(() => import('./pages/Deployments'));
const DeploymentDetails = lazy(() => import('./pages/DeploymentDetails'));
const Dags = lazy(() => import('./pages/Dags'));
const DagRuns = lazy(() => import('./pages/DagRuns'));
const DagDebug = lazy(() => import('./pages/DagDebug'));
const Projects = lazy(() => import('./pages/Projects'));
const DeployedProjects = lazy(() => import('./pages/DeployedProjects'));
const EnvironmentLayout = lazy(() => import('./pages/EnvironmentLayout'));
const EnvironmentConnections = lazy(() => import('./pages/EnvironmentConnections'));
const EnvironmentVariables = lazy(() => import('./pages/EnvironmentVariables'));
const ProjectDetails = lazy(() => import('./pages/ProjectDetails'));
const ProjectCodeEditor = lazy(() => import('./pages/ProjectCodeEditor'));
const NotFound = lazy(() => import('./pages/NotFound'));

function RouteFallback() {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: '100%',
        minHeight: '40vh',
      }}
    >
      <Spin size="large" />
    </div>
  );
}

const { Content } = Layout;

function AppLayout() {
  const location = useLocation();
  const isFlowDeckIde =
    location.pathname.includes('/projects/') && location.pathname.includes('/editor');

  const ideShellLayoutStyle = isFlowDeckIde
    ? {
        flex: '1 1 0%',
        minWidth: 0,
        minHeight: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        background: '#f0f2f5',
      }
    : { background: '#f0f2f5' };

  const ideContentStyle = isFlowDeckIde
    ? {
        padding: 0,
        background: '#fff',
        flex: '1 1 0px',
        minHeight: 0,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }
    : undefined;

  return (
    <Layout
      className={isFlowDeckIde ? 'flow-deck-ide-app-root' : undefined}
      style={{
        display: 'flex',
        flex: '1 1 0%',
        minHeight: 0,
        height: '100%',
        width: '100%',
        ...(isFlowDeckIde ? { overflow: 'hidden' } : {}),
      }}
    >
      <Sidebar />
      <Layout
        className={isFlowDeckIde ? 'flow-deck-ide-app-inner' : undefined}
        style={ideShellLayoutStyle}
      >
        <Header />
        <Content
          className={isFlowDeckIde ? 'flow-deck-ide-app-content' : 'app-main-content'}
          style={
            isFlowDeckIde
              ? ideContentStyle
              : {
                  margin: '20px 16px 32px',
                  padding: '24px 24px 32px',
                  background: '#fff',
                  borderRadius: 12,
                  boxShadow: '0 1px 2px rgba(0, 0, 0, 0.04)',
                  maxWidth: 1440,
                  width: '100%',
                  marginLeft: 'auto',
                  marginRight: 'auto',
                }
          }
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}

function AuthenticatedShell() {
  return (
    <RequireAuth>
      <AppLayout />
    </RequireAuth>
  );
}

function AppRoutes() {
  return (
    <Suspense fallback={<RouteFallback />}>
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<AuthenticatedShell />}>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route
          path="/tenants"
          element={
            <RequireAdmin>
              <Tenants />
            </RequireAdmin>
          }
        />
        <Route
          path="/users"
          element={
            <RequireAdmin>
              <Users />
            </RequireAdmin>
          }
        />
        <Route path="/deployments" element={<Deployments />} />
        <Route path="/deployments/:deploymentId" element={<DeploymentDetails />} />
        <Route path="/environment" element={<EnvironmentLayout />}>
          <Route index element={<Navigate to="connections" replace />} />
          <Route path="connections" element={<EnvironmentConnections />} />
          <Route path="variables" element={<EnvironmentVariables />} />
        </Route>
        <Route path="/dags" element={<Dags />} />
        <Route path="/dag-runs" element={<DagRuns />} />
        <Route path="/dag-debug" element={<DagDebug />} />
        <Route path="/projects" element={<Projects />} />
        <Route path="/deployed-projects" element={<DeployedProjects />} />
        <Route path="/projects/:projectId" element={<ProjectDetails />} />
        <Route path="/projects/:projectId/editor" element={<ProjectCodeEditor />} />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
    </Suspense>
  );
}

function App() {
  return (
    <div className="app-viewport">
      <ConfigProvider
        theme={{
          token: {
            borderRadiusLG: 10,
            fontFamily: "'Plus Jakarta Sans', system-ui, -apple-system, 'Segoe UI', sans-serif",
          },
        }}
      >
        <Router>
          <AuthProvider>
            <AppRoutes />
          </AuthProvider>
        </Router>
      </ConfigProvider>
    </div>
  );
}

export default App;
