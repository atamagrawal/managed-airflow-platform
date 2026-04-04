import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation, Outlet } from 'react-router-dom';
import { Layout } from 'antd';
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
import ProjectDetails from './pages/ProjectDetails';
import ProjectCodeEditor from './pages/ProjectCodeEditor';

const { Content } = Layout;

function AppLayout() {
  const location = useLocation();
  const isProjectEditor =
    location.pathname.includes('/projects/') && location.pathname.includes('/editor');

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sidebar />
      <Layout>
        <Header />
        <Content
          style={
            isProjectEditor
              ? { padding: 0, background: '#fff' }
              : { margin: '24px 16px', padding: 24, background: '#fff' }
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
        <Route path="/dags" element={<Dags />} />
        <Route path="/projects" element={<Projects />} />
        <Route path="/deployed-projects" element={<DeployedProjects />} />
        <Route path="/projects/:projectId" element={<ProjectDetails />} />
        <Route path="/projects/:projectId/editor" element={<ProjectCodeEditor />} />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  );
}

function App() {
  return (
    <Router>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </Router>
  );
}

export default App;
