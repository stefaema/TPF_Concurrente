#!/usr/bin/env bash
set -e

JAVAC=/home/javi/Programas/idea-IU-261.23567.138/jbr/bin/javac
JAVA=/home/javi/Programas/idea-IU-261.23567.138/jbr/bin/java

mkdir -p out
$JAVAC -encoding UTF-8 -d out src/*.java
echo "Compilación exitosa → out/"
