import React from 'react';
import { BRAND } from '../brand';

/**
 * Two-tone wordmark from {@link BRAND.name} (split on first space when present). {@code size}: sm | md | lg | xl
 */
export default function FlowDeckWordmark({ size = 'md' }) {
  const cls = `flowdeck-wordmark flowdeck-wordmark--${size}`;
  const name = BRAND.name;
  const i = name.indexOf(' ');
  if (i <= 0) {
    return (
      <span className={cls}>
        <span className="flowdeck-wordmark-flow">{name}</span>
      </span>
    );
  }
  return (
    <span className={cls}>
      <span className="flowdeck-wordmark-flow">{name.slice(0, i)}</span>
      <span className="flowdeck-wordmark-space"> </span>
      <span className="flowdeck-wordmark-deck">{name.slice(i + 1)}</span>
    </span>
  );
}
