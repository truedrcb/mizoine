// Entry of the CodeMirror 6 bundle (DEV.md step 39, T3.1). Built by `npm run build` (see README.md) into
// src/main/resources/ui/vendor/codemirror.js. It offers the same field interface as the textarea of step 37:
// { getText, setText, onChange }. Which language a file gets is decided in ui/editor/editor.js (languageFor, tested).
import { EditorView, basicSetup } from 'codemirror';
import { EditorState } from '@codemirror/state';
import { StreamLanguage } from '@codemirror/language';
import { markdown } from '@codemirror/lang-markdown';
import { json } from '@codemirror/lang-json';
import { yaml } from '@codemirror/lang-yaml';
import { html } from '@codemirror/lang-html';
import { css } from '@codemirror/lang-css';
import { javascript } from '@codemirror/lang-javascript';
import { xml } from '@codemirror/lang-xml';
import { java } from '@codemirror/lang-java';
import { properties } from '@codemirror/legacy-modes/mode/properties';
import { shell } from '@codemirror/legacy-modes/mode/shell';

const LANGUAGES = {
	markdown: () => markdown(),
	json: () => json(),
	yaml: () => yaml(),
	html: () => html(),
	css: () => css(),
	javascript: () => javascript({ typescript: true }),
	xml: () => xml(),
	java: () => java(),
	properties: () => StreamLanguage.define(properties),
	shell: () => StreamLanguage.define(shell),
};

/**
 * The editor lives in a shadow root of `host`: there CodeMirror's styles go into a constructed style sheet
 * (adoptedStyleSheets). In the document itself it would insert a <style> element, which Mizoine's strict CSP
 * (no inline styles) blocks.
 *
 * @param language a name from languageFor(); unknown names ('text') get no highlighting
 */
export function codeMirrorField(host, language) {
	const listeners = [];
	const shadow = host.attachShadow({ mode: 'open' });
	const parent = document.createElement('div');
	shadow.appendChild(parent);
	const view = new EditorView({
		parent,
		root: shadow,
		state: EditorState.create({
			doc: '',
			extensions: [
				basicSetup,
				EditorView.lineWrapping,
				EditorView.theme({ '&': { height: '75vh', border: '1px solid #ccc' }, '.cm-scroller': { overflow: 'auto' } }),
				...(LANGUAGES[language] ? [LANGUAGES[language]()] : []),
				EditorView.updateListener.of(update => {
					if (update.docChanged) {
						listeners.forEach(listener => listener());
					}
				}),
			],
		}),
	});
	return {
		getText: () => view.state.doc.toString(),
		setText: text => view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text } }),
		onChange: listener => listeners.push(listener),
	};
}
