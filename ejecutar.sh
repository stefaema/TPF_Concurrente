#!/usr/bin/env bash
set -e

JAVA=/home/javi/Programas/idea-IU-261.23567.138/jbr/bin/java

cd "$(dirname "$0")"
# Sin argumentos → modo interactivo (menú).
# Con argumento   → modo batch, ej: ./ejecutar.sh balanceada
$JAVA -cp out Main "$@"
