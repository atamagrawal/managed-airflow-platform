import React from 'react';
import { Tabs, Tag } from 'antd';
import { CloseOutlined, FileOutlined } from '@ant-design/icons';
import './EditorTabs.css';

const EditorTabs = ({ openFiles, activeFileId, onTabChange, onTabClose, modifiedFiles }) => {
  const items = openFiles.map((file) => {
    const isModified = modifiedFiles.has(file.fileId);
    const status = file.status;

    const getStatusColor = (status) => {
      const colors = {
        DRAFT: 'default',
        VALID: 'success',
        INVALID: 'error',
        DEPLOYED: 'processing',
        FAILED: 'error',
      };
      return colors[status] || 'default';
    };

    return {
      key: file.fileId,
      label: (
        <div className="editor-tab-label">
          <FileOutlined style={{ marginRight: 6 }} />
          <span className="editor-tab-filename">{file.fileName || `${file.name}.py`}</span>
          {isModified && (
            <span className="editor-tab-modified" title="Unsaved changes">
              •
            </span>
          )}
          {status && (
            <Tag
              color={getStatusColor(status)}
              size="small"
              style={{ marginLeft: 8, fontSize: 10 }}
            >
              {status}
            </Tag>
          )}
        </div>
      ),
      closeIcon: (
        <CloseOutlined
          onClick={(e) => {
            e.stopPropagation();
            onTabClose(file.fileId);
          }}
          className="editor-tab-close"
        />
      ),
    };
  });

  if (openFiles.length === 0) {
    return (
      <div className="editor-tabs-empty">
        <FileOutlined style={{ fontSize: 48, color: '#d9d9d9', marginBottom: 16 }} />
        <div style={{ color: '#8c8c8c' }}>No files open</div>
        <div style={{ color: '#bfbfbf', fontSize: 12, marginTop: 4 }}>
          Select a file from the tree to start editing
        </div>
      </div>
    );
  }

  return (
    <div className="editor-tabs-container">
      <Tabs
        type="editable-card"
        activeKey={activeFileId}
        onChange={onTabChange}
        hideAdd
        items={items}
        className="editor-tabs"
      />
    </div>
  );
};

export default EditorTabs;
