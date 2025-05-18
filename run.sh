#!/usr/bin/env sh
BIN_DIR="$(dirname "$0")/build/install/compiler/bin"
$BIN_DIR/compiler "$@"
clang -arch x86_64 -o main "$2"


