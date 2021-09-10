#!/usr/bin/env bash
set -euo pipefail
# 20210909WF - init
#    export individual paths (as named in inkscape gui)

grep -Po '(?<=id=)".*_path"' creatures.svg|
   sed 's/"//g' |
   while read pathid; do
      id=${pathid/_path/}
      echo "# $pathid"
      inkscape  -o ../creatures/$id.svg -j -i $id creatures.svg
   done


