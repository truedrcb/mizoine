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

### Display concept: a repository like a ticket system *(idea, 2026-11-27)*

Think of a repository as a support or project-planning ticket system (for example Jira), made of plain folders:

- **A folder can be anything, depending on where it is and what it holds:**
  - a **topic** (a project, a backlog), whose subfolders are its issues or backlog items;
  - an **issue**, whose subfolders are its comments and attachments;
  - a single **comment**, **attachment** or **email**.
- **Rendering a folder** shows:
  - its **description**: its `README.md`;
  - then, depending on the structure of its subfolders, its **comments** (each subfolder's `README.md`), or its
    **attachments** (e.g. pictures) where a subfolder is recognized as such.
- **Flexibility first:** no fixed schema, no limits and no hard-coded number of levels. The same folder can be
  an issue in one place and a comment elsewhere; what it is follows from its place and content, not from a type
  that must be declared up front.

This keeps the principles: the files stay plain (a `README.md` per folder, subfolders, attachments as files), Git
versions all of it, and the view is only a view (principle "display is only a view, not storage").

#### Folder page layout *(idea, 2026-11-27)*

- **A default layout** for any folder, used when nothing else is specified.
- **Special layouts**, chosen by the **folder's own tags or status** (its `.tags`), not by those of its subfolders
  or anything else: e.g. an issue with `status:open` may be shown differently from a topic.
- **The layout tag is `layout:…`** *(decided 2026-11-27)*: e.g. `layout:issue`, `layout:topic`. A `key:value` tag,
  so a folder has at most one layout (one value per key, D67); without it the default layout, except the root's
  own one. Other tags and statuses (`status:open`) may still change details within a layout.

Default layout (draft):

```
title / menu
------------------------------------------------------------
main description: README.md rendered to HTML
------------------------------------------------------------
previews of the other Markdown files (.md) in the folder: their first lines (sorted by file name)
------------------------------------------------------------
previews of images, documents and subfolders
```

**The list of repositories** (the start page `/`) stays simple: no layout, no tree *(decided 2026-11-27)*. Layouts
start at a repository's root.

**What a layout may render on the folder page** *(idea, 2026-11-27)*: the default layout renders only `README.md` and
shows previews (first lines) of the other Markdown files. Other layouts may render more on the same page:
- **all Markdown files** of the folder, one after the other (e.g. a topic made of several notes);
- also the **`README.md` of each subfolder**, e.g. the comments of a ticket (`layout:issue`), each with a link to
  its subfolder.

- **Order: by name** *(decided 2026-11-27)*, for files and subfolders alike. Where the date matters, it is in the
  name in a sortable form (e.g. `2026-11-27 …` or the date-and-time names of "automatic naming"), so the order is
  plain to see and the same in any file manager; no hidden date order.

Open (nothing decided): how deep (only direct subfolders, or deeper too), and how a rendered part shows its own
file (heading with the name, Edit link).

Special layout for the **repository's root folder** (draft):

```
header / menu
------------------------------------------------------------
README (README.md of the root, rendered)
------------------------------------------------------------
expandable tree of folders and files, with move, delete and rename
```

Open questions (to be planned, nothing decided):
- The tree: loaded level by level when a folder is expanded (the folder listing's JSON, as for any client), so a
  large repository stays fast; what is expanded is no view of its own, so it needs no URL and is not shared
  between windows (BACKLOG "every object has an address"; at most kept in the page's address after `#`).
- Moving in the tree: dragging an entry onto a folder, or the Rename-with-a-path of step 36d, or both (on the phone
  dragging is awkward); never overwriting (D30); delete only empty folders (D15, `.tags` rule D70).
  **Moving folders is rare** *(2026-11-27)*: it need not be quick or prominent; one plain way is enough.
- **Moving out of an inbox** (sorting new items into their place) is the frequent case and a **special case, to be
  planned separately** *(2026-11-27)*.
- Where the user may not write (above a grant, D4), the tree shows the way down without actions.
- **Layouts are defined in Mizoine's code** *(decided 2026-11-27)*: a few fixed ones, chosen by `layout:…`; they
  need not be customizable. An unknown `layout:…` value shows the default layout.
- **Preview of a document** *(decided 2026-11-27, proven in the first Mizoine version)*: medium-sized thumbnails of
  **all its pages in a line**, wrapping at the page width, so the whole document is seen at a glance. Open: who
  draws them (the server, e.g. PDFBox for PDF; office documents through a converter); very long documents.
- **Thumbnails are pre-generated and cached on disk** *(decided 2026-11-27)*, outside the repository (a cache, as
  the search index, not storage), never drawn while a page is shown:
  - generating them is a **separate action that starts a background process**, as reindexing on request (no
    automatic generation at startup or on a timer);
  - a document **without thumbnails yet** shows a message saying so and a button to generate (or regenerate) its
    preview;
  - the folder page has a control to **update all preview thumbnails in the folder**.

  Decided *(2026-11-27)*:
  - **Cache location:** a folder of its own, set like the search index (`mizoine.search.index-dir`).
  - **Changed files are not tracked exactly:** when in doubt, the user clicks "regenerate"; no check that a
    thumbnail still matches its file.
  - **The folder control** updates the folder's own previews and those of subfolders **only where they are shown
    on that folder's page** (e.g. a subfolder of pictures shown as one document); nothing deeper.
  - **Pictures get cached thumbnails too**, made the same way.
  - **No MCP tools** for thumbnails (agents read the files themselves). **The HTTP API includes the thumbnail
    operations** *(decided 2026-11-27)*: generate for a file or a folder (the background process), get a thumbnail
    image, and a **status request** for a generation (running, done, failed; how far along), described in the API
    description as every endpoint (AGENTS.md). The UI uses only these, as it uses the file API.
  - **Several thumbnails per document or subfolder, in the API** *(proposal 2026-11-27, to decide)*:
    - **One kind of thumbnail:** the image of **one page of one file**. A PDF or office document has one per page,
      a picture one (page 1). A **subfolder of pictures** has none of its own: its preview is a document whose pages
      are its pictures' thumbnails, in name order. So everything a preview shows is a list of pages.
    - Addresses below the repository, as `.search` and `.tags` (technical names, never a repository path; each a
      separate operation in the API description):

      | Request | What |
      |---|---|
      | `GET /{repo}/.thumbnails/{file}` | the file's pages: `{"state": "missing" \| "ready" \| "partial", "pages": [{"page": 1, "image": "/{repo}/.thumbnail/1/{file}", "width": …, "height": …}, …]}` |
      | `GET /{repo}/.thumbnails/{folder}/` | the previews of that folder's page, one per entry shown there (files, and subfolders shown as documents), each as above, by name: `{"entries": {"letter.pdf": {…}, "scans/": {"pages": [… "/{repo}/.thumbnail/1/scans/001.jpg" …]}}}` |
      | `GET /{repo}/.thumbnail/{page}/{file}` | the image itself (JPEG); 404 when not generated |
      | `POST /{repo}/.thumbnails/{file or folder/}` | start generating (the background process): a file's pages, or what the folder's page shows (D: only shown subfolders); 202 with the job's address |
      | `GET /{repo}/.thumbnails/jobs/{id}` | the job's status: `{"state": "queued" \| "running" \| "done" \| "failed", "done": 3, "total": 12, "failed": ["broken.pdf"]}` |

    - The page number comes before the path in the image's address, so any file name works (`{file}` is the rest
      of the path).
    - **Access as for the file:** whoever may read the file sees its thumbnails; anything else is 404, the same as
      for the file (D2), checked on every request, so a removed grant hides them at once.
    - The folder answer lets a folder page load all its previews with one request, then the images.
    - Open (to decide): who may start a generation (read access, as it only fills a cache; or write access, as it is
      costly); one job at a time per repository, queued; the image size (one medium size, a size parameter later);
      JPEG (built into Java) or WebP; how the cache is laid out (by real path, so a moved file needs regenerating,
      which "regenerate when in doubt" covers).
  - **Progress** is shown from the status request; how detailed (a count of done files, or only running/done) is
    nice to have. Nice to have too: **cleanup** of thumbnails of files that are gone, as an action of the
    repository's UI.
- **How a document's preview looks** *(sample, 2026-11-27)*:

  ```
  ------------------------------------------------------------
  title: file name, date, button to refresh the thumbnails, …
  ------------------------------------------------------------
  [page 1] [page 2] [page 3] [page 4] [page 5] …   (wrapping at the page width)
  ------------------------------------------------------------
  ```

  **Important** *(2026-11-27)*: each thumbnail keeps the **width-to-height ratio of its own page**, the whole page
  shown, never stretched; pages of one document may differ (portrait and landscape, mixed sizes), and each
  thumbnail follows its page. **All thumbnails have the same height** *(decided 2026-11-27, for the better look)*;
  the width follows each page's ratio. The API gives each page's own `width` and `height`.
  - *Very low priority:* in extreme cases (a very tall page, e.g. a long receipt or a web page printed as one page)
    the thumbnail may show only a part of the page, so that it does not shrink to a thin strip.

  The same for a subfolder of pictures (its name as title, its pictures as the pages) and for other document types
  (office documents such as `.doc`, `.ppt`: to be planned later).
- **A subfolder with several pictures** *(decided 2026-11-27)* is previewed the same way, as the pages of one
  document: their thumbnails in a line, wrapping at the page width, in the order of their names (e.g. the scanned
  pages of a letter, or the photos of a receipt). Open: when a subfolder counts as such (only pictures in it; or
  pictures besides a `README.md`).
- What a preview is otherwise: a picture as a thumbnail; a subfolder as its title (first heading of its
  `README.md`), first lines and tags, so that a folder of comments reads like a thread.
- Only **Markdown files** (`.md`) are in that part, besides `README.md` *(decided 2026-11-27)*, each shown as a
  **preview instead of a bare link** *(decided 2026-11-27)*: its first few lines, linking to the file: **3–5 lines,
  rendered** (as the Markdown page renders them), with the **file name as its title** *(decided 2026-11-27)*. Open:
  where the other files of the folder appear (other text files such as `.txt` or `.java`, documents): with the
  previews, presumably.
- Pictures: how large previews get, and whether they are made by the browser (the files as they are) or by the
  server (small copies, which would be a cache, not storage).
- What the folder page shows when there is no `README.md`: the folder's name as title, the rest as above.
- How a folder without JavaScript looks (all of it server-rendered, as the pages now).

Open questions about the concept as a whole (to be planned, nothing decided):
- How a folder's role is recognized: by its content alone (a `README.md`; only pictures or files → attachments; an
  `.eml` → an email), by a name convention, or by an explicit hint where wanted (e.g. a tag such as `kind:comment`
  in `.tags`, D67), with content as the default.
- Which file is the description: `README.md` only, or also `index.md` or the folder's only Markdown file.
- ~~The order of comments~~: decided, by name (see "What a layout may render").
- How deep one page renders: the folder and its direct subfolders (comments, attachments), deeper levels as links,
  or expandable.
- How to add a comment or an attachment from the page: a new subfolder with a `README.md` (automatically named),
  or a file; the same through the API and MCP, so agents can comment too.
- How it relates to search, tags and statuses (an issue's `status:open` as its folder's tag, step 61) and to the
  listing (step 36e).
- **Restricted space for AI** *(new requirement, 2026-11-15)*:
  - AI can be allowed to work in a restricted space (one or several folders) without exposing the rest.
  - In this space the AI can maintain and save a **knowledge base** — plain Markdown files, just like everything else.
  - This base is covered by the **general ability to save everything to Git**: the AI space is part of the shared storage, not separate from it; commit and push are done by a human using the same mechanism as for other files.
  - A possible implementation is section 7 (HTTPS access is not mandatory for this).
- Good search.
- Ability to add capabilities gradually.

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
