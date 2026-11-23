// End-to-end flow of the editor against a running server (started by EditorEndToEndTest): the real editor.js,
// real HTTP, a real browser session (form login, session cookie, CSRF cookie and header). Not a node --test file.
//
// Usage: node editor-flow.mjs <base URL> <user> <password> <file path> <text 1> <text 2>
// Prints one line per step, "<step> <state>", then "done".
import { FileSession, csrfToken } from '../../../main/resources/ui/editor/editor.js';

const [base, user, password, file, firstText, secondText] = process.argv.slice(2);

/** The browser's cookie store, for this one origin. */
const cookies = new Map();
const cookieHeader = () => [...cookies].map(([name, value]) => `${name}=${value}`).join('; ');

/** fetch as the browser does it for this page: same origin, cookies sent and kept, redirects not followed. */
async function browserFetch(url, options = {}) {
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

async function login() {
	const page = await (await browserFetch('/login')).text();
	const token = /name="_csrf" type="hidden" value="([^"]+)"/.exec(page)[1];
	const response = await browserFetch('/login', {
		method: 'POST',
		headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
		body: new URLSearchParams({ username: user, password, _csrf: token }).toString(),
	});
	console.log(`login ${response.status}`);
}

await login();
const session = new FileSession(file, browserFetch);
console.log(`load ${(await session.load()).state}`);
console.log(`save-1 ${(await session.save(firstText, csrfToken(cookieHeader()))).state}`);
console.log(`save-2 ${(await session.save(secondText, csrfToken(cookieHeader()))).state}`);
console.log('done');
