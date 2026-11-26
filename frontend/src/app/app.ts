import { Component } from '@angular/core';
import { Shell } from './shell/shell';

@Component({
  selector: 'app-root',
  imports: [Shell],
  template: `<lx-shell />`,
})
export class App {}
