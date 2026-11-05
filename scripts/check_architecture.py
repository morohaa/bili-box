#!/usr/bin/env python3
"""Check Kotlin package boundaries without Android SDK or third-party packages."""

import argparse
from dataclasses import dataclass
from pathlib import Path
import re
import sys


ROOT_PACKAGE = "io.github.bilibox"
DEFAULT_SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java"
NAME = r"(?:`[^`\n]+`|\w+)"
QUALIFIED = NAME + r"(?:\s*\.\s*" + NAME + r")*"
PACKAGE = re.compile(r"(?m)^\s*package[ \t]+(" + QUALIFIED + r")")
IMPORT = re.compile(
    r"(?m)^[ \t]*import[ \t]+(" + QUALIFIED + r"(?:\.\*)?)"
    r"(?:[ \t]+as[ \t]+(" + NAME + r"))?[^\n]*"
)
REFERENCE = re.compile(r"\b" + re.escape(ROOT_PACKAGE) + r"(?:\s*\.\s*" + NAME + r")+")


def code_only(text):
    """Mask comments/literals, retaining positions and string template expressions."""
    result = list(text)
    length = len(text)

    def blank(start, end):
        for index in range(start, end):
            if text[index] not in "\r\n":
                result[index] = " "

    def string(start, delimiter):
        index = start + len(delimiter)
        blank(start, index)
        while index < length:
            if text.startswith(delimiter, index):
                blank(index, index + len(delimiter))
                return index + len(delimiter)
            if delimiter != '"""' and text[index] == "\\":
                blank(index, min(index + 2, length))
                index += 2
            elif delimiter != "'" and text.startswith("${", index):
                blank(index, index + 2)
                index = code(index + 2, template=True)
            elif delimiter != "'" and text[index] == "$":
                blank(index, index + 1)
                index += 1
                identifier = re.match(r"\w+", text[index:])
                if identifier:
                    index += len(identifier.group())
            else:
                blank(index, index + 1)
                index += 1
        return index

    def code(index, template=False):
        braces = 0
        while index < length:
            if text.startswith("//", index):
                end = text.find("\n", index)
                end = length if end < 0 else end
                blank(index, end)
                index = end
            elif text.startswith("/*", index):
                start, depth = index, 1
                index += 2
                while index < length and depth:
                    if text.startswith("/*", index):
                        depth += 1
                        index += 2
                    elif text.startswith("*/", index):
                        depth -= 1
                        index += 2
                    else:
                        index += 1
                blank(start, index)
            elif text.startswith('"""', index):
                index = string(index, '"""')
            elif text[index] in "\"'":
                index = string(index, text[index])
            elif text[index] == "`":
                end = text.find("`", index + 1)
                index = length if end < 0 else end + 1
            elif template and text[index] == "}" and braces == 0:
                blank(index, index + 1)
                return index + 1
            else:
                if template and text[index] in "{}":
                    braces += 1 if text[index] == "{" else -1
                index += 1
        return index

    code(0)
    return "".join(result)


def normalize(name):
    return re.sub(r"\s+", "", name).replace("`", "")


def group(name):
    if not name.startswith(ROOT_PACKAGE + "."):
        return None
    parts = name[len(ROOT_PACKAGE) + 1:].split(".")
    if parts[0] in {"app", "MainActivity"}:
        return "app"
    if parts[0] in {"core", "data"}:
        return parts[0]
    if parts[0] == "feature" and len(parts) > 1:
        return ".".join(parts[:2])
    return None  # Generated R / BuildConfig do not belong to a feature.


@dataclass(frozen=True)
class Dependency:
    file: str
    line: int
    source: str
    target: str

    def location(self):
        return f"{self.file}:{self.line}: {self.source} -> {self.target}"


def dependencies(path, text):
    clean = code_only(text)
    package = PACKAGE.search(clean)
    if not package:
        return [], [f"{path}:1: missing package declaration"]
    source = group(normalize(package.group(1)))
    imports = list(IMPORT.finditer(clean))
    body = list(clean)
    for match in [package] + imports:
        body[match.start():match.end()] = " " * (match.end() - match.start())
    body = "".join(body)
    identifiers = set(re.findall(NAME, body))
    references, errors = [], []
    for match in imports:
        name = normalize(match.group(1))
        if not name.startswith(ROOT_PACKAGE + "."):
            continue
        line = text.count("\n", 0, match.start()) + 1
        if name.endswith(".*"):
            errors.append(f"{path}:{line}: use explicit imports for project packages: {name}")
        elif (match.group(2) or match.group(1).split(".")[-1]).strip() in identifiers:
            references.append((name, line))
    for match in REFERENCE.finditer(body):
        references.append((normalize(match.group()), text.count("\n", 0, match.start()) + 1))
    edges = []
    for name, line in references:
        target = group(name)
        if source and target and source != target:
            edges.append(Dependency(str(path), line, source, target))
    return edges, errors


def check(sources):
    edges, errors = [], []
    for path, text in sources:
        found, issues = dependencies(path, text)
        edges.extend(found)
        errors.extend(issues)
    forbidden = {
        "core": {"data", "app"},
        "data": {"app"},
        "feature.search": {"feature.home"},
        "feature.listen": {"feature.player"},
        "feature.library": {"feature.player"},
        "feature.player": {"feature.library"},
    }
    graph = {}
    for edge in edges:
        feature_target = edge.target.startswith("feature.")
        if (edge.target in forbidden.get(edge.source, set())
                or edge.source in {"core", "data"} and feature_target
                or edge.source.startswith("feature.") and edge.target == "app"):
            errors.append(edge.location() + ": forbidden dependency")
        if edge.source.startswith("feature.") and feature_target:
            graph.setdefault(edge.source, {}).setdefault(edge.target, edge)

    visited, active = set(), []

    def visit(node):
        visited.add(node)
        active.append(node)
        for target in sorted(graph.get(node, {})):
            if target in active:
                cycle = active[active.index(target):] + [target]
                locations = [graph[a][b].location() for a, b in zip(cycle, cycle[1:])]
                errors.append("feature dependency cycle: " + " -> ".join(cycle)
                              + "\n  " + "\n  ".join(locations))
            elif target not in visited:
                visit(target)
        active.pop()

    for node in sorted(graph):
        if node not in visited:
            visit(node)
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    files = sorted(args.source_root.rglob("*.kt"))
    if not files:
        parser.error(f"no Kotlin sources found in {args.source_root}")
    errors = check((path, path.read_text(encoding="utf-8")) for path in files)
    if errors:
        print("Architecture check failed:\n" + "\n".join(errors), file=sys.stderr)
        return 1
    print(f"Architecture check passed ({len(files)} Kotlin files).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
