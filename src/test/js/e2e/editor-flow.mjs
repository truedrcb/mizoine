// End-to-end flow of the editor against a running server (started by EditorEndToEndTest): the real editor.js,
// real HTTP, a real browser session (form login, session cookie, CSRF cookie and header). Not a node --test file.
//
// Usage: node editor-flow.mjs <base URL> <user> <password> <scenario> <file path> [<text 1> <text 2>]
// Scenarios:
//   edit     load, save text 1, save text 2;
//   missing  load a file that does not exist, try to save (step 66: the editor never creates a file);
//   deleted  load, the file is deleted by another client, save, reload, save again.
// Prints one line per step, "<step> <state>", then "puts <how many PUT requests were sent>" and "done".
import { FileSession, csrfToken } from '../../../main/resources/ui/editor/editor.js';

const [base, user, password, scenario, file, firstText = 'first', secondText = 'second'] = process.argv.slice(2);
let puts = 0;

/** The browser's cookie store, for this one origin. */
const cookies = new Map();
const cookieHeader = () => [...cookies].map(([name, value]) => `${name}=${value}`).join('; ');

/** fetch as the browser does it for this page: same origin, cookies sent and kept, redirects not followed. */
async function browserFetch(url, options = {}) {
	if (options.method === 'PUT') {
		puts++;
	}
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

const save = async (step, text) => console.log(`${step} ${(await session.save(text, csrfToken(cookieHeader()))).state}`);
const load = async step => console.log(`${step} ${(await session.load()).state}`);

await login();
const session = new FileSession(file, browserFetch);
await load('load');
if (scenario === 'deleted') {
	// another client (another tab, an agent) deletes the file while it is open
	const deleted = await browserFetch(file, { method: 'DELETE',
		headers: { 'X-Requested-With': 'XMLHttpRequest', 'X-XSRF-TOKEN': csrfToken(cookieHeader()) } });
	console.log(`delete ${deleted.status}`);
}
await save('save-1', firstText);
if (scenario === 'deleted') {
	await load('reload');
}
if (scenario !== 'missing') {
	await save('save-2', secondText);
}
console.log(`puts ${puts}`);
console.log('done');
