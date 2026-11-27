import { Component, computed, inject, input, output, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import auraMenu from '@primeuix/themes/aura/menu';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { NavLink, inLink } from '../core/navigation';
import { registerComponentTokens } from '../theme/component-tokens';

/**
 * The bottom tab bar's "More" menu (under 640 px): the screens that did not fit, least used last (tabBarOverflow). A
 * PrimeNG Menu, so arrow keys, Enter and Escape work and focus returns to the "More" tab. The shell owns the tab and
 * calls toggle(); this loads in a deferred chunk. The screens' icons are the rail's (SHELL_ICONS).
 */
@Component({
  selector: 'lx-more-menu',
  imports: [NgIcon, Menu],
  template: `
    <p-menu #menu [model]="items()" [popup]="true" appendTo="body" ariaLabel="More screens"
            (onShow)="opened.emit(true)" (onHide)="opened.emit(false)">
      <ng-template #item let-item>
        <span class="p-menu-item-link lx-more-item" [class.lx-more-current]="item.state.current" [attr.aria-current]="item.state.current ? 'page' : null">
          <ng-icon [name]="item.icon" size="20" aria-hidden="true" />
          <span class="lx-more-label">{{ item.label }}</span>
          @if (item.state.badge > 0) {
            <span class="lx-more-count" aria-hidden="true">{{ item.state.badge }}</span><span class="lx-sr-only">, {{ item.state.badgeText }}</span>
          }
        </span>
      </ng-template>
    </p-menu>
  `,
  styles: `
    .lx-more-item { display: flex; align-items: center; gap: 12px; min-width: 12rem; }
    .lx-more-label { flex: 1; }
    .lx-more-current { color: var(--p-lx-accent); font-weight: 600; }
    .lx-more-count {
      min-width: 20px; height: 20px; box-sizing: border-box; padding: 0 6px; border-radius: var(--lx-r-pill); display: inline-grid; place-items: center;
      font-size: 11px; font-weight: 650; background: var(--p-lx-surface-2); color: var(--p-lx-text-2);
    }
  `,
})
export class MoreMenu {
  private readonly router = inject(Router);
  /** The screens that did not fit, and the URL shown, to mark the current one. */
  readonly links = input.required<readonly NavLink[]>();
  readonly url = input.required<string>();
  readonly opened = output<boolean>();
  private readonly menu = viewChild.required<Menu>('menu');

  readonly items = computed<MenuItem[]>(() => this.links().map((link) => ({
    id: `more-${link.id}`, label: link.label, icon: link.icon,
    state: { current: inLink(this.url(), link), badge: link.badge, badgeText: link.badgeText },
    command: () => { void this.router.navigate(link.path); },
  })));

  constructor() {
    registerComponentTokens({ menu: auraMenu });
  }

  toggle(event: Event): void {
    this.menu().toggle(event);
  }
}
