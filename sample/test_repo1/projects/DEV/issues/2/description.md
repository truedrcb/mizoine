Simple text styles
===========

* *Emphasis*
* **Strong emphasis**
* ~~Strikethrough~~
* <u>Underline</u>
* <kbd>Keyboard</kbd>
* `Code snippet`

## Horizontal ruler
---

## Links
- Internal link: [DEV-1](issue)
- [Internal link to DEV-1 with text](issue-DEV-1)

# Code highlighting
```java
    /**
     * Read current git status and update branches list
     * @return Status
     */
	public Status getGitStatus() {
		final File gitDir = getGitDir();

		if (gitDir != null) {
			try {
				try(final Git git = new Git(new FileRepository(gitDir))) {
					final Status status = git.status().call();
					tagMap.clear();
					// TODO: Refactor this hidden functionality
					for(final Ref ref : git.branchList().setListMode(ListMode.ALL).call()) {
						addTagRef(ref);
					}
					for(final Ref ref : git.tagList().call()) {
						addTagRef(ref);
					}
					addTagRef(git.getRepository().exactRef(Constants.HEAD));
					return status;
				} catch (NoWorkTreeException | GitAPIException e) {
					LOGGER.error("Git status reading problem: " + gitDir, e);
				}
			} catch (IOException e) {
				LOGGER.error("Git status IO problem: " + gitDir, e);
			}
		}

		return null;
	}

```
