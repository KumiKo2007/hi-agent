# 第三方许可与项目源码许可

项目自有源码使用根目录的 [MIT License](LICENSE)，版权人 KumiKo2007。该声明不改变下列已识别的、带独立许可头的仓库内第三方文件，以及通过包管理器安装的依赖的原有许可证。

| 仓库内文件 | 来源 | 原有许可 |
| --- | --- | --- |
| `AgentX/mvnw` | Apache Maven Wrapper 启动脚本 | Apache-2.0 |
| `AgentX/mvnw.cmd` | Apache Maven Wrapper 启动脚本 | Apache-2.0 |
| `AgentX/.mvn/wrapper/maven-wrapper.properties` | Apache Maven Wrapper 配置 | Apache-2.0 |

这三个文件的原始许可头保持不变；适用的 [Apache License 2.0 全文](third_party/licenses/Apache-2.0.txt) 单独保存，不作为项目自有源码的根许可证。Maven 依赖声明和 npm 锁文件保留原有依赖信息，依赖仍遵守其上游许可；构建产物若分发，应另行核对实际纳入的依赖及其通知义务。

从仓库根目录执行 `python3 scripts/check_license.py`，可重复检查 MIT 声明、项目源码中的冲突许可头和上述第三方例外。`python3 -m unittest discover -s scripts/tests -p 'test_*.py'` 验证审计器能识别新增的冲突声明。审计范围为仓库跟踪的源码与声明文件，不替代依赖树的逐项许可清单。
