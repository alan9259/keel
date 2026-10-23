#!/usr/bin/env python3
"""Copy the iOS backup structs (Keel/Services/Backup/KeelBackup.swift) without their
init(_:) and model() methods, which need the app's SwiftData models. What is left is the
exact Codable shape iOS reads and writes; the Swift tools here compile against it."""
import re, sys

src = open(sys.argv[1]).read()
out, i = [], 0
lines = src.split('\n')
skip_depth = None
depth = 0
for line in lines:
    stripped = line.strip()
    if skip_depth is None and re.match(r'(init\(_ m:|func model\()', stripped):
        skip_depth = depth
    opens, closes = line.count('{'), line.count('}')
    if skip_depth is not None:
        depth += opens - closes
        if depth <= skip_depth:
            skip_depth = None
        continue
    depth += opens - closes
    if stripped.startswith('import '):
        continue
    out.append(line)
print('import Foundation\n' + '\n'.join(out))
