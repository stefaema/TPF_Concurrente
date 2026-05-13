#!/usr/bin/env bash
set -e

JAVA=/home/javi/Programas/idea-IU-261.23567.138/jbr/bin/java
POLITICA=${1:-balanceada}

if [[ "$POLITICA" != "balanceada" && "$POLITICA" != "priorizada" ]]; then
    echo "Uso: $0 [balanceada|priorizada]"
    exit 1
fi

cd "$(dirname "$0")"
$JAVA -cp out Main "$POLITICA"
