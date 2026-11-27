/**
 * @primeuix/themes 3.1.1 ships the Aura CommandMenu token set without the declaration file its other sets have; the
 * shape is the package's own CommandMenuTokenSections. Delete this file once the package ships dist/aura/commandmenu/index.d.ts.
 */
declare module '@primeuix/themes/aura/commandmenu' {
  import type { CommandMenuTokenSections } from '@primeuix/themes/types/commandmenu';

  const tokens: {
    root: CommandMenuTokenSections.Root;
    header: CommandMenuTokenSections.Header;
    input: CommandMenuTokenSections.Input;
    list: CommandMenuTokenSections.List;
    empty: CommandMenuTokenSections.Empty;
    footer: CommandMenuTokenSections.Footer;
  };
  export default tokens;
}
