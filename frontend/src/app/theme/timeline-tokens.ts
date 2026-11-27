import auraTimeline from '@primeuix/themes/aura/timeline';
import { lexvayTokens as t } from './lexvay-preset';

/**
 * Timeline as Lexvay draws it (Changes, Audit trail): the screens supply their own marker (an icon disc or an avatar),
 * so only the connector shows, a 1 px `line` rule from one marker to the next, and the content sits 16 px from it.
 */
export const lexvayTimeline = {
  ...auraTimeline,
  event: { minHeight: '0' },
  vertical: { eventContent: { padding: '0 0 20px 16px' } },
  eventConnector: { color: t.line, size: '1px' },
};
