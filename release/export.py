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

安全：
    --out 若指向已存在的非空目录或文件，直接拒绝退出（不递归删除），请另选唯一输出目录。
    任务5（测试）正式交付物按精确文件路径核验，缺项返回退出码 2（不会「假通过」）。

二进制文件一律经 `git show <commit>:<path>` 取原始字节写出，不经过文本转码，
从而保证 docx/pdf/drawio/png 与版本库字节一致（sha256 逐件核对）。
"""
import argparse
import hashlib
import json
import os
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


# 文档映射： (编号, 中文名, 版本(None=无版本段), 源路径)
DOCS = [
    ('CS-CI-PKG-01', '总开发任务书', 'v3.0', 'taskbook.md'),
    ('CS-CI-DOC-01', '仓库约定', None, 'AGENTS.md'),
    ('CS-CI-DOC-02', '项目名片', None, 'README.md'),
    ('CS-CI-DOC-03', '仓库结构指南', 'v0.2', 'docs/configuration/repo-structure.md'),
    ('CS-CI-DOC-04', '配置管理计划', 'v1.7', 'docs/configuration/configuration-management-plan.md'),
    ('CS-CI-DOC-05', '配置管理计划', 'v1.7', 'docs/configuration/T08_配置管理计划.docx'),
    ('CS-CI-DOC-06', '配置项索引', None, 'docs/configuration/configuration-index.md'),
    ('CS-CI-DOC-30', '初始项目计划', 'v0.9', 'docs/management/initial-project-plan.md'),
    ('CS-CI-DOC-31', '详细项目计划', 'v3.1.3', 'docs/management/detailed-project-plan.md'),
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
HIST = [
    ('CS-CI-DOC-04', '配置管理计划', 'v1.6', '2981c37', 'docs/configuration/configuration-management-plan.md'),
    ('CS-CI-DOC-05', '配置管理计划', 'v1.6', '2981c37', 'docs/configuration/T08_配置管理计划.docx'),
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

    # 2) 文档重命名导出（当前版）
    items = build_docs(commit)
    report = []
    for it in items:
        data = git_show(it['commit'], it['src'])
        dst = os.path.join(docsdir, it['out'])
        with open(dst, 'wb') as f:
            f.write(data)
        with open(dst, 'rb') as f:
            written = f.read()
        it['sha256'] = sha256(written)
        it['size'] = len(written)
        it['byte_preserved'] = (written == data)
        report.append(it)

    # 3) 历史版本（同目录，编号_版本_名称）
    hist = []
    for ci, name, ver, hc, src in HIST:
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

    # 5) 与发布清单哈希交叉核对（openapi / demo）
    cross = {}
    for label, path, expected in [
        ('openapi-contract', 'contracts/openapi.yaml', '4ee84d2111a489d83b28e4d4e41969c2fb8c38bf476d3beb86d84d3039eef5c7'),
        ('demo-data', 'test-data/demo.json', 'b29450bf4d40473f5d6db8e33b40f9d2d203af07f8f8f6a4b1476fc8936d8130'),
    ]:
        actual = sha256(git_show(commit, path))
        cross[label] = dict(expected=expected, actual=actual, match=(actual == expected))

    result = {
        'commit': commit,
        'out': outdir,
        'doc_count': len(report),
        'historical': hist,
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
    print('字节保真逐件通过=%s' % all_byte_ok)
    print('任务5正式交付物：%s' % ('齐全' if not task5_missing else '缺失 %d 件' % len(task5_missing)))
    for t in task5:
        print('  [%s] %s : %s' % ('OK' if t['present'] else '缺', t['label'], t['path']))
    for k, v in cross.items():
        print('  哈希交叉核对 %s: %s' % (k, '匹配' if v['match'] else '不匹配(%s...)' % v['actual'][:12]))
    if task5_missing:
        print('导出不完整：任务5正式交付物缺失，不能作为最终课程包（退出码 2）。')
        sys.exit(2)


if __name__ == '__main__':
    main()
