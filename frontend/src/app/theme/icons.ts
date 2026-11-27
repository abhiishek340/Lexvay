import {
  phosphorArrowRight,
  phosphorBookBookmark,
  phosphorCaretDown,
  phosphorCheckCircle,
  phosphorClockCounterClockwise,
  phosphorFileText,
  phosphorGitDiff,
  phosphorInfo,
  phosphorListChecks,
  phosphorMonitor,
  phosphorMoon,
  phosphorScales,
  phosphorSignOut,
  phosphorSquaresFour,
  phosphorSun,
  phosphorUserCircle,
  phosphorWarning,
} from '@ng-icons/phosphor-icons/regular';

/**
 * The icons of the shell, the sign-in page and the home screen: all the initial bundle registers (app.config.ts).
 * Every other icon lives with the chunk that shows it (docs/DESIGN.md, "Bundle"): a `<SCREEN>_ICONS` constant next
 * to the screen, provided by the screen's route, or by a shared component's `viewProviders`. A screen registered
 * under these merges with them, so it names only what these do not. `icons.spec.ts` fails on a name nobody registers.
 */
export const SHELL_ICONS = {
  phosphorArrowRight,
  phosphorBookBookmark,
  phosphorCaretDown,
  phosphorCheckCircle,
  phosphorClockCounterClockwise,
  phosphorFileText,
  phosphorGitDiff,
  phosphorInfo,
  phosphorListChecks,
  phosphorMonitor,
  phosphorMoon,
  phosphorScales,
  phosphorSignOut,
  phosphorSquaresFour,
  phosphorSun,
  phosphorUserCircle,
  phosphorWarning,
};

/** The name of a Phosphor icon, as `<ng-icon name>` takes it; the registry that holds it depends on the screen. */
export type IconName = `phosphor${string}`;
