import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import { BRAND, defaultHtmlMetaDescription } from './brand';

document.title = BRAND.name;
const metaDesc = document.querySelector('meta[name="description"]');
if (metaDesc) {
  metaDesc.setAttribute('content', defaultHtmlMetaDescription());
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
