#!/usr/bin/env bash
# Build and run Fluid Swap.
#   ./run.sh         -> compile + launch the game window
#   ./run.sh test    -> compile + run the headless collision tests
set -e
cd "$(dirname "$0")"

javac -d out src/fluidswap/*.java

if [ "$1" = "test" ]; then
  java -cp out fluidswap.CollisionTest
else
  java -cp out fluidswap.Main
fi
