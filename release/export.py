#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
配置库课程导出脚本（字节保真、可复现）

用法：
    python release/export.py [--commit <commit>] [--out <目录>]

默认 commit 为 HEAD，但会先解析为唯一提交 SHA（git rev-parse），归档与全部
当前工件的读取/报告都使用该 SHA，避免同一导出混入不同提交。

产出结构：
    <out>/
    ├── campus-sched-完整快照/     # git archive 整库快照（含全部源码/文档/工程，字节保真）
    ├── 配置项文档/               # 按「编号_版本_名称」重命名的文档（当前版 + 历史版同目录）
    └── export-report.json        # 全量映射 + sha256 + 任务5正式交付物核验 + 哈希交叉核对

安全与核验：
    --out 若指向已存在的非空目录或文件，直接拒绝退出（不递归删除），请另选唯一输出目录。
    任务5（测试）正式交付物按精确文件路径核验，缺项返回退出码 2（不会「假通过」）。
    发布清单逐件核对各散列的文件路径（以候选快照内 release/manifest.json 为准），不匹配返回退出码 3。
    版本与快照绑定：.md 文档导出名以快照实际内容头部「版本：」为准，不随脚本工作树声明版本漂移。
    发布引用采用「冻结快照 + 随包授权登记/发布元数据」：内容字节取自 --commit 冻结快照；
    release/manifest.json 的 commit/baselineStatus 等授权登记以工作树（随包）为权威，冻结快照内
    自述值（打 tag 前）仅作透明对照，不作为发布引用。
退出码：1=致命错误（提交无法解析 / 输出目录冲突 / 清单无法解析）；2=任务5正式交付物缺失；3=发布清单哈希不匹配；4=随包授权登记 commit 与所选发布标签/提交不一致。

二进制文件一律经 `git show <commit>:<path>` 取原始字节写出，不经过文本转码，
从而保证 docx/pdf/drawio/png 与版本库字节一致（sha256 逐件核对）。
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def git(args):
    return subprocess.run(['git', '-C', REPO] + args, capture_output=True)


def git_show(commit, path):
    p = git(['show', '%s:%s' % (commit, path)])
    if p.returncode != 0:
        raise RuntimeError('git show %s:%s 失败: %s' % (commit, path, p.stderr.decode('utf-8', 'replace')))
    return p.stdout


def resolve_commit(ref):
    """把 ref（HEAD/分支/tag/短 SHA）解析为唯一提交 SHA，供所有当前工件统一读取。"""
    p = git(['rev-parse', '--verify', '%s^{commit}' % ref])
    if p.returncode != 0:
        sys.exit('无法解析提交 %s：%s' % (ref, p.stderr.decode('utf-8', 'replace')))
    return p.stdout.decode('utf-8').strip()


def sha256(b):
    return hashlib.sha256(b).hexdigest()


def outname(ci, name, ver, src):
    ext = os.path.splitext(src)[1]
    base = '%s_%s' % (ci, name) if ver is None else '%s_%s_%s' % (ci, ver, name)
    return base + ext


def content_version(data, src):
    """从 .md 头部提取「版本：vX.Y」；无清晰版本头返回 None（沿用 DOCS 声明版本）。

    版本与来源快照绑定：导出文件名以快照实际内容头部为准，而非脚本工作树的声明版本。
    这样导出发布标签（冻结快照，配置管理计划为 v1.7）时不会错标为工作树的 v1.8。
    """
    if not src.endswith('.md'):
        return None
    text = data.decode('utf-8', 'replace')[:3000]
    m = re.search(r'版本\s*[：:]\s*(v\d+(?:\.\d+){1,2})', text)
    return m.group(1) if m else None


# 文档映射： (编号, 中文名, 版本(None=无版本段), 源路径)
DOCS = [
    ('CS-CI-PKG-01', '总开发任务书', 'v3.0', 'taskbook.md'),
    ('CS-CI-DOC-01', '仓库约定', None, 'AGENTS.md'),
    ('CS-CI-DOC-02', '项目名片', None, 'README.md'),
    ('CS-CI-DOC-03', '仓库结构指南', 'v0.2', 'docs/configuration/repo-structure.md'),
    ('CS-CI-DOC-04', '配置管理计划', 'v1.8', 'docs/configuration/configuration-management-plan.md'),
    ('CS-CI-DOC-05', '配置管理计划', 'v1.7', 'docs/configuration/T08_配置管理计划.docx'),
    ('CS-CI-DOC-06', '配置项索引', None, 'docs/configuration/configuration-index.md'),
    ('CS-CI-DOC-30', '初始项目计划', 'v0.9', 'docs/management/initial-project-plan.md'),
    ('CS-CI-DOC-31', '详细项目计划', 'v3.1.4', 'docs/management/detailed-project-plan.md'),
    ('CS-CI-DOC-32', '决策记录', None, 'docs/management/decision-log.md'),
    ('CS-CI-DOC-33', '风险清单', None, 'docs/management/risks.md'),
    ('CS-CI-DOC-34', '集成记录', None, 'docs/management/integration-log.md'),
    ('CS-CI-DOC-35', '软件开发计划', None, 'docs/management/T08_软件开发计划.docx'),
    ('CS-CI-DOC-40', '软件需求规约', 'v1.1', 'docs/requirements/software-requirements.md'),
    ('CS-CI-DOC-41', '用例文档', 'v1.1', 'docs/requirements/use-cases.md'),
    ('CS-CI-DOC-42', '业务规则', 'v1.1', 'docs/requirements/business-rules.md'),
    ('CS-CI-DOC-43', '需求追踪', 'v1.1', 'docs/requirements/traceability.md'),
    ('CS-CI-DOC-44', '补充规约', 'v1.1', 'docs/requirements/supplementary-specification.md'),
    ('CS-CI-DOC-45', '软件需求规约', None, 'docs/requirements/T08_软件需求规约.docx'),
    ('CS-CI-DOC-50', '系统设计', 'v0.2', 'docs/design/system-design.md'),
    ('CS-CI-DOC-51', '数据库设计', None, 'docs/design/database-design.md'),
    ('CS-CI-DOC-52', '迁移登记', None, 'docs/design/migration-register.md'),
    ('CS-CI-DOC-53', '软件设计', None, 'docs/design/software-design-task4.md'),
    ('CS-CI-DOC-53', '软件设计', None, 'docs/design/software-design-task4.docx'),
    ('CS-CI-DOC-53', '软件设计', None, 'docs/design/software-design-task4.pdf'),
    ('CS-CI-DOC-60', '用户手册', 'v0.2', 'docs/ui/user-manual.md'),
    ('CS-CI-DOC-61', '界面说明', 'v1.1', 'docs/ui/interface-description.md'),
    ('CS-CI-DOC-62', 'UI接口接入矩阵', 'v0.1', 'docs/ui/api-matrix.md'),
    ('CS-CI-DOC-63', '用户界面说明文档', None, 'docs/ui/T08_用户界面说明文档.docx'),
    ('CS-CI-DOC-64', '用户手册', 'v0.2', 'docs/ui/user-manual.pdf'),
    ('CS-CI-DOC-20', '测试计划', 'v0.2', 'docs/testing/test-plan.md'),
    ('CS-CI-DOC-21', '测试设计', 'v0.2', 'docs/testing/test-design.md'),
    ('CS-CI-DOC-22', '测试数据设计', 'v0.2', 'docs/testing/test-data-plan.md'),
    ('CS-CI-DOC-23', '测试ID需求清单', None, 'docs/testing/testid-requirements.md'),
    ('CS-CI-DOC-24', '需求覆盖矩阵', 'v3.0', 'docs/testing/coverage-matrix.md'),
    ('CS-CI-DOC-25', '缺陷登记表', 'v0.4', 'docs/testing/defect-register.md'),
    ('CS-CI-DOC-26', 'PR评审清单', None, 'docs/testing/pr-review-checklist.md'),
    ('CS-CI-DOC-27', '软件测试计划', None, 'docs/testing/T08_软件测试计划.docx'),
    ('CS-CI-DOC-28', '软件测试报告', 'v1.0', 'docs/testing/T08_软件测试报告.docx'),
    ('CS-CI-DOC-29', '软件测试报告', 'v1.1', 'docs/testing/test-report.md'),
    ('CS-CI-DOC-70', 'OpenAPI契约', None, 'contracts/openapi.yaml'),
    ('CS-CI-DATA-01', '演示数据集', 'v3.0', 'test-data/demo.json'),
    ('CS-CI-MODEL-01', '首次资源确认活动图', 'v1.0', 'models/activity/首次资源确认活动图.drawio'),
    ('CS-CI-MODEL-01', '首次资源确认活动图', 'v1.0', 'models/activity/首次资源确认活动图.png'),
    ('CS-CI-MODEL-02', '首次活动申请与发布用例图', 'v1.0', 'models/use-case/首次活动申请与发布用例图.drawio'),
    ('CS-CI-MODEL-02', '首次活动申请与发布用例图', 'v1.0', 'models/use-case/首次活动申请与发布用例图.png'),
    ('CS-CI-REL-01', '发布说明', 'v1.0.0', 'release/README.md'),
    ('CS-CI-REL-01', '发布清单', 'v1.0.0', 'release/manifest.json'),
    ('CS-CI-REL-01', '导出清单', 'v1.0.0', 'release/export-manifest.md'),
]

# 公共契约与个人任务书（agents/）
AGENT_COMMON = ['01-开发基线', '02-数据与接口契约', '03-验收与演示数据', '04-协作与集成规程', '05-界面规范']
AGENT_ROLES = {
    '55230223-foundation': '项目经理',
    '22220314-activity': '系统分析员',
    '55230315-scheduling': '设计师',
    '55230316-qa': '测试经理',
    '55231031-change': '配置经理',
    '17231018-ui': '界面设计师',
}

# 历史版本： (编号, 中文名, 版本, commit, 源路径) —— 与当前版同目录并存，_v<版本> 区分
# 版本↔commit 以各文档头部「修订历史」为准（真实 git 核实，不臆造）；未独立入库的工作稿不在此列（见 export-manifest §4.1 说明）。
HIST = [
    # 配置管理计划 Markdown（CS-CI-DOC-04）：v1.0/v1.1/v1.4/v1.5/v1.6/v1.7 均有独立 Git 提交（v1.2 未独立提交，内容并入 v1.4）
    ('CS-CI-DOC-04', '配置管理计划', 'v1.0', 'bfa2cd6', 'docs/configuration/configuration-management-plan.md'),
    ('CS-CI-DOC-04', '配置管理计划', 'v1.1', '5d07897', 'docs/configuration/configuration-management-plan.md'),
    ('CS-CI-DOC-04', '配置管理计划', 'v1.4', '298780f', 'docs/configuration/configuration-management-plan.md'),
    ('CS-CI-DOC-04', '配置管理计划', 'v1.5', '1382623', 'docs/configuration/configuration-management-plan.md'),
    ('CS-CI-DOC-04', '配置管理计划', 'v1.6', '2981c37', 'docs/configuration/configuration-management-plan.md'),
    ('CS-CI-DOC-04', '配置管理计划', 'v1.7', '7726193', 'docs/configuration/configuration-management-plan.md'),
    # 配置管理计划 Word（CS-CI-DOC-05，课程提交版）：v1.0 起独立入库
    ('CS-CI-DOC-05', '配置管理计划', 'v1.0', '5d07897', 'docs/configuration/T08_配置管理计划.docx'),
    ('CS-CI-DOC-05', '配置管理计划', 'v1.4', '298780f', 'docs/configuration/T08_配置管理计划.docx'),
    ('CS-CI-DOC-05', '配置管理计划', 'v1.5', '1382623', 'docs/configuration/T08_配置管理计划.docx'),
    ('CS-CI-DOC-05', '配置管理计划', 'v1.6', '2981c37', 'docs/configuration/T08_配置管理计划.docx'),
    # 总开发任务书（CS-CI-PKG-01）
    ('CS-CI-PKG-01', '总开发任务书', 'v2.0', '9a80fa3', 'taskbook.md'),
    # 详细项目计划（CS-CI-DOC-31）
    ('CS-CI-DOC-31', '详细项目计划', 'v3.0', 'b30c0f2', 'docs/management/detailed-project-plan.md'),
    ('CS-CI-DOC-31', '详细项目计划', 'v3.1', 'a5e0724', 'docs/management/detailed-project-plan.md'),
    ('CS-CI-DOC-31', '详细项目计划', 'v3.1.1', '1fc3dd9', 'docs/management/detailed-project-plan.md'),
    ('CS-CI-DOC-31', '详细项目计划', 'v3.1.2', '2055c63', 'docs/management/detailed-project-plan.md'),
    ('CS-CI-DOC-31', '详细项目计划', 'v3.1.3', '4f0f304', 'docs/management/detailed-project-plan.md'),
    # 测试计划（CS-CI-DOC-20）
    ('CS-CI-DOC-20', '测试计划', 'v0.1', 'b5b1db3', 'docs/testing/test-plan.md'),
    # 系统设计（CS-CI-DOC-50）
    ('CS-CI-DOC-50', '系统设计', 'v0.1', 'f6bd329', 'docs/design/system-design.md'),
    # 用户手册 Markdown（CS-CI-DOC-60）
    ('CS-CI-DOC-60', '用户手册', 'v0.1', 'd94dbaf', 'docs/ui/user-manual.md'),
    # 初始项目计划（CS-CI-DOC-30）
    ('CS-CI-DOC-30', '初始项目计划', 'v0.5', '364f07b', 'docs/management/initial-project-plan.md'),
    ('CS-CI-DOC-30', '初始项目计划', 'v0.6', 'a5e0724', 'docs/management/initial-project-plan.md'),
    ('CS-CI-DOC-30', '初始项目计划', 'v0.7', '1fc3dd9', 'docs/management/initial-project-plan.md'),
    ('CS-CI-DOC-30', '初始项目计划', 'v0.8', '2055c63', 'docs/management/initial-project-plan.md'),
    # 测试设计（CS-CI-DOC-21）
    ('CS-CI-DOC-21', '测试设计', 'v0.1', '4f5a7a1', 'docs/testing/test-design.md'),
    # 测试数据设计（CS-CI-DOC-22）
    ('CS-CI-DOC-22', '测试数据设计', 'v0.1', '1cb2e86', 'docs/testing/test-data-plan.md'),
    # 缺陷登记表（CS-CI-DOC-25）
    ('CS-CI-DOC-25', '缺陷登记表', 'v0.1', '56f9149', 'docs/testing/defect-register.md'),
    ('CS-CI-DOC-25', '缺陷登记表', 'v0.2', 'a48367e', 'docs/testing/defect-register.md'),
    ('CS-CI-DOC-25', '缺陷登记表', 'v0.3', '19ae94b', 'docs/testing/defect-register.md'),
    # 测试报告 Markdown（CS-CI-DOC-29）
    ('CS-CI-DOC-29', '软件测试报告', 'v0.1', 'a48367e', 'docs/testing/test-report.md'),
    ('CS-CI-DOC-29', '软件测试报告', 'v0.4', 'b97cf70', 'docs/testing/test-report.md'),
    ('CS-CI-DOC-29', '软件测试报告', 'v0.6', 'db038b1', 'docs/testing/test-report.md'),
    ('CS-CI-DOC-29', '软件测试报告', 'v0.7', '291b31d', 'docs/testing/test-report.md'),
    ('CS-CI-DOC-29', '软件测试报告', 'v0.8', 'e1e175c', 'docs/testing/test-report.md'),
    ('CS-CI-DOC-29', '软件测试报告', 'v0.9', '19ae94b', 'docs/testing/test-report.md'),
    ('CS-CI-DOC-29', '软件测试报告', 'v1.0', '8f30a49', 'docs/testing/test-report.md'),
]

# 任务5（测试）正式交付物：课程要求的精确文件路径（含 PR #23 软件测试设计，合入 main 后齐备）
TASK5_FORMAL = {
    '测试计划 Word': 'docs/testing/T08_软件测试计划.docx',
    '测试设计 Markdown v1.0': 'docs/testing/test-design-v1.0.md',
    '测试设计 Word': 'docs/testing/T08_任务5_软件测试设计.docx',
    '结构化用例 JSON': 'test-data/test-design-cases-v1.0.json',
    '测试数据设计': 'docs/testing/test-data-plan.md',
    '需求覆盖矩阵': 'docs/testing/coverage-matrix.md',
    '缺陷登记表': 'docs/testing/defect-register.md',
    '测试报告 Word': 'docs/testing/T08_软件测试报告.docx',
    '测试报告 Markdown': 'docs/testing/test-report.md',
}


def build_docs(commit):
    items = []
    for ci, name, ver, src in DOCS:
        items.append(dict(ci=ci, name=name, ver=ver, src=src,
                          out=outname(ci, name, ver, src), commit=commit))
    for bn in AGENT_COMMON:
        src = 'agents/common/%s.md' % bn
        items.append(dict(ci='CS-CI-PKG-02', name='公共契约-' + bn, ver='v3.0', src=src,
                          out=outname('CS-CI-PKG-02', '公共契约-' + bn, 'v3.0', src), commit=commit))
    for d, role in AGENT_ROLES.items():
        src = 'agents/%s/agent.md' % d
        nm = '个人任务书-' + role
        items.append(dict(ci='CS-CI-PKG-03', name=nm, ver='v3.0', src=src,
                          out=outname('CS-CI-PKG-03', nm, 'v3.0', src), commit=commit))
    return items


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--commit', default='HEAD')
    ap.add_argument('--out', default=os.path.join(tempfile.gettempdir(), 'campus-sched-export'))
    args = ap.parse_args()

    # 提交锁定：解析为唯一 SHA，供归档与全部当前工件统一使用
    commit = resolve_commit(args.commit)
    outdir = args.out

    # 输出目录安全：拒绝覆盖已有非空目录/文件
    if os.path.exists(outdir):
        if os.path.isfile(outdir):
            sys.exit('输出路径已存在且为文件，拒绝覆盖：%s（请另选 --out）' % outdir)
        if os.listdir(outdir):
            sys.exit('输出目录非空，拒绝删除：%s（请另选 --out 或先清空）' % outdir)
        os.rmdir(outdir)

    docsdir = os.path.join(outdir, '配置项文档')
    snapdir = os.path.join(outdir, 'campus-sched-完整快照')
    os.makedirs(docsdir, exist_ok=True)
    os.makedirs(snapdir, exist_ok=True)

    # 1) 整库快照（git archive，字节保真）
    p = git(['archive', '--format=tar', commit])
    if p.returncode != 0:
        sys.exit('git archive 失败: ' + p.stderr.decode('utf-8', 'replace'))
    tar_tmp = os.path.join(outdir, '_snapshot.tar')
    with open(tar_tmp, 'wb') as f:
        f.write(p.stdout)
    with tarfile.open(tar_tmp) as t:
        t.extractall(snapdir, filter='data')
    os.remove(tar_tmp)

    # 2) 文档重命名导出（当前版）：.md 文档版本与来源快照实际内容绑定
    items = build_docs(commit)
    report = []
    current_keys = set()
    for it in items:
        data = git_show(it['commit'], it['src'])
        # 版本↔快照绑定：.md 文档按快照实际内容头部「版本：」命名，脚本声明版本仅作对照
        cv = content_version(data, it['src'])
        it['declared_version'] = it['ver']
        if cv is not None:
            it['ver'] = cv
            it['out'] = outname(it['ci'], it['name'], cv, it['src'])
            it['version_match'] = (it['declared_version'] == cv)
            current_keys.add((it['ci'], cv))
        else:
            it['version_match'] = None
            current_keys.add((it['ci'], it['ver']))
        dst = os.path.join(docsdir, it['out'])
        with open(dst, 'wb') as f:
            f.write(data)
        with open(dst, 'rb') as f:
            written = f.read()
        it['sha256'] = sha256(written)
        it['size'] = len(written)
        it['byte_preserved'] = (written == data)
        report.append(it)

    # 3) 历史版本（同目录，编号_版本_名称）；与当前版同号的跳过（避免同名覆盖）
    hist = []
    skipped_hist = []
    for ci, name, ver, hc, src in HIST:
        if (ci, ver) in current_keys:
            skipped_hist.append(dict(ci=ci, name=name, ver=ver, commit=hc, reason='与当前版同号，跳过'))
            continue
        data = git_show(hc, src)
        fn = outname(ci, name, ver, src)
        dst = os.path.join(docsdir, fn)
        with open(dst, 'wb') as f:
            f.write(data)
        with open(dst, 'rb') as f:
            written = f.read()
        hist.append(dict(ci=ci, name=name, ver=ver, src=src, commit=hc, out=fn,
                         sha256=sha256(written), size=len(written),
                         byte_preserved=(written == data)))
    report = report + hist

    # 4) 核对：任务5 正式交付物齐全（精确文件路径，缺项返回非零）+ 字节保真
    all_byte_ok = all(it['byte_preserved'] for it in report)
    task5 = []
    task5_missing = []
    for label, path in TASK5_FORMAL.items():
        p = git(['cat-file', '-e', '%s:%s' % (commit, path)])
        present = (p.returncode == 0)
        task5.append(dict(label=label, path=path, present=present))
        if not present:
            task5_missing.append((label, path))

    # 5) 发布元数据与清单校验（「冻结快照 + 随包授权登记/发布元数据」）：
    #    - 随包元数据（工作树 release/manifest.json）：权威的 commit / baselineStatus，随包提供、不在冻结快照内
    #    - 冻结快照内清单（git show <commit>:release/manifest.json）：逐件核对组件散列（可复现，匹配该快照内容）
    try:
        with open(os.path.join(REPO, 'release', 'manifest.json'), encoding='utf-8') as f:
            release_manifest = json.load(f)
    except Exception as e:
        sys.exit('随包发布元数据 release/manifest.json（工作树）无法解析：%s' % e)
    try:
        frozen_manifest = json.loads(git_show(commit, 'release/manifest.json').decode('utf-8'))
    except Exception as e:
        sys.exit('release/manifest.json 在 %s 无法解析：%s' % (commit, e))
    cross = []
    manifest_mismatch = []
    for comp in frozen_manifest.get('components', []):
        name = comp.get('name')
        sha = comp.get('sha256')
        path = comp.get('file') or comp.get('artifact')
        if not sha:
            cross.append(dict(name=name, file=path, expected=None, actual=None, match=None,
                              note=comp.get('note', '无散列，跳过')))
            continue
        p = git(['cat-file', '-e', '%s:%s' % (commit, path)])
        if p.returncode != 0:
            cross.append(dict(name=name, file=path, expected=sha, actual=None, match=False, note='文件缺失'))
            manifest_mismatch.append((name, path, '文件缺失'))
            continue
        actual = sha256(git_show(commit, path))
        match = (actual == sha)
        cross.append(dict(name=name, file=path, expected=sha, actual=actual, match=match, note=comp.get('note')))
        if not match:
            manifest_mismatch.append((name, path, '清单 %s… / 实际 %s…' % (sha[:12], actual[:12])))

    release_commit = release_manifest.get('commit')
    frozen_declared = frozen_manifest.get('commit')
    release_mismatch = (release_commit != commit)

    # 随包授权登记：把工作树（随包）权威清单作为独立文件写入导出目录（与冻结快照内的历史清单并存）
    release_manifest_file = '随包授权登记-manifest.json'
    with open(os.path.join(outdir, release_manifest_file), 'w', encoding='utf-8') as f:
        json.dump(release_manifest, f, ensure_ascii=False, indent=2)

    result = {
        'commit': commit,
        'out': outdir,
        'release_commit': release_commit,
        'release_commit_matches_candidate': (release_commit == commit),
        'frozen_manifest_commit': frozen_declared,
        'baseline_status': release_manifest.get('baselineStatus'),
        'release_commit_note': release_manifest.get('commitNote'),
        'release_manifest_file': release_manifest_file,
        'doc_count': len(report),
        'historical': hist,
        'skipped_historical': skipped_hist,
        'all_byte_preserved': all_byte_ok,
        'task5_formal_artifacts': task5,
        'manifest_hash_crosscheck': cross,
        'items': report,
    }
    with open(os.path.join(outdir, 'export-report.json'), 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print('commit=%s' % commit)
    print('out=%s' % outdir)
    print('文档件数=%d（含历史版本 %d 件）' % (len(report), len(hist)))
    if skipped_hist:
        print('  已跳过与当前版同号的历史版本 %d 件' % len(skipped_hist))
    print('字节保真逐件通过=%s' % all_byte_ok)
    print('任务5正式交付物：%s' % ('齐全' if not task5_missing else '缺失 %d 件' % len(task5_missing)))
    for t in task5:
        print('  [%s] %s : %s' % ('OK' if t['present'] else '缺', t['label'], t['path']))
    ver_mismatch = [it for it in report if it.get('version_match') is False]
    if ver_mismatch:
        print('版本与快照核对（导出名以快照内容为准，声明版本仅对照）：')
        for it in ver_mismatch:
            print('  [注意] %s：脚本声明 %s / 快照内容 %s' % (it['src'], it['declared_version'], it['ver']))
    print('发布元数据与清单校验：')
    print('  随包授权登记已写入独立文件：%s' % release_manifest_file)
    print('  随包授权登记 commit=%s（%s）——权威发布引用' % (
        release_commit,
        '与所选发布标签/提交一致' if release_commit == commit else '与所选发布标签/提交不一致（退出码 4）'))
    print('  冻结快照内清单自述 commit=%s（打 tag 前历史值，非权威，保留原字节）' % frozen_declared)
    print('  基线状态：%s' % release_manifest.get('baselineStatus'))
    if release_manifest.get('commitNote'):
        print('  发布说明：%s' % release_manifest['commitNote'])
    for v in cross:
        if v['match'] is None:
            print('  [跳过] %s : %s（%s）' % (v['name'], v['file'], v['note']))
        else:
            print('  [%s] %s : %s' % ('匹配' if v['match'] else '不匹配', v['name'], v['file']))
    if release_mismatch:
        print('随包授权登记 commit（%s）与所选发布标签/提交（%s）不一致，不能作为正式发布包（退出码 4）。' % (release_commit, commit))
    if task5_missing:
        print('导出不完整：任务5正式交付物缺失，不能作为最终课程包（退出码 2）。')
    if manifest_mismatch:
        for name, path, why in manifest_mismatch:
            print('  [不匹配] %s : %s（%s）' % (name, path, why))
        print('发布清单哈希校验失败，不能作为最终课程包（退出码 3）。')
    if release_mismatch:
        sys.exit(4)
    if manifest_mismatch:
        sys.exit(3)
    if task5_missing:
        sys.exit(2)


if __name__ == '__main__':
    main()
