import { DOCUMENT } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { Router, provideRouter, withComponentInputBinding, withRouterConfig, withViewTransitions } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { accessInterceptor } from './core/access';
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
      // Route changes cross-fade (styles.css); a change within one screen (a tab, a filter) does not, and reduced motion
      // turns the animation off in CSS. The animation is switched off, never the transition skipped: a skipped
      // transition rejects its promises, which Angular logs as an error in development.
      withViewTransitions({
        skipInitialTransition: true,
        onViewTransitionCreated: () => {
          const router = inject(Router);
          const to = router.getCurrentNavigation()?.finalUrl;
          const root = inject(DOCUMENT).documentElement;
          if (to && sameScreen(router.url, router.serializeUrl(to))) {
            root.setAttribute('data-transition', 'none');
          } else {
            root.removeAttribute('data-transition');
          }
        },
      })),
    // A session the server ended (role, access or password changed) signs out here too (core/access.ts).
    provideHttpClient(withInterceptors([accessInterceptor])),
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
