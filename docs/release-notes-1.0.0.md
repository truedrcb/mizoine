# Mizoine 1.0.0

Mizoine is a personal knowledge base on plain files and Git. It is a small web server that lets the people you
list, and their AI agents, see and change the folders you give them, nothing else. Your files stay plain Markdown
and other files in ordinary folders. Edit them with any tool, and keep their history with Git.

1.0.0 is a new start. It shares the name with the earlier Mizoine issue tracker
([old version](https://github.com/truedrcb/mizoine/tree/master)) but none of its code or data format. There is
nothing to migrate: point Mizoine at your folders and it shows them as they are.

## What you can do

- **Browse your repositories** in the browser: the start page lists the repositories you may access, each
  folder lists its subfolders and files.
- **Read Markdown as a page** on the file's own URL. It shows pictures and relative links, GitHub-style tables,
  task lists and heading anchors.
- **Edit text files in the browser.** Syntax highlighting covers Markdown, JSON, YAML, HTML, CSS, JavaScript, XML,
  Java, properties and shell. Saving never overwrites someone else's change: the editor says so and keeps your
  text.
- **Use Git from the browser:** status, stage, unstage, commit, pull (fast-forward only) and push (never forced).
  Commits carry your name as author.
- **Work with files over plain HTTP**, for scripts and `curl`: download, upload, create and delete folders,
  move and rename. Every download has an `ETag`, so a write can be made to happen only if the file is unchanged.
- **Connect AI agents over MCP:** Claude Code with a password, and Claude on the web and in the phone app
  through Mizoine's own OAuth login. An agent reads and writes files only in its own folders and never uses
  Git. You review its changes with `git diff` and commit them yourself.

## Access and security

- **Only listed users log in.** Users are kept in a `users.yaml` file with BCrypt password hashes. The built-in
  `hash-password` command makes a hash; plain-text passwords are refused.
- **Folder grants:** a small YAML file per repository says which user may access which folders.
  - Anything outside a user's grants is answered with "not found", the same as something that does not exist.
  - Symbolic links count where they lead, so a link cannot open a folder that is not granted.
- **Git access is granted separately** (`.git` in the user's list, or `/`). It covers the whole repository's
  changes, so give it only to people who may see them. The real `.git` folder is never shown, read or written,
  for anybody.
- **Brute-force protection:** failed logins are limited per client address, 5 within 5 minutes by default.
- **Protection against cross-site requests:** the browser needs a CSRF token for its writes.
- **Downloads cannot run in your browser:** files are sent in a sandbox, and HTML or SVG files are downloaded
  instead of shown.
- **Writes are atomic:** readers and Git see the old or the new file, never a part of it.
- **Listens only on 127.0.0.1 by default.** Put nginx (or another proxy) in front for HTTPS; an example
  configuration is included.

## Running it

- **Needs Java 25.** Download `mizoine-1.0.0.jar` from the release, or build it with Maven 3.9+ (building also
  needs Node.js and Git).
- **Setup:** see "Run" in the [README](../README.md) for users and repositories (the starting point is
  `users.example.yaml` and `repositories.example/`), and the start script `docs/mizoine.sh`.
- **Set `LANG=C.UTF-8`** where Mizoine runs. Otherwise file names with non-ASCII characters cannot be opened.
- **Changes to `users.yaml` and the repository files take effect without a restart.**
- **For Git commits from the browser:** the server user needs its own Git identity (`git config --global
  user.name` and `user.email`). Pull and push use the server user's SSH key or credential helper. Mizoine stores
  no Git passwords.
- **For Claude on the web and phone:** set `mizoine.public-url` and one OAuth client per chat app, as described
  in the README section "Connect an AI agent (MCP)".

## Known limitations

- **The browser cannot yet upload, delete or create folders.** Use `curl` or an agent for now; a file browser
  with these actions is planned.
- **Git in the browser covers the everyday steps only.** Merges, conflicts, branches and a branch that has
  diverged are handled by hand on the server.
- **OAuth logins of chat apps are kept in memory.** After a restart of Mizoine, press Connect again in the chat
  app.
- **Links are not updated after a move.** Moving or renaming a file does not change links to it in other files.
- **Markdown pages leave some content out:** HTML inside Markdown is shown as text, and pictures from other sites
  are not shown.

## API

The pages, the file API and the Git actions are described in [openapi.yaml](openapi.yaml). The MCP tools and
the OAuth login for chat apps are described in the README.
