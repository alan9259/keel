#!/bin/zsh
# Build the iOS-shape tools and either write the shared fixture or read a backup as iOS would.
#   ./run.sh fixture            -> fixtures/backup/v1-ios-sample.keelbackup
#   ./run.sh read <file>        -> decodes <file> with the iOS structs and JSONDecoder
set -e
here=${0:A:h}; repo=${here:h:h:h}
out=$(mktemp -d)
python3 $here/extract_structs.py $repo/Keel/Services/Backup/KeelBackup.swift > $out/KeelBackupStructs.swift
case $1 in
  fixture) cp $here/make_fixture.swift $out/main.swift  # top-level code must be in main.swift
           swiftc -O -o $out/make $out/KeelBackupStructs.swift $out/main.swift 2>&1 | grep -v warning || true
           $out/make $repo/fixtures/backup/v1-ios-sample.keelbackup ;;
  read)    cp $here/read_backup.swift $out/main.swift
           swiftc -O -o $out/read $out/KeelBackupStructs.swift $out/main.swift 2>&1 | grep -v warning || true
           $out/read $2 ;;
  *) echo "usage: run.sh fixture | read <file>"; exit 2 ;;
esac
