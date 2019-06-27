# Installation
## Downloads
1. **Java 11** or later. Options:
    - [OpenJDK](https://openjdk.java.net/projects/jdk/)
    - [Java SE Development Kit - Oracle](https://www.oracle.com/technetwork/java/javase/downloads/index.html)
1. **Git**. Options:
    - [Classic command line](https://git-scm.com/)
    - [Sourcetree (GUI)](https://www.sourcetreeapp.com/)
    - IDE: Eclipse, VS Code, etc.
1. `mizoine-0.?.?-SNAPSHOT.jar` + `application.properties`

## Build from source

See: [Creating an Executable Jar](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#getting-started-first-application-executable-jar)
```bash
mvn clean package
```

## Configuration
### Simple `application.properties`

Mandatory settings:
- Repository path
- At least one user
- Git credentials for the user

```properties
repository.home=/var/amadeus/mizoine

users.amadeus.password=MySecretPassword

users.amadeus.git.username=amadeus
users.amadeus.git.password=MySecretGitPassword
```

#### Uploading settings (optional)

```properties
spring.servlet.multipart.max-file-size=128MB
spring.servlet.multipart.max-request-size=128MB
```

#### Logging settings (optional)

```properties
logging.file=mizoine-common.log

logging.level.com.gratchev.mizoine.repository.Repositories=info
```

#### More repositories (optional)

Mizoine supports multiple repositories within one Mizoine instance.

Each repository must be accessed via different HTTP host. Easiest way: `http://localhost:731/` for basic repository, and `http://127.0.0.1:731/` for alternative repository (with different home path).

Using reverse proxy any number of repositories can be configured for the same Mizoine instance.

```properties
repositories.work.home=/var/amadeus/mizoine-work
repositories.work.host=127.0.0.1
```

# Starting

## Simple start

```bash
java -jar mizoine-0.1.0-SNAPSHOT.jar
```

## Linux start

There can be problems with file name encoding in Linux. Some non-ASCII characters can be read improperly by the `jgit` library. Git page shows then corrupted file names and non-existing file changes.

In this case - explicitly set locale encoding when running java.

```bash
LC_ALL=en_US.UTF-8 java -jar mizoine-0.1.0-SNAPSHOT.jar
```

## Self-updating runner script (sample)

Sample of **re-start-mizoine.sh** (should be started from Mizoine installation directory)
```bash
#!/bin/sh
apfile="application.properties"
newjar="~/mizoine-0.1.0-SNAPSHOT.jar"
workingjar="mizoine-0.1.0-SNAPSHOT.jar"
if [ -f "$apfile" ]
then
	while :
	do
			echo "$apfile found. Starting Mizoine."
			if [ "$workingjar" -ot "$newjar" ]; then
				echo "New version of jar found. Copying"
			    cp -f "$newjar" "$workingjar"
			fi

			LC_ALL=en_US.UTF-8 java -jar $workingjar $1 $2 $3 $4
			echo "Mizoine stopped."
	done
else
	echo "$apfile not found."
fi
```