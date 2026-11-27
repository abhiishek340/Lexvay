import { Component, computed, input, output, viewChild } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import auraMenu from '@primeuix/themes/aura/menu';
import { MenuItem } from 'primeng/api';
import { Menu } from 'primeng/menu';
import { NavLink } from '../core/navigation';
import { registerComponentTokens } from '../theme/component-tokens';

/**
 * The phone tab bar's "More" menu: the screens that do not fit the bar (navigation.ts, onPhoneBar). The shell owns the
 * button and calls toggle(); this PrimeNG Menu loads in a deferred chunk with its token set. Its icons are the rail's,
 * which the shell registers.
 */
@Component({
  selector: 'lx-more-menu',
  imports: [RouterLink, RouterLinkActive, NgIcon, Menu],
  template: `
    <p-menu #menu [model]="items()" [popup]="true" appendTo="body" ariaLabel="More screens" styleClass="lx-more-menu"
            (onShow)="opened.emit(true)" (onHide)="opened.emit(false)">
      <ng-template #item let-item>
        <!-- data-pc-section lets the Menu activate this link from the keyboard (Enter), as it does its own links. -->
        <a class="p-menu-item-link lx-more-link" data-pc-section="itemlink" tabindex="-1" [routerLink]="item.state.path" routerLinkActive="lx-more-active"
           ariaCurrentWhenActive="page">
          <ng-icon [name]="item.state.icon" size="20" aria-hidden="true" />
          <span>{{ item.label }}</span>
          @if (item.state.badge > 0) { <span class="lx-more-count" aria-hidden="true">{{ item.state.badge }}</span><span class="lx-sr-only">, {{ item.state.badgeText }}</span> }
        </a>
      </ng-template>
    </p-menu>
  `,
  styles: `
    /* The palette's chunk may have registered a transparent Menu root for its own list; this one is a raised popup. */
    ::ng-deep .p-menu.lx-more-menu { background: var(--p-lx-raised); box-shadow: var(--p-lx-e3); border: 0; border-radius: 12px; }
    .lx-more-link { display: flex; align-items: center; gap: 12px; text-decoration: none; color: var(--p-lx-text); }
    .lx-more-link ng-icon { color: var(--p-lx-text-2); }
    .lx-more-active, .lx-more-active ng-icon { color: var(--p-lx-accent); }
    .lx-more-count { margin-left: auto; min-width: 20px; padding: 0 6px; border-radius: 999px; text-align: center; font-size: 12px; font-weight: 600; background: var(--p-lx-surface-2); }
  `,
})
export class MoreMenu {
  readonly links = input.required<readonly NavLink[]>();
  readonly opened = output<boolean>();
  private readonly menu = viewChild.required<Menu>('menu');
  readonly items = computed<MenuItem[]>(() => this.links().map((link) => ({ label: link.label, state: link })));

  constructor() {
    registerComponentTokens({ menu: auraMenu });
  }

  toggle(event: Event): void {
    this.menu().toggle(event);
  }
}
