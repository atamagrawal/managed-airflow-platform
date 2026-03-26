import React, { useRef } from 'react';
import './CodeEditorPane.css';

const CodeEditorPane = ({
  value,
  onChange,
  language = 'python',
  settings = {},
  onMount,
}) => {
  const containerRef = useRef(null);

  // Handle keyboard shortcuts in fallback editor
  const handleFallbackKeyDown = (e) => {
    // Tab key support - insert 4 spaces
    if (e.key === 'Tab') {
      e.preventDefault();
      const textarea = e.target;
      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const newValue = value.substring(0, start) + '    ' + value.substring(end);
      onChange?.(newValue);

      // Set cursor position after the inserted spaces
      setTimeout(() => {
        textarea.selectionStart = textarea.selectionEnd = start + 4;
      }, 0);
    }

    // Ctrl/Cmd + S to save
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault();
      if (onMount?.onSave) onMount.onSave();
    }
  };

  return (
    <div className="code-editor-fallback" ref={containerRef}>
      <textarea
        className="code-editor-fallback-textarea"
        value={value}
        onChange={(e) => onChange?.(e.target.value || '')}
        onKeyDown={handleFallbackKeyDown}
        spellCheck={false}
        placeholder="Start typing your Python code here..."
      />
    </div>
  );
};

export default CodeEditorPane;
