"""theme.sh pushes a theme's inputs/ folder (input_logos): skips unchanged files, deletes removed ones.

Same harness as test_theme_video_clips.py (a fake tv.py running /bin/sh, a stub `content`); theme.ps1 is read, not run.
"""
import os
from pathlib import Path
import shutil
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_theme_push_ownership as push  # noqa: E402

LOGO = b"\x89PNG\r\n\x1a\n" + b"logo" * 100
LONG_AGO = 1_000_000_000


@unittest.skipUnless(shutil.which("md5sum"), "the push compares files with md5sum, as the TV's shell does")
class ThemeInputsTest(unittest.TestCase):
    run_theme = push.ThemePushTest.run_theme

    def setUp(self):
        push.ThemePushTest.setUp(self)
        (self.tools / "tv.py").write_text(push.FAKE_TV.replace('    Device().shell(" ".join(args))',
                                                               '    sys.stdout.write(Device().shell(" ".join(args)))'))
        self.inputs = self.theme / "inputs"
        self.inputs.mkdir()
        (self.inputs / "ps5.png").write_bytes(LOGO)
        self.on_tv = self.device / "inputs" / "ps5.png"

    def push_theme(self):
        result = self.run_theme(str(self.theme))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def test_a_logo_reaches_the_tv(self):
        self.push_theme()
        self.assertEqual(LOGO, self.on_tv.read_bytes())

    def test_a_logo_the_tv_has_is_not_sent_again(self):
        self.push_theme()
        os.utime(self.on_tv, (LONG_AGO, LONG_AGO))
        result = self.push_theme()
        self.assertIn("inputs/ps5.png is already on the TV", result.stdout)
        self.assertEqual(LONG_AGO, int(self.on_tv.stat().st_mtime))

    def test_a_logo_of_the_same_size_but_other_bytes_is_replaced(self):
        self.push_theme()
        changed = LOGO[:-1] + b"X"
        (self.inputs / "ps5.png").write_bytes(changed)
        self.push_theme()
        self.assertEqual(changed, self.on_tv.read_bytes())

    def test_a_logo_the_theme_dropped_is_removed(self):
        (self.inputs / "xbox.png").write_bytes(LOGO + b"2")
        self.push_theme()
        (self.inputs / "ps5.png").unlink()
        self.push_theme()
        self.assertFalse(self.on_tv.exists())
        self.assertTrue((self.device / "inputs" / "xbox.png").exists())

    def test_a_theme_without_inputs_removes_the_last_themes_and_a_reset_too(self):
        self.push_theme()
        shutil.rmtree(self.inputs)
        self.push_theme()
        self.assertFalse(self.on_tv.exists())
        self.inputs.mkdir()
        (self.inputs / "ps5.png").write_bytes(LOGO)
        self.push_theme()
        result = self.run_theme("--reset")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(self.on_tv.exists())

    def test_a_name_the_push_cannot_carry_is_refused_before_anything_is_sent(self):
        (self.inputs / "my logo.png").write_bytes(LOGO)
        result = self.run_theme(str(self.theme))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("unsupported inputs filename: my logo.png", result.stderr)
        self.assertFalse((self.device / "config.json").exists())

    def test_theme_ps1_pushes_inputs_by_the_same_rule(self):
        source = (push.TOOLS / "theme.ps1").read_text(encoding="utf-8")
        shell = (push.TOOLS / "theme.sh").read_text(encoding="utf-8")
        for script, text in (("theme.sh", shell), ("theme.ps1", source)):
            with self.subTest(script=script):
                self.assertIn("inputs/*; do", text, "no line removes the logos a theme dropped")
                self.assertIn("unsupported inputs filename", text)
                self.assertIn("md5sum", text, "an unchanged logo is sent again")
        self.assertIn("$(DropInputs '')", source, "-Reset leaves the logos on the TV")
        self.assertIn('$(drop_inputs "")', shell, "--reset leaves the logos on the TV")


if __name__ == "__main__":
    unittest.main()
