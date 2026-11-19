# Technology decisions: file API, AI connection, user interface

*Written 2026-11-19 for planning DEV.md steps 28–39. Status: all decided 2026-11-19: T1 A (REST), T2 C (remote
MCP), T3 D (small library without build), T3.1 CodeMirror 6 (later; a plain textarea first). T3.2 is a proposal. Facts about
external products (MCP clients, Agent Skills, WebDAV clients) are as known at the time of writing: check them
again before deciding.*

Three decisions, in this order, because each builds on the previous one:

- **T1 — file API style:** how files are read, written and deleted over HTTP.
- **T2 — AI connection:** how an AI agent uses that API.
- **T3 — user interface:** how a browser UI uses it.

Fixed by BACKLOG.md and the current code, whatever is chosen:

- **Plain files on disk stay the only source of truth.** Every layer can be switched off.
- **Mizoine runs no Git commands.** A human commits.
- **Permissions are the existing folder grants.** A grant allows reading and writing its folder and everything
  below it.
  - Folders above a grant (D4) stay read-only and show only the way down.
  - Hidden names (`.git`, …, D3) are never readable or writable.
- **One API for everyone.** The UI and the AI use the same API, and so do curl and scripts. There is no second
  API for any of them.

---

## T1 — File API style

> **Decided 2026-11-19: A, REST on the file URLs** (GET / PUT / DELETE), built so that WebDAV can be added later
> on the same URLs.

| | A. REST on the file URLs (GET / PUT / DELETE) | B. A subset of WebDAV | C. JSON operations (`POST /api/write`, …) |
|---|---|---|---|
| **How** | The URL is the path: `GET /notes/a/b.md`, `PUT /notes/a/b.md` (the body is the content), `DELETE /notes/a/b.md`; folders end with `/` | A plus `PROPFIND` (listing), `MKCOL`, `MOVE`, `COPY` and, for most desktop clients, `LOCK` | One endpoint per operation, parameters in JSON |
| **Standard** | HTTP semantics (RFC 9110), as in S3 and WebDAV | RFC 4918 | Own |
| **Clients** | curl, fetch, any HTTP library, AI agents with a shell | A's clients, plus OS file managers, rclone, Obsidian sync plugins | Only own code |
| **Complexity** | Low: three methods, one code path for access checks | Medium to high: XML properties, depth headers; `LOCK` is needed before macOS Finder mounts a share as writable | Low, but everything is invented |
| **Drawbacks** | No rename or move in plain HTTP (it needs an extension, see below) | Many quirks in client behaviour. Windows' built-in WebDAV client (WebClient) is deprecated | Not compatible with anything; harder for AI agents to guess |

**Recommendation: A now, built so that B can be added later on the same URLs.** A is the subset of WebDAV that
needs no XML. `MOVE` is the natural extension when renaming is needed.

Details of A, to be fixed in the DEV.md steps:

- **Concurrent edits:** the UI, an AI agent and an outside editor (vim, Obsidian, Git pull) can change the same
  file. Every file response carries an `ETag`.
  - `PUT` and `DELETE` accept `If-Match: <etag>` → `412 Precondition Failed` if the file changed since it was read.
  - `If-None-Match: *` means "create only" → `412` if the file exists.
  - Without these headers, the write simply wins (as in curl or `cp`).
- **Atomic writes:** the content is written to a hidden temporary file in the same folder, then moved over the
  target in one step. Readers and Git never see half a file. The temporary name starts with `.`, so it is never
  listed.
- **Safe reading in the browser:** user files come from the same origin as the application. An uploaded
  `.html` or `.svg` must not run with the user's session. So every file response gets:
  - `Content-Security-Policy: sandbox` (scripts never run);
  - `X-Content-Type-Options: nosniff`;
  - `Content-Disposition: attachment` for types a browser would execute (HTML, SVG, XML, JavaScript, unknown).
  Images, PDF and plain text show inline. The alternative, a separate domain for downloads, is more setup and
  not needed with these headers.
- **Free with Spring:** `Range` requests (video, large PDFs) and `HEAD` when a controller returns a `Resource`.
  `If-None-Match` → `304` saves bandwidth.
- **Folders are created and removed explicitly** (decided 2026-11-19, DEV.md D14 and D15). There are
  standards to follow:

  | | WebDAV (RFC 4918) | Solid Protocol | Mizoine |
  |---|---|---|---|
  | Folder URL | any | ends with `/` | ends with `/` (D5) |
  | Create a folder | `MKCOL` | `PUT` to the `…/` URL, or implicitly | `PUT …/`, explicitly only |
  | Write into a missing parent | 409 | parents created implicitly | 409 |
  | Create an existing folder | 405 | (PUT, idempotent) | 204; `If-None-Match: *` → 412 |
  | Delete a folder with content | recursive | 409 | 409 |

  The table is a summary from the specifications. Mizoine uses `PUT …/` rather than `MKCOL`, because Spring
  MVC, Spring Security's firewall and OpenAPI 3.1 support only the standard HTTP methods. `MKCOL` can become an
  alias if WebDAV is added.
- **Upload from a plain HTML form** (`POST multipart/form-data` to a folder) only if T3 is a UI without
  JavaScript. A JavaScript UI uses `PUT` like everybody else.
- **CSRF:** cross-site HTML forms cannot send `PUT` or `DELETE`. A cross-site `fetch` with them needs a CORS
  preflight, which Mizoine does not allow. Proposal:
  - a CSRF token is required for requests authenticated by the session cookie (the UI sends it in a header);
  - it is not required for requests with an `Authorization` header (AI agents, curl, scripts).
  Today Spring Security requires the token for every write, so Basic-auth clients get 403.

---

## T2 — AI connection

> **Decided 2026-11-19: C, a remote MCP server in Mizoine, with OAuth.**
> Requirement: Claude or another agent on the smartphone stores and retrieves knowledge in a Mizoine
> repository, as a technical agent user whose grants are limited to a few folders. Connecting must be as
> standard and easy as possible.
>
> - **Only C reaches a smartphone chat.** A connector is added once by URL, then it works everywhere:
>   - Claude: added on claude.ai, it also works in the desktop and mobile apps; Claude Code connects to remote
>     MCP servers too, so one connection serves every place;
>   - other vendors' agents that support remote MCP.
> - **Login is OAuth: the simplest variant that connectors accept.** Claude offers three ways to register:
>   - "Use Claude's published identity";
>   - "Register automatically" (dynamic client registration);
>   - "Use your own OAuth client".
>
>   The last one needs the least in Mizoine: one client configured on the server, whose ID and secret are
>   entered once in Claude. Dynamic registration can follow for agents that need it.
> - **Anthropic's cloud connects, not the phone.** Mizoine must be reachable from the internet, as it already
>   is behind nginx. Cloudflare's bot protection must not block those requests.
> - **The agent user is a normal user** in `users.yaml` with grants to its folders. During "Connect", the
>   browser on the phone shows Mizoine's login page, and the agent user's credentials are entered once.
> - **A, B and D are not needed** for this: C also covers Claude Code.
>
> Sources (checked 2026-11-19):
> - [Claude help: custom connectors using remote MCP](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp)
> - [Spring: securing MCP servers with Spring AI](https://spring.io/blog/2025/09/30/spring-ai-mcp-server-security/)
> - [Spring AI MCP security](https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-security.html)
>
> The comparison below is kept as the background of the decision.

What an agent needs: list a folder, read a file, write a file, delete a file — only inside its grants.
Who should be able to use it, from BACKLOG §10: Claude Code (and other coding agents), a desktop chat app, and
a chat on the smartphone.

| | A. REST + instructions (Skill) | B. Local MCP server (stdio) | C. Remote MCP server in Mizoine (`/mcp`) | D. WebDAV mount |
|---|---|---|---|---|
| **How** | The agent calls the T1 API with curl. A Skill (`SKILL.md`: what the endpoints do, curl examples, where the credentials come from) teaches it once. `docs/openapi.yaml` is the reference | A small separate program on the user's machine offers MCP tools (`list`, `read`, `write`, `delete`) and calls the T1 API | Mizoine itself speaks MCP over HTTP ("Streamable HTTP"); chat apps connect to it by URL | The share is mounted as a drive (rclone, davfs2, Finder); the agent works with ordinary local files |
| **Claude Code, coding agents** | Yes | Yes | Yes | Yes, as local files |
| **Desktop chat apps** | Only with code execution and network access | Yes (configured per app) | Yes | No |
| **Smartphone chat** | No | No | Yes, where the app supports remote MCP connectors | No |
| **Other vendors** | Any agent with a shell; Skills are an open format that other agents read too | Every MCP client | Every MCP client with remote servers | Any agent with file access |
| **Login** | Basic auth or a token from an environment variable | Same, in the program's config | MCP requires OAuth 2.1 for remote servers that need a login. Static tokens work only in some clients | Basic auth in the mount config |
| **Complexity in Mizoine** | None beyond T1 | None; the program is a separate piece (~100–200 lines, plus packaging) | High: an MCP endpoint (Java MCP SDK or Spring AI), and an OAuth authorization server for chat apps (Spring Authorization Server: client registration, consent page, tokens) | High: WebDAV (T1 B) |
| **Drawbacks** | Depends on the agent having a shell; the agent sees curl output | One more program to install and keep up to date per machine | The largest new attack surface (OAuth); MCP and its auth parts still change quickly; must be reachable from the internet (it already is, behind nginx) | Mount tools differ per OS; no fine-grained errors for the agent |
| **Fits "thin independent layers"** | Very well | Well (separate piece) | Less: Mizoine takes on a second protocol and an auth server | Well, but B of T1 is a large layer |

Further points:

- **The AI agent is just a user with grants.** The "restricted space for AI" from BACKLOG §4 / §7 already
  works this way: a user `agent` in `users.yaml` with access to one folder.
  - What is still missing is **tokens instead of passwords**. They can be revoked one by one, without changing
    the human's password.
  - Proposal: a token list in `users.yaml` (only hashes, like passwords), sent as `Authorization: Bearer …`. A
    small step, useful for A, B and D. For C, OAuth replaces it.
- **Writes by AI are reviewed with `git diff`** (BACKLOG §7). Nothing in T2 changes that. MCP tool descriptions
  can say so, so that the agent knows its writes are not final until a human commits.
- **Order of building:** A costs almost nothing once T1 exists, and it is useful immediately. B and C are thin
  adapters over the same service and can follow when needed. Nothing done for A is lost.

**Recommendation:**

1. A (REST + Skill) together with T1, plus tokens for agents.
2. C when access from the smartphone chat is wanted. Plan it as its own step with the OAuth part. Before
   building it, check whether the chat apps in use accept a static token: that would avoid the OAuth server.
3. B only if a desktop app without remote MCP must be supported.
4. D not for AI. If WebDAV comes, it is for file managers.

---

## T3 — User interface (JavaScript in the browser)

> **Decided 2026-11-19: D, a small library without build** (Preact + htm or Lit, copied into `ui/vendor/`).
> Which of the two is chosen in DEV.md step 36 (D26).

Constraints from the current code:

- **CSP** is `default-src 'self'`:
  - no inline scripts or styles;
  - no CDN, so libraries are copied into `src/main/resources/ui/`;
  - no `eval`. Some frameworks compile templates at runtime with `eval` (Vue's full build, standard
    Alpine.js); they need their CSP-safe builds, or a build step.
- **Public files** live under `/ui/**` (step 24), so the UI's code can load before login. The data comes from
  the API and needs a login.
- **Tests:** the Java side is tested with JUnit. A JavaScript UI needs its own test runner, which means Node.js
  at least for development. Node's built-in `node --test` needs no npm packages.

| | A. Server pages (Thymeleaf) + small scripts | B. htmx + Thymeleaf fragments | C. Plain JavaScript modules (no build) | D. Small library without build (Preact + htm, Lit) | E. SPA with build (React / Vue / Svelte + Vite) |
|---|---|---|---|---|---|
| **How** | As now; a few `ui/*.js` files add upload, delete, edit | HTML attributes trigger requests; the server returns HTML pieces | ES modules in `ui/`, `fetch` the JSON API, build the DOM | Same as C, with components and state from a ~5–20 kB library copied into `ui/` | Separate front-end project; the build output goes into the jar (frontend-maven-plugin) |
| **Uses the same API as the AI** | Partly (pages are HTML) | No: needs HTML endpoints next to JSON | Yes | Yes | Yes |
| **Toolchain** | Maven only | Maven + one copied library | Maven (+ Node only for tests) | Maven (+ Node for tests) | Maven + Node + npm, at every build |
| **Complexity to start** | Lowest | Low | Low | Low to medium | Medium to high |
| **Good for** | Lists, simple forms | Server-driven pages | Moderate UI: listings, upload with progress, a Markdown editor | Richer UI with components | Large, complex UI |
| **Drawbacks** | Rich interaction (drag & drop, editor, live preview) becomes hard | A second, HTML-only API; the AI cannot use it | Structure is up to us; grows untidy without discipline | One library to update by hand | Dependency churn and npm supply-chain risk; a second build system against "minimum of tools" |
| **Fits BACKLOG §8 (HTML→Markdown in JS)** | Neutral | Neutral | Well: the converter is a plain module, usable anywhere | Well | Possible, but tied to the build |

Further points:

- **Markdown display:** planned 2026-11-19 as step 38, see T3.2 below: rendered on the server. A live preview
  in the editor (in the browser) can come later.
- **Gradual path:** A or C can start inside the current pages (a script enhances the listing with upload and
  delete). Moving later to D replaces only JavaScript, not the API.

**Recommendation:** C (plain JavaScript modules, no build), with D (Preact + htm or Lit) as the next step if
the UI grows complex.

- It uses the same JSON API as the AI and needs no new build system.
- It fits the strict CSP and `/ui/**`.
- It matches BACKLOG §8.
- E only if the UI becomes a large application.

### T3.1 — Text editor in the browser

> **Decided 2026-11-19: CodeMirror 6, but not yet.** For the moment it is more than needed. The editor starts as
> a plain `<textarea>` with the same file API, saving and conflict handling (DEV.md step 37). CodeMirror 6
> replaces the textarea when syntax highlighting is wanted (step 39); saving does not change.

*Requirement added 2026-11-19:* edit text files directly in the browser, with syntax highlighting for Markdown,
JSON, YAML and other formats where possible. Editing on the smartphone matters too (see T2).

| | A. CodeMirror 6 | B. Ace | C. Monaco (VS Code's editor) | D. CodeMirror 5 | E. `<textarea>` + highlighter (Prism, highlight.js) |
|---|---|---|---|---|---|
| **Languages** | Official packages for Markdown (with highlighted code blocks inside), JSON, YAML, HTML, CSS, JavaScript, XML, Java, Python, SQL and more; many older modes (shell, TOML, properties, …) through `legacy-modes` | 100+ modes in the download | Very many, as in VS Code | Very many modes | Many, but highlighting is drawn over the textarea |
| **Smartphone** | Good: built on the browser's own text editing (touch, keyboards, autocorrect) | Works, weaker on touch | **Not supported** on mobile browsers | Works, weaker | Native textarea: best typing, simplest look |
| **Without a build tool** | Not directly: it ships as many small npm modules that import each other by package name. Fix: bundle once into one file and copy it into `ui/` (a script in the repo, Node only when the editor is updated), or an import map over ~15 copied files | Yes: ready-made single files to copy into `ui/` | No: needs a bundler and web workers | Yes: plain script files | Yes |
| **Strict CSP (no inline styles)** | It injects its styles. Supported by setting a nonce (`EditorView.cspNonce`), which needs a per-response nonce in Mizoine's CSP; check whether its constructed style sheets already pass without | Has a strict-CSP mode (`useStrictCSP`, styles loaded from a file) | Needs exceptions for workers and styles | Styles from a CSS file: fine | Fine |
| **Size (rough)** | ~300–500 kB for the needed languages, before compression | ~400 kB core + modes as needed | Several MB | ~200 kB + modes | ~20–50 kB |
| **Maintenance** | Active, current generation | Active | Active | Maintenance only (superseded by 6) | Active |
| **Drawbacks** | The one-time bundling step; the most code to set up | Older design; less natural on touch | No phone; heavy | Legacy | Not a real editor (no undo per language features, folding, search); overlay quirks |

Further points:

- **The editor uses only the public file API** (requested 2026-11-19): `GET` to load, `PUT` to create or
  save, the same requests as curl and the agents' MCP tools. No editor-specific endpoint. The editor is a static
  file under `/ui/`, and the server does not know about it, so it can be replaced or switched off (BACKLOG
  principle 6).
- **Saving** (step 30):
  - load the file with its `ETag`;
  - save with `PUT` and `If-Match`. If someone else changed the file in the meantime (an agent, vim, `git pull`),
    the save fails with 412 and the editor offers to reload or to keep its text.
  - This is a strong reason to always send `If-Match` from the UI (D13).
- **What is editable:** text files only, up to a size limit (for example 2 MB). Binary files and larger files
  are only downloaded. The type is detected by name, then by content (no NUL bytes, valid UTF-8). The file is
  saved exactly as UTF-8 text, keeping its line endings.
- **Markdown preview** (rendered next to the editor) is a separate step, after T3.2.

**Recommendation: A, CodeMirror 6, bundled once into `ui/vendor/`.** It is the best on the phone and has
current language packages for Markdown, JSON and YAML. The application's own code stays without a build. Only
the vendor bundle is made by a small script, when the editor is updated. Fallback if the bundling step is
unwanted: B (Ace), which needs no build at all and has a strict-CSP mode, but is weaker on touch.

---

### T3.2 — Markdown as a readable page

*Requirement added 2026-11-19:*
- `/repo/folder/document.md` opened in the browser shows the rendered document;
- embedded pictures work and are simple to reference;
- links to other documents are clickable;
- bookmarks work: `/repo/folder/document.md#topic`.

**Same URL, chosen by the `Accept` header** (HTTP content negotiation, as for folders, D6):

| Request | Answer |
|---|---|
| Browser navigation (`Accept: text/html,…`) | The rendered page (`text/html`) |
| `Accept: text/markdown` (editor, agents), `*/*` or none (curl, wget) | The file as it is (`text/markdown`) |
| `…/document.md?raw` | The file as it is, for links in the page ("source", "download") |

Notes:
- Every answer carries `Vary: Accept`, so that caches keep both versions apart.
- Missing `Accept` and `*/*` give the raw file here: for files, curl and downloads expect the bytes. This is the
  opposite of folder pages (D6), where there are no bytes to give.

**Pictures and links need no special syntax.** The rendered page has the document's own URL, so the browser
resolves relative references by itself:

| In `notes/projects/plan.md` | The browser loads |
|---|---|
| `![Diagram](images/diagram.png)` | `/notes/projects/images/diagram.png`: an ordinary file download (step 28), with the session |
| `[Budget](budget.md)` | `/notes/projects/budget.md`: rendered again |
| `[Section](../other.md#costs)` | `/notes/other.md`, then it scrolls to `costs` |
| `[Shared](shared/)` | the folder listing |

- These are the same relative links that GitHub, VS Code's preview and Obsidian (with "relative path" links)
  understand, so the files stay portable (BACKLOG principle 1).
- Names with spaces are written as `<my file.md>` or `my%20file.md` (CommonMark).
- Not covered by the standard, so not planned:
  - links starting with `/` (they point to the application root, not the repository root);
  - Obsidian's `[[wiki links]]`.

**Bookmarks:**
- Every heading gets an `id` derived from its text, as GitHub does: `## Costs and Risks` → `#costs-and-risks`;
  repeated headings get `-1`, `-2`; non-ASCII letters stay (`## Über uns` → `#über-uns`).
- The browser jumps to `#topic` by itself, so links made for GitHub keep working.

**Library:**

| | commonmark-java | flexmark-java | In the browser (markdown-it, marked) |
|---|---|---|---|
| Standard | CommonMark, extensions for GitHub's tables, strikethrough, task lists, autolinks, heading IDs | CommonMark plus many dialects | CommonMark / GFM |
| Size, dependencies | Small, no dependencies | Large, many modules | Needs JavaScript; pages are empty without it |
| Drawbacks | Fewer extensions | Heavy; development slowed down | Sanitizing in the browser; agents and curl get no page; a second rendering place |

**Recommendation: commonmark-java on the server.**
- One place renders and sanitizes, and the page works without JavaScript.
- Links and bookmarks are plain HTML.
- A live preview in the editor can later use a browser library, or the server rendering.

**Safety:** a Markdown file may come from an agent or an import, so it is not trusted.
- HTML written inside the Markdown is shown as text, not run.
- Links with `javascript:` and other unsafe schemes are dropped.
- The page keeps the strict CSP: no scripts from the document, and pictures only from Mizoine itself.
  External pictures (`https://…`) are therefore not shown (D25).
- Large files are not rendered: above a limit (as in D21) the raw file is returned.

## Summary of recommendations

| Decision | Recommended | Next option | Not now |
|---|---|---|---|
| T1 file API | **Decided:** REST on the file URLs: GET / PUT / DELETE, ETag, atomic writes, safe headers | WebDAV subset on the same URLs (`MOVE`, `PROPFIND`, …) for file managers | JSON operations |
| T2 AI | **Decided:** remote MCP in Mizoine (`/mcp`), OAuth with one configured client | Dynamic client registration for more agents | REST + Skill, local MCP program, WebDAV mount |
| T3 UI | **Decided:** Preact + htm or Lit, without build (D26: which one) | Plain JavaScript modules | SPA with a Node build |
| T3.1 editor | **Decided:** CodeMirror 6, bundled once into `ui/vendor/`; a plain textarea until then | Ace (no build at all) | Monaco (no phone) |
| T3.2 Markdown page | Rendered on the server with commonmark-java, on the file's own URL by `Accept`; relative pictures and links; GitHub-style heading IDs | Live preview in the editor (browser) | Rendering only in the browser |
