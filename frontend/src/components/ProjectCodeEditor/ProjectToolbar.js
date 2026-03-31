import React from 'react';
import {
  Button,
  Dropdown,
  Switch,
  Typography,
  Badge,
} from 'antd';
import {
  SaveOutlined,
  RocketOutlined,
  PlayCircleOutlined,
  FormatPainterOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  SettingOutlined,
  FileAddOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import './ProjectToolbar.css';

const { Text } = Typography;

const ProjectToolbar = ({
  project,
  currentFile,
  isModified,
  onSave,
  onFormat,
  onDeploy,
  onTrigger,
  onFullscreen,
  isFullscreen,
  onNewFile,
  onBack,
  editorSettings,
  onSettingsChange,
  saving,
  deploying,
  triggering,
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

  return (
    <div className="project-editor-toolbar">
      <div className="toolbar-section">
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={onBack}
          size="small"
          type="text"
        >
          Back
        </Button>
        <Button
          icon={<FileAddOutlined />}
          onClick={onNewFile}
          size="small"
          type="text"
        >
          New
        </Button>
        <Badge dot={isModified} offset={[-5, 5]}>
          <Button
            type={isModified ? 'primary' : 'text'}
            icon={<SaveOutlined />}
            onClick={onSave}
            disabled={!currentFile || !isModified}
            loading={saving}
            size="small"
          >
            Save
          </Button>
        </Badge>
        <Button
          icon={<FormatPainterOutlined />}
          onClick={onFormat}
          disabled={!currentFile}
          size="small"
          type="text"
        />
      </div>

      <div className="toolbar-section toolbar-center">
        {currentFile && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {currentFile.name || currentFile.fileName}
          </Text>
        )}
      </div>

      <div className="toolbar-section toolbar-right">
        <Button
          type="primary"
          icon={<RocketOutlined />}
          onClick={onDeploy}
          loading={deploying}
          size="small"
        >
          Deploy
        </Button>
        {project?.status === 'DEPLOYED' && (
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={onTrigger}
            loading={triggering}
            size="small"
            style={{ backgroundColor: '#52c41a', borderColor: '#52c41a' }}
          >
            Trigger
          </Button>
        )}
        <Button
          icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
          onClick={onFullscreen}
          size="small"
          type="text"
        />
        <Dropdown
          menu={{ items: settingsMenuItems }}
          placement="bottomRight"
          trigger={['click']}
        >
          <Button icon={<SettingOutlined />} size="small" type="text" />
        </Dropdown>
      </div>
    </div>
  );
};

export default ProjectToolbar;
