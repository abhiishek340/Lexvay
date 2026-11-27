import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { ThemeService } from './core/theme.service';
import { SHELL_ICONS } from './theme/icons';
import { LexvayPreset } from './theme/lexvay-preset';
import { PRIMEUI_LICENSE } from './theme/license';

/** HttpClient's default XSRF support sends the XSRF-TOKEN cookie back as X-XSRF-TOKEN, matching the backend. */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
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
