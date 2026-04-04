import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation, Outlet } from 'react-router-dom';
import { ConfigProvider, Layout } from 'antd';
import './App.css';
import { AuthProvider } from './context/AuthContext';
import RequireAuth from './components/RequireAuth';
import RequireAdmin from './components/RequireAdmin';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Tenants from './pages/Tenants';
import Users from './pages/Users';
import Deployments from './pages/Deployments';
import DeploymentDetails from './pages/DeploymentDetails';
import Dags from './pages/Dags';
import Projects from './pages/Projects';
import DeployedProjects from './pages/DeployedProjects';
import EnvironmentLayout from './pages/EnvironmentLayout';
import EnvironmentConnections from './pages/EnvironmentConnections';
import EnvironmentVariables from './pages/EnvironmentVariables';
import ProjectDetails from './pages/ProjectDetails';
import ProjectCodeEditor from './pages/ProjectCodeEditor';
import NotFound from './pages/NotFound';

const { Content } = Layout;

function AppLayout() {
  const location = useLocation();
  const isProjectEditor =
    location.pathname.includes('/projects/') && location.pathname.includes('/editor');

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sidebar />
      <Layout style={{ background: '#f0f2f5' }}>
        <Header />
        <Content
          className={isProjectEditor ? undefined : 'app-main-content'}
          style={
            isProjectEditor
              ? { padding: 0, background: '#fff' }
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
        <Route path="/projects" element={<Projects />} />
        <Route path="/deployed-projects" element={<DeployedProjects />} />
        <Route path="/projects/:projectId" element={<ProjectDetails />} />
        <Route path="/projects/:projectId/editor" element={<ProjectCodeEditor />} />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}

function App() {
  return (
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
  );
}

export default App;
