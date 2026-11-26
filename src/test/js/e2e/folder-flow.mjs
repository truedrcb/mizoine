// End-to-end flow of the folder page's actions against a running server (started by FolderEndToEndTest): the real
// folder.js, real HTTP, a browser's session with the CSRF cookie the folder page gives. Not a node --test file.
//
// Usage: node folder-flow.mjs <base URL> <user> <password> <folder URL>
// Prints one line per step, "<step> <state>", then "done".
import { csrfToken } from '../../../main/resources/ui/editor/editor.js';
import { FolderActions } from '../../../main/resources/ui/folder/folder.js';
import { browser } from './browser.mjs';

const [base, user, password, folder] = process.argv.slice(2);
const session = browser(base);
const step = (name, result) => console.log(`${name} ${result.state}`);

console.log(`login ${await session.login(user, password)}`);
// the folder page, as the browser opens it: it gives the CSRF cookie its script sends back
console.log(`page ${(await session.fetch(folder, { headers: { Accept: 'text/html' } })).status}`);
const actions = new FolderActions(folder, session.fetch, () => csrfToken(session.cookies()));

step('create', await actions.createFile('neu.md'));
step('create-again', await actions.createFile('neu.md'));
step('upload', await actions.upload('Foto.png', new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x00, 0xff])));
step('upload-again', await actions.upload('Foto.png', new Uint8Array([1])));
step('replace', await actions.upload('Foto.png', new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a]), true));
step('rename', await actions.rename('neu.md', 'Plan ü.md'));
step('rename-onto-existing', await actions.rename('Plan ü.md', 'Foto.png'));
step('delete', await actions.remove('Plan ü.md'));
console.log('done');
