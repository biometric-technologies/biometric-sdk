"""Markdown summary of a Kover XML report: line and branch coverage per package, lowest first.

Usage: python3 coverage-summary.py build/reports/kover/report.xml >> $GITHUB_STEP_SUMMARY"""
import sys
import xml.etree.ElementTree as ET

def counters(node):
    out = {}
    for c in node.findall('counter'):
        missed, covered = int(c.get('missed')), int(c.get('covered'))
        out[c.get('type')] = (covered, missed + covered)
    return out

def pct(pair):
    covered, total = pair
    return f"{100 * covered / total:.0f}%" if total else "n/a"

root = ET.parse(sys.argv[1]).getroot()
total = counters(root)
print(f"### Test coverage (JVM): lines {pct(total.get('LINE', (0, 0)))}, branches {pct(total.get('BRANCH', (0, 0)))}\n")
print("| Package | Lines | Branches | Lines covered |")
print("|---|---|---|---|")
rows = []
for package in root.findall('package'):
    c = counters(package)
    line = c.get('LINE', (0, 0))
    rows.append((line[0] / line[1] if line[1] else 1.0, package.get('name').replace('/', '.'), line, c.get('BRANCH', (0, 0))))
for _, name, line, branch in sorted(rows):
    print(f"| `{name}` | {pct(line)} | {pct(branch)} | {line[0]} of {line[1]} |")
