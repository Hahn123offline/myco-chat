#!/usr/bin/env python3
"""语言无关契约的样例校验器（T0）。

- 支持的最小 JSON Schema 子集见 specs/README.md。
- 遇到未实现的 keyword 直接报错（不静默放行）。
- 退出码 0 = 所有样例与预期一致；1 = 有不一致。
用法：python specs/validate_fixtures.py [--verbose]
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FIXTURES = ROOT / "fixtures" / "fixtures.json"

SUPPORTED = {
    "type", "required", "properties", "additionalProperties", "items", "enum",
    "const", "pattern", "minLength", "maxLength", "minItems", "minProperties",
    "oneOf", "anyOf", "$ref", "$defs", "$schema", "$id", "title", "format",
    "minimum", "maximum",
}

URI_RE = re.compile(r"^https?://[^\s]+$")


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def resolve_ref(root: dict, ref: str) -> dict:
    if not ref.startswith("#/"):
        raise ValueError(f"only local $ref supported: {ref}")
    cur = root
    for part in ref[2:].split("/"):
        part = part.replace("~1", "/").replace("~0", "~")
        cur = cur[part]
    return cur


def type_ok(value, t) -> bool:
    if isinstance(t, list):
        return any(type_ok(value, x) for x in t)
    if t == "object":
        return isinstance(value, dict)
    if t == "array":
        return isinstance(value, list)
    if t == "string":
        return isinstance(value, str)
    if t == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if t == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if t == "boolean":
        return isinstance(value, bool)
    if t == "null":
        return value is None
    raise ValueError(f"unsupported type: {t}")


def validate(value, schema: dict, root: dict, path: str = "$") -> list[str]:
    errors: list[str] = []
    unknown = set(schema) - SUPPORTED
    if unknown:
        raise ValueError(f"unsupported schema keyword(s) at {path}: {sorted(unknown)}")

    if "$ref" in schema:
        return validate(value, resolve_ref(root, schema["$ref"]), root, path)

    if "oneOf" in schema:
        matched = 0
        branch_errors = []
        for i, sub in enumerate(schema["oneOf"]):
            errs = validate(value, sub, root, f"{path}.oneOf[{i}]")
            if not errs:
                matched += 1
            else:
                branch_errors.append((i, errs))
        if matched != 1:
            detail = "; ".join(f"branch{i}: {e[0]}" for i, e in branch_errors[:3])
            errors.append(f"{path}: oneOf expected exactly 1 match, got {matched} ({detail})")
        return errors

    if "anyOf" in schema:
        if not any(not validate(value, sub, root, path) for sub in schema["anyOf"]):
            errors.append(f"{path}: anyOf no branch matched")
        return errors

    if "type" in schema and not type_ok(value, schema["type"]):
        return [f"{path}: expected type {schema['type']}, got {type(value).__name__}"]

    if "const" in schema and value != schema["const"]:
        errors.append(f"{path}: expected const {schema['const']!r}, got {value!r}")
    if "enum" in schema and value not in schema["enum"]:
        errors.append(f"{path}: {value!r} not in enum {schema['enum']}")

    if isinstance(value, str):
        if "pattern" in schema and not re.search(schema["pattern"], value):
            errors.append(f"{path}: {value!r} does not match {schema['pattern']!r}")
        if "minLength" in schema and len(value) < schema["minLength"]:
            errors.append(f"{path}: shorter than minLength {schema['minLength']}")
        if "maxLength" in schema and len(value) > schema["maxLength"]:
            errors.append(f"{path}: longer than maxLength {schema['maxLength']}")
        if schema.get("format") == "uri" and not URI_RE.match(value):
            errors.append(f"{path}: not an http(s) URI: {value!r}")

    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            errors.append(f"{path}: {value} < minimum {schema['minimum']}")
        if "maximum" in schema and value > schema["maximum"]:
            errors.append(f"{path}: {value} > maximum {schema['maximum']}")

    if isinstance(value, dict):
        for key in schema.get("required", []):
            if key not in value:
                errors.append(f"{path}: missing required property {key!r}")
        props = schema.get("properties", {})
        for key, sub in props.items():
            if key in value:
                errors.extend(validate(value[key], sub, root, f"{path}.{key}"))
        addl = schema.get("additionalProperties", True)
        if addl is False:
            for key in value:
                if key not in props:
                    errors.append(f"{path}: unexpected property {key!r}")
        elif isinstance(addl, dict):
            for key, item in value.items():
                if key not in props:
                    errors.extend(validate(item, addl, root, f"{path}.{key}"))
        if "minProperties" in schema and len(value) < schema["minProperties"]:
            errors.append(f"{path}: fewer than minProperties {schema['minProperties']}")

    if isinstance(value, list):
        if "minItems" in schema and len(value) < schema["minItems"]:
            errors.append(f"{path}: fewer than minItems {schema['minItems']}")
        if "items" in schema:
            for i, item in enumerate(value):
                errors.extend(validate(item, schema["items"], root, f"{path}[{i}]"))

    return errors


def main() -> int:
    verbose = "--verbose" in sys.argv
    if not FIXTURES.exists():
        print(f"fixtures not found: {FIXTURES}")
        return 2
    data = load_json(FIXTURES)
    total = failed = 0
    for block in data:
        schema_path = ROOT / block["schema"]
        if not schema_path.exists():
            print(f"FAIL missing schema: {schema_path}")
            failed += 1
            continue
        schema = load_json(schema_path)
        for case in block["cases"]:
            total += 1
            errs = validate(case["doc"], schema, schema)
            ok = not errs
            expected = case["valid"]
            if ok != expected:
                failed += 1
                print(f"FAIL {block['schema']} / {case['name']}: expected valid={expected}, got {ok}")
                for e in errs[:5]:
                    print(f"      {e}")
            elif verbose:
                print(f"ok   {block['schema']} / {case['name']} (valid={expected})")
    print(f"\n{total - failed}/{total} cases match expectations")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
