# Personal Knowledge Base + File Storage (Backlog)

**Project status** (updated: 2026-11-15)

This document captures the current understanding of goals, principles and direction of development.
Come back to it to quickly restore context.

---

## 1. Goal

Create a tool for:

1. A **personal knowledge base** built on plain Markdown files
2. A **personal file storage** (attachments, documents, images, etc.)

The foundation is the existing project [mizoine](https://github.com/truedrcb/mizoine) (Personal Lightweight Issue Tracker on Java/Spring Boot + Git).

---

## 2. Immutable principles

These requirements are mandatory and must not be violated:

1. **The source of truth is plain files only**
   No specialized DB as the primary storage. Everything must be readable and editable without the tool (vim, Obsidian, VS Code, Finder, etc.).

2. **Git integration**
   Versioning and synchronization via Git. Changes made manually or by other tools must be picked up correctly.

3. **Web application**
   Remote work through a browser. The application must be easy to run on any computer (like the current mizoine jar).

4. **Changes without the tool**
   Files can be edited, folders added/deleted/renamed entirely bypassing the tool. On the next start everything must display correctly.

5. **Flexible folder structure**
   No rigid schema like the current Project → Issue. The structure must be able to evolve during use.

6. **Minimum of tools + maximum independence**
   Use as few tools as possible. Each piece is simple, understandable, easy to update and as independent from the others as possible.
   Any layer can be disabled or replaced and the system must not break.

---

## 3. Architectural principle: thin independent layers

```
Files + Git                          ← the only source of truth
    │
    ├── [Access layer]   Simple file / Git management (Forgejo, File Browser, own minimal server — see section 7)
    │
    ├── [View layer]     Convenient display of notes (mizoine)
    │
    ├── [Import layer]   Automation (separate scripts / functions)
    │
    └── [Extras]         Search, conversions, etc. — as needed
```

Rules:
- No layer is mandatory.
- Improvements = adding simple independent pieces, not growing a monolith.
- Automation must be "transparent": the same thing can be done by hand.
- Display is only a view, not storage.

### Every object has an address (URL)

- **All documents can be referenced by URL.** Every document, file and folder, and every view of one (its page,
  the editor, the Git page of a repository), has its own URL. Nothing is reachable only by clicking through the
  application.
- **A URL can be saved anywhere and opened directly.** A link kept elsewhere (another note, an email, a
  bookmark, a chat) opens the object itself in the browser, without navigating to it first. Without a login, the
  login comes first and then leads straight to that object.
- **URLs are stable, like permalinks.** The URL follows the file's place in the repository, so it stays the same
  as long as the file is not moved or renamed. It does not depend on the session, the order of clicks, or state
  kept in the browser or on the server. Moving a file changes its URL, also when done without the tool (principle
  4); stable names (see automatic naming in section 4) keep links from breaking.
- **Several documents open at the same time, in parallel browser windows or tabs.** Each window works on its own
  object, independently of the others: nothing in one window changes or depends on what another shows. Two
  windows (or two people) changing the same file do not overwrite each other unknowingly: a save made after
  someone else's is refused, and the newer version is shown.

---

## 4. Important functional requirements

- Automated **import** of files of various types with text extraction:
  - Email → text (simplified Markdown) + all attachments + text from attached documents
  - PDF, DOCX, images (OCR when necessary) and other formats
  - **HTML → Markdown** (see the current first step)
- Automatic **naming** of folders/files (short ID based on date and time, as in the current mizoine version). Easy to sort and quick to type by hand.
- Convenient **display**: not just a single `.md` file, but also related markdown files (comments, additional notes, etc.) — as mizoine already partially does.
- **Restricted space for AI** *(new requirement, 2026-11-15)*:
  - AI can be allowed to work in a restricted space (one or several folders) without exposing the rest.
  - In this space the AI can maintain and save a **knowledge base** — plain Markdown files, just like everything else.
  - This base is covered by the **general ability to save everything to Git**: the AI space is part of the shared storage, not separate from it; commit and push are done by a human using the same mechanism as for other files.
  - A possible implementation is section 7 (HTTPS access is not mandatory for this).
- Good search.
- Ability to add capabilities gradually.

### Display concept: a repository like a ticket system *(idea, 2026-11-27)*

Think of a repository as a support or project-planning ticket system (for example Jira), made of plain folders:

- **A folder can be anything, depending on where it is and what it holds:**
  - a **topic** (a project, a backlog), whose subfolders are its issues or backlog items;
  - an **issue**, whose subfolders are its comments and attachments;
  - a single **comment**, **attachment** or **email**.
- **Rendering a folder** shows its **description** (its `README.md`), then, depending on the structure of its
  subfolders, its **comments** (each subfolder's `README.md`) or its **attachments** (e.g. pictures).
- **Flexibility first:** no fixed schema, no limits and no hard-coded number of levels. The same folder can be
  an issue in one place and a comment elsewhere; what it is follows from its place and content, not from a type
  that must be declared up front.

This keeps the principles: the files stay plain (a `README.md` per folder, subfolders, attachments as files), Git
versions all of it, and the view is only a view (principle "display is only a view, not storage").

#### Layouts *(decided 2026-11-27)*

- **A default layout** for any folder, used when nothing else is specified.
- **Special layouts**, chosen only by the **folder's own `layout:…` tag** (in its `.tags`; not by its subfolders'
  tags or anything else): e.g. `layout:issue`, `layout:topic`. A `key:value` tag, so a folder has at most one layout
  (one value per key, D67). Other tags and statuses (`status:open`) may change details within a layout.
- **Layouts are defined in Mizoine's code**: a few fixed ones; they need not be customizable. An unknown
  `layout:…` value shows the default layout.
- **The repository's root** has its own layout (below). **The list of repositories** (the start page `/`) stays
  simple: no layout, no tree. Layouts start at a repository's root.
- **Order: by name**, for files and subfolders alike, in every layout. Where the date matters, it is in the name in
  a sortable form (e.g. `2026-11-27 …` or the date-and-time names of "automatic naming"), so the order is plain to
  see and the same in any file manager; no hidden date order.
- **Folders and files are managed on the pages**, where the user may write, with the same API as everywhere:
  - folders: create (New folder), delete (only empty ones, D15; a folder with only `.tags` counts as empty, D70),
    rename and, rarely, move (one plain way is enough, step 36c/36d);
  - files: delete (after asking, as the folder page does since step 73) and rename, in the previews (e.g. in a
    document preview's title line, next to its refresh button) and in the root's tree.
- **Moving out of an inbox** (sorting new items into their place) is the frequent case and a special case, to be
  planned separately.

Default layout:

```
title / menu
------------------------------------------------------------
main description: README.md rendered to HTML
------------------------------------------------------------
previews of the other Markdown files (.md) in the folder (by name)
------------------------------------------------------------
previews of pictures, documents and subfolders
```

- The Markdown part has only **Markdown files** (`.md`) besides `README.md`, each as a **preview, not a bare link**:
  the file name as its title, its first **3–5 lines rendered** (as the Markdown page renders them), linking to the
  file.
- **Other layouts may render more** on the same page: **all Markdown files** of the folder, one after the other
  (e.g. a topic made of several notes); and also the **`README.md` of each subfolder**, e.g. the comments of a ticket
  (`layout:issue`), each with a link to its subfolder.

Root layout:

```
header / menu
------------------------------------------------------------
README (README.md of the root, rendered)
------------------------------------------------------------
expandable tree of folders and files, with create, move, delete and rename
```

- The tree loads level by level when a folder is expanded (the folder listing's JSON, as for any client), so a
  large repository stays fast. What is expanded is no view of its own: no URL, not shared between windows.
- Where the user may not write (above a grant, D4), the tree shows the way down without actions.

#### Previews *(decided 2026-11-27)*

- **A document** (PDF; office documents such as `.doc`, `.ppt` later) is previewed as thumbnails of **all its
  pages in a line**, wrapping at the page width, so the whole document is seen at a glance (proven in the first
  Mizoine version):

  ```
  ------------------------------------------------------------
  title: file name, date, button to refresh the thumbnails, …
  ------------------------------------------------------------
  [page 1] [page 2] [page 3] [page 4] [page 5] …   (wrapping at the page width)
  ------------------------------------------------------------
  ```

- **A subfolder with several pictures** is previewed the same way, as the pages of one document: its name as
  title, its pictures as the pages, in the order of their names (e.g. the scanned pages of a letter).
- **A picture** is previewed by its thumbnail; **a subfolder** otherwise by its title (first heading of its
  `README.md`), first lines and tags, so that a folder of comments reads like a thread.
- **Shape:** each thumbnail keeps the **width-to-height ratio of its own page**, the whole page shown, never
  stretched; pages of one document may differ (portrait and landscape). **All thumbnails have the same height**,
  the width follows each page's ratio. *Very low priority:* for an extreme page (very tall, e.g. a long receipt) the
  thumbnail may show only a part of it, so that it does not shrink to a thin strip.

#### Thumbnails *(decided 2026-11-27)*

- **Pre-generated on the server and cached on disk**, for documents and pictures alike, outside the repository (a
  cache, not storage), in a folder of its own set like the search index (`mizoine.search.index-dir`); never drawn
  while a page is shown.
- **Generating is a separate action that starts a background process**, as reindexing on request: nothing at
  startup or on a timer. A preview without thumbnails yet says so, with a button to generate them; the folder page
  has a control to **update all previews in the folder**: its own and those of the subfolders shown on its page,
  nothing deeper.
- **Changed files are not tracked exactly:** when in doubt, the user clicks "regenerate".
- **No MCP tools** (agents read the files themselves). **The HTTP API has the thumbnail operations** (generate,
  the images, the status of a generation), in the API description as every endpoint (AGENTS.md); the UI uses only
  these.
- **Progress** is shown from the status request (how detailed: nice to have). Nice to have too: **cleanup** of
  thumbnails of files that are gone, as an action of the repository's UI.

API *(proposal, to decide)*:

- **One kind of thumbnail:** the image of **one page of one file**: a document has one per page, a picture one
  (page 1). A subfolder of pictures has none of its own: its preview is a list of its pictures' thumbnails. So
  everything a preview shows is a list of pages.
- Addresses below the repository, technical names as `.search` and `.tags` (each a separate operation in the API
  description):

  | Request | What |
  |---|---|
  | `GET /{repo}/.thumbnails/{file}` | the file's pages: `{"state": "missing" \| "ready" \| "partial", "pages": [{"page": 1, "image": "/{repo}/.thumbnail/1/{file}", "width": …, "height": …}, …]}` |
  | `GET /{repo}/.thumbnails/{folder}/` | the previews of that folder's page, one per entry shown there (files, and subfolders shown as documents), by name: `{"entries": {"letter.pdf": {…}, "scans/": {"pages": [… "/{repo}/.thumbnail/1/scans/001.jpg" …]}}}` |
  | `GET /{repo}/.thumbnail/{page}/{file}` | the image itself; 404 when not generated |
  | `POST /{repo}/.thumbnails/{file or folder/}` | start generating in the background: a file's pages, or what the folder's page shows (its own previews and the subfolders shown there); 202 with the job's address |
  | `GET /{repo}/.thumbnail-jobs/{id}` | the job's status: `{"state": "queued" \| "running" \| "done" \| "failed", "done": 3, "total": 12, "failed": ["broken.pdf"]}` (an address of its own, so that no folder named `jobs` is mistaken for it) |

- The page number comes before the path in the image's address, so any file name works.
- **Access as for the file:** whoever may read the file sees its thumbnails; anything else is 404, as for the file
  (D2), checked on every request.
- One request gives a folder page all its previews, then the images.

#### Still open (nothing decided)

- How a folder's role is recognized: by its content alone (a `README.md`; only pictures → a picture document; an
  `.eml` → an email), by a name convention, or by an explicit hint where wanted (e.g. a tag `kind:comment`), with
  content as the default. In particular: when a subfolder counts as a picture document (only pictures in it, or
  pictures besides a `README.md`).
- Which file is the description: `README.md` only, or also `index.md` or the folder's only Markdown file; what a
  folder without one shows (its name as title, the rest as above).
- Where the folder's other files appear (other text files such as `.txt` or `.java`, archives): with the previews,
  presumably, as a name with size and kind.
- How deep one page renders (direct subfolders only, deeper levels as links, or expandable), and how a rendered part
  shows its own file (a heading with the name, an Edit link).
- How to add a comment or an attachment from the page: a new subfolder with a `README.md` (automatically named), or
  a file; the same through the API and MCP, so agents can comment too.
- Moving in the root's tree: dragging, or the Rename-with-a-path of step 36d (on the phone dragging is awkward).
- Whether the root may have a `layout:…` tag of its own, or always shows the root layout.
- Thumbnails: who may start a generation (read access, as it only fills a cache; or write access, as it is
  costly); one job at a time per repository, queued; the image height; JPEG (built into Java) or WebP; the library
  for PDF (e.g. PDFBox) and a converter for office documents; how the cache is laid out (by real path: a moved
  file needs regenerating, which "regenerate when in doubt" covers).
- How all this relates to search and to the listing of step 36e.

---

## 5. Studied knowledge-organization methods

### PARA (Tiago Forte)
- **Projects** — active efforts with a deadline
- **Areas** — ongoing areas of responsibility
- **Resources** — reference material and interests
- **Archives** — inactive items

Organizes by **actionability/relevance**, not by topic.
Works well as an optional starter skeleton.

### Zettelkasten (Niklas Luhmann)
- Atomic notes (one idea = one note)
- Your own words (don't copy)
- Links matter more than folders
- Types: Fleeting → Literature → Permanent notes

### Feynman method
- Explain the topic in simple words
- Find gaps in understanding
- Fill the gaps and simplify again

**Conclusion**: the tool must not rigidly impose a single method. Better to support flexibility + convenient templates/help.

---

## 6. Current experiment: Forgejo + hybrid

**Forgejo** is being tried as the access layer for files and Git.

**Hybrid scheme:**
- Forgejo (or the simpler File Browser) — file management, Git, basic web access
- mizoine — in parallel: convenient display, search, import, automation

Both work with the same folder/repository.

---

## 7. New idea: minimal file server with access for AI

*Added: 2026-11-15. Status: idea, not a decision. One way to fulfil the "Restricted space for AI" requirement from section 4.*

**Essence:**
Build a minimal server with **HTTPS** access that works directly with files on disk (the working copy of a Git repository) and implements the project task "Agents-to-git Adapter": *agents edit files, the human commits and pushes*.

**HTTPS access is not mandatory** *(clarification, 2026-11-15)*:
The requirement from section 4 can be met without it. HTTPS access is an optional layer that provides two things:
- the knowledge base becomes **more accessible** (from any device, without intermediate tools);
- external AI can be connected **more safely**, because access and permissions are fully under your own control.

**Requirements (if the server is built):**
- Access only for **registered users** (no anonymous access).
- Two roles:
  - **Regular user** — sees everything and can invoke **basic Git functions** (status, diff, commit, push, pull).
  - **AI agent** — access limited to **one or several folders**; can read and edit files, **no access to Git**. The agent's folders live inside the shared repository, so its knowledge base ends up in Git along with everything else.
- Works directly with files on disk: no own storage, no DB as the source of truth (principle 1).
- The server is a thin layer: it can be switched off and the files with Git remain fully functional (principles 4 and 6).

**Place in the architecture:**
This is the third candidate for the **access layer** (alongside Forgejo and File Browser). It can also replace a temporary scheme such as "cloud-synced folder → sync to server → commit".

**What it gives:**
- The AI edits the same files that are in the repository, without intermediate synchronization via a cloud-synced folder.
- The human sees the agent's changes as a regular `git diff` and decides what to commit.
- Commit and push can be done from a smartphone with one button.

**Risks and limitations:**
- An own server with authentication is code that has to be maintained and secured; it contradicts "minimum of tools" if it is added on top of another tool rather than replacing it.
- The folder restriction for the AI must be enforced reliably on the server (path normalization, forbid `..` and symlinks pointing outside, `.git` folder inaccessible to the agent).
- A protocol the AI can actually use is needed (see open questions).

---

## 8. Current first step (in progress)

### HTML → Markdown conversion on the frontend (JS)

**Idea:**
The HTML-to-Markdown conversion function already exists in the Java backend of mizoine.
It is more convenient to implement it on the **frontend (JavaScript)**.

**Why this is a good first step:**
- A completely **independent** function — can be used anywhere (in mizoine, in a browser extension, in a standalone script, in another tool)
- Does not depend on the backend or the rest of the architecture
- Easy to test and evolve
- Only needs a convenient way to paste the result into the Markdown editor

**Implementation plan:**
1. Port / rewrite the conversion logic in JS
2. Use the same tests that already exist for the Java version (for compatibility and quality verification)
3. Allow for further development (support for more complex HTML, tables, lists, junk cleanup, etc.)
4. Make a convenient UX: paste HTML → get clean Markdown → easily insert into the editor

**Advantages of the approach:**
- Maximum independence
- Can be developed separately from the main application
- Matches the "thin independent layers" principle

---

## 9. Phased plan

1. **Now (first step)**
   Implement HTML → Markdown conversion in JS (independent function + convenient pasting into the MD editor).

2. **Near term**
   - Set up/verify the hybrid: Forgejo (or File Browser) + mizoine on one repository
   - Improve the display of related markdown files in mizoine
   - Evaluate the minimal file server with AI access idea (section 7): prototype or off-the-shelf solution

3. **Later**
   Add import automation (email, PDF, DOCX, etc.) — preferably also as independent functions/scripts.

4. **Gradually**
   Strengthen the needed aspects while keeping simplicity, layer independence and the "files come first" principle.

---

## 10. Open questions

- Final choice of the "standard" access tool (Forgejo vs File Browser vs own minimal server)
- Minimal server: write our own or assemble from existing parts (nginx + WebDAV, File Browser with users and visibility scopes)?
- Minimal server: which protocol does the AI use for access (MCP server, WebDAV, plain REST)? This determines whether the AI can use it from a smartphone, from a browser and from Claude Code
- Minimal server: a separate tool or part of mizoine (then the access layer and the view layer merge)?
- What is the higher priority as a first step: HTML→MD or the minimal server?
- Forgejo stores repositories in bare form, not as a working folder — how exactly do it and mizoine work with "one folder"? (see section 6)
- Convenient UX for pasting the HTML→MD result into the editor
- Target (but flexible) folder structure and naming rules
- Priorities of the next independent functions after HTML→MD

---

## 11. Useful links

- mizoine: https://github.com/truedrcb/mizoine
- Forgejo: https://forgejo.org

---

*This file can be updated as the project evolves.*
