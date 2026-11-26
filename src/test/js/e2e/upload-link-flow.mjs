// End-to-end flow of an upload link against a running server (started by UploadLinkEndToEndTest): the link made
// over the API (HTTP Basic, as an agent's tool would), then the real folder.js uploads through it as the link's page
// does: no login, no cookies, no CSRF token. Not a node --test file.
//
// Usage: node upload-link-flow.mjs <base URL> <user> <password> <folder URL>
// Prints one line per step, "<step> <state>", then "done".
import { FolderActions, uploadOne } from '../../../main/resources/ui/folder/folder.js';

const [base, user, password, folder] = process.argv.slice(2);
const made = await fetch(`${base}${folder}?upload-link`, {
	method: 'POST',
	headers: { Authorization: `Basic ${Buffer.from(`${user}:${password}`).toString('base64')}`,
		'Content-Type': 'application/json' },
	body: '{}',
});
console.log(`link ${made.status}`);
// the page's own address on this server, as window.location.pathname on the phone
const link = new URL((await made.json()).url).pathname;
console.log(`page ${(await fetch(base + link)).status}`);

const actions = new FolderActions(link, (url, options) => fetch(base + url, { ...options, redirect: 'manual' }), () => null);
const photo = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]);
const step = async (name, picked) => console.log(`${name} ${(await uploadOne(actions, picked, photo, () => true)).result.state}`);
await step('upload', 'IMG_0001.JPG');
await step('upload-again', 'IMG_0001.JPG');
console.log('done');
