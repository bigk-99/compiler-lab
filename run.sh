#!/usr/bin/env sh
INPUT="$1"
OUTPUT="${@: -1}"
ASM_FILE="assembly.s"

BIN_DIR="$(dirname "$0")/build/install/compiler/bin"
COMPILER="$BIN_DIR/compiler"

"$COMPILER" "$INPUT" "$ASM_FILE"

gcc -o "$OUTPUT" "$ASM_FILE"



