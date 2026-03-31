import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Layout } from 'antd';
import './App.css';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import Dashboard from './pages/Dashboard';
import Tenants from './pages/Tenants';
import Deployments from './pages/Deployments';
import DeploymentDetails from './pages/DeploymentDetails';
import Dags from './pages/Dags';
import DagForm from './pages/DagForm';
import DagDetails from './pages/DagDetails';
import Projects from './pages/Projects';
import DeployedProjects from './pages/DeployedProjects';
import ProjectDetails from './pages/ProjectDetails';
import ProjectCodeEditor from './pages/ProjectCodeEditor';
import CodeEditor from './pages/CodeEditor';

const { Content } = Layout;

function AppContent() {
  const location = useLocation();
  const isCodeEditor = location.pathname.startsWith('/code-editor') || location.pathname.includes('/editor');

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sidebar />
      <Layout>
        <Header />
        <Content
          style={
            isCodeEditor
              ? { padding: 0, background: '#fff' }
              : { margin: '24px 16px', padding: 24, background: '#fff' }
          }
        >
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/tenants" element={<Tenants />} />
            <Route path="/deployments" element={<Deployments />} />
            <Route path="/deployments/:deploymentId" element={<DeploymentDetails />} />
            <Route path="/projects" element={<Projects />} />
            <Route path="/deployed-projects" element={<DeployedProjects />} />
            <Route path="/projects/:projectId" element={<ProjectDetails />} />
            <Route path="/projects/:projectId/editor" element={<ProjectCodeEditor />} />
            <Route path="/dags" element={<Dags />} />
            <Route path="/dags/new" element={<DagForm />} />
            <Route path="/dags/create" element={<DagForm />} />
            <Route path="/dags/:dagId" element={<DagDetails />} />
            <Route path="/dags/:dagId/edit" element={<DagForm />} />
            <Route path="/code-editor" element={<CodeEditor />} />
            <Route path="/dag-editor" element={<CodeEditor />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

function App() {
  return (
    <Router>
      <AppContent />
    </Router>
  );
}

export default App;
