#!/usr/bin/env python3
"""Removes from a pom the modules and the dependencies on the artifacts named (ADR 171).

    pom_strip.py POM ARTIFACT...

A dependency is removed whatever its type or scope; a module by its directory name, which is its artifact's.
"""
import re
import sys

path, artifacts = sys.argv[1], set(sys.argv[2:])
pom = open(path).read()


def dependency(match):
    artifact = re.search(r'<artifactId>([^<]+)</artifactId>', match.group(0)).group(1)
    return '' if artifact in artifacts else match.group(0)


pom = re.sub(r'[ \t]*<dependency>(?:(?!</dependency>).)*</dependency>[ \t]*\n', dependency, pom, flags=re.S)
pom = re.sub(r'[ \t]*<module>([^<]+)</module>[ \t]*\n',
             lambda m: '' if m.group(1).rsplit('/', 1)[-1] in artifacts else m.group(0), pom)
open(path, 'w').write(pom)
