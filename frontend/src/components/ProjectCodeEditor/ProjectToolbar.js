import React, { useMemo } from 'react';
import {
  Button,
  Dropdown,
  Switch,
  Typography,
  Badge,
  Tooltip,
} from 'antd';
import {
  SaveOutlined,
  CloudUploadOutlined,
  PlayCircleOutlined,
  FormatPainterOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  SettingOutlined,
  FileAddOutlined,
  ArrowLeftOutlined,
  DownOutlined,
  PauseCircleOutlined,
  RocketOutlined,
  ExperimentOutlined,
  LoadingOutlined,
  MinusCircleOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { BRAND } from '../../brand';
import './ProjectToolbar.css';

const { Text } = Typography;

function MenuItemBody({ title, description }) {
  return (
    <div className="toolbar-sync-menu-item">
      <span className="toolbar-sync-menu-item-title">{title}</span>
      <span className="toolbar-sync-menu-item-desc">{description}</span>
    </div>
  );
}

function FlowDeckToolbarMark() {
  return (
    <span className="flow-deck-ide-toolbar-mark" aria-hidden>
      <span className="flow-deck-ide-toolbar-mark-bar flow-deck-ide-toolbar-mark-bar--1" />
      <span className="flow-deck-ide-toolbar-mark-bar flow-deck-ide-toolbar-mark-bar--2" />
      <span className="flow-deck-ide-toolbar-mark-bar flow-deck-ide-toolbar-mark-bar--3" />
    </span>
  );
}

const ProjectToolbar = ({
  project,
  currentFile,
  isModified,
  onSave,
  onFormat,
  onDeployToEnvironment,
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
  localStackPhase,
  onStartLocalTest,
  onStopLocalTest,
  localTestDeployment,
  aiEnabled,
  aiPanelOpen,
  onToggleAiPanel,
}) => {
  const linkedCount = project?.linkedDeploymentIds?.length ?? 0;
  const canRunDags = linkedCount > 0;

  const sharedLocalStatus = localTestDeployment?.status;
  /** API-reported provisioning (row may still say STOPPED while start request is in flight). */
  const apiProvisioning =
    sharedLocalStatus === 'DEPLOYING' || sharedLocalStatus === 'PENDING';
  const inFlightStart = localStackBusy && localStackPhase === 'start';
  const inFlightStop = localStackBusy && localStackPhase === 'stop';
  /** Pill: server says provisioning, or start request is in flight (row often still STOPPED until the call finishes). */
  const showDeploying = inFlightStart || apiProvisioning;
  const showStopping = inFlightStop;
  const showInFlight = showDeploying || showStopping;
  const sharedLocalRunning = sharedLocalStatus === 'RUNNING';
  const sharedLocalStopped =
    !localTestDeployment ||
    sharedLocalStatus === 'STOPPED' ||
    sharedLocalStatus === 'FAILED';

  const syncMenuItems = useMemo(() => {
    const pushGroup = {
      type: 'group',
      label: 'Push project to Airflow',
      children: [
        {
          key: 'deploy-env',
          icon: <CloudUploadOutlined />,
          disabled: deploying,
          label: (
            <MenuItemBody
              title="Choose deployment…"
              description="Syncs DAGs and files to that environment (links the project if needed)."
            />
          ),
        },
      ],
    };

    if (deploymentProvider !== 'local') {
      return [pushGroup];
    }

    const testEnvironmentGroup = {
      type: 'group',
      label: 'Test environment',
      children: [
        {
          key: 'start-test',
          icon: <RocketOutlined />,
          disabled: localStackBusy || apiProvisioning || sharedLocalRunning,
          label: (
            <MenuItemBody
              title="Start / refresh test environment"
              description={`Creates the ${BRAND.name} test environment for this tenant if needed, builds from your project’s image config, starts the runtime, then syncs this project.`}
            />
          ),
        },
        {
          key: 'stop-test',
          icon: <PauseCircleOutlined />,
          disabled:
            localStackBusy || apiProvisioning || sharedLocalStopped,
          label: (
            <MenuItemBody
              title="Stop test environment"
              description={`Shuts down the ${BRAND.name} test environment for this tenant (other deployments unchanged).`}
            />
          ),
        },
      ],
    };

    return [pushGroup, testEnvironmentGroup];
  }, [
    deploymentProvider,
    deploying,
    localStackBusy,
    apiProvisioning,
    sharedLocalRunning,
    sharedLocalStopped,
  ]);

  const handleSyncMenuClick = ({ key, domEvent }) => {
    domEvent?.stopPropagation();
    if (key === 'deploy-env') onDeployToEnvironment?.();
    else if (key === 'start-test') onStartLocalTest?.();
    else if (key === 'stop-test') onStopLocalTest?.();
  };

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

  let testEnvState = 'off';
  if (sharedLocalRunning) testEnvState = 'running';
  else if (showInFlight) testEnvState = 'starting';
  else if (localTestDeployment) testEnvState = 'stopped';

  const testEnvTooltip = `${BRAND.name} test environment for this tenant (one row per tenant). Start it from Sync → Test environment to run Airflow with this project’s build. Run DAGs stays disabled until the project is linked to a deployment.`;

  const testEnvStatusEl =
    deploymentProvider === 'local' ? (
      <Tooltip title={testEnvTooltip} placement="bottom">
        <div
          className={`flow-deck-test-env flow-deck-test-env--${testEnvState}`}
          role="status"
          aria-live="polite"
        >
          <span className="flow-deck-test-env-glyph" aria-hidden>
            <ExperimentOutlined />
          </span>
          <span className="flow-deck-test-env-body">
            <span className="flow-deck-test-env-kicker">Test env</span>
            <span className="flow-deck-test-env-line">
              {sharedLocalRunning && (
                <>
                  <span className="flow-deck-test-env-dot flow-deck-test-env-dot--running" />
                  <span>Ready</span>
                </>
              )}
              {showStopping && (
                <>
                  <LoadingOutlined spin className="flow-deck-test-env-state-icon" />
                  <span>Stopping…</span>
                </>
              )}
              {showDeploying && !showStopping && (
                <>
                  <LoadingOutlined spin className="flow-deck-test-env-state-icon" />
                  <span>Deploying…</span>
                </>
              )}
              {!sharedLocalRunning && !showInFlight && localTestDeployment && (
                <>
                  <MinusCircleOutlined className="flow-deck-test-env-state-icon flow-deck-test-env-state-icon--muted" />
                  <span>Stopped</span>
                </>
              )}
              {!sharedLocalRunning && !showInFlight && !localTestDeployment && (
                <>
                  <span className="flow-deck-test-env-dot flow-deck-test-env-dot--off" />
                  <span>Not started</span>
                </>
              )}
            </span>
          </span>
        </div>
      </Tooltip>
    ) : null;

  return (
    <div className="flow-deck-ide-toolbar">
      <div className="flow-deck-ide-toolbar-accent" aria-hidden />
      <div className="toolbar-section toolbar-section--left">
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={onBack}
          size="small"
          type="text"
          className="flow-deck-ide-toolbar-btn-ghost"
          title={`Back to ${BRAND.navProjects}`}
        >
          {BRAND.navProjects}
        </Button>
        <span className="flow-deck-ide-toolbar-divider" aria-hidden />
        <Button
          icon={<FileAddOutlined />}
          onClick={onNewFile}
          size="small"
          type="text"
          className="flow-deck-ide-toolbar-btn-ghost"
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
            className={!isModified ? 'flow-deck-ide-toolbar-btn-ghost' : undefined}
          >
            Save
          </Button>
        </Badge>
        <Tooltip title="Format Python (basic)">
          <Button
            icon={<FormatPainterOutlined />}
            onClick={onFormat}
            disabled={!currentFile}
            size="small"
            type="text"
            className="flow-deck-ide-toolbar-btn-ghost"
          />
        </Tooltip>
      </div>

      <div className="toolbar-section toolbar-center">
        <div className="flow-deck-ide-brand-chip">
          <FlowDeckToolbarMark />
          <div className="flow-deck-ide-brand-chip-text">
            <span className="flow-deck-ide-brand-chip-name">{BRAND.ideName}</span>
            {currentFile && (
              <span className="flow-deck-ide-brand-chip-file" title={currentFile.name || currentFile.fileName}>
                {currentFile.name || currentFile.fileName}
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="toolbar-section toolbar-right">
        {testEnvStatusEl}
        <div className="flow-deck-ide-action-cluster">
          <Dropdown
            menu={{
              items: syncMenuItems,
              onClick: handleSyncMenuClick,
            }}
            trigger={['click']}
            placement="bottomRight"
            popupClassName="flow-deck-sync-menu-root"
          >
            <Button
              type="default"
              icon={<CloudUploadOutlined />}
              size="small"
              loading={deploying}
              className="flow-deck-ide-sync-btn"
              aria-label="Sync project to Airflow or manage the test environment"
              title={`Push DAGs to a deployment, or start/stop the ${BRAND.name} test environment.`}
            >
              Sync
              <DownOutlined className="flow-deck-ide-sync-caret" />
            </Button>
          </Dropdown>
          <Button
            type="primary"
            icon={<PlayCircleOutlined />}
            onClick={onTrigger}
            loading={triggering}
            disabled={!canRunDags}
            title={
              canRunDags
                ? 'Open the DAG picker and run a task in a linked deployment.'
                : 'Push this project to at least one deployment first (Sync → Choose deployment…, or start the test environment).'
            }
            size="small"
            className={`flow-deck-ide-run-btn${canRunDags ? ' flow-deck-ide-run-btn--active' : ''}`}
          >
            Run DAGs
          </Button>
        </div>
        <span className="flow-deck-ide-toolbar-divider flow-deck-ide-toolbar-divider--vertical" aria-hidden />
        {aiEnabled && (
          <Tooltip title={aiPanelOpen ? 'Close AI assistant' : 'Open AI assistant'}>
            <Button
              icon={<RobotOutlined />}
              onClick={onToggleAiPanel}
              size="small"
              type="text"
              className={`flow-deck-ide-toolbar-btn-ghost${aiPanelOpen ? ' flow-deck-ide-toolbar-btn-ghost--active' : ''}`}
              aria-label="AI assistant"
            />
          </Tooltip>
        )}
        <Button
          icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
          onClick={onFullscreen}
          size="small"
          type="text"
          className="flow-deck-ide-toolbar-btn-ghost"
        />
        <Dropdown
          menu={{ items: settingsMenuItems }}
          placement="bottomRight"
          trigger={['click']}
        >
          <Button icon={<SettingOutlined />} size="small" type="text" className="flow-deck-ide-toolbar-btn-ghost" />
        </Dropdown>
      </div>
    </div>
  );
};

export default ProjectToolbar;
