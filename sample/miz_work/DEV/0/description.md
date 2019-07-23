# Class `Repository`

- Represents one Mizoine repository in file system and its all basic actions
- Repository levels directly from root: level 1 - **projects**, level 2 - **issues**, level 3 - **ments** (comments and attachments)
- 

## File Repository

- Implementation of `Repository` based on file system
- Uses *NIO2* API to access files and directories. See class `java.nio.file.Path`. See also https://gquintana.github.io/2017/09/02/Java-File-vs-Path.html

