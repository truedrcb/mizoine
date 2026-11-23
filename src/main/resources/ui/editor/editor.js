// The editor's logic (DEV.md step 37), without the page: what it loads, saves and shows is decided here.
// It uses only the public file API: GET to load, PUT to create or save, on the file's own URL.
// Tests: src/test/js/editor.test.mjs (node --test).

/** Marks requests of this script: no Basic login dialog on 401, and no "page to return to" after login. */
const SCRIPT = { 'X-Requested-With': 'XMLHttpRequest' };

/**
 * The `file` parameter of /ui/editor.html (D28): the file's URL path on this server, as in a link.
 * @returns {{url: string, folderUrl: string, name: string} | null} null if it is not a file path on this server
 */
export function parseFileParam(search) {
	const url = new URLSearchParams(search).get('file');
	// a path on this server: no scheme, no other host ('//'), no backslash or control characters, not a folder,
	// and at least repository and file name
	if (!url || !/^\/[^/]/.test(url) || /[\\\u0000-\u001f]/.test(url) || url.endsWith('/')
			|| url.split('/').length < 3) {
		return null;
	}
	const slash = url.lastIndexOf('/');
	return { url, folderUrl: url.substring(0, slash + 1), name: decodeName(url.substring(slash + 1)) };
}

function decodeName(segment) {
	try {
		return decodeURIComponent(segment);
	} catch {
		return segment;
	}
}

/** D27: Spring Security's CSRF token, from the cookie XSRF-TOKEN. */
export function csrfToken(cookies) {
	const cookie = cookies.split(';').map(part => part.trim()).find(part => part.startsWith('XSRF-TOKEN='));
	return cookie ? decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)) : null;
}

/**
 * A textarea turns CRLF into LF. A file that used CRLF throughout gets it back, so that saving does not change
 * every line; any other file keeps LF.
 */
export function restoreLineEndings(original, edited) {
	const usesCrlf = original.includes('\r\n') && !/(^|[^\r])\n/.test(original);
	return usesCrlf ? edited.replace(/\r?\n/g, '\r\n') : edited;
}

const toLf = text => text.replace(/\r\n/g, '\n');

const SAVE_RESULTS = { 401: 'login', 403: 'login', 412: 'conflict', 413: 'too-large' };

/** One file being edited: what was loaded or last saved, and its ETag. */
export class FileSession {

	#url;
	#fetch;
	/** The file's content as stored, or null for a file that does not exist yet. */
	#stored = null;
	#etag = null;

	constructor(url, fetch) {
		this.#url = url;
		this.#fetch = fetch;
	}

	/** @returns {Promise<{state: 'loaded'|'new', text: string} | {state: 'login'} | {state: 'error', status: number}>} */
	async load() {
		// '*/*': the raw file, never the rendered page (D22)
		const response = await this.#request({ headers: { Accept: '*/*', ...SCRIPT }, cache: 'no-store' });
		if (response.status === 404) {
			this.#stored = null;
			this.#etag = null;
			return { state: 'new', text: '' };
		}
		if (response.status === 401) {
			return { state: 'login' };
		}
		if (!response.ok) {
			return { state: 'error', status: response.status };
		}
		this.#stored = await response.text();
		this.#etag = response.headers.get('ETag');
		return { state: 'loaded', text: toLf(this.#stored) };
	}

	/**
	 * Saves only if nobody changed the file since it was loaded or last saved (If-Match), or, for a new file,
	 * only if it still does not exist (If-None-Match: *). Nothing is overwritten silently.
	 */
	async save(text, csrf) {
		const body = restoreLineEndings(this.#stored ?? '', text);
		const precondition = this.#stored === null ? { 'If-None-Match': '*' } : { 'If-Match': this.#etag };
		const response = await this.#request({
			method: 'PUT',
			headers: { 'Content-Type': 'text/plain;charset=UTF-8', ...SCRIPT, ...precondition,
				...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}) },
			body,
		});
		if (!response.ok) {
			const state = SAVE_RESULTS[response.status];
			return state ? { state } : { state: 'error', status: response.status };
		}
		this.#stored = body;
		this.#etag = response.headers.get('ETag');
		return { state: 'saved' };
	}

	/** No answer at all (network down, server stopped) counts as status 0: an error, never silence. */
	async #request(options) {
		try {
			return await this.#fetch(this.#url, options);
		} catch {
			return { status: 0, ok: false };
		}
	}

	isChanged(text) {
		return text !== toLf(this.#stored ?? '');
	}
}

/** Step 39: file extension → language for syntax highlighting (the names the CodeMirror bundle knows). */
const LANGUAGES = {
	md: 'markdown', markdown: 'markdown', json: 'json', yaml: 'yaml', yml: 'yaml', html: 'html', htm: 'html',
	css: 'css', js: 'javascript', mjs: 'javascript', ts: 'javascript', xml: 'xml', svg: 'xml', java: 'java',
	properties: 'properties', sh: 'shell',
};

/** @returns {string} a language name, or 'text' for plain text */
export function languageFor(fileName) {
	const dot = fileName.lastIndexOf('.');
	return dot > 0 ? LANGUAGES[fileName.substring(dot + 1).toLowerCase()] ?? 'text' : 'text';
}
