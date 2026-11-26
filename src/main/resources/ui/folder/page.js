// The folder page (DEV.md steps 71–73): connects folder.js to the server's folder page. All decisions are in
// folder.js (tested); this file only shows them. Without JavaScript the page still lists and links everything.
import { csrfToken } from '../editor/editor.js';
import { checkNewFileName, editorUrl, FolderActions, renameTo, reloads, uploadOne } from './folder.js';

const MESSAGES = {
	uploaded: 'uploaded.',
	replaced: 'replaced.',
	exists: 'A file of that name exists already: nothing changed.',
	'too-large': 'Too large for the server: not uploaded.',
	login: 'Not logged in: reload the page and log in.',
	'not-found': 'Not possible here.',
	conflict: 'The folder is gone, or a folder of that name is in the way.',
	error: 'Error, nothing changed.',
};

function message(result) {
	if (result.state === 'refused') {
		return result.error;
	}
	const detail = result.status === 0 ? ' (no answer from the server)' : result.status ? ` (${result.status})` : '';
	return MESSAGES[result.state] + detail;
}

async function textFormats() {
	try {
		const response = await fetch('/ui/editor/text-formats.json');
		return response.ok ? await response.json() : null;
	} catch {
		return null;
	}
}

/** Step 71 (D76): New file, the one way to create a file in the browser; then it opens in the editor. */
function newFile(form, actions, formats) {
	const status = form.querySelector('.status');
	form.addEventListener('submit', async event => {
		event.preventDefault();
		const checked = checkNewFileName(form.elements.name.value, formats);
		if ('error' in checked) {
			status.textContent = checked.error;
			return;
		}
		const result = await actions.createFile(checked.name);
		if (result.state === 'created') {
			window.location.assign(editorUrl(result.url));
		} else {
			status.textContent = message(result);
		}
	});
	form.hidden = false;
}

/**
 * Step 72 (D77): the picked files, one after the other, each with a line saying how it went. A name that exists is
 * replaced only after the user said yes; when all are done, the listing reloads.
 */
function upload(form, actions) {
	const input = form.elements.files;
	const lines = form.querySelector('.uploads');
	input.addEventListener('change', async () => {
		lines.replaceChildren();
		const done = [];
		for (const file of input.files) {
			const line = lines.appendChild(document.createElement('li'));
			line.textContent = `${file.name}: uploading …`;
			const { name, result } = await uploadOne(actions, file.name, file,
				checked => window.confirm(`${checked} exists already. Replace it?`));
			line.textContent = `${name}: ${message(result)}`;
			done.push(result);
		}
		input.value = '';
		if (done.some(reloads)) {
			window.location.reload();
		}
	});
	form.hidden = false;
}

function button(label, onClick) {
	const element = document.createElement('button');
	element.type = 'button';
	element.textContent = label;
	element.addEventListener('click', onClick);
	return element;
}

/** Step 73: Rename and Delete for each file; the listing reloads after either. */
function renameAndDelete(item, actions) {
	const name = item.dataset.file;
	const status = document.createElement('span');
	status.className = 'status';
	status.setAttribute('role', 'status');
	const done = result => {
		if (reloads(result)) {
			window.location.reload();
		} else {
			status.textContent = message(result);
		}
	};
	item.append(' ', button('Rename', async () => {
		const target = renameTo(name, window.prompt(`New name for ${name}:`, name));
		if ('name' in target) {
			done(await actions.rename(name, target.name));
		} else if ('error' in target) {
			status.textContent = target.error;
		}
	}), ' ', button('Delete', async () => {
		if (window.confirm(`Delete ${name}?`)) {
			done(await actions.remove(name));
		}
	}), ' ', status);
}

const form = document.getElementById('new-file');
if (form) {
	const actions = new FolderActions(form.dataset.folder, (url, options) => fetch(url, options),
		() => csrfToken(document.cookie));
	upload(document.getElementById('upload'), actions);
	document.querySelectorAll('li[data-file]').forEach(item => renameAndDelete(item, actions));
	const formats = await textFormats();
	if (formats) {
		newFile(form, actions, formats);
	}
}
