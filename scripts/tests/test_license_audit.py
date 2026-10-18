"""从仓库公开的审计命令验证许可声明。"""

import subprocess
import sys
import shutil
import tempfile
import unittest
from pathlib import Path


REPO = Path(__file__).resolve().parents[2]
AUDIT = REPO / "scripts" / "check_license.py"
FIXTURE_FILES = (
    "LICENSE",
    "README.md",
    "THIRD_PARTY_LICENSES.md",
    "third_party/licenses/Apache-2.0.txt",
    "AgentX/mvnw",
    "AgentX/mvnw.cmd",
    "AgentX/.mvn/wrapper/maven-wrapper.properties",
    "AgentX/pom.xml",
    "agentx-frontend-plus/package.json",
    "agentx-frontend-plus/package-lock.json",
)


def run_audit(root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(AUDIT), "--root", str(root)],
        text=True,
        capture_output=True,
        check=False,
    )


def copy_fixture(root: Path) -> None:
    for relative in FIXTURE_FILES:
        destination = root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / relative, destination)
    subprocess.run(["git", "init", "-q", str(root)], check=True)


class LicenseAuditTest(unittest.TestCase):
    def test_repository_license_and_third_party_exceptions_are_consistent(self):
        result = run_audit(REPO)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_conflicting_readme_summary_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_fixture(root)
            readme = root / "README.md"
            readme.write_text(
                readme.read_text(encoding="utf-8").replace("**开源协议**: MIT", "**开源协议**: Apache-2.0"),
                encoding="utf-8",
            )
            result = run_audit(root)
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("README", result.stderr)

    def test_new_source_with_conflicting_license_header_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_fixture(root)
            source = root / "src" / "vendor.go"
            source.parent.mkdir(parents=True)
            source.write_text("// SPDX-License-Identifier: Apache-2.0\npackage vendor\n", encoding="utf-8")
            result = run_audit(root)
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("src/vendor.go:1", result.stderr)

    def test_backend_license_url_must_match_mit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_fixture(root)
            pom = root / "AgentX" / "pom.xml"
            pom.write_text(
                pom.read_text(encoding="utf-8").replace(
                    "https://opensource.org/license/MIT", "https://www.apache.org/licenses/LICENSE-2.0"
                ),
                encoding="utf-8",
            )
            result = run_audit(root)
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("pom.xml", result.stderr)

    def test_source_cannot_add_a_second_license_to_mit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            copy_fixture(root)
            source = root / "src" / "dual.go"
            source.parent.mkdir(parents=True)
            source.write_text("// SPDX-License-Identifier: MIT OR Apache-2.0\npackage dual\n", encoding="utf-8")
            result = run_audit(root)
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("src/dual.go:1", result.stderr)


if __name__ == "__main__":
    unittest.main()
