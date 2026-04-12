import React, { useMemo, useRef, useState, useLayoutEffect } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { oneDark } from '@codemirror/theme-one-dark';
import './CodeEditorPane.css';
import { buildCodeMirrorExtensions } from './inferMonacoLanguage';

const defaultInitialHeight = () =>
  typeof window !== 'undefined'
    ? Math.max(280, Math.floor(window.innerHeight - 200))
    : 400;

const CodeEditorPane = ({
  value,
  onChange,
  file,
  readOnly = false,
  settings = {},
  onMount,
  isFullscreen = false,
}) => {
  const onMountRef = useRef(onMount);
  onMountRef.current = onMount;

  const containerRef = useRef(null);
  const [editorHeight, setEditorHeight] = useState(defaultInitialHeight);

  useLayoutEffect(() => {
    const el = containerRef.current;
    if (!el || typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const apply = () => {
      const h = el.getBoundingClientRect().height;
      if (h > 0) {
        /* Ceil avoids a 1px gap from sub-pixel layout rounding */
        setEditorHeight(Math.ceil(h));
      }
    };
    apply();
    requestAnimationFrame(apply);
    const ro = new ResizeObserver(apply);
    ro.observe(el);
    const onWinResize = () => apply();
    window.addEventListener('resize', onWinResize);
    return () => {
      ro.disconnect();
      window.removeEventListener('resize', onWinResize);
    };
  }, [isFullscreen]);

  const extensions = useMemo(
    () =>
      buildCodeMirrorExtensions({
        file,
        settings,
        readOnly,
        getActions: () => onMountRef.current,
      }),
    [file, settings, readOnly]
  );

  const theme = useMemo(() => {
    const t = settings.theme || 'vs-dark';
    return t === 'vs-dark' || t === 'hc-black' ? oneDark : undefined;
  }, [settings.theme]);

  const basicSetup = useMemo(
    () => ({
      lineNumbers: settings.lineNumbers !== 'off',
      foldGutter: true,
      highlightActiveLine: true,
    }),
    [settings.lineNumbers]
  );

  return (
    <div ref={containerRef} className="code-editor-pane">
      <CodeMirror
        value={value ?? ''}
        height={`${editorHeight}px`}
        theme={theme}
        extensions={extensions}
        onChange={(v) => onChange?.(v)}
        editable={!readOnly}
        basicSetup={basicSetup}
      />
    </div>
  );
};

export default CodeEditorPane;
