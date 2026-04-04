import React from 'react';
import { Breadcrumb, Typography } from 'antd';
import { useLocation } from 'react-router-dom';
import { getBreadcrumbItems } from '../utils/breadcrumbs';

const { Title, Paragraph } = Typography;

/**
 * Consistent page chrome: breadcrumb, title, optional description, optional actions.
 */
export default function PageHeader({ title, description, extra, prepend, showBreadcrumb = true }) {
  const { pathname } = useLocation();

  return (
    <header className="page-shell-header">
      {prepend}
      {showBreadcrumb ? (
        <Breadcrumb className="page-shell-breadcrumb" items={getBreadcrumbItems(pathname)} />
      ) : null}
      <div className="page-shell-header-row">
        <div className="page-shell-header-titles">
          <Title level={2} className="page-shell-title">
            {title}
          </Title>
          {description ? (
            <Paragraph type="secondary" className="page-shell-description">
              {description}
            </Paragraph>
          ) : null}
        </div>
        {extra != null && extra !== false ? (
          <div className="page-shell-header-extra">{extra}</div>
        ) : null}
      </div>
    </header>
  );
}
