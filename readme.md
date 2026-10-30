# Tandem Tales Client (Java)

This Java library provides the abstract `edu.uky.cs.nil.tt.Client` and
`edu.uky.cs.nil.tt.ClientFactory` classes that can be extended to create
[Tandem Tales](https://sgware.github.io/tt) agents. See
[this example Java agent](https://github.com/sgware/tt-random-agent-java)
for a demonstration of how to download and run a Tandem Tales server, define a
Java agent, connect it to the server, and play with it.

## Download and Install

You can download a [pre-compiled JAR file here](build/jar) that includes the
source files and all dependencies.

You can add this library to a [Maven](https://maven.apache.org) project's
`pom.xml` file like this:
```
<project>
  ...
  <dependencies>
    <!-- Tandem Tales Client (Java) -->
    <dependency>
      <groupId>edu.uky.cs.nil.tt</groupId>
      <artifactId>tt-client-java</artifactId>
      <version>0.11.2</version> <!-- use most recent version -->
    </dependency>
  </dependencies>
  ...
</project>
```

## Build from Source

You can download and install this library from source using
[Maven](https://maven.apache.org).

It has one dependency:
- [Tandem Tales Server](https://github.com/sgware/tt-server)

Assuming you have [Git](https://git-scm.com/install), the
[Java Development Kit](https://www.oracle.com/java/technologies/downloads), and
[Maven](https://maven.apache.org) installed and on your path, you can download
the dependencies and compile the Tandem Tales Client library from source like this.
```
git clone https://github.com/sgware/tt-client-java.git
cd tt-client-java
mvn clean install
```

## Documentation

The [documentation](https://sgware.github.io/tt-client-java) includes a short
description, example, and API reference.

## License

Tandem Tales and its associated libraries were developed by Stephen G. Ware PhD,
Associate Professor of Computer Science at the University of Kentucky.
Development was sponsored in part by a grant from the US National Science
Foundation, #2145153.

Tandem Tales is released under the GNU General Public License version 3.0 (GPL
3). This means you are free to share and modify this software, even for
commercial purposes, as long as you give credit to the original creators and you
also release your modifications under the GPL 3 license. See the license file
for details. The University of Kentucky retains all right not specifically
granted.

To license Tandem Tales or this library for a project not compatible with the
terms of the GPL license, contact the University of Kentucky Office of
Technology Commercialization at <otcinfo@uky.edu>.

## Version History

- Version 0.11.2: Small bug fixes and typo corrections.
- Version 0.11.1: Small fixes to the external API to match other client
  libraries.
- Version 0.11.0: Implemented external API for text embedding and completion.
- Version 0.10.0: First public release.