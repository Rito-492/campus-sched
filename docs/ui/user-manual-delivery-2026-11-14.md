# 用户手册交付核验

编写人：杨畅（17231018）。文档版本v0.2，编写日期2026-11-11，最终核验日期2026-11-14。适用前端0.1.0、main=750763b及本轮维护候选；当前同步了CM PR #21后的行为，未声明正式发布签收。

## 交付内容

- [Word可编辑版](user-manual-v0.2-2026-11-11.docx)
- [PDF阅读版](user-manual.pdf)
- [Markdown维护源文](user-manual.md)

18页、16章，包含封面、目录、使用准备、角色和导航、登录与活动工作台、创建及编辑草稿、资源检查、首次审批与预约、发布报名撤回取消、日历、资格和故障、改期创建与确认、通知历史、账号授权、异常恢复、状态与已知限制、维护与参考资料。使用当前真实按钮名称和业务规则；正文说明已合入的CM改期规则、存量快照限制及QA和发布签收边界，不虚构学校制度或合规认证。

首页按用户要求简化：保留姓名杨畅、学号17231018，删除岗位、日期、版本、基线及文档控制信息；保留标题、阅读说明及截图说明。Markdown正文未变，Word和PDF同步更新；重新检查首页，第2至18页渲染图与原核验版逐字节一致。最新成品哈希及检查见`cover-revision-verification.json`；原`verification.json`和`manifest.json`保留为首页修订前的记录。

## 新主线内容修订

同步750763b后更新第11、14、15、16章，说明所有者不自确认、旧申请撤回重建及版本冲突后的重新预览。Word/PDF保持18页，变化的第13、16、17、18页重新渲染检查，其余14页与已检查的首页修订版图像一致。当前成品与全文核对见`artifacts/test-results/ui-2026-11-14/manual-verification.json`，旧manifest及cover-revision-verification仅是旧版本快照。PDF当前名为`user-manual.pdf`，链接已同步。

## 原始版式核验过程

后续自查修正：第7章的报名按钮名称已改为“报名参加”，Word/PDF同步重导出；第9页重新检查，其余17页与上一版一致。当前成品以`artifacts/test-results/ui-local-review-2026-11-14/manual-resolution-verification.json`及`resolution-manifest.json`为准；下述原始核验及新主线修订的记录保留原时点。

Word通过本机Microsoft Word更新字段和目录并导出PDF。本机缺少LibreOffice，标准render_docx.py初次转换明确失败；随后采用原生Word导出的同一PDF，复用该脚本的rasterize流程生成页面图检查。未把首次转换失败计为通过。

初稿23页存在少量段落跨页后形成大面积留白，以及长截图过小的问题。调整行段距、表格留白及Word图片裁剪后，最终18页；截图只放大真实界面的相关区域，不生成或更改业务数据。已检查全部18页，最后改动的第8、10、12、18页再次检查，其余14页与已检查图像逐字节一致。

| 检查 | 结果 |
|---|---|
| Word和PDF | 可读取，PDF18页，16个正文章节 |
| 目录及导航 | 16个目录链接，PDF65个书签 |
| 全文一致性 | Markdown正文及表格文字均能在PDF中定位，无缺失 |
| 版式 | 无文字截断、乱码、孤立尾段页；表格与放大图示可读 |
| 无障碍结构脚本 | high=0、medium=0、low=0；不将脚本结果称为外部认证 |
| 修订及批注 | 无遗留修订和批注 |
| 敏感信息 | 隔离环境凭据值匹配0命中 |
| 原有文件保护 | T08_用户界面说明文档.docx与HEAD原文件字节一致 |

原始检查：[证据目录](../../artifacts/test-results/ui-manual-2026-11-11/)，含`verification.json`、`a11y.json`及本次`manifest.json`。内部渲染图和构建辅助脚本保留在被忽略的运行目录，不作为用户成品交付；正文、Word和PDF均在docs/ui中单独版本化。

最初手册编写阶段只补充文档；本次主线修订另有109项单测与16项真实业务复验，详见ui-validation-2026-11-14.md。“未提交、推送或创建PR”仅为当时验证完成时的历史状态。用户随后授权提交，当前已推送至[PR #27](https://github.com/Rito-492/campus-sched/pull/27)。项目经理已独立检查18页Word/PDF、正文一致性和版式，并给出[草稿技术评审](https://github.com/Rito-492/campus-sched/pull/27#pullrequestreview-5901778021)；其状态为COMMENTED，尚非正式APPROVED，最终接收及发布签收仍待完成。
