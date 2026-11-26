// Steps 71–73: the folder page's logic (src/main/resources/ui/folder/folder.js), without a browser.
// Run: node --test src/test/js/   (also part of mvn verify)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import { checkName, checkNewFileName, editorUrl, FolderActions, renameTo, uploadOne, reloads }
	from '../../main/resources/ui/folder/folder.js';

const FORMATS = JSON.parse(readFileSync(new URL('../../main/resources/ui/editor/text-formats.json', import.meta.url), 'utf8'));

/** A fake fetch: answers with the given responses in turn and records every request. */
function fakeFetch(...responses) {
	const calls = [];
	const fetch = async (url, options = {}) => {
		calls.push({ url, method: options.method ?? 'GET', headers: options.headers ?? {}, body: options.body });
		const next = responses.shift() ?? { status: 500 };
		return { status: next.status, ok: next.status >= 200 && next.status < 300, headers: new Headers(next.headers ?? {}) };
	};
	fetch.calls = calls;
	return fetch;
}

const actions = fetch => new FolderActions('/notes/projects/', fetch, () => 'token');

// names: as the server's rules (D3, D81), normalised to NFC first (D78)

test('a name is normalised to NFC', () => {
	// "ü" decomposed, as macOS may give it: u + combining diaeresis
	assert.deepEqual(checkName('Rückblick.md'), { name: 'Rückblick.md' });
});

for (const [input, error] of [['', 'Write a name.'], ['a/b.md', 'A name cannot contain / or \\.'],
	['a\\b.md', 'A name cannot contain / or \\.'], ['a\u0000b', 'A name cannot contain / or \\.'],
	['50%.md', 'A name cannot contain %.'], ['.env', 'A name cannot start with "." (hidden).'],
	['.Tags', 'A name cannot start with "." (hidden).'], ['..', 'A name cannot start with "." (hidden).']]) {
	test(`${JSON.stringify(input)} is refused: ${error}`, () => {
		assert.deepEqual(checkName(input), { error });
	});
}

test('the tags file and ordinary names are fine', () => {
	assert.deepEqual(checkName('.tags'), { name: '.tags' });
	assert.deepEqual(checkName('Plan 2026 (draft).md'), { name: 'Plan 2026 (draft).md' });
});

// step 71 (D76): a new file is a known text format (D73), so that the editor opens it

test('a new file must be of a known text format', () => {
	assert.deepEqual(checkNewFileName('plan.md', FORMATS), { name: 'plan.md' });
	assert.deepEqual(checkNewFileName('run.bat', FORMATS), { name: 'run.bat' });
	assert.deepEqual(checkNewFileName('photo.png', FORMATS),
		{ error: 'Not a text format: photo.png cannot be edited here. Upload it instead.' });
	assert.deepEqual(checkNewFileName('.env', FORMATS), { error: 'A name cannot start with "." (hidden).' });
});

test('the editor of a file is opened by its URL (D28)', () => {
	assert.equal(editorUrl('/notes/projects/a%20b.md'), '/ui/editor.html?file=%2Fnotes%2Fprojects%2Fa%2520b.md');
});

test('creating a file is one PUT of its URL that never overwrites', async () => {
	const fetch = fakeFetch({ status: 201 });

	assert.deepEqual(await actions(fetch).createFile('Rückblick 1.md'),
		{ state: 'created', url: '/notes/projects/R%C3%BCckblick%201.md' });
	const put = fetch.calls[0];
	assert.equal(put.url, '/notes/projects/R%C3%BCckblick%201.md');
	assert.equal(put.method, 'PUT');
	assert.equal(put.headers['If-None-Match'], '*');
	assert.equal(put.headers['If-Match'], undefined);
	assert.equal(put.headers['X-XSRF-TOKEN'], 'token');
	assert.equal(put.headers['X-Requested-With'], 'XMLHttpRequest');
	assert.equal(put.body, '');
});

for (const [status, state] of [[412, 'exists'], [401, 'login'], [403, 'login'], [404, 'not-found'],
	[409, 'conflict'], [500, 'error']]) {
	test(`creating answered with ${status} is ${state}`, async () => {
		assert.equal((await actions(fakeFetch({ status })).createFile('plan.md')).state, state);
	});
}

test('creating without an answer is an error with status 0', async () => {
	const failing = new FolderActions('/notes/', async () => { throw new TypeError('network'); }, () => null);
	assert.deepEqual(await failing.createFile('plan.md'), { state: 'error', status: 0 });
});

// step 72 (D77): upload, one PUT per file on its URL; never over an existing file without asking

test('an upload is a PUT of the file to its URL in this folder, only if nothing has that name', async () => {
	const fetch = fakeFetch({ status: 201 });
	const picture = new Uint8Array([0x89, 0x50, 0x4e, 0x47]);

	assert.deepEqual(await actions(fetch).upload('Foto 1.jpg', picture), { state: 'uploaded' });
	const put = fetch.calls[0];
	assert.equal(put.url, '/notes/projects/Foto%201.jpg');
	assert.equal(put.method, 'PUT');
	assert.equal(put.headers['If-None-Match'], '*');
	assert.equal(put.headers['X-XSRF-TOKEN'], 'token');
	assert.equal(put.body, picture);
});

test('replacing, after the user said yes, is a PUT without the create-only condition', async () => {
	const fetch = fakeFetch({ status: 204 });

	assert.deepEqual(await actions(fetch).upload('plan.md', 'new', true), { state: 'replaced' });
	assert.equal(fetch.calls[0].headers['If-None-Match'], undefined);
	assert.equal(fetch.calls[0].headers['If-Match'], undefined);
});

for (const [status, state] of [[412, 'exists'], [413, 'too-large'], [401, 'login'], [404, 'not-found'],
	[409, 'conflict'], [500, 'error']]) {
	test(`an upload answered with ${status} is ${state}`, async () => {
		assert.equal((await actions(fakeFetch({ status })).upload('a.pdf', 'x')).state, state);
	});
}

// step 73: rename (a move in the same folder, step 41) and delete (step 32)

test('rename is a move to the new name in the same folder, which never overwrites (D30)', async () => {
	const fetch = fakeFetch({ status: 201 });

	assert.deepEqual(await actions(fetch).rename('old name.md', 'neu ü.md'), { state: 'renamed' });
	const move = fetch.calls[0];
	assert.equal(move.url, '/notes/projects/old%20name.md');
	assert.equal(move.method, 'POST');
	assert.equal(move.headers.Destination, '/notes/projects/neu%20%C3%BC.md');
	assert.equal(move.headers['X-XSRF-TOKEN'], 'token');
});

for (const [status, state] of [[412, 'exists'], [404, 'not-found'], [409, 'conflict'], [403, 'login'], [500, 'error']]) {
	test(`a rename answered with ${status} is ${state}`, async () => {
		assert.equal((await actions(fakeFetch({ status })).rename('a.md', 'b.md')).state, state);
	});
}

test('delete is a DELETE of the file URL', async () => {
	const fetch = fakeFetch({ status: 204 });

	assert.deepEqual(await actions(fetch).remove('plan.md'), { state: 'deleted' });
	assert.equal(fetch.calls[0].url, '/notes/projects/plan.md');
	assert.equal(fetch.calls[0].method, 'DELETE');
	assert.equal(fetch.calls[0].headers['X-XSRF-TOKEN'], 'token');
});

for (const [status, state] of [[404, 'not-found'], [409, 'conflict'], [401, 'login'], [500, 'error']]) {
	test(`a delete answered with ${status} is ${state}`, async () => {
		assert.equal((await actions(fakeFetch({ status })).remove('a.md')).state, state);
	});
}

// the decisions of the page's dialogs, so that the page only shows them

test('a typed new name: cancelled or unchanged is nothing to do, else checked', () => {
	assert.deepEqual(renameTo('plan.md', null), { skip: true });
	assert.deepEqual(renameTo('plan.md', 'plan.md'), { skip: true });
	assert.deepEqual(renameTo('plan.md', 'a/b.md'), { error: 'A name cannot contain / or \\.' });
	assert.deepEqual(renameTo('plan.md', 'Plan neu.md'), { name: 'Plan neu.md' });
	// the same name once normalised (D78): nothing to do either
	assert.deepEqual(renameTo('R\u00fcck.md', 'Ru\u0308ck.md'), { skip: true });
});

test('one picked file: a refused name makes no request', async () => {
	const fetch = fakeFetch();
	assert.deepEqual(await uploadOne(actions(fetch), '.env', 'x', () => true),
		{ name: '.env', result: { state: 'refused', error: 'A name cannot start with "." (hidden).' } });
	assert.equal(fetch.calls.length, 0);
});

test('one picked file: an existing name is replaced only if the user says yes', async () => {
	const asked = [];
	const yes = name => { asked.push(name); return true; };
	const replacing = fakeFetch({ status: 412 }, { status: 204 });
	assert.deepEqual(await uploadOne(actions(replacing), 'Ru\u0308ck.pdf', 'x', yes),
		{ name: 'R\u00fcck.pdf', result: { state: 'replaced' } });
	assert.deepEqual(asked, ['R\u00fcck.pdf']);
	assert.equal(replacing.calls[1].headers['If-None-Match'], undefined);

	const keeping = fakeFetch({ status: 412 });
	assert.deepEqual(await uploadOne(actions(keeping), 'a.pdf', 'x', () => false),
		{ name: 'a.pdf', result: { state: 'exists' } });
	assert.equal(keeping.calls.length, 1);
});

test('the listing reloads after a change only', () => {
	for (const state of ['uploaded', 'replaced', 'renamed', 'deleted']) {
		assert.equal(reloads({ state }), true, state);
	}
	for (const state of ['exists', 'refused', 'error', 'login', 'too-large']) {
		assert.equal(reloads({ state }), false, state);
	}
});
