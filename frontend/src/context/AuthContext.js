import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authAPI } from '../services/api';
import { getStoredToken, setStoredToken } from '../authStorage';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => getStoredToken());
  const [user, setUser] = useState(null);
  const [authReady, setAuthReady] = useState(false);

  const refreshMe = useCallback(async () => {
    const t = getStoredToken();
    if (!t) {
      setUser(null);
      setAuthReady(true);
      return;
    }
    try {
      const { data } = await authAPI.me();
      setUser(data);
    } catch {
      setStoredToken(null);
      setToken(null);
      setUser(null);
    } finally {
      setAuthReady(true);
    }
  }, []);

  useEffect(() => {
    setAuthReady(false);
    refreshMe();
  }, [refreshMe]);

  const login = useCallback(async (username, password) => {
    const { data } = await authAPI.login(username, password);
    const access = data.accessToken;
    setStoredToken(access);
    setToken(access);
    setUser({
      username: data.username,
      roles: data.roles || [],
      tenantScope: data.tenantScope,
      admin: data.admin,
    });
    setAuthReady(true);
    return data;
  }, []);

  const logout = useCallback(() => {
    setStoredToken(null);
    setToken(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({
      token,
      user,
      authReady,
      login,
      logout,
      refreshMe,
      isAdmin: Boolean(user?.admin),
      tenantScope: user?.tenantScope ?? null,
    }),
    [token, user, authReady, login, logout, refreshMe]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
