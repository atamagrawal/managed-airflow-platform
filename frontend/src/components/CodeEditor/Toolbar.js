import React from 'react';
import {
  Button,
  Space,
  Tooltip,
  Dropdown,
  Switch,
  Divider,
  Typography,
  Badge,
} from 'antd';
import {
  SaveOutlined,
  RocketOutlined,
  FormatPainterOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  SettingOutlined,
  PlayCircleOutlined,
  FileAddOutlined,
  FolderOutlined,
} from '@ant-design/icons';
import './Toolbar.css';

const { Text } = Typography;

const Toolbar = ({
  currentFile,
  isModified,
  onSave,
  onFormat,
  onDeploy,
  onTrigger,
  onFullscreen,
  isFullscreen,
  onNewFile,
  onManageProjects,
  editorSettings,
  onSettingsChange,
  saving,
  deploying,
}) => {
  const settingsMenuItems = [
    {
      key: 'theme',
      label: (
        <div className="toolbar-settings-item">
          <Text>Dark Theme</Text>
          <Switch
            size="small"
            checked={editorSettings?.theme === 'vs-dark'}
            onChange={(checked) =>
              onSettingsChange({ theme: checked ? 'vs-dark' : 'light' })
            }
          />
        </div>
      ),
    },
    {
      key: 'minimap',
      label: (
        <div className="toolbar-settings-item">
          <Text>Minimap</Text>
          <Switch
            size="small"
            checked={editorSettings?.minimap}
            onChange={(checked) => onSettingsChange({ minimap: checked })}
          />
        </div>
      ),
    },
    {
      key: 'lineNumbers',
      label: (
        <div className="toolbar-settings-item">
          <Text>Line Numbers</Text>
          <Switch
            size="small"
            checked={editorSettings?.lineNumbers !== 'off'}
            onChange={(checked) =>
              onSettingsChange({ lineNumbers: checked ? 'on' : 'off' })
            }
          />
        </div>
      ),
    },
    {
      key: 'wordWrap',
      label: (
        <div className="toolbar-settings-item">
          <Text>Word Wrap</Text>
          <Switch
            size="small"
            checked={editorSettings?.wordWrap === 'on'}
            onChange={(checked) =>
              onSettingsChange({ wordWrap: checked ? 'on' : 'off' })
            }
          />
        </div>
      ),
    },
    {
      type: 'divider',
    },
    {
      key: 'fontSize',
      label: (
        <div className="toolbar-settings-item">
          <Text>Font Size: {editorSettings?.fontSize || 14}px</Text>
        </div>
      ),
    },
  ];

  // Allow deploying DRAFT (saved without deployment) and VALID DAGs
  const canDeploy = currentFile && (currentFile.status === 'DRAFT' || currentFile.status === 'VALID');
  const canTrigger = currentFile && currentFile.status === 'DEPLOYED';

  return (
    <div className="editor-toolbar">
      <div className="toolbar-section">
        <Space size="small">
          <Tooltip title="New File (Ctrl+N)">
            <Button
              icon={<FileAddOutlined />}
              onClick={onNewFile}
              size="small"
            >
              New
            </Button>
          </Tooltip>

          <Tooltip title="Manage Projects">
            <Button
              icon={<FolderOutlined />}
              onClick={onManageProjects}
              size="small"
            >
              Projects
            </Button>
          </Tooltip>

          <Divider type="vertical" />

          <Tooltip title="Save (Ctrl+S)">
            <Badge dot={isModified} offset={[-5, 5]}>
              <Button
                type={isModified ? 'primary' : 'default'}
                icon={<SaveOutlined />}
                onClick={onSave}
                disabled={!currentFile || !isModified}
                loading={saving}
                size="small"
              >
                Save
              </Button>
            </Badge>
          </Tooltip>

          <Tooltip title="Format Code (Alt+Shift+F)">
            <Button
              icon={<FormatPainterOutlined />}
              onClick={onFormat}
              disabled={!currentFile}
              size="small"
            >
              Format
            </Button>
          </Tooltip>
        </Space>
      </div>

      <div className="toolbar-section toolbar-center">
        {currentFile && (
          <Space size="middle">
            <Text type="secondary" style={{ fontSize: 12 }}>
              {currentFile.fileName || `${currentFile.name}.py`}
            </Text>
            {currentFile.deploymentId && (
              <>
                <Divider type="vertical" style={{ margin: 0 }} />
                <Text type="secondary" style={{ fontSize: 11 }}>
                  Deployment: {currentFile.deploymentId}
                </Text>
              </>
            )}
          </Space>
        )}
      </div>

      <div className="toolbar-section toolbar-right">
        <Space size="small">
          {canTrigger && (
            <Tooltip title="Trigger DAG">
              <Button
                type="default"
                icon={<PlayCircleOutlined />}
                onClick={onTrigger}
                size="small"
              >
                Trigger
              </Button>
            </Tooltip>
          )}

          {canDeploy && (
            <Tooltip title="Deploy to Airflow">
              <Button
                type="primary"
                icon={<RocketOutlined />}
                onClick={onDeploy}
                loading={deploying}
                size="small"
              >
                Deploy
              </Button>
            </Tooltip>
          )}

          <Divider type="vertical" />

          <Tooltip title={isFullscreen ? 'Exit Fullscreen' : 'Fullscreen'}>
            <Button
              icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
              onClick={onFullscreen}
              size="small"
            />
          </Tooltip>

          <Dropdown
            menu={{ items: settingsMenuItems }}
            placement="bottomRight"
            trigger={['click']}
          >
            <Button icon={<SettingOutlined />} size="small">
              Settings
            </Button>
          </Dropdown>
        </Space>
      </div>
    </div>
  );
};

export default Toolbar;
