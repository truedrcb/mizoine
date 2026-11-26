// A browser's session for the end-to-end flows (EditorEndToEndTest, FolderEndToEndTest): same origin, cookies sent
// and kept (session, CSRF cookie), redirects not followed, form login. Not a node --test file.

/** @returns {{fetch, login, cookies: () => string, requests: Array<{method: string, url: string}>}} */
export function browser(base) {
	/** The browser's cookie store, for this one origin. */
	const cookies = new Map();
	const cookieHeader = () => [...cookies].map(([name, value]) => `${name}=${value}`).join('; ');
	const requests = [];

	async function browserFetch(url, options = {}) {
		requests.push({ method: options.method ?? 'GET', url });
		const response = await fetch(base + url, {
			...options,
			redirect: 'manual',
			headers: { ...(options.headers ?? {}), Cookie: cookieHeader() },
		});
		for (const cookie of response.headers.getSetCookie()) {
			const [pair] = cookie.split(';');
			const separator = pair.indexOf('=');
			cookies.set(pair.substring(0, separator).trim(), pair.substring(separator + 1).trim());
		}
		return response;
	}

	/** @returns the status of the login form's POST (302 when logged in) */
	async function login(user, password) {
		const page = await (await browserFetch('/login')).text();
		const token = /name="_csrf" type="hidden" value="([^"]+)"/.exec(page)[1];
		const response = await browserFetch('/login', {
			method: 'POST',
			headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
			body: new URLSearchParams({ username: user, password, _csrf: token }).toString(),
		});
		return response.status;
	}

	return { fetch: browserFetch, login, cookies: cookieHeader, requests };
}
