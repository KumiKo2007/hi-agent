# 课时学生讲义与教师教案

在教学准备确认当前模块大纲后，选择课时和自己可用的对话模型，创建独立课时生成运行。双稿保存为同一份产物修订：知识点、关键推导和例题各存一份，学生讲义与教师教案均读取这一份内容。教师专属部分包括重难点、教学步骤和时间、讲解提示与常见误区。

`POST /teaching/lessons` 接收 `outlineId`、`expectedOutlineRevisionId`、从1开始的 `lessonNumber`、`modelId`。大纲必须是当前已确认修订；系统固定选材、知识版本、输入大纲修订、课时主题/目标、教学参数和模型连接协议。后续大纲编辑不会改变已创建目标。

`POST /teaching/lessons/{id}/generate` 执行无工具模型调用。只使用真实模型注册表授权的当前用户模型。一次完成后原子保存一个共同修订；重复成功请求返回同一结果。失败保留安全中文原因，半份稿、缺少必要章节、无效引用或时间不匹配不发布。失败重试和崩溃恢复由后续恢复步骤提供，目前可核查配置后新建目标。

`GET /teaching/lessons`、`GET /teaching/lessons/{id}`、`GET /teaching/lessons/{id}/revisions/{revisionId}` 均限制所有者，刷新后可读保存内容。Run与revision固定关联，不伪造会话。

引用采用实际固定选材 sourceId，物理页/Word段落定位由服务器解析，不接受模型编造定位。SOURCE表示模型声明该来源有依据，仍需教师核查文本是否支持；SUPPLEMENT明确为补充或自编；UNSUPPORTED标明证据缺口。没有一般数学正确保证，也没有教师复核完成声明。

数学预览使用[remark-math / rehype-katex](https://github.com/remarkjs/remark-math)与[KaTeX安全选项](https://katex.org/docs/options)。来源和模型输出按不可信文本处理，不允许HTML、外部图片或工具执行；无法解析的数学表达式明确显示错误，教师须修正后使用。此步骤不包含分层练习、答案开关、图形、编辑与Word/PDF导出。

自动化的 `TeachingLessonHttpTest` 通过公开HTTP边界与真实PostgreSQL、真实模型/Provider注册表；仅替换外部模型传输。自编导数样例独立检查平均变化率取极限的定义、`f(x)=x²`在x=1处的增量比`2+h`、导数2与切线`y=2x−1`。这是固定样例的计算与传递验证，不能代替真实教材、真实模型和教师教学质量复核。
