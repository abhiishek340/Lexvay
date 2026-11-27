import { MapState, TileStatus } from '../../ui/state-map/state-map-model';

/**
 * The sign-in page's picture: a map whose tiles move from quiet to in review to signed, over and over, six seconds a
 * turn (DESIGN.md "Sign in"). An illustration only: the map is drawn without names or numbers and hidden from
 * assistive technology, and nothing about any tenant is shown before sign-in.
 */
export const STORY_MS = 6000;
export const STORY_STEP_MS = 250;
const REQUIREMENTS = 8;
/** When each state's review begins, its review lasts, it is signed, and when every tile fades back. */
const START_STAGGER = 180;
const REVIEW_MS = 2300;
const SIGNED_AFTER = 2600;
const FADE_AT = 5400;

/** The states in the picture, west to east, and the outcome each one is signed with. */
const CAST: readonly { code: string; outcome: Exclude<TileStatus, 'IN_REVIEW'> }[] = [
  { code: 'WA', outcome: 'MEETS' }, { code: 'OR', outcome: 'MEETS' }, { code: 'CA', outcome: 'DOES_NOT_MEET' }, { code: 'NV', outcome: 'MEETS' },
  { code: 'UT', outcome: 'MEETS' }, { code: 'AZ', outcome: 'NOT_DETERMINED' }, { code: 'CO', outcome: 'MEETS' }, { code: 'TX', outcome: 'MEETS' },
  { code: 'MN', outcome: 'MEETS' }, { code: 'IL', outcome: 'DOES_NOT_MEET' }, { code: 'TN', outcome: 'MEETS' }, { code: 'OH', outcome: 'MEETS' },
  { code: 'GA', outcome: 'MEETS' }, { code: 'NC', outcome: 'NOT_DETERMINED' }, { code: 'NY', outcome: 'MEETS' }, { code: 'MA', outcome: 'MEETS' },
];

/** The map at `elapsed` milliseconds into the story (any value; the story repeats every STORY_MS). */
export function storyAt(elapsed: number): MapState[] {
  const t = ((elapsed % STORY_MS) + STORY_MS) % STORY_MS;
  if (t >= FADE_AT) { return []; }
  return CAST.flatMap(({ code, outcome }, i): MapState[] => {
    const since = t - i * START_STAGGER;
    if (since < 0) { return []; }
    const reviewed = Math.min(REQUIREMENTS, Math.floor((since / REVIEW_MS) * (REQUIREMENTS + 1)));
    const tile: TileStatus = since >= SIGNED_AFTER ? outcome : 'IN_REVIEW';
    return [{ code, tile, reviewed, total: REQUIREMENTS, status: '', action: null }];
  });
}

/** The still picture shown when motion is reduced: the story's fullest moment. */
export const STORY_STILL = storyAt(FADE_AT - STORY_STEP_MS);
