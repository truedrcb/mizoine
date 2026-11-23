# CodeMirror bundle

Builds `src/main/resources/ui/vendor/codemirror.js`, the editor with syntax highlighting of the page
`/ui/editor.html` (DEV.md step 39). The application's own JavaScript needs no build; only this vendor bundle does,
and only when its libraries change. `mvn verify` does not need npm.

`.npmrc` here makes every `npm install` pin exact versions (`save-exact`) and run no install scripts of the
downloaded packages (`ignore-scripts`), so the commands below need no extra flags.

All commands in this folder (`cd tools/codemirror`).

## Upgrade the libraries

```sh
npm outdated                                   # what has a newer version
npm install codemirror@latest @codemirror/view@latest esbuild@latest   # the packages to upgrade, @latest each
npm run build                                  # rebuild the bundle
```

To upgrade everything, list every package of `package.json` with `@latest` in the `npm install` line.
`npm install` updates `package.json` and `package-lock.json`.

Then:

1. `mvn verify` (from the project root);
2. open the editor once in the browser (a `.md` and a `.yaml` file): highlighting, typing, Save. No automated test
   runs the bundle itself, only `editor.js`;
3. commit `package.json`, `package-lock.json`, `src/main/resources/ui/vendor/codemirror.js` and
   `codemirror.LICENSE.txt`.

## Rebuild without upgrading

```sh
npm ci          # exactly the versions in package-lock.json
npm run build   # esbuild → ../../src/main/resources/ui/vendor/codemirror.js, plus the MIT license notice
```

## How it fits

- `entry.js` offers the same interface as the textarea it replaces: `codeMirrorField(host, language)` →
  `{ getText, setText, onChange }`. Which language a file gets is decided in `ui/editor/editor.js`
  (`languageFor`, tested with the other editor tests).
- The editor is mounted in a shadow root: there CodeMirror puts its styles into a constructed style sheet. In the
  document itself it would insert a `<style>` element, which Mizoine's CSP blocks.
