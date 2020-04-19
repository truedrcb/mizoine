# Mizoine User Stories

## Edit everything

- Extract text from attachments, allow to edit it
- Allow editing any detail: Automatically gathered as well
- Crop/rotate pictures
- Rename/move files and folders

## Import email

- Import all parts: text and html
- Import attachments: extract text from attachments

## Save all details

- Save all mail or attachment attributes in text
	- Recipients, subject, dates, anything
	- EXIF data: Location, dates, etc.
- Automatically save creation dates

## Combine everything

- Multiple images/files per attachment
- Description for any entity
- Move comments/attachments between issues
- Interlink issues/attachments/comments

## Access/link everyting

- Permalink for any project/issue/comment/attachment
- Links independent of domain
- Links short and readable
- Links as stable as possible

## Organise

- Tags, statuses for issues and *ments
- Colours and icons for tags
- Colours and icons for projects
- Hide issues by status
- Hide *ments by status
- Collapse/expand *ments initially by status
- Sort *ments by creation date
	- Creation date can be explicitly converted to short id
	- Always sort by short id (is also simple sortable as text)
	- *ment folder name sample: `00123 my free text` = short id + any text
	- Allow to rename folder according to creation date (which must be editable)

## Search

- By text (full words)
	- Including description, title, author
- By file name
- By tag/status
- Sort by date/relevance

## Backup/synchronize

- Basic git features directly from UI
- Show current updates (changed files, staging)
- Show history
- Warn about remote updates
