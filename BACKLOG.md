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
