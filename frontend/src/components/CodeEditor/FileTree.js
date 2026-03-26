import React, { useState, useEffect, useCallback } from 'react';
import { Tree, Input, Spin, Empty, Dropdown, message, Modal } from 'antd';
import {
  FileOutlined,
  FolderOutlined,
  SearchOutlined,
  DeleteOutlined,
  EditOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { dagAPI, deploymentAPI } from '../../services/api';
import './FileTree.css';

const { Search } = Input;

const buildTreeData = (deploymentsList, dagsList) => {
  const treeNodes = [];

  // 1. Group DRAFT DAGs (no deployment) by project
  const draftDags = dagsList.filter((dag) => !dag.deploymentId);
  if (draftDags.length > 0) {
    // Group by project (stored in tags field)
    const projectGroups = {};
    draftDags.forEach((dag) => {
      const projectName = dag.tags || 'Uncategorized';
      if (!projectGroups[projectName]) {
        projectGroups[projectName] = [];
      }
      projectGroups[projectName].push(dag);
    });

    // Add project nodes
    Object.entries(projectGroups).forEach(([projectName, dags]) => {
      treeNodes.push({
        title: `${projectName} (Draft)`,
        key: `project-${projectName}`,
        icon: <FolderOutlined style={{ color: '#52c41a' }} />,
        children: dags.map((dag) => ({
          title: dag.fileName || `${dag.name}.py`,
          key: `dag-${dag.dagId}`,
          icon: <FileOutlined style={{ color: '#52c41a' }} />,
          isLeaf: true,
          dag: dag,
        })),
      });
    });
  }

  // 2. Group deployed DAGs by deployment
  const deployedDags = dagsList.filter((dag) => dag.deploymentId);
  deploymentsList.forEach((deployment) => {
    const deploymentDags = deployedDags.filter(
      (dag) => dag.deploymentId === deployment.deploymentId
    );

    if (deploymentDags.length > 0) {
      treeNodes.push({
        title: `${deployment.name} (Deployed)`,
        key: `deployment-${deployment.deploymentId}`,
        icon: <FolderOutlined style={{ color: '#1890ff' }} />,
        children: deploymentDags.map((dag) => ({
          title: dag.fileName || `${dag.name}.py`,
          key: `dag-${dag.dagId}`,
          icon: <FileOutlined style={{ color: '#1890ff' }} />,
          isLeaf: true,
          dag: dag,
        })),
      });
    }
  });

  return treeNodes;
};

const FileTree = ({ onFileSelect, activeFileId, onRefresh }) => {
  const [treeData, setTreeData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const [expandedKeys, setExpandedKeys] = useState([]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [deploymentsRes, dagsRes] = await Promise.all([
        deploymentAPI.getAll(),
        dagAPI.getAll(),
      ]);

      const deploymentsList = deploymentsRes.data;
      const dagsList = dagsRes.data;

      // Build tree structure
      const tree = buildTreeData(deploymentsList, dagsList);
      setTreeData(tree);

      // Auto-expand first folder (project or deployment)
      if (tree.length > 0) {
        setExpandedKeys([tree[0].key]);
      }
    } catch (error) {
      message.error('Failed to load files');
      console.error('Error loading data:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSelect = (selectedKeys, info) => {
    if (info.node.isLeaf && info.node.dag) {
      onFileSelect(info.node.dag);
    }
  };

  const getFilteredTreeData = () => {
    if (!searchValue) return treeData;

    const filterTree = (data) => {
      return data
        .map((node) => {
          if (node.isLeaf) {
            // Filter leaf nodes by title
            if (node.title.toLowerCase().includes(searchValue.toLowerCase())) {
              return node;
            }
            return null;
          } else {
            // Filter parent nodes recursively
            const filteredChildren = filterTree(node.children || []);
            if (filteredChildren.length > 0) {
              return { ...node, children: filteredChildren };
            }
            return null;
          }
        })
        .filter(Boolean);
    };

    return filterTree(treeData);
  };

  const handleRefresh = () => {
    loadData();
    if (onRefresh) onRefresh();
  };

  const getContextMenuItems = (node) => {
    const items = [
      {
        key: 'refresh',
        icon: <ReloadOutlined />,
        label: 'Refresh',
        onClick: handleRefresh,
      },
    ];

    if (node.isLeaf && node.dag) {
      items.unshift(
        {
          key: 'edit',
          icon: <EditOutlined />,
          label: 'Edit',
          onClick: () => onFileSelect(node.dag),
        },
        {
          key: 'delete',
          icon: <DeleteOutlined />,
          label: 'Delete',
          danger: true,
          onClick: () => handleDelete(node.dag),
        }
      );
    }

    return items;
  };

  const handleDelete = (dag) => {
    Modal.confirm({
      title: 'Delete DAG',
      content: `Are you sure you want to delete ${dag.name}?`,
      okText: 'Delete',
      okType: 'danger',
      onOk: async () => {
        try {
          await dagAPI.delete(dag.dagId);
          message.success('DAG deleted successfully');
          handleRefresh();
        } catch (error) {
          message.error('Failed to delete DAG');
          console.error('Error deleting DAG:', error);
        }
      },
    });
  };

  const titleRender = (nodeData) => {
    const isActive = activeFileId === nodeData.dag?.dagId;

    return (
      <Dropdown
        menu={{ items: getContextMenuItems(nodeData) }}
        trigger={['contextMenu']}
      >
        <div
          style={{
            padding: '2px 4px',
            borderRadius: '4px',
            backgroundColor: isActive ? '#1890ff20' : 'transparent',
            fontWeight: isActive ? 600 : 400,
          }}
        >
          {nodeData.title}
        </div>
      </Dropdown>
    );
  };

  return (
    <div className="file-tree-container">
      <div className="file-tree-header">
        <div className="file-tree-title">Files</div>
        <ReloadOutlined
          className="file-tree-refresh"
          onClick={handleRefresh}
          spin={loading}
        />
      </div>

      <div className="file-tree-search">
        <Search
          placeholder="Search files..."
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          prefix={<SearchOutlined />}
          allowClear
        />
      </div>

      <div className="file-tree-content">
        {loading ? (
          <div className="file-tree-loading">
            <Spin />
          </div>
        ) : getFilteredTreeData().length === 0 ? (
          <Empty
            description="No files found"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          <Tree
            showIcon
            treeData={getFilteredTreeData()}
            expandedKeys={expandedKeys}
            onExpand={setExpandedKeys}
            onSelect={handleSelect}
            titleRender={titleRender}
            className="file-tree"
          />
        )}
      </div>
    </div>
  );
};

export default FileTree;
