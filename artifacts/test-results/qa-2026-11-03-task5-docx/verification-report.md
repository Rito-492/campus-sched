# 测试设计 Word 生成与自校验证据

- 生成时间：2026-11-06T17:32:14+08:00（Asia/Shanghai 时区机器本地时间）
- 生成命令：`python3 qa/scripts/render-test-design.py`
- 数据源：`test-data/test-design-cases-v1.0.json`（SHA-256 `3191d050a5f146047e7f635e133c02365ffac743831f0e2cbe6649884a160d84`）
- 输出：`docs/testing/test-design-v1.0.docx`（SHA-256 `2254e34c95fa2a2f171af5ed09b4bad6a47575bb89136f078f3e9ad8e76121f5`）
- 场景 24 条；用例 138 条（正面 47 / 负面 91）；状态「未执行」138/138
- 追溯：UC 20/20；D-AT 24/24；N-AT 6/6；API 40/40（缺口见文档 0.5 节）

## 自校验明细（全部真实执行）

- [通过] OOXML 包结构：9 个部件
- [通过] XML 良构：[Content_Types].xml
- [通过] XML 良构：_rels/.rels
- [通过] XML 良构：word/document.xml
- [通过] XML 良构：word/_rels/document.xml.rels
- [通过] XML 良构：word/styles.xml
- [通过] XML 良构：word/settings.xml
- [通过] XML 良构：word/footer1.xml
- [通过] XML 良构：docProps/core.xml
- [通过] XML 良构：docProps/app.xml
- [通过] 样式定义 Heading1
- [通过] 样式定义 Heading2
- [通过] 样式定义 Heading3
- [通过] 样式定义 Heading4
- [通过] 样式定义 TableText
- [通过] 正文字体宋体：styles.xml docDefaults
- [通过] 标题字体黑体：styles.xml Heading1~4
- [通过] 标题带大纲级别（导航窗格）：outlineLvl×4
- [通过] 页脚页码域（PAGE/NUMPAGES）
- [通过] 页面 A4 与页边距：11906×16838 twips，左右边距 1247 twips
- [通过] 表格数量与预期一致：实际 190，预期 190
- [通过] 全部表格首行重复表头：tblHeader 190 / 表格 190
- [通过] 表格不超宽（列宽合计=版心宽 9412 twips）：190 张表，最大列宽合计 9412
- [通过] 封面/正文含必备事实：校园活动协同与资源调度系统
- [通过] 封面/正文含必备事实：软件测试设计
- [通过] 封面/正文含必备事实：1.0
- [通过] 封面/正文含必备事实：汤雨润（55230316）
- [通过] 封面/正文含必备事实：2026-11-03
- [通过] 正文完整包含全部场景与用例字段（无文本截断）：缺失 0 项
- [通过] caseID 一致性：全部用例 id 出现在正文：JSON 用例 138 条，正文命中 138 条
- [通过] caseID 一致性：全部场景 id 出现在正文：JSON 场景 24 条，正文命中 24 条
- [通过] caseID 一致性：JSON 内用例 id 无重复：重复 0 条
- [通过] 用例详情标题与用例数一一对应：详情标题 138 个 vs 用例 138 条（每用例 1 张纵向表）
- [通过] 用例状态核对：138/138 条为「未执行」
- [提示] 统计口径：全部数字按 JSON 计算，未生成任何通过率/执行结果。

## 工具与视觉检查限制（如实披露）

- 本机无 LibreOffice/soffice、pandoc、pdftoppm、python-docx、npm docx（已逐一探测，见 tool-probe.txt），无法在本环境做真实 Word 版面渲染截图。
- 本证据为 XML 良构性与所列结构/全文检查：OOXML 部件齐全（含 word/_rels/document.xml.rels）、样式/字体/大纲级别、页码域、表格宽度与重复表头、caseID 一致性、全部用例字段逐字出现在正文（无截断）。
- 未做完整 XSD schema 校验（本机无 defusedxml/lxml，docx skill 的 validate.py 不可运行），也未做视觉/分页验证；结构校验不等于视觉检查。
- 未使用 HTML 渲染冒充 Word 版面；最终分页与表格视觉效果须由项目经理在 Word/WPS 中人工核对（见 docs/testing/task5-submission-v1.0.md §6）。
