# JRSync Metadata

This document describes the format for metadata JRSync uses to communicate
information used to synchronize files. For simplicity, files are described in
terms of data types offered by Java's DataOutputStream/RandomAccessFile.

# The Sums File

The sums file specifies checksum information useful for performing a block
search on another file. It is a binary file format to keep the storage and/or
transmission cost down for larger files.

## The Header

The file header consists of enough information to properly verify and initialize
the necessary buffers and temp file space, determine and initialize the
appropriate message digests for computing cryptographic hashes, compute the
number of whole blocks in the file, and finally to determine if two files have
matching content. The File-Source attribute is intended for specifying an
externally accessible location for getting the content described in the file.
The format, and the means to access that content is intentionally unspecified.

<File-Hash-Type/UTF-8>
<File-Hash-Length/byte>
<File-Hash/bytes>
<File-Size/long>
<File-Source/UTF-8>
<Block-Hash-Type/UTF-8>
<Block-Hash-Length/byte>
<Block-Size/int>

## Sums

The remainder of the file consists of File-Size/Block-Size block descriptors in
the order they occur in the file and of the form:

<Block-Checksum/int>
<Block-Hash/bytes>

The checksum is always the rolling checksum value implemented by JRSync. The
Block-Hash values are always Block-Hash-Length bytes long. Each checksum/hash
pair corresponds to an implicit 0-based index in the file.