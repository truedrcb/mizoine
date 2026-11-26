// Step 37: the editor's logic (src/main/resources/ui/editor/editor.js), without a browser.
// Run: node --test src/test/js/   (also part of mvn verify)
import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import { parseFileParam, csrfToken, restoreLineEndings, FileSession, textFormat } from '../../main/resources/ui/editor/editor.js';

/** Step 68: the one list of text formats, as the page fetches it from /ui/editor/text-formats.json. */
const FORMATS = JSON.parse(readFileSync(new URL('../../main/resources/ui/editor/text-formats.json', import.meta.url), 'utf8'));
const newSession = (url, fetch) => new FileSession(url, fetch, FORMATS.maxBytes);

/** Every request of every test in this file: none may create a file (step 66). */
const allCalls = [];

/** A fake fetch: answers with the given responses in turn and records every request. */
function fakeFetch(...responses) {
	const calls = [];
	const fetch = async (url, options = {}) => {
		const call = { url, method: options.method ?? 'GET', headers: options.headers ?? {}, body: options.body };
		calls.push(call);
		allCalls.push(call);
		const next = responses.shift() ?? { status: 500 };
		return {
			status: next.status,
			ok: next.status >= 200 && next.status < 300,
			headers: new Headers(next.headers ?? {}),
			text: async () => next.body ?? '',
			arrayBuffer: async () => (next.bytes ?? new TextEncoder().encode(next.body ?? '')).buffer,
		};
	};
	fetch.calls = calls;
	return fetch;
}

// the file parameter (D28): only a path on this server

test('file parameter gives the file, its page and its folder', () => {
	assert.deepEqual(parseFileParam('?file=/notes/projects/plan.md'), {
		url: '/notes/projects/plan.md',
		folderUrl: '/notes/projects/',
		name: 'plan.md',
	});
});

test('an encoded name is shown decoded but requested encoded', () => {
	const file = parseFileParam('?file=' + encodeURIComponent('/notes/a%20b%20%D0%B4.md'));
	assert.equal(file.url, '/notes/a%20b%20%D0%B4.md');
	assert.equal(file.name, 'a b д.md');
});

for (const bad of [null, '', 'notes/plan.md', '//evil.example/x.md', 'https://evil.example/x.md',
	'javascript:alert(1)', '/notes/', '/plan.md', '/notes/a\\b.md', '/notes/\u0000.md']) {
	test(`not a file on this server: ${JSON.stringify(bad)}`, () => {
		const search = bad === null ? '' : '?file=' + encodeURIComponent(bad);
		assert.equal(parseFileParam(search), null);
	});
}

// D27: the CSRF token from the cookie

test('csrf token comes from the XSRF-TOKEN cookie', () => {
	assert.equal(csrfToken('a=1; XSRF-TOKEN=abc%2Ddef; b=2'), 'abc-def');
	assert.equal(csrfToken('a=1'), null);
	assert.equal(csrfToken(''), null);
});

// line endings stay as they were: a textarea turns CRLF into LF

test('CRLF files are saved with CRLF', () => {
	assert.equal(restoreLineEndings('a\r\nb\r\n', 'a\nb\nc\n'), 'a\r\nb\r\nc\r\n');
});

test('LF files and files without line breaks keep LF', () => {
	assert.equal(restoreLineEndings('a\nb\n', 'a\nb\nc'), 'a\nb\nc');
	assert.equal(restoreLineEndings('', 'new\nfile'), 'new\nfile');
});

test('mixed line endings are kept as LF', () => {
	assert.equal(restoreLineEndings('a\r\nb\nc', 'x\ny'), 'x\ny');
});

// loading: GET of the file URL, never the rendered page

test('load is a GET of the file URL asking for the raw file', async () => {
	const fetch = fakeFetch({ status: 200, body: '# Мой дом\r\n', headers: { ETag: '"v1"' } });
	const session = newSession('/notes/plan.md', fetch);

	assert.deepEqual(await session.load(), { state: 'loaded', text: '# Мой дом\n' });
	assert.equal(fetch.calls[0].url, '/notes/plan.md');
	assert.equal(fetch.calls[0].method, 'GET');
	assert.equal(fetch.calls[0].headers.Accept, '*/*');
	assert.equal(fetch.calls[0].headers['X-Requested-With'], 'XMLHttpRequest');
});

// step 66 (D76): the editor never creates a file; a link to a missing path cannot make the user create one

test('a missing file is shown as missing, and Save sends nothing', async () => {
	const fetch = fakeFetch({ status: 404 });
	const session = newSession('/notes/run.sh', fetch);

	assert.deepEqual(await session.load(), { state: 'missing' });
	assert.deepEqual(await session.save('echo hi', 't'), { state: 'not-loaded' });
	assert.equal(fetch.calls.length, 1);
	assert.equal(session.isChanged('echo hi'), false);
});

for (const failed of [{ status: 401 }, { status: 500 }, null]) {
	test(`after a load that failed (${failed?.status ?? 'no answer'}), Save sends nothing`, async () => {
		const fetch = failed ? fakeFetch(failed) : async () => { throw new TypeError('network'); };
		const calls = [];
		const session = newSession('/notes/plan.md', async (url, options) => {
			calls.push(options);
			return fetch(url, options);
		});
		await session.load();

		assert.deepEqual(await session.save('text', 't'), { state: 'not-loaded' });
		assert.equal(calls.length, 1);
		assert.equal(session.isChanged('text'), false);
	});
}

test('a file deleted elsewhere is not created again by Save', async () => {
	const fetch = fakeFetch({ status: 200, body: 'a', headers: { ETag: '"v1"' } }, { status: 412 }, { status: 404 });
	const session = newSession('/notes/plan.md', fetch);
	await session.load();

	// the server refuses an If-Match on a missing file; Reload then shows it missing, with no Save
	assert.deepEqual(await session.save('b', 't'), { state: 'conflict' });
	assert.equal(fetch.calls[1].headers['If-Match'], '"v1"');
	assert.deepEqual(await session.load(), { state: 'missing' });
	assert.deepEqual(await session.save('b', 't'), { state: 'not-loaded' });
	assert.equal(fetch.calls.length, 3);
});

after(() => {
	assert.deepEqual(allCalls.filter(call => 'If-None-Match' in call.headers), [], 'no request may create a file');
});

test('not logged in while loading', async () => {
	const session = newSession('/notes/plan.md', fakeFetch({ status: 401 }));
	assert.deepEqual(await session.load(), { state: 'login' });
});

// saving: PUT of the same URL with If-Match of the loaded version, never anything else

test('save is a PUT of the same URL with If-Match, the CSRF header and the text', async () => {
	const fetch = fakeFetch({ status: 200, body: 'old\r\n', headers: { ETag: '"v1"' } },
		{ status: 204, headers: { ETag: '"v2"' } });
	const session = newSession('/notes/plan.md', fetch);
	await session.load();

	assert.deepEqual(await session.save('new\n', 'token'), { state: 'saved' });
	const put = fetch.calls[1];
	assert.equal(put.url, '/notes/plan.md');
	assert.equal(put.method, 'PUT');
	assert.equal(put.headers['If-Match'], '"v1"');
	assert.equal(put.headers['If-None-Match'], undefined);
	assert.equal(put.headers['X-XSRF-TOKEN'], 'token');
	assert.equal(put.headers['X-Requested-With'], 'XMLHttpRequest');
	assert.equal(put.body, 'new\r\n');
});

test('the next save uses the new ETag', async () => {
	const fetch = fakeFetch({ status: 200, body: 'a', headers: { ETag: '"v1"' } },
		{ status: 204, headers: { ETag: '"v2"' } }, { status: 204, headers: { ETag: '"v3"' } });
	const session = newSession('/notes/plan.md', fetch);
	await session.load();
	await session.save('b', 't');
	await session.save('c', 't');

	assert.equal(fetch.calls[2].headers['If-Match'], '"v2"');
});

test('changed elsewhere: conflict, and the text is kept for the user', async () => {
	const session = newSession('/notes/plan.md', fakeFetch({ status: 200, body: 'a', headers: { ETag: '"v1"' } },
		{ status: 412 }));
	await session.load();

	assert.deepEqual(await session.save('mine', 't'), { state: 'conflict' });
	assert.equal(session.isChanged('mine'), true);
});

for (const [status, state] of [[401, 'login'], [403, 'login'], [413, 'too-large'], [500, 'error']]) {
	test(`save answered with ${status} is ${state}`, async () => {
		const session = newSession('/notes/plan.md', fakeFetch({ status: 200, body: 'a', headers: { ETag: '"v1"' } },
			{ status }));
		await session.load();
		const result = await session.save('b', 't');
		assert.equal(result.state, state);
	});
}

// changed or not: Save is only offered when there is something to save

test('unchanged text is not a change, also for CRLF files', async () => {
	const session = newSession('/notes/plan.md', fakeFetch({ status: 200, body: 'a\r\nb', headers: { ETag: '"v1"' } },
		{ status: 204, headers: { ETag: '"v2"' } }));
	await session.load();

	assert.equal(session.isChanged('a\nb'), false);
	assert.equal(session.isChanged('a\nb!'), true);
	await session.save('a\nb!', 't');
	assert.equal(session.isChanged('a\nb!'), false);
});

test('text round-trips: non-ASCII and line endings unchanged', async () => {
	const original = 'Über\r\nмой дом\r\n😀\r\n';
	const fetch = fakeFetch({ status: 200, body: original, headers: { ETag: '"v1"' } },
		{ status: 204, headers: { ETag: '"v2"' } });
	const session = newSession('/notes/plan.md', fetch);
	const { text } = await session.load();
	await session.save(text, 't');

	assert.equal(fetch.calls[1].body, original);
});

// step 69 (D73, D75): only known text formats open, from the one list; their highlighting comes from it too

for (const [name, language] of [['plan.md', 'markdown'], ['README.markdown', 'markdown'], ['data.json', 'json'],
	['config.yaml', 'yaml'], ['config.yml', 'yaml'], ['page.html', 'html'], ['page.htm', 'html'], ['style.css', 'css'],
	['app.js', 'javascript'], ['app.mjs', 'javascript'], ['app.cjs', 'javascript'], ['app.ts', 'javascript'],
	['pom.xml', 'xml'], ['icon.svg', 'xml'], ['schema.xsd', 'xml'], ['style.xslt', 'xml'], ['Main.java', 'java'],
	['app.properties', 'properties'], ['run.sh', 'shell'], ['run.bash', 'shell'], ['PLAN.MD', 'markdown'],
	['a.md', 'markdown'], ['notes.txt', 'text'], ['run.bat', 'text'], ['app.py', 'text'], ['Makefile', 'text'],
	['Dockerfile', 'text'], ['.tags', 'text']]) {
	test(`${name} opens, highlighted as ${language}`, () => {
		assert.equal(textFormat(FORMATS, name), language);
	});
}

for (const name of ['photo.png', 'doc.pdf', 'data.bin', 'LICENSE', 'makefile', '.hidden', '.md', 'plan.', '']) {
	test(`${JSON.stringify(name)} is no text format: the editor does not open it`, () => {
		assert.equal(textFormat(FORMATS, name), null);
	});
}

// a guard, not a second rule: a file with a text name that does not hold text is never saved over

for (const [what, bytes] of [['not UTF-8', [0x89, 0x50, 0x4e, 0x47, 0xff, 0xfe]], ['a NUL byte', [0x61, 0x00, 0x62]]]) {
	test(`a file with ${what} loads as not-text, and Save sends nothing`, async () => {
		const fetch = fakeFetch({ status: 200, bytes: new Uint8Array(bytes), headers: { ETag: '"v1"' } });
		const session = newSession('/notes/photo.txt', fetch);

		assert.deepEqual(await session.load(), { state: 'not-text' });
		assert.deepEqual(await session.save('x', 't'), { state: 'not-loaded' });
		assert.equal(session.isChanged('x'), false);
		assert.equal(fetch.calls.length, 1);
	});
}

test('a file larger than the list allows loads as not-text', async () => {
	const big = new Uint8Array(FORMATS.maxBytes + 1).fill(0x61);
	const session = newSession('/notes/big.txt', fakeFetch({ status: 200, bytes: big, headers: { ETag: '"v1"' } }));
	assert.deepEqual(await session.load(), { state: 'not-text' });
	const fits = new Uint8Array(FORMATS.maxBytes).fill(0x61);
	const fitting = newSession('/notes/big.txt', fakeFetch({ status: 200, bytes: fits, headers: { ETag: '"v1"' } }));
	assert.equal((await fitting.load()).state, 'loaded');
});

test('a byte order mark is kept exactly', async () => {
	const fetch = fakeFetch({ status: 200, bytes: new Uint8Array([0xef, 0xbb, 0xbf, 0x61]), headers: { ETag: '"v1"' } },
		{ status: 204, headers: { ETag: '"v2"' } });
	const session = newSession('/notes/bom.txt', fetch);
	const { text } = await session.load();
	await session.save(text, 't');

	assert.equal(fetch.calls[1].body, '\uFEFFa');
});

// no answer at all (network down, server stopped): an error, never a silent failure, and the text stays

const unreachable = async () => { throw new TypeError('Failed to fetch'); };

test('load without an answer is an error', async () => {
	const session = newSession('/notes/plan.md', unreachable);
	assert.deepEqual(await session.load(), { state: 'error', status: 0 });
});

test('save without an answer is an error and the text is still unsaved', async () => {
	let answers = 0;
	const fetch = async () => {
		if (answers++ > 0) {
			throw new TypeError('Failed to fetch');
		}
		return { status: 200, ok: true, headers: new Headers({ ETag: '"v1"' }), arrayBuffer: async () => new TextEncoder().encode('a').buffer };
	};
	const session = newSession('/notes/plan.md', fetch);
	await session.load();

	assert.deepEqual(await session.save('b', 't'), { state: 'error', status: 0 });
	assert.equal(session.isChanged('b'), true);
});
