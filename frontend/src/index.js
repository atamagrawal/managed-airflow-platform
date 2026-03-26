import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';

// Monaco editor disabled - using simple textarea editor for reliability
console.log('[CodeEditor] Using fallback textarea editor (Monaco disabled)');

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
