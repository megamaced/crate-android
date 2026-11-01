# Translation readiness and releases

Translations are maintained on [Weblate](https://hosted.weblate.org/projects/crate/android-app/). Merge translation updates as they arrive once the normal checks pass. Incomplete languages can stay in the repository while translation continues.

## Enabling a new language

A new language is ready for release when:

1. At least **80% of translatable resources** have accepted translations.
2. All core text for sign-in, navigation, adding/editing items, settings and shared errors is translated. `criticalResourcePatterns` in [`app/translation-policy.json`](../app/translation-policy.json) identifies these resources, including new strings added under those prefixes.
3. Android lint, locale verification, unit tests and builds pass, including format-argument and plural checks.
4. A fluent speaker has checked the core flows, wording and layout in a build with that language enabled. Record the reviewer and the result in the enabling PR.

The 80% threshold is this project's policy, not an Android or Weblate requirement. Suggestions awaiting acceptance, empty translations and `translatable="false"` resources do not count. Each string, plural or string array counts as one resource. A plural needs a nonempty `other` form and no empty declared forms; Android lint checks language-specific plural and format rules. An array needs a translation for every item.

Add the Android locale qualifier (for example, `et`, `pt-rBR` or `b+sr+Latn`) to `enabledLocales` in `app/translation-policy.json` through a normal reviewed PR. That list controls Gradle's `localeFilters` and Android's generated per-app language list. Reaching a percentage in Weblate does not change it automatically.

Once a language is enabled, keep it enabled when new source strings temporarily lower its coverage. Missing strings fall back to English. The report must not block routine source changes or remove an existing language because its percentage dropped.

## Readiness report

Run locally:

```bash
python3 scripts/translation_readiness.py
```

Every CI build publishes the same report in its job summary. It shows coverage, core-string coverage, missing core groups and languages ready for a fluent-speaker review. Ready languages also produce a CI notice. No Weblate token or network request is needed, so the report works on fork PRs and uses exactly the resources being built.

The report is informational for incomplete translations and for existing languages whose coverage drops. Invalid policy files, missing catalogues for enabled languages and malformed resource XML fail the check.
