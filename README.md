# JRsync

A library for efficiently synchronizing files, based on the rsync algorithm. It
is intended to be usable by applications written for the Android mobile
operating system with no additional dependencies, as well as from server
applications.

## Why?

After looking for a solution for the
[CIMS project](http://cims-bioko.github.io/),
I could not find an acceptable solution for efficient, general purpose file
synchronization usable for both client and server components written in
Java/Android. I was looking for something that had the following properties:

  * Usable in Java and Android without external dependencies
  * Capable of efficient incremental synchronization of large files
  * Minimalistic - Small and focused over large and feature-rich
  * Reasonable test coverage
  * A commercial-friendly, permissive license (non-viral open source license)

Since I couldn't find what I was looking for, I decided to create it.

## Requirements

To build this library, you'll need:

  * JDK 7+
  * Apache Maven

## Building

To build the library, you just need to run:

```
mvn clean install
```