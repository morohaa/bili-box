"""Regression cases for package boundaries and documentation-only references."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_architecture import check, code_only  # noqa: E402


def source(package, target, symbol="Target", usage=None):
    return (
        f"package io.github.bilibox.{package}\n"
        f"import io.github.bilibox.{target}.{symbol}\n"
        + (f"val value = {symbol}()\n" if usage is None else usage)
    )


class ArchitectureTest(unittest.TestCase):
    def test_allowed_composition(self):
        sources = [
            ("App.kt", source("app", "feature.home")),
            ("Home.kt", source("feature.home", "feature.search")),
            ("Search.kt", source("feature.search", "data.bili")),
            ("Client.kt", source("data.bili", "core.model")),
            ("Online.kt", source("feature.online", "feature.player")),
            ("Player.kt", source("feature.player", "feature.listen")),
        ]
        self.assertEqual([], check(sources))

    def test_reverse_layer_dependencies(self):
        for origin, target in [
            ("core.model", "data.bili"),
            ("core.ui", "feature.player"),
            ("data.bili", "feature.search"),
            ("core.ui", "app"),
            ("data.bili", "app"),
            ("feature.search", "app"),
        ]:
            with self.subTest(origin=origin, target=target):
                errors = check([("Wrong.kt", source(origin, target))])
                self.assertTrue(any("forbidden dependency" in error for error in errors))
                self.assertIn("Wrong.kt:2:", errors[0])

    def test_named_feature_boundaries(self):
        for origin, target in [
            ("feature.search", "feature.home"),
            ("feature.listen", "feature.player"),
            ("feature.library", "feature.player.details"),
            ("feature.player.details", "feature.library"),
        ]:
            with self.subTest(origin=origin):
                self.assertIn("forbidden dependency", check([("Wrong.kt", source(origin, target))])[0])

    def test_cycles_include_paths_and_locations(self):
        for nodes in [("a", "b"), ("a", "b", "c")]:
            sources = [
                (f"{node}.kt", source(f"feature.{node}", f"feature.{nodes[(i + 1) % len(nodes)]}"))
                for i, node in enumerate(nodes)
            ]
            errors = check(sources)
            self.assertEqual(1, len(errors))
            self.assertIn("feature dependency cycle:", errors[0])
            for node in nodes:
                self.assertIn(f"{node}.kt:2:", errors[0])

    def test_subpackages_are_one_feature(self):
        self.assertEqual([], check([
            ("Details.kt", source("feature.player.details", "feature.player")),
            ("Player.kt", source("feature.player", "feature.player.details")),
        ]))

    def test_kdoc_only_import_does_not_create_cycle(self):
        self.assertEqual([], check([
            ("Home.kt", source("feature.home", "feature.search", "SearchResults")),
            ("Search.kt", source("feature.search", "feature.home", "HomeScreen", "/** See [HomeScreen]. */\n")),
        ]))

    def test_comments_and_literals_do_not_create_dependencies(self):
        content = source("core.model", "feature.player", usage='''
// Target() and io.github.bilibox.app.BiliBoxApp()
/* nested /* Target() */ io.github.bilibox.feature.player.Target() */
val literal = "Target() and io.github.bilibox.app.BiliBoxApp()"
val raw = """Target() and io.github.bilibox.feature.player.Target()"""
val quote = '\\"'
''')
        self.assertEqual([], check([("Model.kt", content)]))
        self.assertEqual(content.count("\n"), code_only(content).count("\n"))

    def test_alias_and_member_import(self):
        for imported, usage in [("Target as Alias", "Alias()"), ("Object.member", "member()")]:
            content = source("data.bili", "feature.search", imported, "val x = " + usage)
            self.assertIn("forbidden dependency", check([("Client.kt", content)])[0])

    def test_fully_qualified_reference_and_activity(self):
        for target in ["feature.player.Target", "app.BiliBoxApp", "MainActivity"]:
            content = "package io.github.bilibox.core.ui\nval x = io.github.bilibox." + target + "()"
            self.assertIn("forbidden dependency", check([("Ui.kt", content)])[0])

    def test_templates_preserve_real_references(self):
        for template in [
            '"$Target"',
            '"${Target()}"',
            '"""${io.github.bilibox.feature.player.Target()}"""',
            '"${run { "${Target()}" }}"',
        ]:
            with self.subTest(template=template):
                content = source("data.bili", "feature.player", usage="val text = " + template)
                self.assertIn("forbidden dependency", check([("Client.kt", content)])[0])

    def test_escaped_template_is_literal(self):
        content = source("data.bili", "feature.player", usage=r'val text = "\$Target"')
        self.assertEqual([], check([("Client.kt", content)]))

    def test_project_wildcards_are_rejected(self):
        content = "package io.github.bilibox.core.ui\nimport io.github.bilibox.feature.player.*\n"
        self.assertIn("use explicit imports", check([("Ui.kt", content)])[0])

    def test_external_wildcards_are_allowed(self):
        self.assertEqual([], check([("Ui.kt", "package io.github.bilibox.core.ui\nimport androidx.compose.runtime.*\n")]))

    def test_cli_exit_status_and_empty_source(self):
        script = str(Path(__file__).resolve().parents[1] / "check_architecture.py")
        with tempfile.TemporaryDirectory() as directory:
            def run():
                return subprocess.run(
                    [sys.executable, "-B", script, "--source-root", directory],
                    capture_output=True, text=True,
                )
            self.assertEqual(2, run().returncode)
            path = Path(directory) / "Test.kt"
            path.write_text(source("data.bili", "core.model"), encoding="utf-8")
            self.assertEqual(0, run().returncode)
            path.write_text(source("data.bili", "feature.search"), encoding="utf-8")
            failure = run()
            self.assertEqual(1, failure.returncode)
            self.assertIn("Test.kt:2:", failure.stderr)


if __name__ == "__main__":
    unittest.main()
