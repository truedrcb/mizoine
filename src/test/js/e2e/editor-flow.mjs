// End-to-end flow of the editor against a running server (started by EditorEndToEndTest): the real editor.js,
// real HTTP, a real browser session (form login, session cookie, CSRF cookie and header). Not a node --test file.
//
// Usage: node editor-flow.mjs <base URL> <user> <password> <scenario> <file path> [<text 1> <text 2>]
// Scenarios:
//   edit     load, save text 1, save text 2;
//   missing  load a file that does not exist, try to save (step 66: the editor never creates a file);
//   deleted  load, the file is deleted by another client, save, reload, save again.
// Prints one line per step, "<step> <state>", then "puts <how many PUT requests were sent>" and "done".
import { readFileSync } from 'node:fs';
import { FileSession, csrfToken } from '../../../main/resources/ui/editor/editor.js';
import { browser } from './browser.mjs';

const { maxBytes } = JSON.parse(readFileSync(new URL('../../../main/resources/ui/editor/text-formats.json', import.meta.url), 'utf8'));

const [base, user, password, scenario, file, firstText = 'first', secondText = 'second'] = process.argv.slice(2);
const { fetch: browserFetch, login, cookies: cookieHeader, requests } = browser(base);

const save = async (step, text) => console.log(`${step} ${(await session.save(text, csrfToken(cookieHeader()))).state}`);
const load = async step => console.log(`${step} ${(await session.load()).state}`);

console.log(`login ${await login(user, password)}`);
const session = new FileSession(file, browserFetch, maxBytes);
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
console.log(`puts ${requests.filter(request => request.method === 'PUT').length}`);
console.log('done');
