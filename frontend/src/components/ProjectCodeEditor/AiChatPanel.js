import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Button, Input, Tooltip, Popover, Select, message as antMessage } from 'antd';
import {
  SendOutlined,
  DeleteOutlined,
  RobotOutlined,
  UserOutlined,
  CopyOutlined,
  CheckOutlined,
  KeyOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  ToolOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { aiAPI, projectAPI } from '../../services/api';
import './AiChatPanel.css';

const { TextArea } = Input;
const { Option } = Select;

/** localStorage key for the full provider config object. */
const LS_CONFIG_KEY = 'ai_provider_config';

/** Migrate stale configs — swap the high-TPM-cost 70b default for 8b-instant. */
(function migrateConfig() {
  try {
    const raw = localStorage.getItem(LS_CONFIG_KEY);
    if (!raw) return;
    const cfg = JSON.parse(raw);
    if (cfg.provider === 'groq' && cfg.model === 'llama-3.3-70b-versatile') {
      cfg.model = 'llama-3.1-8b-instant';
      localStorage.setItem(LS_CONFIG_KEY, JSON.stringify(cfg));
    }
  } catch {}
})();

const PROVIDERS = [
  {
    id: 'groq',
    label: 'Groq',
    badge: 'Free · Fast',
    badgeColor: '#52c41a',
    placeholder: 'gsk_...',
    needsKey: true,
    hint: (
      <>
        Free key at{' '}
        <a href="https://console.groq.com/keys" target="_blank" rel="noreferrer">
          console.groq.com
        </a>
        . No credit card needed.
      </>
    ),
    defaultModel: 'llama-3.1-8b-instant',
    modelOptions: ['llama-3.1-8b-instant', 'llama-3.3-70b-versatile', 'gemma2-9b-it', 'llama3-70b-8192'],
  },
  {
    id: 'ollama',
    label: 'Ollama',
    badge: 'Local · Free',
    badgeColor: '#1677ff',
    placeholder: '',
    needsKey: false,
    hint: (
      <>
        Run models on your machine.{' '}
        <a href="https://ollama.ai" target="_blank" rel="noreferrer">
          ollama.ai
        </a>{' '}
        — start with <code>ollama run llama3.2</code>.
      </>
    ),
    defaultModel: 'llama3.2',
    modelOptions: ['llama3.2', 'codellama', 'qwen2.5-coder', 'mistral'],
  },
  {
    id: 'anthropic',
    label: 'Anthropic',
    badge: 'Claude',
    badgeColor: '#722ed1',
    placeholder: 'sk-ant-...',
    needsKey: true,
    hint: (
      <>
        Get a key at{' '}
        <a href="https://console.anthropic.com/settings/keys" target="_blank" rel="noreferrer">
          console.anthropic.com
        </a>
        .
      </>
    ),
    defaultModel: 'claude-3-5-haiku-20241022',
    modelOptions: ['claude-3-5-haiku-20241022', 'claude-3-5-sonnet-20241022'],
  },
  {
    id: 'openai',
    label: 'OpenAI',
    badge: 'GPT',
    badgeColor: '#13c2c2',
    placeholder: 'sk-...',
    needsKey: true,
    hint: (
      <>
        Get a key at{' '}
        <a href="https://platform.openai.com/api-keys" target="_blank" rel="noreferrer">
          platform.openai.com
        </a>
        .
      </>
    ),
    defaultModel: 'gpt-4o-mini',
    modelOptions: ['gpt-4o-mini', 'gpt-4o'],
  },
];

// ── Helpers ───────────────────────────────────────────────────────────────────

function getProviderMeta(id) {
  return PROVIDERS.find((p) => p.id === id) || PROVIDERS[0];
}

function loadConfig() {
  try {
    const raw = localStorage.getItem(LS_CONFIG_KEY);
    if (raw) return JSON.parse(raw);
  } catch {}
  return null;
}

function saveConfig(cfg) {
  localStorage.setItem(LS_CONFIG_KEY, JSON.stringify(cfg));
}

function isConfigReady(cfg, serverKeyConfigured) {
  if (!cfg) return !!serverKeyConfigured;
  const meta = getProviderMeta(cfg.provider);
  if (!meta.needsKey) return true; // Ollama needs no key
  return !!(cfg.apiKey || serverKeyConfigured);
}

// ── Code block ────────────────────────────────────────────────────────────────

function CodeBlock({ code, language }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    navigator.clipboard?.writeText(code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  };
  return (
    <div className="ai-chat-code-block">
      <div className="ai-chat-code-block-header">
        <span className="ai-chat-code-lang">{language || 'code'}</span>
        <Tooltip title={copied ? 'Copied!' : 'Copy'}>
          <Button
            type="text"
            size="small"
            icon={copied ? <CheckOutlined style={{ color: '#a6e3a1' }} /> : <CopyOutlined />}
            onClick={copy}
            className="ai-chat-code-copy-btn"
          />
        </Tooltip>
      </div>
      <pre className="ai-chat-code-pre">
        <code>{code}</code>
      </pre>
    </div>
  );
}

function MessageContent({ content }) {
  const parts = [];
  const fenceRe = /```(\w*)\n([\s\S]*?)```/g;
  let last = 0;
  let m;
  while ((m = fenceRe.exec(content)) !== null) {
    if (m.index > last) parts.push({ type: 'text', value: content.slice(last, m.index) });
    parts.push({ type: 'code', language: m[1], value: m[2] });
    last = m.index + m[0].length;
  }
  if (last < content.length) parts.push({ type: 'text', value: content.slice(last) });

  return (
    <div className="ai-chat-message-content">
      {parts.map((p, i) =>
        p.type === 'code' ? (
          <CodeBlock key={i} code={p.value} language={p.language} />
        ) : (
          <span key={i} className="ai-chat-text-segment">
            {p.value}
          </span>
        )
      )}
    </div>
  );
}

function ToolCallBubble({ calls }) {
  return (
    <div className="ai-chat-tool-calls">
      {calls.map((c, i) => (
        <span key={i} className="ai-chat-tool-tag">
          <ToolOutlined />
          {c.name === 'read_file'
            ? `Reading ${c.input?.path}`
            : c.name === 'write_file'
            ? `Writing ${c.input?.path}`
            : 'Listing files'}
        </span>
      ))}
    </div>
  );
}

function TypingIndicator({ toolSteps }) {
  return (
    <div className="ai-chat-bubble ai-chat-bubble--assistant ai-chat-bubble--thinking">
      <span className="ai-chat-bubble-avatar"><RobotOutlined /></span>
      <div className="ai-chat-bubble-body">
        {toolSteps.length > 0 && <ToolCallBubble calls={toolSteps} />}
        <div className="ai-typing-indicator">
          <span className="ai-typing-dot" />
          <span className="ai-typing-dot" />
          <span className="ai-typing-dot" />
        </div>
      </div>
    </div>
  );
}

// ── Provider config popover (settings gear in header) ────────────────────────

function ProviderConfigPopover({ serverKeyConfigured, onConfigChanged }) {
  const [cfg, setCfg] = useState(() => loadConfig() || { provider: 'groq', apiKey: '', model: '' });
  const [show, setShow] = useState(false);
  const [showKey, setShowKey] = useState(false);
  const meta = getProviderMeta(cfg.provider);

  const save = () => {
    saveConfig(cfg);
    antMessage.success(`${meta.label} config saved`);
    setShow(false);
    onConfigChanged?.();
  };

  const clear = () => {
    const cleared = { provider: cfg.provider, apiKey: '', model: '' };
    setCfg(cleared);
    saveConfig(cleared);
    antMessage.info('Key cleared');
    onConfigChanged?.();
  };

  const currentCfg = loadConfig();
  const hasKey = currentCfg && (currentCfg.apiKey || !getProviderMeta(currentCfg.provider).needsKey);

  const content = (
    <div className="ai-key-popover">
      <div className="ai-key-popover-row">
        <label className="ai-key-popover-label">Provider</label>
        <Select
          value={cfg.provider}
          onChange={(v) => setCfg((c) => ({ ...c, provider: v, model: '' }))}
          size="small"
          style={{ width: '100%' }}
        >
          {PROVIDERS.map((p) => (
            <Option key={p.id} value={p.id}>
              <span
                className="ai-provider-badge"
                style={{ background: p.badgeColor + '22', color: p.badgeColor }}
              >
                {p.badge}
              </span>{' '}
              {p.label}
            </Option>
          ))}
        </Select>
      </div>

      {meta.needsKey && (
        <div className="ai-key-popover-row">
          <label className="ai-key-popover-label">API Key</label>
          <Input
            type={showKey ? 'text' : 'password'}
            value={cfg.apiKey}
            onChange={(e) => setCfg((c) => ({ ...c, apiKey: e.target.value }))}
            placeholder={meta.placeholder}
            onPressEnter={save}
            suffix={
              <span
                style={{ cursor: 'pointer', color: '#888' }}
                onClick={() => setShowKey((v) => !v)}
              >
                {showKey ? <EyeOutlined /> : <EyeInvisibleOutlined />}
              </span>
            }
            size="small"
          />
          <span className="ai-key-popover-hint">{meta.hint}</span>
        </div>
      )}

      <div className="ai-key-popover-row">
        <label className="ai-key-popover-label">Model (optional)</label>
        <Select
          value={cfg.model || meta.defaultModel}
          onChange={(v) => setCfg((c) => ({ ...c, model: v }))}
          size="small"
          style={{ width: '100%' }}
        >
          {meta.modelOptions.map((m) => (
            <Option key={m} value={m}>
              {m}
            </Option>
          ))}
        </Select>
      </div>

      {serverKeyConfigured && !meta.needsKey === false && (
        <p className="ai-key-popover-hint" style={{ marginTop: 6 }}>
          A server key is also configured and will be used as fallback.
        </p>
      )}

      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', marginTop: 10 }}>
        {cfg.apiKey && (
          <Button size="small" danger onClick={clear}>
            Clear key
          </Button>
        )}
        <Button size="small" onClick={() => setShow(false)}>
          Cancel
        </Button>
        <Button size="small" type="primary" onClick={save}>
          Save
        </Button>
      </div>
    </div>
  );

  return (
    <Popover
      content={content}
      title="AI provider settings"
      open={show}
      onOpenChange={setShow}
      trigger="click"
      placement="bottomRight"
    >
      <Tooltip title={hasKey ? `${currentCfg?.provider || 'AI'} configured` : 'Configure AI provider'}>
        <Button
          type="text"
          size="small"
          icon={<SettingOutlined />}
          className={`ai-chat-key-btn${hasKey ? ' ai-chat-key-btn--set' : ''}`}
        />
      </Tooltip>
    </Popover>
  );
}

// ── First-run setup screen ────────────────────────────────────────────────────

function KeySetupScreen({ onConfigured, serverKeyConfigured }) {
  const [selectedProvider, setSelectedProvider] = useState('groq');
  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [saving, setSaving] = useState(false);
  const meta = getProviderMeta(selectedProvider);

  // Server already has the key → can proceed without entering a personal key
  const serverCoversKey = serverKeyConfigured && selectedProvider === 'groq';
  const canSave = !meta.needsKey || apiKey.trim() || serverCoversKey;

  const save = () => {
    setSaving(true);
    const cfg = {
      provider: selectedProvider,
      apiKey: apiKey.trim(),
      model: model || meta.defaultModel,
    };
    saveConfig(cfg);
    setTimeout(() => {
      setSaving(false);
      onConfigured();
    }, 300);
  };

  return (
    <div className="ai-chat-panel ai-chat-setup">
      <div className="ai-chat-setup-inner">
        <RobotOutlined className="ai-chat-setup-icon" />
        <h3 className="ai-chat-setup-title">Set up AI Assistant</h3>
        <p className="ai-chat-setup-desc">Choose a provider. Your settings are stored only in this browser.</p>

        <div className="ai-provider-cards">
          {PROVIDERS.map((p) => (
            <button
              key={p.id}
              className={`ai-provider-card${selectedProvider === p.id ? ' ai-provider-card--selected' : ''}`}
              onClick={() => {
                setSelectedProvider(p.id);
                setApiKey('');
                setModel('');
              }}
            >
              <span
                className="ai-provider-card-badge"
                style={{ background: p.badgeColor + '22', color: p.badgeColor }}
              >
                {p.badge}
              </span>
              <span className="ai-provider-card-name">{p.label}</span>
            </button>
          ))}
        </div>

        {meta.needsKey ? (
          serverCoversKey ? (
            <div className="ai-chat-setup-no-key-note">
              <CheckOutlined style={{ color: '#52c41a', marginRight: 6 }} />
              Server key already configured — ready to use.
            </div>
          ) : (
            <>
              <Input
                type={showKey ? 'text' : 'password'}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                onPressEnter={canSave ? save : undefined}
                placeholder={meta.placeholder}
                className="ai-chat-setup-input"
                size="large"
                suffix={
                  <span
                    style={{ cursor: 'pointer', color: '#888' }}
                    onClick={() => setShowKey((v) => !v)}
                  >
                    {showKey ? <EyeOutlined /> : <EyeInvisibleOutlined />}
                  </span>
                }
              />
              <p className="ai-chat-setup-hint">{meta.hint}</p>
            </>
          )
        ) : (
          <div className="ai-chat-setup-no-key-note">
            <CheckOutlined style={{ color: '#52c41a', marginRight: 6 }} />
            No API key needed — Ollama runs locally.
          </div>
        )}

        <Button
          type="primary"
          size="large"
          block
          loading={saving}
          disabled={!canSave}
          onClick={save}
          className="ai-chat-setup-btn"
        >
          Save &amp; start chatting
        </Button>
      </div>
    </div>
  );
}

// ── Main panel ────────────────────────────────────────────────────────────────

const WELCOME =
  "Hi! I can read, write, and reason about your project files. Ask me to explain code, fix bugs, add a new DAG, or anything else.";

const SUGGESTIONS = [
  "Add an example DAG to dags/",
  "Review the open file for issues",
  "List all files in this project",
  "Explain what this DAG does",
];

const AiChatPanel = ({
  projectId,
  fileContent,
  fileName,
  files,
  fileContents,
  onApplyFileChange,
  serverKeyConfigured,
}) => {
  const [keyReady, setKeyReady] = useState(() => isConfigReady(loadConfig(), serverKeyConfigured));
  const [messages, setMessages] = useState([{ role: 'assistant', content: WELCOME }]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [toolSteps, setToolSteps] = useState([]);
  const bottomRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    if (serverKeyConfigured) setKeyReady(true);
  }, [serverKeyConfigured]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading, toolSteps]);

  // ── Tool execution ──────────────────────────────────────────────────────────

  const executeTool = useCallback(
    async (name, input) => {
      if (name === 'list_files') {
        const list = (files || []).map((f) => ({
          path: f.filePath || f.fileName,
          type: f.fileType,
        }));
        return JSON.stringify(list);
      }

      if (name === 'read_file') {
        const path = input?.path || '';
        const file = (files || []).find(
          (f) => f.filePath === path || f.fileName === path
        );
        if (!file) return `Error: file not found: ${path}`;
        if (fileContents?.[file.fileId]) return fileContents[file.fileId];
        try {
          const { data } = await projectAPI.getFiles(projectId);
          const found = data?.find((f) => f.fileId === file.fileId);
          return found?.content || `Error: could not read ${path}`;
        } catch {
          return `Error: could not read ${path}`;
        }
      }

      if (name === 'write_file') {
        const path = input?.path || '';
        const content = input?.content || '';
        const file = (files || []).find(
          (f) => f.filePath === path || f.fileName === path
        );
        if (!file) return `Error: file not found: ${path}`;
        if (onApplyFileChange) {
          onApplyFileChange(file.fileId, content);
          return `Successfully updated ${path}`;
        }
        return `Error: editor not available`;
      }

      return `Error: unknown tool ${name}`;
    },
    [files, fileContents, projectId, onApplyFileChange]
  );

  // ── Agentic send loop ───────────────────────────────────────────────────────

  const send = async () => {
    const text = input.trim();
    if (!text || loading) return;

    // Fall back to groq (server key) if the user hasn't configured a personal key
    const cfg = loadConfig() || { provider: 'groq' };
    const userApiKey = cfg.apiKey || undefined;
    const userProvider = cfg.provider || 'groq';
    const userModel = cfg.model || undefined;

    const userMsg = { role: 'user', content: text };
    const history = [...messages.filter((m) => m.content !== WELCOME), userMsg];
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);
    setToolSteps([]);

    try {
      let currentHistory = history;
      let iterations = 0;
      const MAX_ITERATIONS = 10;

      while (iterations++ < MAX_ITERATIONS) {
        const { data } = await aiAPI.chat(
          currentHistory,
          fileContent,
          fileName,
          userApiKey,
          true, // useTools
          userProvider,
          userModel
        );

        if (data.error) {
          setMessages((prev) => [
            ...prev,
            { role: 'assistant', content: `⚠️ ${data.error}` },
          ]);
          break;
        }

        if (data.stopReason === 'tool_use') {
          setToolSteps((prev) => [...prev, ...data.toolCalls]);

          currentHistory = [
            ...currentHistory,
            { role: 'assistant', content: data.assistantContent },
          ];

          const toolResults = await Promise.all(
            data.toolCalls.map(async (tc) => {
              const result = await executeTool(tc.name, tc.input);
              return { type: 'tool_result', tool_use_id: tc.id, content: result };
            })
          );

          currentHistory = [
            ...currentHistory,
            { role: 'user', content: toolResults },
          ];
          continue;
        }

        setMessages((prev) => [
          ...prev,
          {
            role: 'assistant',
            content: data.reply,
            toolCalls: toolSteps.length ? [...toolSteps] : undefined,
          },
        ]);
        break;
      }
    } catch (e) {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: '⚠️ Could not reach the AI service. Check your provider settings (⚙️).',
        },
      ]);
    } finally {
      setLoading(false);
      setToolSteps([]);
      inputRef.current?.focus();
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  const clearChat = () => {
    setMessages([{ role: 'assistant', content: WELCOME }]);
    setInput('');
    setToolSteps([]);
  };

  const handleConfigChanged = () => {
    if (isConfigReady(loadConfig(), serverKeyConfigured)) {
      setKeyReady(true);
    }
  };

  if (!keyReady) {
    return (
      <KeySetupScreen
        onConfigured={() => setKeyReady(true)}
        serverKeyConfigured={serverKeyConfigured}
      />
    );
  }

  const activeCfg = loadConfig();
  const activeMeta = getProviderMeta(activeCfg?.provider || 'groq');

  const isOnlyWelcome = messages.length === 1 && messages[0].content === WELCOME;

  return (
    <div className="ai-chat-panel">
      {/* Header */}
      <div className="ai-chat-header">
        <RobotOutlined className="ai-chat-header-icon" />
        <span className="ai-chat-header-title">AI Assistant</span>
        {activeCfg?.provider && (
          <span
            className="ai-provider-badge ai-provider-badge--header"
            style={{ background: activeMeta.badgeColor + '1a', color: activeMeta.badgeColor }}
          >
            {activeMeta.label}
          </span>
        )}
        {fileName && (
          <span className="ai-chat-header-file" title={fileName}>
            {fileName.split('/').pop()}
          </span>
        )}
        <div className="ai-chat-header-actions">
          <ProviderConfigPopover
            serverKeyConfigured={serverKeyConfigured}
            onConfigChanged={handleConfigChanged}
          />
          <Tooltip title="Clear conversation">
            <Button
              type="text"
              size="small"
              icon={<DeleteOutlined />}
              onClick={clearChat}
              className="ai-chat-clear-btn"
            />
          </Tooltip>
        </div>
      </div>

      {/* Messages */}
      <div className="ai-chat-messages">
        {messages.map((msg, i) => (
          <div key={i} className={`ai-chat-bubble ai-chat-bubble--${msg.role}`}>
            <span className="ai-chat-bubble-avatar">
              {msg.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
            </span>
            <div className="ai-chat-bubble-body">
              {msg.toolCalls?.length > 0 && <ToolCallBubble calls={msg.toolCalls} />}
              <div className="ai-chat-message-content">
                <MessageContent content={msg.content} />
              </div>
            </div>
          </div>
        ))}

        {/* Quick-start suggestions when conversation is empty */}
        {isOnlyWelcome && !loading && (
          <div className="ai-chat-suggestions">
            {SUGGESTIONS.map((s) => (
              <button
                key={s}
                className="ai-chat-suggestion"
                onClick={() => { setInput(s); setTimeout(() => inputRef.current?.focus(), 0); }}
              >
                {s}
              </button>
            ))}
          </div>
        )}

        {loading && <TypingIndicator toolSteps={toolSteps} />}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="ai-chat-input-row">
        <div className="ai-chat-input-wrap">
          <TextArea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Message AI… (Enter to send)"
            autoSize={{ minRows: 1, maxRows: 6 }}
            className="ai-chat-textarea"
            disabled={loading}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={send}
            disabled={!input.trim() || loading}
            className="ai-chat-send-btn"
            aria-label="Send"
          />
        </div>
        <span className="ai-chat-input-hint">Shift+Enter for newline</span>
      </div>
    </div>
  );
};

export default AiChatPanel;
