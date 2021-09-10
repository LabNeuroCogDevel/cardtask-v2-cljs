#!/usr/bin/env bash
set -euo pipefail
# 20210909WF - init
#    export individual paths (as named in inkscape gui)

grep -Po '(?<=id=)"alien_[^"]*"' aliens.svg|
   sed 's/"//g' |
   while read pathid; do
      echo "# $pathid"
      inkscape  -o $pathid.svg -j -i $pathid aliens.svg
   done


