/**
 * Apache Airflow versions users may choose in the UI.
 * Extend this list as the platform adds and tests additional releases.
 */
export const SUPPORTED_AIRFLOW_VERSIONS = [{ value: '3.1.8', label: '3.1.8' }];

export const DEFAULT_AIRFLOW_VERSION = SUPPORTED_AIRFLOW_VERSIONS[0].value;

/**
 * Options for Ant Design Select; if {@code storedVersion} is set and not in the supported list,
 * append it so edit flows still display legacy values.
 */
export function getAirflowVersionSelectOptions(storedVersion) {
  const opts = [...SUPPORTED_AIRFLOW_VERSIONS];
  if (
    storedVersion &&
    String(storedVersion).trim() &&
    !opts.some((o) => o.value === storedVersion)
  ) {
    opts.push({
      value: storedVersion,
      label: `${storedVersion} (legacy)`,
    });
  }
  return opts;
}
