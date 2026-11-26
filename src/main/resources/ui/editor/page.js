// The editor page (DEV.md step 37): connects editor.js to /ui/editor.html. All decisions are in editor.js
// (tested); this file only shows them.
import { parseFileParam, csrfToken, FileSession, languageFor } from './editor.js';

/** The editing part, behind a small interface: step 39 replaces the textarea with CodeMirror. */
export function textareaField(textarea) {
	return {
		getText: () => textarea.value,
		setText: text => { textarea.value = text; },
		onChange: listener => textarea.addEventListener('input', listener),
	};
}

/**
 * Step 39: CodeMirror with syntax highlighting, in place of the textarea. If the bundle cannot be loaded,
 * the textarea stays: editing still works, without highlighting.
 */
async function editingField(textarea, fileName) {
	try {
		const { codeMirrorField } = await import('../vendor/codemirror.js');
		const host = document.createElement('div');
		host.id = 'code';
		textarea.replaceWith(host);
		return codeMirrorField(host, languageFor(fileName));
	} catch (error) {
		console.error('Syntax highlighting not available, plain text field:', error);
		return textareaField(textarea);
	}
}

const MESSAGES = {
	loaded: '',
	missing: 'This file does not exist. The editor does not create files.',
	'not-loaded': 'Nothing loaded: nothing to save.',
	saved: 'Saved.',
	login: 'Not logged in. Log in (opens a new tab), then press Save again, or Reload if nothing was loaded. Your text stays here.',
	conflict: 'Changed elsewhere since you opened it: not saved. Your text stays here; Reload shows the other version.',
	'too-large': 'Too large to save.',
	error: 'Error, not saved.',
};

const element = id => document.getElementById(id);

function show(result) {
	const detail = result.status === 0 ? ' (no answer from the server)' : result.status ? ` (${result.status})` : '';
	element('status').textContent = MESSAGES[result.state] + detail;
	element('login-link').hidden = result.state !== 'login';
	element('reload').hidden = result.state !== 'conflict' && result.state !== 'login';
	// step 66: nothing to edit at a path that does not exist (the text field is CodeMirror's or the textarea)
	(element('code') ?? element('text')).hidden = result.state === 'missing';
}

async function start(file, field) {
	document.title = `${file.name} – Mizoine`;
	element('file-name').textContent = file.name;
	element('page-link').href = file.url;
	element('folder-link').href = file.folderUrl;
	const session = new FileSession(file.url, (url, options) => fetch(url, options));
	const changed = () => session.isChanged(field.getText());
	const update = () => { element('save').disabled = !changed(); };

	const load = async () => {
		const result = await session.load();
		if ('text' in result) {
			field.setText(result.text);
		}
		show(result);
		update();
	};
	field.onChange(update);
	element('save').addEventListener('click', async () => {
		element('save').disabled = true;
		show(await session.save(field.getText(), csrfToken(document.cookie)));
		update();
	});
	element('reload').addEventListener('click', load);
	window.addEventListener('beforeunload', event => {
		if (changed()) {
			event.preventDefault();
		}
	});
	await load();
}

const file = parseFileParam(window.location.search);
if (file) {
	start(file, await editingField(element('text'), file.name));
} else {
	element('status').textContent = 'No file given: open the editor from a document page (Edit).';
	element('text').disabled = true;
}
