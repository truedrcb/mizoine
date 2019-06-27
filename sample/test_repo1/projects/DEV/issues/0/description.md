![Logo][RsAuG1]

# Features
* Simple 2-level structure: project/issue
* Completely file based (generic file editor in browser as well)
* Git-based versioning, back up, distributed repositories
* Markdown syntax (https://daringfireball.net/projects/markdown/syntax)
  * See also images/links syntax https://meta.stackexchange.com/questions/2133/whats-the-recommended-syntax-for-an-image-with-a-link
* JSON metadata

# Directory structure
## repository root
All files open as static resources for logged in user
* **`projects`**
    * **project_a** (Project Id)
        * ...
    * **project_b**
      * `description.md`
      * `meta.json`
      * **`tags`**
        * my-tag-a
        * my-tag-b
      * **`issues`**
         * **0** (issue number within project, sample _project_b-0_)
            * ...
         * **1**
            * ...
         * **2**
            * `description.md`
            * `meta.json`
            * my-text1`.md` (multiple descriptions not implemented yet :!:)
            * my-text2`.md`
            * ...
            * **`comments`**
                * **2017-11-28-133544a** (Date/time-based generated unique Id)
                    * `description.md`
                    * `meta.json`
                    * **`tags`**
                        * my-tag-c
                        * hidden
            * **`attachments`**
                * **Qv3C-a** (Date/time-based generated unique _short Id_)  
                * **X_sR22**
                    * **`meta`**
                        * `description.md`
                        * `meta.json`
                    * my-picture1.jpg (original file name and original extension, defining file type)
                    * my-picture2.jpg (multiple pages partially suported :!:)
                    * ...
                    * **`tags`**
                        * my-tag-d
                        * hidden
            * **`tags`**
                * my-tag-a
                * status-done
                * my-tag-b
                * hidden
* **`users`** *Not implemented yet* :!:
    * **user_a** (User name)
      * `description.md`
      * `meta.json`

# Frameworks
## Bootstrap
![Bootstrap][hQzzR1]
http://getbootstrap.com

## Spring Boot
![Spring Boot][SphSp0]
https://spring.io/guides/gs/spring-boot/
