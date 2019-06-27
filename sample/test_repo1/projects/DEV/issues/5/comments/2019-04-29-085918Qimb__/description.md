See https://packages.debian.org/stretch-backports/amd64/openjdk-11-jdk/download

* Add backports to sources list
> You should be able to use any of the listed mirrors by adding a line to your /etc/apt/sources.list like this:
> `deb http://ftp.de.debian.org/debian stretch-backports main`
* Update apt
`apt-get update`
* Install OpenJDK 11
`apt-get install openjdk-11-jdk`