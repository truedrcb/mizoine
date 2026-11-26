// The folder page's logic (DEV.md steps 71–73), without the page: names, requests and their answers are decided
// here. It uses only the public file API on the files' own URLs, as curl and the editor do.
// Tests: src/test/js/folder.test.mjs (node --test).
import { SCRIPT, send, textFormat } from '../editor/editor.js';

/** The name of the tags file: the one name starting with "." that is not hidden (D81). */
const TAGS_FILE = '.tags';

/**
 * A name typed or picked in the browser: normalised to NFC (D78), then checked by the server's rules (D3, D81),
 * so that nothing is sent that the server would refuse anyway.
 * @returns {{name: string} | {error: string}}
 */
export function checkName(input) {
	const name = input.normalize('NFC');
	if (name === '') {
		return { error: 'Write a name.' };
	}
	if (/[/\\\u0000-\u001f]/.test(name)) {
		return { error: 'A name cannot contain / or \\.' };
	}
	if (name.includes('%')) {
		return { error: 'A name cannot contain %.' };
	}
	if (name.startsWith('.') && name !== TAGS_FILE) {
		return { error: 'A name cannot start with "." (hidden).' };
	}
	return { name };
}

/** Step 71 (D76): a new file is created to be edited, so it must be a known text format (D73). */
export function checkNewFileName(input, formats) {
	const checked = checkName(input);
	if ('error' in checked || textFormat(formats, checked.name) !== null) {
		return checked;
	}
	return { error: `Not a text format: ${checked.name} cannot be edited here. Upload it instead.` };
}

/**
 * Step 73: what a name typed for Rename means: nothing to do (cancelled, or the same name), or a checked name.
 * @returns {{skip: true} | {name: string} | {error: string}}
 */
export function renameTo(current, typed) {
	if (typed === null) {
		return { skip: true };
	}
	const checked = checkName(typed);
	return 'name' in checked && checked.name === current.normalize('NFC') ? { skip: true } : checked;
}

/**
 * Step 72 (D77): one picked file: its name checked; an existing name replaced only if the user says yes.
 * @param replace asks the user, with the checked name, whether to replace the file of that name
 * @returns {Promise<{name: string, result: {state: string}}>}
 */
export async function uploadOne(actions, name, body, replace) {
	const checked = checkName(name);
	if ('error' in checked) {
		return { name, result: { state: 'refused', error: checked.error } };
	}
	const first = await actions.upload(checked.name, body);
	const result = first.state === 'exists' && replace(checked.name) ? await actions.upload(checked.name, body, true) : first;
	return { name: checked.name, result };
}

/** Whether the listing changed, and so reloads. */
export function reloads(result) {
	return ['uploaded', 'replaced', 'renamed', 'deleted'].includes(result.state);
}

/** The editor of a file (D28): the file's URL path as the one parameter. */
export function editorUrl(fileUrl) {
	return '/ui/editor.html?file=' + encodeURIComponent(fileUrl);
}

const ANSWERS = { 401: 'login', 403: 'login', 404: 'not-found', 409: 'conflict' };
const CREATE_RESULTS = { ...ANSWERS, 412: 'exists' };
const UPLOAD_RESULTS = { ...ANSWERS, 201: 'uploaded', 204: 'replaced', 412: 'exists', 413: 'too-large' };
const RENAME_RESULTS = { ...ANSWERS, 201: 'renamed', 412: 'exists' };
const DELETE_RESULTS = { ...ANSWERS, 204: 'deleted' };

function result(response, states) {
	const state = states[response.status];
	return state ? { state } : { state: 'error', status: response.status };
}

/** What the folder page does in one folder: each action one request of the file API. */
export class FolderActions {

	#folderUrl;
	#fetch;
	#csrf;

	/**
	 * @param folderUrl the folder's URL path, encoded, ending with '/'
	 * @param csrf gives the CSRF token for a write (D27)
	 */
	constructor(folderUrl, fetch, csrf) {
		this.#folderUrl = folderUrl;
		this.#fetch = fetch;
		this.#csrf = csrf;
	}

	fileUrl(name) {
		return this.#folderUrl + encodeURIComponent(name);
	}

	#headers(more) {
		const token = this.#csrf();
		return { ...SCRIPT, ...more, ...(token ? { 'X-XSRF-TOKEN': token } : {}) };
	}

	/** Step 71 (D76): an empty file, only if nothing has that name yet (If-None-Match: *). */
	async createFile(name) {
		const url = this.fileUrl(name);
		const response = await send(this.#fetch, url, {
			method: 'PUT',
			headers: this.#headers({ 'Content-Type': 'text/plain;charset=UTF-8', 'If-None-Match': '*' }),
			body: '',
		});
		return response.status === 201 ? { state: 'created', url } : result(response, CREATE_RESULTS);
	}

	/**
	 * Step 72 (D77): a file from the picker, as is. Only if nothing has that name yet (If-None-Match: *), unless the
	 * user said to replace it.
	 */
	async upload(name, body, replace = false) {
		const response = await send(this.#fetch, this.fileUrl(name), {
			method: 'PUT',
			headers: this.#headers(replace ? {} : { 'If-None-Match': '*' }),
			body,
		});
		return result(response, UPLOAD_RESULTS);
	}

	/** Step 73: a move to the new name in this folder (step 41); an existing name is never overwritten (D30). */
	async rename(name, newName) {
		const response = await send(this.#fetch, this.fileUrl(name), {
			method: 'POST',
			headers: this.#headers({ Destination: this.fileUrl(newName) }),
		});
		return result(response, RENAME_RESULTS);
	}

	/** Step 73: after the user said yes. */
	async remove(name) {
		const response = await send(this.#fetch, this.fileUrl(name), { method: 'DELETE', headers: this.#headers({}) });
		return result(response, DELETE_RESULTS);
	}
}
