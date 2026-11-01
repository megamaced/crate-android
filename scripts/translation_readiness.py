#!/usr/bin/env python3
"""Report language readiness from committed resources, without enabling locales."""

import argparse
from fnmatch import fnmatchcase
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


LOCALE = re.compile(r"(?:[a-z]{2,3}(?:-r(?:[A-Z]{2}|[0-9]{3}))?|b\+[a-z]{2,3}(?:\+[A-Za-z0-9]{2,8})+)")
RESOURCE_TYPES = {"string", "plurals", "string-array"}


def read_resources(path):
    """Keep resource type as part of the key, just as Android does."""
    root = ET.parse(path).getroot()
    if root.tag != "resources":
        raise ValueError(f"{path}: expected a resources element")
    resources = {}
    for node in root:
        if node.tag not in RESOURCE_TYPES or node.get("translatable") == "false":
            continue
        key = (node.tag, node.attrib["name"])
        if key in resources:
            raise ValueError(f"{path}: duplicate {key[0]} {key[1]}")
        resources[key] = node
    return resources


def has_translation(source, translation):
    if translation is None:
        return False
    if source.tag == "string":
        return bool("".join(translation.itertext()).strip())
    items = list(translation.findall("item"))
    if not items or not all("".join(item.itertext()).strip() for item in items):
        return False
    if source.tag == "plurals":
        # Locales have different plural categories. Lint checks their forms;
        # every plural must at least have Android's fallback category.
        return any(item.get("quantity") == "other" for item in items)
    return len(items) == len(source.findall("item"))


def build_report(root):
    policy = json.loads((root / "app/translation-policy.json").read_text())
    threshold = policy["minimumCompletionPercent"]
    enabled = policy["enabledLocales"]
    patterns = policy["criticalResourcePatterns"]
    if type(threshold) is not int or not 1 <= threshold <= 100:
        raise ValueError("minimumCompletionPercent must be an integer from 1 to 100")
    if not isinstance(enabled, list) or not enabled or not all(isinstance(locale, str) and LOCALE.fullmatch(locale) for locale in enabled):
        raise ValueError("enabledLocales must contain Android language qualifiers")
    if "en" not in enabled or len(enabled) != len(set(enabled)):
        raise ValueError("enabledLocales must include en and have no duplicates")
    if not isinstance(patterns, dict) or not patterns:
        raise ValueError("criticalResourcePatterns must contain the core screen groups")

    res = root / "app/src/main/res"
    source = read_resources(res / "values/strings.xml")
    if not source:
        raise ValueError("The source catalogue has no translatable resources")
    core_groups = {}
    for group, globs in patterns.items():
        if not isinstance(globs, list) or not globs or not all(isinstance(pattern, str) and pattern for pattern in globs):
            raise ValueError(f"{group}: expected a nonempty list of resource patterns")
        matches = set()
        for pattern in globs:
            found = {key for key in source if fnmatchcase(key[1], pattern)}
            if not found:
                raise ValueError(f"{group}: {pattern} matches no translatable source resources")
            matches.update(found)
        core_groups[group] = matches
    core = set().union(*core_groups.values())

    catalogues = {}
    for path in sorted(res.glob("values-*/strings.xml")):
        locale = path.parent.name.removeprefix("values-")
        # Only language directories participate, not night/layout qualifiers.
        if LOCALE.fullmatch(locale) and locale != "car":
            catalogues[locale] = read_resources(path)
    missing = set(enabled) - {"en"} - catalogues.keys()
    if missing:
        raise ValueError(f"Enabled languages have no strings.xml: {', '.join(sorted(missing))}")

    languages = []
    for locale in sorted({"en"} | catalogues.keys()):
        translations = source if locale == "en" else catalogues[locale]
        complete = {key for key, node in source.items() if has_translation(node, translations.get(key))}
        missing_core = core - complete
        meets_criteria = len(complete) * 100 >= threshold * len(source) and not missing_core
        shipped = locale in enabled
        languages.append({
            "locale": locale,
            "translated": len(complete),
            "total": len(source),
            "coverage": len(complete) * 100 / len(source),
            "core_translated": len(core & complete),
            "core_total": len(core),
            "enabled": shipped,
            "ready_for_review": meets_criteria and not shipped,
            "status": "Enabled" if shipped else "Ready for review" if meets_criteria else "In progress",
            "missing_core": {group: len(keys - complete) for group, keys in core_groups.items() if keys - complete},
        })
    return threshold, languages


def markdown_report(threshold, languages):
    lines = [
        "## Translation readiness",
        "",
        f"New languages need at least **{threshold}%** coverage and **all core strings** before a fluent-speaker review and an enabling PR.",
        "Only committed, nonempty translations of translatable source resources count. Fixed strings and Weblate suggestions do not count.",
        "",
        "| Locale | Translated resources | Coverage | Core resources | Status |",
        "| --- | ---: | ---: | ---: | --- |",
    ]
    for language in languages:
        lines.append(
            f"| {language['locale']} | {language['translated']}/{language['total']} | "
            f"{language['coverage']:.1f}% | {language['core_translated']}/{language['core_total']} | {language['status']} |"
        )
    lines += ["", "Enabled languages stay enabled when new source strings reduce coverage. Missing translations fall back to English."]
    pending = [language for language in languages if not language["enabled"] and language["missing_core"]]
    if pending:
        lines += ["", "Missing core resources by screen group:", ""]
        for language in pending:
            groups = ", ".join(f"{group}: {count}" for group, count in language["missing_core"].items())
            lines.append(f"- **{language['locale']}** — {groups}")
    lines += ["", "Readiness does not enable a language automatically. Normal Android lint/build checks and a fluent-speaker review are still required."]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--github-summary", action="store_true", help="Also append the report to GITHUB_STEP_SUMMARY")
    args = parser.parse_args()
    try:
        threshold, languages = build_report(args.root)
        report = markdown_report(threshold, languages)
        print(report, end="")
        if args.github_summary:
            with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
                summary.write(report)
        if os.environ.get("GITHUB_ACTIONS") == "true":
            for language in languages:
                if language["ready_for_review"]:
                    print(f"::notice title=Translation ready for review::{language['locale']} meets the {threshold}% and core-string criteria. Arrange a fluent-speaker review before enabling it.")
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"Translation readiness: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
