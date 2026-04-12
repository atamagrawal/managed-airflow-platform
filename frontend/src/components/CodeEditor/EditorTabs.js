import React from 'react';
import { Tag } from 'antd';
import { CloseOutlined, FileOutlined } from '@ant-design/icons';
import './EditorTabs.css';

const EditorTabs = ({ openFiles, activeFileId, onTabChange, onTabClose, modifiedFiles }) => {
  if (openFiles.length === 0) {
    return null;
  }

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

  return (
    <div className="editor-tabs-container editor-tab-bar-root" role="tablist" aria-label="Open files">
      <div className="editor-tab-bar-scroll">
        {openFiles.map((file) => {
          const isActive = file.fileId === activeFileId;
          const isModified = modifiedFiles.has(file.fileId);
          const status = file.status;
          return (
            <div
              key={file.fileId}
              role="tab"
              tabIndex={isActive ? 0 : -1}
              aria-selected={isActive}
              className={`editor-tab-chip ${isActive ? 'editor-tab-chip--active' : ''}`}
              onClick={() => onTabChange(file.fileId)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onTabChange(file.fileId);
                }
              }}
            >
              <span className="editor-tab-label">
                <FileOutlined style={{ marginRight: 6 }} />
                <span className="editor-tab-filename">
                  {file.name || file.fileName || 'Untitled'}
                </span>
                {isModified && (
                  <span className="editor-tab-modified" title="Unsaved changes">
                    •
                  </span>
                )}
                {status && (
                  <Tag color={getStatusColor(status)} style={{ marginLeft: 8, fontSize: 10 }}>
                    {status}
                  </Tag>
                )}
              </span>
              <CloseOutlined
                className="editor-tab-close"
                onClick={(e) => {
                  e.stopPropagation();
                  onTabClose(file.fileId);
                }}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default EditorTabs;
