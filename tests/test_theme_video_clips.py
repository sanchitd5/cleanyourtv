"""theme.sh sends wallpaper_video's clips last, once, and takes away the ones a theme no longer has.

A clip (`wallpapers/*.mp4`) is tens of megabytes where a picture is a few hundred kilobytes, and
b64push.py carries a file 24 KB to a shell command, so a push that emptied the collection and sent it
again, as it does for the pictures, would spend minutes on every `manage.py set`. The clips are
therefore outside CLEAR_WALLPAPERS: one the TV already has at the same size is left where it is, one
whose size changed is replaced, and one the theme dropped is removed - all of them on --reset, and for
a theme that has none. A clip's name is judged with the pictures', before anything is sent.

The harness is tests/test_theme_push_ownership.py's: a fake tv.py whose shell is /bin/sh on this
machine and a stub `content`. The runs need bash and skip without it; theme.ps1 is read, not run.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_theme_push_ownership as push  # noqa: E402  (its harness, not its tests)

CLIP = b"\x00\x00\x00\x18ftypmp42" + b"clip" * 5000   # 20 KB: more than one b64push.py chunk on this fake TV
LONG_AGO = 1_000_000_000


class ThemeVideoClipsTest(unittest.TestCase):
    run_theme = push.ThemePushTest.run_theme

    def setUp(self):
        push.ThemePushTest.setUp(self)
        # The harness's client drops what the shell answers; the real tv.py prints it, and the size check reads it.
        (self.tools / "tv.py").write_text(push.FAKE_TV.replace('    Device().shell(" ".join(args))',
                                                               '    sys.stdout.write(Device().shell(" ".join(args)))'))
        self.clips = self.theme / "wallpapers"
        self.clips.mkdir()
        (self.clips / "01-aurora.mp4").write_bytes(CLIP)
        self.on_tv = self.device / "wallpapers" / "01-aurora.mp4"

    def push_theme(self, **env):
        result = self.run_theme(str(self.theme), **env)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def test_a_clip_reaches_the_tv_whole(self):
        self.push_theme()
        self.assertEqual(CLIP, self.on_tv.read_bytes())

    def test_a_clip_the_tv_has_is_not_sent_again(self):
        self.push_theme()
        os.utime(self.on_tv, (LONG_AGO, LONG_AGO))
        result = self.push_theme()
        self.assertIn("wallpapers/01-aurora.mp4 is already on the TV", result.stdout)
        self.assertEqual(LONG_AGO, int(self.on_tv.stat().st_mtime), "the clip was written a second time")
        self.assertEqual(CLIP, self.on_tv.read_bytes())

    def test_a_clip_of_another_size_is_replaced(self):
        self.push_theme()
        (self.clips / "01-aurora.mp4").write_bytes(CLIP + b"more")
        result = self.push_theme()
        self.assertNotIn("is already on the TV", result.stdout)
        self.assertEqual(CLIP + b"more", self.on_tv.read_bytes())

    def test_a_clip_the_theme_dropped_is_removed_and_the_others_stay(self):
        (self.clips / "02-night.mp4").write_bytes(CLIP + b"2")
        (self.clips / "dusk.jpg").write_bytes(b"\xff\xd8\xff")
        self.push_theme()
        (self.clips / "01-aurora.mp4").unlink()
        os.utime(self.device / "wallpapers" / "02-night.mp4", (LONG_AGO, LONG_AGO))
        self.push_theme()
        self.assertFalse(self.on_tv.exists(), "a clip the theme no longer has is still on the TV")
        kept = self.device / "wallpapers" / "02-night.mp4"
        self.assertEqual(CLIP + b"2", kept.read_bytes())
        self.assertEqual(LONG_AGO, int(kept.stat().st_mtime), "the clip that stayed was sent again")
        self.assertTrue((self.device / "wallpapers" / "dusk.jpg").exists())

    def test_a_theme_without_clips_removes_the_last_themes(self):
        self.push_theme()
        (self.clips / "01-aurora.mp4").unlink()
        self.push_theme()
        self.assertFalse(self.on_tv.exists())

    def test_a_reset_removes_the_clips(self):
        self.push_theme()
        result = self.run_theme("--reset")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(self.on_tv.exists())

    def test_a_clip_whose_upload_fails_stops_the_push_at_1(self):
        (self.tools / "b64push.py").write_text("import sys\nsys.exit(2)\n")
        result = self.run_theme(str(self.theme))
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("stopped at wallpapers/01-aurora.mp4", result.stderr)
        self.assertNotIn("is on the TV", result.stdout)

    def test_a_clip_name_the_push_cannot_carry_is_refused_before_anything_is_sent(self):
        (self.clips / "my clip.mp4").write_bytes(CLIP)
        result = self.run_theme(str(self.theme))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("unsupported wallpaper filename: my clip.mp4", result.stderr)
        self.assertIn("Nothing was sent to the TV.", result.stderr)
        self.assertFalse((self.device / "config.json").exists())

    def test_theme_ps1_treats_clips_the_same_way(self):
        source = (push.TOOLS / "theme.ps1").read_text(encoding="utf-8")
        shell = (push.TOOLS / "theme.sh").read_text(encoding="utf-8")
        for script, text in (("theme.sh", shell), ("theme.ps1", source)):
            with self.subTest(script=script):
                self.assertIn("/wallpapers/*.[mM][pP]4; do", text, "no line removes the clips a theme dropped")
                self.assertIn("is already on the TV", text, "a clip the TV has is sent again")
                self.assertIn("TV_ADB_BINARY", text, "adb's own push is not used where there is one")
        self.assertIn("$(DropClips '')", source, "-Reset leaves the clips on the TV")
        self.assertIn('$(drop_clips "")', shell, "--reset leaves the clips on the TV")


if __name__ == "__main__":
    unittest.main()
