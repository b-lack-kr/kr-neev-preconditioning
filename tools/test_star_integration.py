#!/usr/bin/env python3
"""Host simulations of the star module's uninstall safety gates; no device."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class StarUninstallTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="star-uninstall-")
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.bin = self.base / "bin"
        self.bin.mkdir()
        self.work = self.base / "work"
        (self.work / "kit-0.1.0").mkdir(parents=True)
        (self.work / "transaction").mkdir()
        (self.bin / "getprop").write_text("#!/bin/sh\ncase $1 in ro.build.product) echo NEEV.KOR.0000.V039.001.260902;; sys.boot_completed) echo 1;; esac\n")
        (self.bin / "hmgapid").write_text("#!/bin/sh\necho hmgapid 1.0.2\n")
        (self.bin / "sync").write_text("#!/bin/sh\nexit 0\n")
        (self.bin / "md5").write_text("#!/bin/sh\nmd5sum \"$1\"\n")
        (self.bin / "app_process").write_text(r'''#!/bin/sh
shift 2
class=$1; shift
echo "$class $*" >> "$TEST_LOG"
case "$class $1" in
  *StarOptions\ status) echo "${TEST_STATE:-COMMITTED}";;
  *StarOptions\ rollback) exit "${TEST_ROLLBACK:-0}";;
  *StarState*) printf '%s\\n' "${TEST_KEYS:-head=stock}";;
  *Engine\ publish-state) exit "${TEST_PUBLISH:-0}";;
esac
''')
        for f in self.bin.iterdir(): f.chmod(0o755)
        script = (ROOT / "tune-service/star/uninstall.sh").read_text()
        script = script.replace("export PATH=/system/bin:/system/xbin:/data/nemesis/bin", f'export PATH="{self.bin}:$PATH"')
        script = script.replace("WORK=/data/nemesis/neev-star-options", f'WORK="{self.work}"')
        script = script.replace("/system/bin/app_process.hmgapi-stock", str(self.bin / "app_process"))
        self.script = self.base / "uninstall.sh"
        self.script.write_text(script)

    def run_uninstall(self, **env):
        return subprocess.run(["sh", str(self.script)], capture_output=True, text=True,
                              env={**os.environ, "BOOTCLASSPATH": "test", "TEST_LOG": str(self.base / "log"), **env})

    def events(self):
        f = self.base / "log"
        return f.read_text() if f.exists() else ""

    def test_selected_button_is_not_changed_or_rolled_back(self):
        r = self.run_uninstall(TEST_KEYS="head=hmgapi.star.neev-star-precondition.precondition")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("STAR_REMOVE_BLOCKED", r.stdout)
        self.assertNotIn(" rollback", self.events())

    def test_conflicting_map_blocks_removal_and_publish(self):
        r = self.run_uninstall(TEST_ROLLBACK="1")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn(" rollback", self.events())
        self.assertNotIn("publish-state", self.events())

    def test_publish_failure_blocks_removal(self):
        r = self.run_uninstall(TEST_PUBLISH="1")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("STAR_REMOVE_PUBLISH_FAILED", r.stdout)

    def test_valid_removal_rolls_back_then_publishes(self):
        r = self.run_uninstall()
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("STAR_REMOVED_REBOOT_REQUIRED", r.stdout)
        self.assertLess(self.events().index(" rollback"), self.events().index("publish-state"))

    def test_running_installer_lock_blocks_even_before_transaction(self):
        (self.work / "transaction").rmdir()
        (self.work / "lock").mkdir()
        self.assertNotEqual(self.run_uninstall().returncode, 0)
        self.assertEqual(self.events(), "")


class StarInstallTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="star-install-")
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.bin = self.base / "bin"
        self.bin.mkdir()
        self.work = self.base / "work"
        self.kit = self.base / "kit"
        shutil.copytree(ROOT / "tune-service/star/kit", self.kit)
        (self.bin / "id").write_text("#!/bin/sh\necho 'uid=0(root)'\n")
        (self.bin / "getprop").write_text('''#!/bin/sh
case $1 in
ro.build.product) echo NEEV.KOR.0000.V039.001.260902;;
ro.board.platform) echo tcc803x;; ro.build.version.sdk) echo 19;;
sys.boot_completed) echo 1;; esac
''')
        (self.bin / "hmgapid").write_text("#!/bin/sh\necho hmgapid 1.0.2\n")
        (self.bin / "sync").write_text("#!/bin/sh\nexit 0\n")
        (self.bin / "md5").write_text("#!/bin/sh\nmd5sum \"$1\"\n")
        (self.bin / "app_process").write_text(r'''#!/bin/sh
shift 2
class=$1; shift
echo "$class $*" >> "$TEST_LOG"
case "$class $1" in
  *StarOptions\ status) echo "${TEST_STATE:-ABSENT}";;
  *StarOptions\ rollback) exit "${TEST_ROLLBACK:-0}";;
  *StarState*)
    if [ -e "$TEST_STATE_MARKER" ]; then
      [ "${TEST_AFTER_FAIL:-0}" = 0 ] || exit 1
      echo "${TEST_AFTER:-head=stock}"
    else
      : > "$TEST_STATE_MARKER"; echo head=stock
    fi;;
  *Engine\ verify-map)
    if [ "${TEST_INTERRUPT:-0}" = 1 ]; then kill -TERM "$PPID"; fi
    exit "${TEST_VERIFY:-0}";;
  *Engine\ map) echo 'hooks on';;
  *Engine\ publish-state) exit "${TEST_PUBLISH:-0}";;
esac
''')
        for f in self.bin.iterdir(): f.chmod(0o755)
        hook = self.base / "hook"
        hook.mkdir()
        (hook / "enable").touch()
        script = (ROOT / "tune-service/star/install.sh").read_text()
        replacements = {
            "export PATH=/system/bin:/system/xbin:/data/nemesis/bin": f'export PATH="{self.bin}:$PATH"',
            "PACK=/data/nemesis/apps/kr.neev.preconditioning/star/kit": f'PACK="{self.kit}"',
            "WORK=/data/nemesis/neev-star-options": f'WORK="{self.work}"',
            "/data/hmgapi/hook/enable": str(hook / "enable"),
            "/data/hmgapi/hook/disable": str(hook / "disable"),
            "/data/hmgapi/state/journal.json": str(self.base / "journal.json"),
            "/system/bin/app_process.hmgapi-stock": str(self.bin / "app_process"),
        }
        for old, new in replacements.items():
            assert old in script, old
            script = script.replace(old, new)
        self.script = self.base / "install.sh"
        self.script.write_text(script)

    def run_install(self, **env):
        return subprocess.run(["sh", str(self.script)], capture_output=True, text=True, timeout=20,
                              env={**os.environ, "BOOTCLASSPATH": "test", "TEST_LOG": str(self.base / "log"),
                                   "TEST_STATE_MARKER": str(self.base / "state-marker"), **env})

    def events(self):
        f = self.base / "log"
        return f.read_text() if f.exists() else ""

    def test_first_install_commits_after_key_state_check(self):
        r = self.run_install()
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("STAR_CHOICES_INSTALLED_REBOOT_REQUIRED", r.stdout)
        events = self.events()
        self.assertLess(events.rindex("StarState"), events.index(" commit"))

    def test_already_committed_has_no_map_mutations(self):
        r = self.run_install(TEST_STATE="COMMITTED")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("ALREADY_INSTALLED", r.stdout)
        for operation in (" prepare", " publish ", "verify-map", " install ", " rollback"):
            self.assertNotIn(operation, self.events())

    def test_changed_key_or_unreadable_postcheck_rolls_back(self):
        for env in ({"TEST_AFTER": "head=user-changed"}, {"TEST_AFTER_FAIL": "1"}):
            with self.subTest(env=env):
                # Each case uses a fresh transaction fixture.
                if self.work.exists(): shutil.rmtree(self.work)
                (self.base / "state-marker").unlink(missing_ok=True)
                (self.base / "log").unlink(missing_ok=True)
                r = self.run_install(**env)
                self.assertNotEqual(r.returncode, 0, r.stdout + r.stderr)
                self.assertIn(" rollback", self.events())
                self.assertIn("publish-state", self.events())
                self.assertNotIn(" commit", self.events())

    def test_orphaned_lock_prevents_writes(self):
        (self.work / "lock").mkdir(parents=True)
        r = self.run_install()
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("OPERATION_LOCKED", r.stdout)
        self.assertEqual(self.events(), "")

    def test_interrupted_publish_recovers_before_retry(self):
        first = self.run_install(TEST_INTERRUPT="1")
        self.assertNotEqual(first.returncode, 0)
        self.assertIn("verify-map", self.events())
        self.assertNotIn(" commit", self.events())
        self.assertFalse((self.work / "lock").exists())
        (self.base / "log").unlink()
        second = self.run_install(TEST_STATE="PUBLISHED")
        self.assertEqual(second.returncode, 0, second.stdout + second.stderr)
        self.assertLess(self.events().index(" rollback"), self.events().index("verify-map"))

    def test_interrupted_recovery_failure_never_republishes(self):
        r = self.run_install(TEST_STATE="PUBLISHED", TEST_ROLLBACK="1")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("INTERRUPTED_RECOVERY", r.stdout)
        self.assertNotIn("verify-map", self.events())


if __name__ == "__main__":
    unittest.main()
