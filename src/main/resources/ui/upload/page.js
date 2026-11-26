// The page of an upload link (DEV.md step 76): each picked file is one PUT to the link's own address, under its own
// name (NFC, D78) or the one name the link was made for. All decisions are in ../folder/folder.js (tested); this
// file only shows them. No login and no CSRF token: the link is the permission.
import { FolderActions, uploadOne } from '../folder/folder.js';

const MESSAGES = {
	uploaded: 'uploaded.',
	exists: 'a file of that name exists already: not uploaded. Rename it and try again.',
	'too-large': 'too large for the server: not uploaded.',
	'not-found': 'this upload link is not valid (any more).',
	error: 'error, not uploaded.',
};

function message(result) {
	if (result.state === 'refused') {
		return result.error;
	}
	const detail = result.status === 0 ? ' (no answer from the server)' : result.status ? ` (${result.status})` : '';
	return (MESSAGES[result.state] ?? MESSAGES.error) + detail;
}

const form = document.getElementById('upload-link');
if (form) {
	const actions = new FolderActions(window.location.pathname, (url, options) => fetch(url, options), () => null);
	const input = form.elements.files;
	const lines = form.querySelector('.uploads');
	input.addEventListener('change', async () => {
		for (const file of input.files) {
			const line = lines.appendChild(document.createElement('li'));
			line.textContent = `${file.name}: uploading …`;
			// never replace through a link (D83)
			const { name, result } = await uploadOne(actions, form.dataset.name ?? file.name, file, () => false);
			line.textContent = `${name}: ${message(result)}`;
		}
		input.value = '';
	});
	form.hidden = false;
}
