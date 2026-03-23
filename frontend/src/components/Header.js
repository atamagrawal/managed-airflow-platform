import React from 'react';
import { Layout, Typography } from 'antd';

const { Header: AntHeader } = Layout;
const { Title } = Typography;

const Header = () => {
  return (
    <AntHeader style={{ background: '#fff', padding: '0 24px' }}>
      <Title level={3} style={{ margin: '16px 0' }}>
        Managed Airflow Control Plane
      </Title>
    </AntHeader>
  );
};

export default Header;
