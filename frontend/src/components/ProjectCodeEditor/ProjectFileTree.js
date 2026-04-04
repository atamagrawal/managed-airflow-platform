import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Tree, Input, Spin, Empty, Dropdown, message } from 'antd';
import {
  FileOutlined,
  FolderOutlined,
  SearchOutlined,
  ReloadOutlined,
  FileTextOutlined,
  CodeOutlined,
  DockerOutlined,
  FileMarkdownOutlined,
} from '@ant-design/icons';
import './ProjectFileTree.css';

const { Search } = Input;

const getFileColor = (fileType) => {
  const colors = {
    DAG: '#1890ff',
    CONTRACT: '#eb2f96',
    PLUGIN: '#52c41a',
    INCLUDE: '#faad14',
    TEST: '#722ed1',
    UTIL: '#13c2c2',
    OTHER: '#8c8c8c',
  };
  return colors[fileType] || '#8c8c8c';
};

/** Display labels and ordering aligned with typical Airflow project layout */
const STANDARD_DIRS = [
  { path: 'dags/', label: 'dags', key: 'dags' },
  { path: 'contracts/', label: 'contracts', key: 'contracts' },
  { path: 'plugins/', label: 'plugins', key: 'plugins' },
  { path: 'include/', label: 'include', key: 'include' },
  { path: 'tests/', label: 'tests', key: 'tests' },
];

const configIcon = (name) => {
  const muted = { color: 'var(--project-tree-icon-muted)' };
  switch (name) {
    case 'Dockerfile':
      return <DockerOutlined style={muted} />;
    case 'airflow_settings.yaml':
      return <FileMarkdownOutlined style={muted} />;
    case 'requirements.txt':
    case 'packages.txt':
      return <FileTextOutlined style={muted} />;
    default:
      return <FileTextOutlined style={muted} />;
  }
};

const fileIconForLeaf = (name, fileType) => {
  if (name?.endsWith('.py')) {
    return <CodeOutlined style={{ color: getFileColor(fileType) }} />;
  }
  if (name?.endsWith('.yaml') || name?.endsWith('.yml')) {
    return <FileMarkdownOutlined style={{ color: getFileColor(fileType) }} />;
  }
  return <FileOutlined style={{ color: getFileColor(fileType) }} />;
};

const buildFolderTreeNodes = (files) => {
  const topLevel = [];

  const directories = {
    'dags/': [],
    'contracts/': [],
    'plugins/': [],
    'include/': [],
    'tests/': [],
  };

  files.forEach((file) => {
    const filePath = file.filePath || '';
    const directory = filePath.split('/')[0] + '/';

    if (directories[directory] !== undefined) {
      const fileName = file.fileName;
      directories[directory].push({
        title: fileName,
        key: `file-${file.id}`,
        icon: fileIconForLeaf(fileName, file.fileType),
        isLeaf: true,
        className: 'project-file-tree-leaf',
        ...file,
      });
    }
  });

  STANDARD_DIRS.forEach(({ path, label, key }) => {
    topLevel.push({
      title: label,
      key: `folder-${key}`,
      icon: <FolderOutlined className="project-file-tree-folder-icon" />,
      className: 'project-file-tree-folder',
      children: directories[path],
    });
  });

  return topLevel;
};

const buildConfigTreeNodes = (project) => {
  const configNodes = [];
  const pushConfigFile = (spec) => {
    configNodes.push({
      title: spec.fileName,
      key: spec.treeKey,
      icon: configIcon(spec.fileName),
      isLeaf: true,
      isConfig: true,
      className: 'project-file-tree-leaf project-file-tree-config-leaf',
      fileId: spec.fileId,
      fileName: spec.fileName,
      content: spec.content,
      configKey: spec.configKey,
    });
  };

  if (project.dockerfile) {
    pushConfigFile({
      treeKey: 'config-dockerfile',
      fileId: 'config-dockerfile',
      fileName: 'Dockerfile',
      content: project.dockerfile,
      configKey: 'dockerfile',
    });
  }
  pushConfigFile({
    treeKey: 'config-env',
    fileId: 'config-env',
    fileName: '.env',
    content: project.envFile || '',
    configKey: 'env',
  });

  if (project.requirementsTxt) {
    pushConfigFile({
      treeKey: 'config-requirements',
      fileId: 'config-requirements',
      fileName: 'requirements.txt',
      content: project.requirementsTxt,
      configKey: 'requirements',
    });
  }
  if (project.packagesTxt) {
    pushConfigFile({
      treeKey: 'config-packages',
      fileId: 'config-packages',
      fileName: 'packages.txt',
      content: project.packagesTxt,
      configKey: 'packages',
    });
  }
  if (project.airflowSettingsYaml) {
    pushConfigFile({
      treeKey: 'config-settings',
      fileId: 'config-settings',
      fileName: 'airflow_settings.yaml',
      content: project.airflowSettingsYaml,
      configKey: 'settings',
    });
  }
  if (project.airflowIgnore) {
    pushConfigFile({
      treeKey: 'config-ignore',
      fileId: 'config-ignore',
      fileName: '.airflowignore',
      content: project.airflowIgnore,
      configKey: 'ignore',
    });
  }

  return configNodes;
};

const buildProjectTreeData = (project, files) => [
  ...buildFolderTreeNodes(files || []),
  ...buildConfigTreeNodes(project),
];

const filterTreeByQuery = (data, rawQuery) => {
  const q = rawQuery.trim().toLowerCase();
  if (!q) return data;
  return data
    .map((node) => {
      if (node.isLeaf) {
        return node.title.toLowerCase().includes(q) ? node : null;
      }
      const filteredChildren = filterTreeByQuery(node.children || [], rawQuery);
      const folderMatch = node.title.toLowerCase().includes(q);
      if (filteredChildren.length > 0) {
        return { ...node, children: filteredChildren };
      }
      if (folderMatch) {
        return { ...node, children: node.children || [] };
      }
      return null;
    })
    .filter(Boolean);
};

const collectNonLeafKeys = (nodes) => {
  const keys = [];
  const walk = (list) => {
    list.forEach((node) => {
      if (!node.isLeaf) {
        keys.push(node.key);
        if (node.children?.length) walk(node.children);
      }
    });
  };
  walk(nodes);
  return keys;
};

const ProjectFileTree = ({ project, files, activeFileId, onFileSelect, onRefresh }) => {
  const [treeData, setTreeData] = useState([]);
  const [searchValue, setSearchValue] = useState('');
  const [expandedKeys, setExpandedKeys] = useState([]);
  const [loading, setLoading] = useState(false);

  const buildTree = useCallback(() => {
    if (!project) return;
    setTreeData(buildProjectTreeData(project, files || []));
  }, [project, files]);

  useEffect(() => {
    buildTree();
  }, [buildTree]);

  const searchActive = Boolean(searchValue.trim());
  const displayedTree = useMemo(
    () => (searchActive ? filterTreeByQuery(treeData, searchValue) : treeData),
    [treeData, searchActive, searchValue]
  );

  useEffect(() => {
    setExpandedKeys(collectNonLeafKeys(displayedTree));
  }, [displayedTree]);

  const handleSelect = (selectedKeys, info) => {
    if (info.node.isLeaf) {
      onFileSelect(info.node);
    }
  };

  const handleRefresh = () => {
    setLoading(true);
    if (onRefresh) {
      onRefresh();
    }
    setTimeout(() => {
      buildTree();
      setLoading(false);
      message.success('Files refreshed');
    }, 500);
  };

  const getContextMenuItems = (node) => {
    return [
      {
        key: 'refresh',
        icon: <ReloadOutlined />,
        label: 'Refresh',
        onClick: handleRefresh,
      },
    ];
  };

  const titleRender = (nodeData) => {
    const isActive = activeFileId === nodeData.fileId || activeFileId === nodeData.id?.toString();
    const extra = nodeData.className ? ` ${nodeData.className}` : '';

    return (
      <Dropdown
        menu={{ items: getContextMenuItems(nodeData) }}
        trigger={['contextMenu']}
      >
        <div className={`project-file-tree-title-row${extra} ${isActive ? 'active' : ''}`}>
          {nodeData.title}
        </div>
      </Dropdown>
    );
  };

  return (
    <div className="project-file-tree-container">
      <div className="project-file-tree-header">
        <div className="project-file-tree-header-titles">
          <div className="project-file-tree-explorer-label">Explorer</div>
          <div className="project-file-tree-title" title={project?.name}>
            {project?.name || 'Project'}
          </div>
        </div>
        <ReloadOutlined
          className="project-file-tree-refresh"
          onClick={handleRefresh}
          spin={loading}
        />
      </div>

      <div className="project-file-tree-search">
        <Search
          placeholder="Filter files..."
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          prefix={<SearchOutlined />}
          allowClear
        />
      </div>

      <div className="project-file-tree-content">
        {loading ? (
          <div className="project-file-tree-loading">
            <Spin />
          </div>
        ) : displayedTree.length === 0 ? (
          <Empty
            description="No files found"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <Tree
            showIcon
            blockNode
            treeData={displayedTree}
            expandedKeys={expandedKeys}
            onExpand={setExpandedKeys}
            onSelect={handleSelect}
            titleRender={titleRender}
            className="project-file-tree"
          />
        )}
      </div>
    </div>
  );
};

export default ProjectFileTree;
