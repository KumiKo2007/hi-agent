#!/usr/bin/env python3
"""检查仓库的 MIT 声明，并保护已识别的第三方许可例外。"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


COPYRIGHT = "Copyright (c) 2026 KumiKo2007"
# 审核过的 MIT 全文及原有 Apache-2.0 全文；换行统一后计算摘要。
LICENSE_DIGEST = "0cb9207ae259f94ba50e9caaa25fb5cfb21eacc3c4fd7722f94b0e630e8c2f92"
APACHE_DIGEST = "c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4"
THIRD_PARTY = {
    "AgentX/mvnw": "7e6e5d26712efd78140f2f63dafe8d17028f6c5c97ac1f746a043110b7a1d9ad",
    "AgentX/mvnw.cmd": "1ea18e87640c404d8602406b639eaa3e33def63f064fd8a8099aa52d173076a6",
    "AgentX/.mvn/wrapper/maven-wrapper.properties": "90da0b8373999aed95eb5626f3bf7afa54aa9268db0bae81b0f0bebf1db0a13c",
}
COMMENT_LINE = re.compile(r"^\s*(?:#|//|/\*|\*|<!--|--|@REM)\s*", re.IGNORECASE)
SPDX = re.compile(r"SPDX-License-Identifier:\s*(.+)", re.IGNORECASE)
OTHER_LICENSE = re.compile(r"\bLicensed\s+(?:to|under)\s+", re.IGNORECASE)


def read_text(root: Path, relative: str, errors: list[str]) -> str:
    path = root / relative
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        errors.append(f"无法读取 {relative}: {exc}")
        return ""


def digest(text: str) -> str:
    return hashlib.sha256(text.replace("\r\n", "\n").encode("utf-8")).hexdigest()


def tracked_files(root: Path, errors: list[str]) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(root), "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        capture_output=True,
        check=False,
    )
    if result.returncode:
        errors.append("无法读取 Git 跟踪文件列表；请在 Git 仓库中运行审计")
        return []
    return [part.decode("utf-8", errors="surrogateescape") for part in result.stdout.split(b"\0") if part]


def check_source_headers(root: Path, files: list[str], errors: list[str]) -> None:
    for relative in files:
        if relative in THIRD_PARTY or relative == "third_party/licenses/Apache-2.0.txt":
            continue
        try:
            lines = (root / relative).read_text(encoding="utf-8").splitlines()[:80]
        except (OSError, UnicodeError):
            continue
        for number, line in enumerate(lines, start=1):
            if not COMMENT_LINE.match(line):
                continue
            marker = SPDX.search(line)
            if marker and marker.group(1).rstrip(" */>-").strip().upper() != "MIT":
                errors.append(f"{relative}:{number} 出现非 MIT 的 SPDX 许可声明")
            elif OTHER_LICENSE.search(line) and "MIT" not in line.upper():
                errors.append(f"{relative}:{number} 出现需核对的其他许可头")


def audit(root: Path) -> list[str]:
    errors: list[str] = []
    files = tracked_files(root, errors)

    license_text = read_text(root, "LICENSE", errors)
    if digest(license_text) != LICENSE_DIGEST or not license_text.startswith("MIT License\n\n" + COPYRIGHT):
        errors.append("根 LICENSE 与已审核的 KumiKo2007 MIT 许可证全文不一致")

    readme = read_text(root, "README.md", errors)
    summary = re.search(r"^\s*-\s*\*\*开源协议\*\*:\s*(.+)$", readme, re.MULTILINE)
    if not summary or summary.group(1).strip() != "MIT":
        errors.append("README 概览中的项目许可不是 MIT")
    license_section = readme.partition("## 📄 License")[2]
    if not license_section or "[MIT License](LICENSE)" not in license_section or COPYRIGHT not in license_section:
        errors.append("README 许可段落缺少 MIT 链接或 KumiKo2007 版权声明")
    if "[第三方许可说明](THIRD_PARTY_LICENSES.md)" not in license_section:
        errors.append("README 未指向第三方许可例外")

    manifest = read_text(root, "THIRD_PARTY_LICENSES.md", errors)
    apache_text = read_text(root, "third_party/licenses/Apache-2.0.txt", errors)
    if digest(apache_text) != APACHE_DIGEST:
        errors.append("第三方 Apache-2.0 许可证全文发生变化或缺失")
    for relative, expected in THIRD_PARTY.items():
        body = read_text(root, relative, errors)
        if digest(body) != expected:
            errors.append(f"第三方文件 {relative} 与保留的原文件不一致，请人工复核许可头")
        if "Licensed to the Apache Software Foundation" not in body or "Apache License, Version 2.0" not in body:
            errors.append(f"第三方文件 {relative} 的 Apache-2.0 许可头缺失")
        if f"`{relative}`" not in manifest:
            errors.append(f"第三方许可清单遗漏 {relative}")
    if "third_party/licenses/Apache-2.0.txt" not in manifest:
        errors.append("第三方许可清单未指向 Apache-2.0 全文")

    package = read_text(root, "agentx-frontend-plus/package.json", errors)
    lock = read_text(root, "agentx-frontend-plus/package-lock.json", errors)
    try:
        if json.loads(package).get("license") != "MIT":
            errors.append("前端 package.json 的项目许可不是 MIT")
        if json.loads(lock)["packages"][""].get("license") != "MIT":
            errors.append("前端 package-lock.json 的根项目许可不是 MIT")
    except (json.JSONDecodeError, KeyError, TypeError):
        errors.append("前端包元数据无法解析")

    pom = read_text(root, "AgentX/pom.xml", errors)
    try:
        project = ET.fromstring(pom)
        namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
        if project.findtext("m:licenses/m:license/m:name", namespaces=namespace) != "MIT License":
            errors.append("后端 pom.xml 的项目许可不是 MIT")
        if project.findtext("m:licenses/m:license/m:url", namespaces=namespace) != "https://opensource.org/license/MIT":
            errors.append("后端 pom.xml 的项目许可链接不是 MIT")
    except ET.ParseError:
        errors.append("后端 pom.xml 无法解析")

    check_source_headers(root, files, errors)
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = audit(args.root.resolve())
    if errors:
        for error in errors:
            print(f"许可审计失败：{error}", file=sys.stderr)
        return 1
    print("许可审计通过：项目源码声明 MIT；保留以下第三方 Apache-2.0 例外：")
    for relative in THIRD_PARTY:
        print(f"- {relative}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
