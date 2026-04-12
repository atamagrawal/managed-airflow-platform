import React from 'react';

/**
 * Abstract pipeline / flow mark (no external assets).
 */
export default function BrandMark({ size = 'md' }) {
  const scale = size === 'sm' ? 1 : size === 'lg' ? 1.35 : 1.15;
  const w = 5 * scale;
  const bars = [
    { h: 10 * scale, delay: '0s' },
    { h: 18 * scale, delay: '0.08s' },
    { h: 13 * scale, delay: '0.04s' },
  ];
  return (
    <span className="brand-mark" aria-hidden>
      {bars.map((b, i) => (
        <span
          key={i}
          className="brand-mark-bar"
          style={{
            width: w,
            height: b.h,
            animationDelay: b.delay,
          }}
        />
      ))}
    </span>
  );
}
