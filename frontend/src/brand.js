/**
 * Product branding — edit {@link PRODUCT_NAME} to rename the product across the UI.
 *
 * <p>Control plane OpenAPI title/description use the same display name from
 * {@code application.yml} → {@code app.brand.name}; keep that value in sync when you rename.
 */
export const PRODUCT_NAME = 'Flow Deck';

export const BRAND = Object.freeze({
  name: PRODUCT_NAME,
  /** Primary subtitle */
  tagline: 'Operations console for Apache Airflow',
  /** Compact line for header / sidebar */
  taglineShort: 'Apache Airflow operations',
  /** IDE surface (sentence case for labels) */
  ideName: `${PRODUCT_NAME} IDE`,
  /** Sidebar + page title for the projects list */
  navProjects: 'Projects',
});

/** Default {@code <meta name="description">} before/without route-specific overrides. */
export function defaultHtmlMetaDescription() {
  return `${BRAND.name} — ${BRAND.tagline}. ${BRAND.ideName} for DAGs and project files, deployments, and multi-tenant control.`;
}
