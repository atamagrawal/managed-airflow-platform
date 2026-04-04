import React from 'react';

/**
 * Styled "FlowDeck" wordmark. {@code size}: sm | md | lg | xl
 */
export default function FlowDeckWordmark({ size = 'md' }) {
  const cls = `flowdeck-wordmark flowdeck-wordmark--${size}`;
  return (
    <span className={cls}>
      <span className="flowdeck-wordmark-flow">Flow</span>
      <span className="flowdeck-wordmark-deck">Deck</span>
    </span>
  );
}
