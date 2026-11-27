import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { Router, provideRouter, withComponentInputBinding, withRouterConfig, withViewTransitions } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { MotionPreferences } from './core/motion';
import { sameScreen } from './core/navigation';
import { ThemeService } from './core/theme.service';
import { SHELL_ICONS } from './theme/icons';
import { LexvayPreset } from './theme/lexvay-preset';
import { PRIMEUI_LICENSE } from './theme/license';

/** HttpClient's default XSRF support sends the XSRF-TOKEN cookie back as X-XSRF-TOKEN, matching the backend. */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' }),
      // Route changes cross-fade (styles.css); a change within one screen (a tab, a filter) and reduced motion do not.
      withViewTransitions({
        skipInitialTransition: true,
        onViewTransitionCreated: ({ transition }) => {
          // A skipped or superseded transition rejects these promises; the navigation itself has already happened.
          transition.ready.catch(() => undefined);
          transition.finished.catch(() => undefined);
          const router = inject(Router);
          const to = router.getCurrentNavigation()?.finalUrl;
          if (inject(MotionPreferences).reduced() || (to && sameScreen(router.url, router.serializeUrl(to)))) {
            transition.skipTransition();
          }
        },
      })),
    provideHttpClient(),
    providePrimeNG({
      theme: {
        preset: LexvayPreset,
        options: { darkModeSelector: '[data-theme="dark"]', cssLayer: { name: 'primeng', order: 'primeng, lexvay' } },
      },
      ripple: false,
      ...(PRIMEUI_LICENSE ? { license: PRIMEUI_LICENSE } : {}),
    }),
    provideIcons(SHELL_ICONS),
    MessageService,
    // The theme attribute must be on <html> before the first screen renders.
    provideAppInitializer(() => { inject(ThemeService); }),
  ],
};
