import auraMetergroup from '@primeuix/themes/aura/metergroup';
import { lexvayTokens as t } from './lexvay-preset';

/** MeterGroup drawn like the Meter (ui/meter.ts): a 4 px, square-ended bar on sunken (the setup page's password strength). */
export const lexvayMetergroup = {
  ...auraMetergroup,
  root: { ...auraMetergroup.root, borderRadius: '0', gap: '0.5rem' },
  meters: { ...auraMetergroup.meters, background: t.sunken, size: '4px' },
};
