import { css } from '@codemirror/lang-css';
import { html } from '@codemirror/lang-html';
import { javascript } from '@codemirror/lang-javascript';
import { json } from '@codemirror/lang-json';
import { markdown } from '@codemirror/lang-markdown';
import { python } from '@codemirror/lang-python';
import { sql } from '@codemirror/lang-sql';
import { xml } from '@codemirror/lang-xml';
import { yaml } from '@codemirror/lang-yaml';
import { indentWithTab } from '@codemirror/commands';
import { EditorState } from '@codemirror/state';
import { EditorView, keymap } from '@codemirror/view';

/**
 * Map project file metadata to a high-level language id (used for highlighting).
 * @param {object | null | undefined} file open file from the editor (tree row + tab state)
 * @returns {string}
 */
export function inferMonacoLanguage(file) {
  if (!file) return 'plaintext';

  if (file.isConfig && file.configKey) {
    switch (file.configKey) {
      case 'dockerfile':
        return 'dockerfile';
      case 'settings':
        return 'yaml';
      case 'requirements':
      case 'packages':
      case 'env':
      case 'ignore':
        return 'plaintext';
      default:
        break;
    }
  }

  const raw = file.fileName || file.name || '';
  const name = raw.toLowerCase();

  if (name === 'dockerfile' || name.endsWith('dockerfile')) return 'dockerfile';
  if (name.endsWith('.py')) return 'python';
  if (name.endsWith('.yaml') || name.endsWith('.yml')) return 'yaml';
  if (name.endsWith('.json')) return 'json';
  if (name.endsWith('.md')) return 'markdown';
  if (name.endsWith('.sh') || name.endsWith('.bash')) return 'shell';
  if (name.endsWith('.sql')) return 'sql';
  if (name.endsWith('.xml')) return 'xml';
  if (name.endsWith('.html') || name.endsWith('.htm')) return 'html';
  if (name.endsWith('.css')) return 'css';
  if (name.endsWith('.js') || name.endsWith('.jsx')) return 'javascript';
  if (name.endsWith('.ts') || name.endsWith('.tsx')) return 'typescript';
  if (name.endsWith('.toml')) return 'ini';

  return 'plaintext';
}

/** @param {number} fontSize */
function editorBaseTheme(fontSize) {
  return EditorView.theme({
    '&': { height: '100%', fontSize: `${fontSize}px` },
    '.cm-scroller': { fontFamily: 'Consolas, Monaco, "Courier New", monospace' },
    '.cm-content': { minHeight: '100%' },
  });
}

/**
 * CodeMirror extensions for the open file plus editor chrome (keymaps, theme sizing, wrap, read-only).
 * @param {object} opts
 * @param {object | null | undefined} opts.file
 * @param {object} opts.settings
 * @param {boolean} [opts.readOnly]
 * @param {() => ({ onSave?: function, onFormat?: function } | undefined) | undefined} opts.getActions
 */
export function buildCodeMirrorExtensions({ file, settings, readOnly, getActions }) {
  const exts = [];
  const raw = file?.fileName || file?.name || '';
  const name = raw.toLowerCase();
  const id = inferMonacoLanguage(file);

  switch (id) {
    case 'python':
      exts.push(python());
      break;
    case 'yaml':
      exts.push(yaml());
      break;
    case 'json':
      exts.push(json());
      break;
    case 'markdown':
      exts.push(markdown());
      break;
    case 'javascript':
      exts.push(javascript({ jsx: name.endsWith('.jsx'), typescript: false }));
      break;
    case 'typescript':
      exts.push(javascript({ jsx: name.endsWith('.tsx'), typescript: true }));
      break;
    case 'html':
      exts.push(html());
      break;
    case 'css':
      exts.push(css());
      break;
    case 'xml':
      exts.push(xml());
      break;
    case 'sql':
      exts.push(sql());
      break;
    default:
      break;
  }

  const fontSize = settings?.fontSize ?? 14;

  exts.push(
    keymap.of([
      {
        key: 'Mod-s',
        preventDefault: true,
        run: () => {
          getActions?.()?.onSave?.();
          return true;
        },
      },
      {
        key: 'Shift-Alt-f',
        preventDefault: true,
        run: () => {
          getActions?.()?.onFormat?.();
          return true;
        },
      },
      indentWithTab,
    ])
  );
  exts.push(editorBaseTheme(fontSize));

  if (settings?.wordWrap === 'on') {
    exts.push(EditorView.lineWrapping);
  }
  if (readOnly) {
    exts.push(EditorState.readOnly.of(true));
  }

  return exts;
}
