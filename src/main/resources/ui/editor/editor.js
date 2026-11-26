// The editor's logic (DEV.md step 37), without the page: what it loads, saves and shows is decided here.
// It uses only the public file API: GET to load, PUT to save, on the file's own URL. It never creates a file
// (step 66, D76): a link to a missing path must not make the user create one there.
// Tests: src/test/js/editor.test.mjs (node --test).

/** Marks requests of this script: no Basic login dialog on 401, and no "page to return to" after login. */
export const SCRIPT = { 'X-Requested-With': 'XMLHttpRequest' };

/** One request of the browser UI: no answer at all (network down, server stopped) counts as status 0, never silence. */
export async function send(fetch, url, options) {
	try {
		return await fetch(url, options);
	} catch {
		return { status: 0, ok: false };
	}
}

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

/** Strict: a file that is not UTF-8 is no text (D21); a byte order mark is kept, so it is saved back as it was. */
const UTF8 = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true });

/** One file being edited: what was loaded or last saved, and its ETag. */
export class FileSession {

	#url;
	#fetch;
	#maxBytes;
	/** The file's content as stored, or null while no existing file is loaded: then there is nothing to save. */
	#stored = null;
	#etag = null;

	/** @param maxBytes the largest text file the editor opens (the list of text formats, D21) */
	constructor(url, fetch, maxBytes) {
		this.#url = url;
		this.#fetch = fetch;
		this.#maxBytes = maxBytes;
	}

	/**
	 * @returns {Promise<{state: 'loaded', text: string} | {state: 'missing'|'not-text'|'login'}
	 *     | {state: 'error', status: number}>}
	 */
	async load() {
		// '*/*': the raw file, never the rendered page (D22)
		const response = await this.#request({ headers: { Accept: '*/*', ...SCRIPT }, cache: 'no-store' });
		if (response.status === 404) {
			this.#stored = null;
			this.#etag = null;
			return { state: 'missing' };
		}
		if (response.status === 401) {
			return { state: 'login' };
		}
		if (!response.ok) {
			return { state: 'error', status: response.status };
		}
		const text = decodeText(new Uint8Array(await response.arrayBuffer()), this.#maxBytes);
		if (text === null) {
			// a guard (step 69): a text name on something else; nothing loaded, so it is never saved over
			this.#stored = null;
			this.#etag = null;
			return { state: 'not-text' };
		}
		this.#stored = text;
		this.#etag = response.headers.get('ETag');
		return { state: 'loaded', text: toLf(text) };
	}

	/**
	 * Saves only if nobody changed the file since it was loaded or last saved (If-Match): nothing is overwritten
	 * silently, and a file that is gone is not created again. Without a loaded file, no request at all.
	 */
	async save(text, csrf) {
		if (this.#stored === null) {
			return { state: 'not-loaded' };
		}
		const body = restoreLineEndings(this.#stored, text);
		const response = await this.#request({
			method: 'PUT',
			headers: { 'Content-Type': 'text/plain;charset=UTF-8', ...SCRIPT, 'If-Match': this.#etag,
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

	#request(options) {
		return send(this.#fetch, this.#url, options);
	}

	/** Only a loaded file can have changes. */
	isChanged(text) {
		return this.#stored !== null && text !== toLf(this.#stored);
	}
}

/** @returns {string | null} the text, if the bytes are valid UTF-8 without a NUL and not larger than maxBytes (D21) */
function decodeText(bytes, maxBytes) {
	if (bytes.length > maxBytes) {
		return null;
	}
	try {
		const text = UTF8.decode(bytes);
		return text.includes('\0') ? null : text;
	} catch {
		return null;
	}
}

/**
 * Step 69 (D73, D75): the editor opens only known text formats, by name, from the one list
 * (/ui/editor/text-formats.json, also read by the server): a whole name, else the extension in any case.
 * @returns {string | null} the highlighting ('text' for none), or null for a file the editor does not open
 */
export function textFormat(formats, fileName) {
	if (Object.hasOwn(formats.names, fileName)) {
		return formats.names[fileName];
	}
	const dot = fileName.lastIndexOf('.');
	const extension = dot > 0 ? fileName.substring(dot + 1).toLowerCase() : '';
	return Object.hasOwn(formats.extensions, extension) ? formats.extensions[extension] : null;
}
