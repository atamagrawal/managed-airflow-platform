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
  MoreOutlined,
  PauseCircleOutlined,
} from '@ant-design/icons';
import { BRAND } from '../../brand';
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
  deploymentProvider,
  localStackBusy,
  onStartTestCluster,
  onStopTestCluster,
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
    <div className="flow-deck-ide-toolbar">
      <div className="toolbar-section">
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={onBack}
          size="small"
          type="text"
          title={`Back to ${BRAND.navProjects}`}
        >
          {BRAND.navProjects}
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
        <Text type="secondary" style={{ fontSize: 11, letterSpacing: '0.02em' }}>
          {BRAND.ideName}
        </Text>
        {currentFile && (
          <>
            <Text type="secondary" style={{ fontSize: 11, margin: '0 6px' }}>
              ·
            </Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {currentFile.name || currentFile.fileName}
            </Text>
          </>
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
        {project?.linkedDeploymentIds?.length > 0 && (
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
        {deploymentProvider === 'local' && (
          <Dropdown
            menu={{
              items: [
                {
                  key: 'start-test',
                  label: 'Start test cluster (Docker)',
                  icon: <PlayCircleOutlined />,
                  disabled: localStackBusy,
                  onClick: () => onStartTestCluster?.(),
                },
                {
                  key: 'stop-test',
                  label: 'Stop test cluster',
                  icon: <PauseCircleOutlined />,
                  disabled: localStackBusy,
                  onClick: () => onStopTestCluster?.(),
                },
              ],
            }}
            trigger={['click']}
            placement="bottomRight"
          >
            <Button
              type="text"
              icon={<MoreOutlined style={{ fontSize: 16 }} />}
              size="small"
              aria-label="Local test cluster"
              title="Start or stop local Docker test cluster"
            />
          </Dropdown>
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
