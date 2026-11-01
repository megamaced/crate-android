import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from translation_readiness import build_report


class TranslationReadinessTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.res = self.root / "app/src/main/res"
        self.policy = {
            "minimumCompletionPercent": 80,
            "enabledLocales": ["en"],
            "criticalResourcePatterns": {"Sign-in": ["login_*"]},
        }
        self.write_policy()
        self.source = '<string name="login_action">Log in</string>' + "".join(
            f'<string name="text_{number}">Text {number}</string>' for number in range(1, 10)
        )
        self.resources("values", self.source)

    def write_policy(self):
        path = self.root / "app/translation-policy.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.policy))

    def resources(self, directory, body):
        folder = self.res / directory
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "strings.xml").write_text(f"<resources>{body}</resources>")

    def language(self, locale="et"):
        return next(language for language in build_report(self.root)[1] if language["locale"] == locale)

    def test_eighty_percent_is_ready_only_with_all_core_strings(self):
        self.resources("values-et", "".join(
            f'<string name="text_{number}">Tõlge</string>' for number in range(1, 9)
        ))
        self.assertEqual(self.language()["coverage"], 80)
        self.assertFalse(self.language()["ready_for_review"])
        self.resources("values-et", '<string name="login_action">Logi sisse</string>' + "".join(
            f'<string name="text_{number}">Tõlge</string>' for number in range(1, 8)
        ))
        self.assertTrue(self.language()["ready_for_review"])
        self.assertEqual(self.policy["enabledLocales"], ["en"])

    def test_fixed_empty_and_obsolete_resources_do_not_inflate_coverage(self):
        self.resources("values", self.source + '<string name="brand" translatable="false">Crate</string>')
        self.resources("values-et", '''
            <string name="brand" translatable="false">Crate</string>
            <string name="obsolete">Old translation</string>
            <string name="text_1">   </string>
            <string name="login_action">Logi sisse</string>
        ''')
        language = self.language()
        self.assertEqual((language["translated"], language["total"]), (1, 10))

    def test_an_enabled_language_stays_enabled_when_source_text_grows(self):
        self.policy["enabledLocales"].append("et")
        self.write_policy()
        self.resources("values-et", '<string name="text_1">Tõlge</string>')
        language = self.language()
        self.assertEqual(language["status"], "Enabled")
        self.assertFalse(language["ready_for_review"])
        self.assertTrue(language["enabled"])
        self.assertEqual(language["coverage"], 10)

    def test_incomplete_plural_or_array_does_not_count_as_translated(self):
        self.resources("values", self.source + '''
            <plurals name="count"><item quantity="one">One</item><item quantity="other">Many</item></plurals>
            <string-array name="choices"><item>Yes</item><item>No</item></string-array>
        ''')
        self.resources("values-et", '''
            <plurals name="count"><item quantity="one">Üks</item></plurals>
            <string-array name="choices"><item>Jah</item></string-array>
        ''')
        self.assertEqual(self.language()["translated"], 0)
        # A language with only the 'other' plural category is valid; English's
        # 'one' category must not become a universal completeness requirement.
        self.resources("values-et", '''
            <plurals name="count"><item quantity="other">Mitu</item></plurals>
            <string-array name="choices"><item>Jah</item><item>Ei</item></string-array>
        ''')
        self.assertEqual(self.language()["translated"], 2)

    def test_only_language_directories_are_reported(self):
        for directory in ("values-night", "values-car", "values-land", "values-pl-v31"):
            self.resources(directory, self.source)
        for directory in ("values-et", "values-pt-rBR", "values-b+sr+Latn"):
            self.resources(directory, self.source)
        self.assertEqual([language["locale"] for language in build_report(self.root)[1]], ["b+sr+Latn", "en", "et", "pt-rBR"])

    def test_missing_enabled_catalogue_and_misspelled_core_patterns_fail(self):
        self.policy["enabledLocales"].append("et")
        self.write_policy()
        with self.assertRaisesRegex(ValueError, "no strings.xml"):
            build_report(self.root)
        self.resources("values-et", self.source)
        self.policy["criticalResourcePatterns"]["Sign-in"] = ["signin_*"]
        self.write_policy()
        with self.assertRaisesRegex(ValueError, "matches no"):
            build_report(self.root)

    def test_threshold_is_not_rounded_up(self):
        self.resources("values", '<string name="login_action">Log in</string>' + "".join(
            f'<string name="text_{number}">Text</string>' for number in range(1, 501)
        ))
        self.resources("values-et", '<string name="login_action">Logi sisse</string>' + "".join(
            f'<string name="text_{number}">Tõlge</string>' for number in range(1, 400)
        ))
        self.assertGreater(self.language()["coverage"], 79)
        self.assertFalse(self.language()["ready_for_review"])

    def test_ci_summary_reports_progress_without_failing_or_enabling_it(self):
        self.resources("values-et", '<string name="text_1">Tõlge</string>')
        summary = self.root / "summary.md"
        script = Path(__file__).resolve().parents[1] / "translation_readiness.py"
        result = subprocess.run(
            [sys.executable, str(script), "--root", str(self.root), "--github-summary"],
            env={**os.environ, "GITHUB_STEP_SUMMARY": str(summary)},
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("10.0%", summary.read_text())
        self.assertIn("In progress", summary.read_text())
        self.assertEqual(json.loads((self.root / "app/translation-policy.json").read_text())["enabledLocales"], ["en"])


if __name__ == "__main__":
    unittest.main()
