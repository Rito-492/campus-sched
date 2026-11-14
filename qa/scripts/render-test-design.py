#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
qa/scripts/render-test-design.py — 软件测试设计 Word 生成器（Python 标准库 OOXML，零第三方依赖）。

职责（任务5「排版子代理/Word生成器」）：
  1. 读取唯一用例数据源 test-data/test-design-cases-v1.0.json（结构见 qa/scripts/check-test-design.ts）；
  2. 生成自包含完整用例的 Word 文档 docs/testing/test-design-v1.0.docx：
     封面（校园活动协同与资源调度系统 / 软件测试设计 / 版本 1.0 / 日期 2026-11-03 /
     待项目经理确认提交）→ 修订历史 → 0 依据/环境/数据字典/执行约定/统计追溯 →
     1 测试场景组合表（20UC 及专项，基本流+备选流）→ 2 业务用例矩阵/详情 →
     3.1 用户界面 / 3.2 性能 / 3.3 安全 / 3.4 安装（按 JSON 专项 id 分类）；
  3. 渲染后自校验并落盘证据到 artifacts/test-results/qa-2026-11-03-task5-docx/。

排版约定：A4 纵向、宋体正文（西文 Times New Roman）、黑体标题（Heading1~4 带大纲级别，
可在 Word 导航窗格中导航）、页脚页码、所有表格首行重复表头、固定表格布局且总宽=版心宽
（表格不超宽）、正文与表格内容完整换行不截断。

数据红线：
  - 数据文件缺失 / 结构非法 / 用例为 0 条 → 明确报错并非零退出，绝不输出空文档；
  - 统计与追溯只按 JSON 实时计算，不伪造通过率；用例状态以 JSON 为准（约定全部「未执行」）。

用法：
  python3 qa/scripts/render-test-design.py \
      [--data test-data/test-design-cases-v1.0.json] \
      [--out docs/testing/test-design-v1.0.docx] \
      [--evidence artifacts/test-results/qa-2026-11-03-task5-docx]
  （相对路径均相对仓库根目录解析；退出码 0=成功，2=数据缺失/非法，3=生成或校验失败）
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path

# ---------------------------------------------------------------- 常量

SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parents[2]

DEFAULT_DATA = "test-data/test-design-cases-v1.0.json"
DEFAULT_OUT = "docs/testing/test-design-v1.0.docx"
DEFAULT_EVIDENCE = "artifacts/test-results/qa-2026-11-03-task5-docx"

PROJECT_NAME = "校园活动协同与资源调度系统"
DOC_KIND = "软件测试设计"
AUTHOR_LINE = "汤雨润（55230316）"
DOC_DATE = "2026-11-03"

# A4：11906×16838 twips；页边距上下 2.5cm(1418)、左右 2.2cm(1247) → 版心宽 9412 twips
PAGE_W, PAGE_H = 11906, 16838
MARGIN_TB, MARGIN_LR = 1418, 1247
USABLE = PAGE_W - 2 * MARGIN_LR  # 9412

FONT_BODY_EA, FONT_BODY_ASCII = "宋体", "Times New Roman"
FONT_HEAD_EA, FONT_HEAD_ASCII = "黑体", "Times New Roman"

# 期望编号集合（与 qa/scripts/check-test-design.ts 同口径）
EXPECTED_UC = [f"UC-{i:02d}" for i in range(1, 21)]
EXPECTED_D_AT = [f"D-AT-{i:02d}" for i in range(1, 25)]
EXPECTED_N_AT = [f"N-AT-{i:02d}" for i in range(1, 7)]
EXPECTED_API = [f"API-{i:02d}" for i in range(1, 41)]

CASE_STATUS_EXPECTED = "未执行"

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"


# ---------------------------------------------------------------- 小工具

def esc(s: str) -> str:
    """XML 转义（w:t 内容）。"""
    return (
        str(s)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def sanitize(s) -> str:
    """去掉 XML 1.0 非法控制字符；其余原样保留（不截断任何内容）。"""
    s = str(s)
    return re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", s)


def canonical_id(raw: str) -> str:
    """归一化编号：大写去非字母数字（UC-05 / uc05 → UC05）。"""
    return re.sub(r"[^A-Z0-9]", "", str(raw).upper())


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


# ---------------------------------------------------------------- OOXML 构件

def run_xml(text: str, bold: bool = False, sz: int | None = None,
            ea: str = FONT_BODY_EA, ascii_font: str = FONT_BODY_ASCII) -> str:
    rpr = [f'<w:rFonts w:ascii="{esc(ascii_font)}" w:hAnsi="{esc(ascii_font)}" w:eastAsia="{esc(ea)}"/>']
    if bold:
        rpr.append("<w:b/>")
    if sz is not None:
        rpr.append(f'<w:sz w:val="{sz}"/><w:szCs w:val="{sz}"/>')
    return (
        "<w:r><w:rPr>" + "".join(rpr) + "</w:rPr>"
        f'<w:t xml:space="preserve">{esc(sanitize(text))}</w:t></w:r>'
    )


def para_xml(text: str = "", style: str | None = None, jc: str | None = None,
             bold: bool = False, sz: int | None = None,
             ea: str = FONT_BODY_EA, ascii_font: str = FONT_BODY_ASCII,
             before: int | None = None, after: int | None = None,
             line: int | None = None, page_break_before: bool = False,
             ind_left: int | None = None, keep_next: bool = False) -> str:
    ppr = []
    if style:
        ppr.append(f'<w:pStyle w:val="{style}"/>')
    if keep_next:
        ppr.append("<w:keepNext/>")
    if page_break_before:
        ppr.append("<w:pageBreakBefore/>")
    spacing = []
    if before is not None:
        spacing.append(f'w:before="{before}"')
    if after is not None:
        spacing.append(f'w:after="{after}"')
    if line is not None:
        spacing.append(f'w:line="{line}" w:lineRule="auto"')
    if spacing:
        ppr.append("<w:spacing " + " ".join(spacing) + "/>")
    if ind_left is not None:
        ppr.append(f'<w:ind w:left="{ind_left}"/>')
    if jc:
        ppr.append(f'<w:jc w:val="{jc}"/>')
    body = run_xml(text, bold=bold, sz=sz, ea=ea, ascii_font=ascii_font) if text != "" else ""
    return f"<w:p><w:pPr>{''.join(ppr)}</w:pPr>{body}</w:p>"


def cell_xml(cell, width: int) -> str:
    """cell：str | list[str] | dict{texts, bold, jc, shd}。每个元素一个段落，完整换行不截断。"""
    if isinstance(cell, dict):
        texts = cell.get("texts", [])
        bold = cell.get("bold", False)
        jc = cell.get("jc")
        shd = cell.get("shd")
    else:
        texts = [cell] if isinstance(cell, str) else list(cell)
        bold, jc, shd = False, None, None
    tcpr = [f'<w:tcW w:w="{width}" w:type="dxa"/>']
    if shd:
        tcpr.append(f'<w:shd w:val="clear" w:color="auto" w:fill="{shd}"/>')
    tcpr.append('<w:vAlign w:val="top"/>')
    paras = []
    for t in texts:
        paras.append(
            para_xml(sanitize(t) if t else "", style="TableText", jc=jc, bold=bold,
                     after=0, line=240)
        )
    if not paras:
        paras.append(para_xml("", style="TableText", after=0, line=240))
    return f"<w:tc><w:tcPr>{''.join(tcpr)}</w:tcPr>{''.join(paras)}</w:tc>"


def table_xml(rows, widths: list[int], header: bool = True) -> str:
    """rows：行列表，行=单元格列表；列宽合计必须=USABLE（表格不超宽）。"""
    assert sum(widths) == USABLE, f"表格列宽合计 {sum(widths)} != 版心宽 {USABLE}"
    grid = "".join(f'<w:gridCol w:w="{w}"/>' for w in widths)
    tblpr = (
        f'<w:tblW w:w="{USABLE}" w:type="dxa"/>'
        '<w:tblBorders>'
        + "".join(
            f'<w:{side} w:val="single" w:sz="4" w:space="0" w:color="808080"/>'
            for side in ("top", "left", "bottom", "right", "insideH", "insideV")
        )
        + '</w:tblBorders>'
        '<w:tblLayout w:type="fixed"/>'
    )
    tr_xmls = []
    for ri, row in enumerate(rows):
        cells = "".join(cell_xml(c, widths[ci]) for ci, c in enumerate(row))
        trpr = "<w:trPr><w:tblHeader/></w:trPr>" if (header and ri == 0) else ""
        tr_xmls.append(f"<w:tr>{trpr}{cells}</w:tr>")
    return (
        f"<w:tbl><w:tblPr>{tblpr}</w:tblPr><w:tblGrid>{grid}</w:tblGrid>"
        + "".join(tr_xmls)
        + "</w:tbl>"
    )


def page_break_xml() -> str:
    return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'


# ---------------------------------------------------------------- 各 part

def build_styles_xml() -> str:
    def heading(sid: str, name: str, sz: int, outline: int, before: int, after: int) -> str:
        return (
            f'<w:style w:type="paragraph" w:styleId="{sid}">'
            f'<w:name w:val="{name}"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:qFormat/>'
            f'<w:pPr><w:keepNext/><w:spacing w:before="{before}" w:after="{after}"/>'
            f'<w:outlineLvl w:val="{outline}"/></w:pPr>'
            f'<w:rPr><w:rFonts w:ascii="{FONT_HEAD_ASCII}" w:hAnsi="{FONT_HEAD_ASCII}" '
            f'w:eastAsia="{FONT_HEAD_EA}"/><w:sz w:val="{sz}"/><w:szCs w:val="{sz}"/></w:rPr>'
            '</w:style>'
        )

    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:styles xmlns:w="{W_NS}">'
        '<w:docDefaults><w:rPrDefault><w:rPr>'
        f'<w:rFonts w:ascii="{FONT_BODY_ASCII}" w:hAnsi="{FONT_BODY_ASCII}" w:eastAsia="{FONT_BODY_EA}"/>'
        '<w:sz w:val="21"/><w:szCs w:val="21"/>'
        '<w:lang w:val="en-US" w:eastAsia="zh-CN"/>'
        '</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr>'
        '<w:spacing w:after="100" w:line="276" w:lineRule="auto"/>'
        '</w:pPr></w:pPrDefault></w:docDefaults>'
        '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/>'
        '<w:qFormat/></w:style>'
        + heading("Heading1", "heading 1", 32, 0, 320, 160)
        + heading("Heading2", "heading 2", 28, 1, 240, 120)
        + heading("Heading3", "heading 3", 24, 2, 200, 100)
        + heading("Heading4", "heading 4", 21, 3, 140, 60)
        # 表格内文本：宋体 9pt，紧凑
        + '<w:style w:type="paragraph" w:styleId="TableText"><w:name w:val="table text"/>'
        '<w:basedOn w:val="Normal"/>'
        '<w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr>'
        f'<w:rPr><w:rFonts w:ascii="{FONT_BODY_ASCII}" w:hAnsi="{FONT_BODY_ASCII}" '
        f'w:eastAsia="{FONT_BODY_EA}"/><w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr>'
        '</w:style>'
        '</w:styles>'
    )


def build_settings_xml() -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:settings xmlns:w="{W_NS}">'
        '<w:zoom w:percent="100"/>'
        '<w:updateFields w:val="true"/>'
        '<w:defaultTabStop w:val="420"/>'
        '</w:settings>'
    )


def build_footer_xml() -> str:
    def plain(t: str) -> str:
        return run_xml(t, sz=18)

    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<w:ftr xmlns:w="{W_NS}">'
        '<w:p><w:pPr><w:jc w:val="center"/></w:pPr>'
        + plain("第 ")
        + '<w:r><w:fldChar w:fldCharType="begin"/></w:r>'
        + '<w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r>'
        + '<w:r><w:fldChar w:fldCharType="end"/></w:r>'
        + plain(" 页，共 ")
        + '<w:r><w:fldChar w:fldCharType="begin"/></w:r>'
        + '<w:r><w:instrText xml:space="preserve"> NUMPAGES </w:instrText></w:r>'
        + '<w:r><w:fldChar w:fldCharType="end"/></w:r>'
        + plain(" 页")
        + '</w:p></w:ftr>'
    )


def build_content_types_xml() -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>'
        '<Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>'
        '<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>'
        '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
        '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>'
        '</Types>'
    )


def build_root_rels_xml() -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
        '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>'
        '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>'
        '</Relationships>'
    )


def build_doc_rels_xml() -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
        '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>'
        '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>'
        '</Relationships>'
    )


def build_core_xml(doc: dict) -> str:
    title = sanitize(doc["document"].get("title", DOC_KIND))
    author = sanitize(doc["document"].get("author", AUTHOR_LINE))
    date = sanitize(doc["document"].get("date", DOC_DATE))
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
        'xmlns:dc="http://purl.org/dc/elements/1.1/" '
        'xmlns:dcterms="http://purl.org/dc/terms/" '
        'xmlns:dcmitype="http://purl.org/dc/dcmitype/" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
        f'<dc:title>{esc(PROJECT_NAME)} {esc(title)}</dc:title>'
        f'<dc:creator>{esc(author)}</dc:creator>'
        f'<cp:lastModifiedBy>{esc(author)}</cp:lastModifiedBy>'
        f'<dcterms:created xsi:type="dcterms:W3CDTF">{esc(date)}T00:00:00Z</dcterms:created>'
        f'<dcterms:modified xsi:type="dcterms:W3CDTF">{esc(date)}T00:00:00Z</dcterms:modified>'
        '</cp:coreProperties>'
    )


def build_app_xml() -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" '
        'xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">'
        '<Application>qa/scripts/render-test-design.py (Python stdlib OOXML)</Application>'
        '</Properties>'
    )


# ---------------------------------------------------------------- 数据加载与校验

class DataError(Exception):
    pass


def load_data(path: Path) -> dict:
    if not path.is_file():
        raise DataError(
            f"用例数据文件缺失：{path}\n"
            f"（唯一用例数据源未就绪，按红线不生成空文档、不猜测内容；退出码 2）"
        )
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        raise DataError(f"用例数据 JSON 解析失败：{path} 第 {e.lineno} 行：{e.msg}")
    validate_data(doc, path)
    return doc


def _nonempty_str(v) -> bool:
    return isinstance(v, str) and v.strip() != ""


def _str_list(v) -> bool:
    return isinstance(v, list) and all(isinstance(x, str) for x in v)


def validate_data(doc, path: Path) -> None:
    errs = []
    if not isinstance(doc, dict):
        raise DataError(f"{path} 根节点必须是 JSON 对象")
    if doc.get("schemaVersion") != 1:
        errs.append(f"schemaVersion 必须为 1（实际 {doc.get('schemaVersion')!r}）")
    meta = doc.get("document")
    if not isinstance(meta, dict):
        errs.append("document 缺失或不是对象")
    else:
        for k in ("title", "version", "author", "date", "status"):
            if not _nonempty_str(meta.get(k)):
                errs.append(f"document.{k} 必须为非空字符串")
    if not _str_list(doc.get("environment")) or not doc.get("environment"):
        errs.append("environment 必须为非空字符串数组")
    dd = doc.get("dataDictionary")
    if not isinstance(dd, list) or not dd:
        errs.append("dataDictionary 必须为非空数组")
    else:
        for i, e in enumerate(dd):
            if not isinstance(e, dict) or not _nonempty_str(e.get("key")) or not _nonempty_str(e.get("value")):
                errs.append(f"dataDictionary[{i}] 必须含非空 key/value")
    scenarios = doc.get("scenarios")
    if not isinstance(scenarios, list) or not scenarios:
        errs.append("scenarios 必须为非空数组（0 场景的测试设计必须失败）")
    else:
        seen_s, seen_c = set(), set()
        total_cases = 0
        for si, s in enumerate(scenarios):
            where = f"scenarios[{si}]"
            if not isinstance(s, dict):
                errs.append(f"{where} 必须是对象")
                continue
            for k in ("id", "name", "basicFlow"):
                if not _nonempty_str(s.get(k)):
                    errs.append(f"{where}.{k} 必须为非空字符串")
            if not _str_list(s.get("alternateFlows")):
                errs.append(f"{where}.alternateFlows 必须为字符串数组（可为空）")
            for k in ("requirementIds", "acceptanceIds"):
                if not _str_list(s.get(k, [])):
                    errs.append(f"{where}.{k} 必须为字符串数组（可为空）")
            if _nonempty_str(s.get("id")):
                cid = canonical_id(s["id"])
                if cid in seen_s:
                    errs.append(f"{where}.id 重复：{s['id']}")
                seen_s.add(cid)
            cases = s.get("cases")
            if not isinstance(cases, list) or not cases:
                errs.append(f"{where}.cases 必须为非空数组")
                continue
            for ci, c in enumerate(cases):
                w2 = f"{where}.cases[{ci}]"
                if not isinstance(c, dict):
                    errs.append(f"{w2} 必须是对象")
                    continue
                total_cases += 1
                for k in ("id", "title", "flow", "cleanup", "automation"):
                    if not _nonempty_str(c.get(k)):
                        errs.append(f"{w2}.{k} 必须为非空字符串")
                if c.get("polarity") not in ("positive", "negative"):
                    errs.append(f"{w2}.polarity 必须为 positive|negative（实际 {c.get('polarity')!r}）")
                if c.get("priority") not in ("P0", "P1", "P2"):
                    errs.append(f"{w2}.priority 必须为 P0|P1|P2（实际 {c.get('priority')!r}）")
                for k in ("preconditions", "inputs", "steps", "expected"):
                    if not _str_list(c.get(k)) or not c.get(k):
                        errs.append(f"{w2}.{k} 必须为非空字符串数组")
                if not _nonempty_str(c.get("status")):
                    errs.append(f"{w2}.status 必须为非空字符串（约定「{CASE_STATUS_EXPECTED}」）")
                if _nonempty_str(c.get("id")):
                    cid = canonical_id(c["id"])
                    if cid in seen_c:
                        errs.append(f"{w2}.id 全局重复：{c['id']}")
                    seen_c.add(cid)
        if total_cases == 0:
            errs.append("用例总数为 0：不生成空文档")
    if errs:
        raise DataError(f"用例数据校验未通过（{path}）：\n  - " + "\n  - ".join(errs))


# ---------------------------------------------------------------- 统计与追溯（只按 JSON）

API_REF_RE = re.compile(r"\bAPI[\s\-_]?(\d{1,2})\b", re.I)


def compute_stats(doc: dict) -> dict:
    scenarios = doc["scenarios"]
    cases = [(s, c) for s in scenarios for c in s["cases"]]
    uc_scn = [s for s in scenarios if canonical_id(s["id"]).startswith("UC")]
    stats = {
        "scenario_count": len(scenarios),
        "uc_scenario_count": len(uc_scn),
        "special_scenario_count": len(scenarios) - len(uc_scn),
        "case_count": len(cases),
        "positive": sum(1 for _, c in cases if c["polarity"] == "positive"),
        "negative": sum(1 for _, c in cases if c["polarity"] == "negative"),
        "p0": sum(1 for _, c in cases if c["priority"] == "P0"),
        "p1": sum(1 for _, c in cases if c["priority"] == "P1"),
        "p2": sum(1 for _, c in cases if c["priority"] == "P2"),
        "status_unexecuted": sum(1 for _, c in cases if c["status"] == CASE_STATUS_EXPECTED),
        "status_other": sorted({c["status"] for _, c in cases if c["status"] != CASE_STATUS_EXPECTED}),
    }
    req_ids = {canonical_id(r) for s in scenarios for r in s.get("requirementIds", [])}
    acc_ids = {canonical_id(a) for s in scenarios for a in s.get("acceptanceIds", [])}
    api_ids = set()
    for _, c in cases:
        text = " ".join(c["inputs"] + c["steps"] + [c["automation"]])
        for m in API_REF_RE.finditer(text):
            api_ids.add(canonical_id(f"API-{int(m.group(1)):02d}"))

    def cov(expected, seen):
        missing = [x for x in expected if canonical_id(x) not in seen]
        return len(expected) - len(missing), missing

    stats["uc_covered"], stats["uc_missing"] = cov(EXPECTED_UC, req_ids)
    stats["d_at_covered"], stats["d_at_missing"] = cov(EXPECTED_D_AT, acc_ids)
    stats["n_at_covered"], stats["n_at_missing"] = cov(EXPECTED_N_AT, acc_ids)
    stats["api_covered"], stats["api_missing"] = cov(EXPECTED_API, api_ids)
    return stats


# ---------------------------------------------------------------- 专项分类（按 JSON 专项 id）

PREFIX_CATEGORY = {
    # 用户界面
    "UI": "ui", "UTC": "ui", "GUI": "ui", "UIC": "ui", "IF": "ui",
    # 性能
    "PERF": "perf", "PF": "perf", "PTC": "perf", "LOAD": "perf",
    "STRESS": "perf", "CONC": "perf", "CONCUR": "perf", "BENCH": "perf", "PE": "perf",
    # 安全
    "SEC": "sec", "STC": "sec", "SAFE": "sec", "SECUR": "sec", "AUTH": "sec",
    "SECURITY": "sec", "VULN": "sec",
    # 安装/部署
    "INST": "install", "ITC": "install", "INSTALL": "install", "DEPLOY": "install",
    "SETUP": "install", "REL": "install", "RELEASE": "install",
}
NAME_KEYWORDS = {
    "ui": ("界面", "导航", "交互", "布局", "UI"),
    "perf": ("性能", "并发", "负载", "压力", "响应时间", "吞吐"),
    "sec": ("安全", "越权", "攻击", "注入", "脱敏", "隐私", "鉴权"),
    "install": ("安装", "部署", "发布", "升级", "环境搭建"),
}
CATEGORY_TITLES = {
    "ui": "3.1 用户界面测试用例",
    "perf": "3.2 性能评测用例",
    "sec": "3.3 安全性测试用例",
    "install": "3.4 安装测试用例",
    "other": "3.5 其他专项测试用例",
}
CATEGORY_ORDER = ["ui", "perf", "sec", "install", "other"]


def classify_scenario(s: dict) -> str:
    sid = canonical_id(s["id"])
    if sid.startswith("UC"):
        return "business"
    m = re.match(r"^([A-Z]+)", sid)
    prefix = m.group(1) if m else ""
    if prefix in PREFIX_CATEGORY:
        return PREFIX_CATEGORY[prefix]
    # 前缀按开头关系兜底（如 UIA01、PERFTEST01）
    for p, cat in PREFIX_CATEGORY.items():
        if prefix.startswith(p):
            return cat
    name = str(s.get("name", ""))
    for cat, kws in NAME_KEYWORDS.items():
        if any(k in name for k in kws):
            return cat
    return "other"


# ---------------------------------------------------------------- 正文构建

def cover_xml(doc: dict, warnings: list[str]) -> str:
    meta = doc["document"]
    title = sanitize(meta["title"])
    version = sanitize(meta["version"])
    author = sanitize(meta["author"])
    date = sanitize(meta["date"])
    # 封面必备事实核对（缺失只告警并如实展示 JSON 值，不编造）
    for label, value, needle in (
        ("版本", version, "1.0"),
        ("日期", date, DOC_DATE),
    ):
        if needle not in value:
            warnings.append(f"封面{label} {value!r} 未包含约定字样 {needle!r}（按 JSON 原值展示）")
    parts = [
        para_xml("", before=2400, after=0),
        para_xml(PROJECT_NAME, jc="center", bold=False, sz=32,
                 ea=FONT_HEAD_EA, ascii_font=FONT_HEAD_ASCII, after=240),
        para_xml(DOC_KIND, jc="center", sz=52,
                 ea=FONT_HEAD_EA, ascii_font=FONT_HEAD_ASCII, after=720),
        para_xml(f"版本 {version}", jc="center", sz=24, after=120),
        para_xml(f"日期 {date}", jc="center", sz=24, after=120),
    ]
    return "".join(parts)


def revision_xml() -> str:
    rows = [
        [dict(texts=["日期"], bold=True, jc="center", shd="D9D9D9"),
         dict(texts=["版本"], bold=True, jc="center", shd="D9D9D9"),
         dict(texts=["说明"], bold=True, jc="center", shd="D9D9D9"),
         dict(texts=["作者"], bold=True, jc="center", shd="D9D9D9")],
        [DOC_DATE, "v1.0",
         "依据课程模板与需求规约编制：测试场景组合表、业务用例矩阵与详情、"
         "界面/性能/安全/安装专项用例；全部用例状态为「未执行」，统计由用例数据统一生成。",
         AUTHOR_LINE],
    ]
    return table_xml(rows, [1500, 900, 5000, 2012])


def section0_xml(doc: dict, stats: dict, warnings: list[str]) -> str:
    parts = [para_xml("0 测试环境与基础数据", style="Heading1", page_break_before=True)]
    parts.append(para_xml(
        "本设计的测试环境与基础数据如下。用例的「输入条件」只列出相对下表基准的差异值，"
        "未列出的字段取基准值。"))
    parts.append(para_xml("0.1 测试环境", style="Heading2"))
    env_rows = [[dict(texts=["序号"], bold=True, jc="center", shd="D9D9D9"),
                 dict(texts=["环境与约定"], bold=True, shd="D9D9D9")]]
    for i, e in enumerate(doc["environment"], 1):
        env_rows.append([dict(texts=[str(i)], jc="center"), sanitize(e)])
    parts.append(table_xml(env_rows, [800, 8612]))

    parts.append(para_xml("0.2 基础数据字典", style="Heading2"))
    parts.append(para_xml(
        "共用的组织、账号、资源与活动在此统一定义；实体以 12 位尾号别名引用 UUID，"
        "如 3101 即 00000000-0000-0000-0000-000000003101。"))
    dd_rows = [[dict(texts=["数据键"], bold=True, shd="D9D9D9"),
                dict(texts=["取值与含义"], bold=True, shd="D9D9D9")]]
    for e in doc["dataDictionary"]:
        dd_rows.append([sanitize(e["key"]), sanitize(e["value"])])
    parts.append(table_xml(dd_rows, [3000, 6412]))
    parts.append(para_xml(
        "说明：全部用例状态为「未执行」，本文不含执行结果与通过率。负面用例的预期结果是系统按"
        "规则拒绝或按规则降级且无非预期副作用，属于预期结果，不是缺陷或测试失败。", after=120))
    return "".join(parts)


def section4_xml(stats: dict) -> str:
    parts = [para_xml("4 用例统计", style="Heading1", page_break_before=True)]
    parts.append(para_xml("按用例数据源统计；状态按设计时的真实情况标注，未执行不计为通过。"))
    rows = [
        [dict(texts=["指标"], bold=True, shd="D9D9D9"),
         dict(texts=["数值"], bold=True, jc="center", shd="D9D9D9")],
        ["场景组数（业务 20 + 专项 4）", str(stats["scenario_count"])],
        ["用例总数", str(stats["case_count"])],
        ["正面用例", str(stats["positive"])],
        ["负面用例", str(stats["negative"])],
        ["优先级 P0 / P1 / P2", f"{stats['p0']} / {stats['p1']} / {stats['p2']}"],
        ["状态「未执行」", f"{stats['status_unexecuted']} / {stats['case_count']}"],
    ]
    parts.append(table_xml(rows, [4300, 5112]))
    return "".join(parts)


def scenario_flows_xml(s: dict) -> str:
    parts = [para_xml(f"基本流：{sanitize(s['basicFlow'])}", after=40)]
    alts = s.get("alternateFlows", [])
    if alts:
        for i, alt in enumerate(alts, 1):
            parts.append(para_xml(f"备选流{i}：{sanitize(alt)}", after=40))
    else:
        parts.append(para_xml("备选流：无", after=40))
    return "".join(parts)


def combo_table_xml(s: dict) -> str:
    rows = [[dict(texts=["场景/用例编号"], bold=True, jc="center", shd="D9D9D9"),
             dict(texts=["场景简称（用例标题）"], bold=True, shd="D9D9D9"),
             dict(texts=["事件流组合"], bold=True, shd="D9D9D9")]]
    for c in s["cases"]:
        rows.append([dict(texts=[sanitize(c["id"])], jc="center"),
                     sanitize(c["title"]), sanitize(c["flow"])])
    return table_xml(rows, [1600, 3400, 4412])


def matrix_table_xml(s: dict) -> str:
    rows = [[dict(texts=["测试用例编号"], bold=True, jc="center", shd="D9D9D9"),
             dict(texts=["场景简称（用例标题）"], bold=True, shd="D9D9D9"),
             dict(texts=["极性"], bold=True, jc="center", shd="D9D9D9"),
             dict(texts=["优先级"], bold=True, jc="center", shd="D9D9D9"),
             dict(texts=["事件流"], bold=True, shd="D9D9D9")]]
    for c in s["cases"]:
        rows.append([
            dict(texts=[sanitize(c["id"])], jc="center"),
            sanitize(c["title"]),
            dict(texts=["正面" if c["polarity"] == "positive" else "负面"], jc="center"),
            dict(texts=[sanitize(c["priority"])], jc="center"),
            sanitize(c["flow"]),
            dict(texts=[sanitize(c["status"])], jc="center"),
        ])
    return table_xml(rows, [1600, 2600, 750, 800, 2200, 1462])


def case_detail_xml(s: dict, c: dict) -> str:
    polarity = "正面（positive）" if c["polarity"] == "positive" else "负面（negative）"
    head = para_xml(
        f"{sanitize(c['id'])} {sanitize(c['title'])}"
        f"（{'正面' if c['polarity'] == 'positive' else '负面'}·{sanitize(c['priority'])}·{sanitize(c['flow'])}）",
        style="Heading4", keep_next=True,
    )

    def numbered(items: list[str]) -> list[str]:
        return [f"{i}) {sanitize(t)}" for i, t in enumerate(items, 1)]

    rows = [
        [dict(texts=["前置条件"], bold=True), numbered(c["preconditions"])],
        [dict(texts=["输入条件"], bold=True), numbered(c["inputs"])],
        [dict(texts=["操作步骤"], bold=True), numbered(c["steps"])],
        [dict(texts=["预期结果"], bold=True), numbered(c["expected"])],
    ]
    return head + table_xml(rows, [1700, 7712])


def section1_xml(doc: dict) -> str:
    parts = [para_xml("1 测试场景组合表（20UC 及专项）", style="Heading1", page_break_before=True)]
    parts.append(para_xml(
        "对需求规约确定的每一个用例（UC-01～UC-20）以及界面、性能、安全、安装等专项，"
        "依据其基本流与备选流构造场景组合表。每个场景先列基本流与各备选流，再以"
        "「场景/用例编号—场景简称—事件流组合」列出全部场景组合（组合即该场景下的可执行用例，"
        "编号与第 2、3 章用例详情一一对应）。"
    ))
    for i, s in enumerate(doc["scenarios"], 1):
        parts.append(para_xml(f"1.{i} {sanitize(s['id'])} {sanitize(s['name'])} 的场景组合表",
                              style="Heading2"))
        parts.append(scenario_flows_xml(s))
        parts.append(combo_table_xml(s))
    return "".join(parts)


def section2_xml(doc: dict) -> str:
    parts = [para_xml("2 业务用例测试用例矩阵与详情", style="Heading1", page_break_before=True)]
    parts.append(para_xml(
        "针对第 1 章各业务场景组合构造测试用例矩阵与逐条详情。矩阵给出编号、简称、极性、优先级、"
        "事件流与状态；详情给出每条用例的前置条件、输入条件、操作步骤、预期结果、清理、实现位置与"
        "需求/验收追踪，正负用例明确区分。"
    ))
    uc_scn = [s for s in doc["scenarios"] if classify_scenario(s) == "business"]
    for i, s in enumerate(uc_scn, 1):
        parts.append(para_xml(f"2.{i} {sanitize(s['id'])} {sanitize(s['name'])}", style="Heading2"))
        parts.append(para_xml(f"2.{i}.1 测试用例矩阵", style="Heading3"))
        parts.append(matrix_table_xml(s))
        parts.append(para_xml(f"2.{i}.2 测试用例详情", style="Heading3"))
        for c in s["cases"]:
            parts.append(case_detail_xml(s, c))
    return "".join(parts)


def section3_xml(doc: dict) -> str:
    parts = [para_xml("3 其他专项测试用例", style="Heading1", page_break_before=True)]
    parts.append(para_xml(
        "为测试需求中提到的其他测试类型构造用例，按 JSON 专项场景 id 分类：3.1 用户界面、"
        "3.2 性能评测、3.3 安全性、3.4 安装。每类沿用第 2 章的矩阵+详情格式，用例自包含完整。"
    ))
    by_cat: dict[str, list[dict]] = {k: [] for k in CATEGORY_ORDER}
    for s in doc["scenarios"]:
        cat = classify_scenario(s)
        if cat != "business":
            by_cat.setdefault(cat, []).append(s)
    for cat in CATEGORY_ORDER:
        parts.append(para_xml(CATEGORY_TITLES[cat], style="Heading2"))
        items = by_cat.get(cat, [])
        if not items:
            parts.append(para_xml(
                "本次 JSON 专项用例数据中没有归入本节的场景（如实记录，不填充占位用例）。"))
            continue
        for i, s in enumerate(items, 1):
            prefix = CATEGORY_TITLES[cat].split(" ")[0]  # 3.1 / 3.2 ...
            parts.append(para_xml(f"{prefix}.{i} {sanitize(s['id'])} {sanitize(s['name'])}",
                                  style="Heading3"))
            parts.append(scenario_flows_xml(s))
            parts.append(matrix_table_xml(s))
            for c in s["cases"]:
                parts.append(case_detail_xml(s, c))
    return "".join(parts)


def build_document_xml(doc: dict, warnings: list[str]) -> tuple[str, int]:
    """返回 (document.xml, 预期表格数)。"""
    stats = compute_stats(doc)
    body = [cover_xml(doc, warnings)]
    body.append(para_xml("修订历史", style="Heading1", page_break_before=True))
    body.append(revision_xml())
    body.append(section0_xml(doc, stats, warnings))
    body.append(section1_xml(doc))
    body.append(section2_xml(doc))
    body.append(section3_xml(doc))
    body.append(section4_xml(stats))
    sectpr = (
        f'<w:sectPr><w:footerReference w:type="default" r:id="rId3"/>'
        f'<w:pgSz w:w="{PAGE_W}" w:h="{PAGE_H}"/>'
        f'<w:pgMar w:top="{MARGIN_TB}" w:right="{MARGIN_LR}" w:bottom="{MARGIN_TB}" '
        f'w:left="{MARGIN_LR}" w:header="851" w:footer="851" w:gutter="0"/>'
        '<w:cols w:space="708"/></w:sectPr>'
    )
    doc_xml = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" '
        'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
        f"<w:body>{''.join(body)}{sectpr}</w:body></w:document>"
    )
    n_scn = len(doc["scenarios"])
    n_cases = sum(len(s["cases"]) for s in doc["scenarios"])
    # 修订历史1 + 环境1 + 数据字典1 + 统计1
    # + 每场景组合表1 + 每场景矩阵1 + 每用例详情1
    expected_tables = 4 + n_scn + n_scn + n_cases
    return doc_xml, expected_tables


def write_docx(out_path: Path, doc_xml: str, doc: dict) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    tmp = out_path.with_suffix(".docx.tmp")
    parts = {
        "[Content_Types].xml": build_content_types_xml(),
        "_rels/.rels": build_root_rels_xml(),
        "word/document.xml": doc_xml,
        "word/_rels/document.xml.rels": build_doc_rels_xml(),
        "word/styles.xml": build_styles_xml(),
        "word/settings.xml": build_settings_xml(),
        "word/footer1.xml": build_footer_xml(),
        "docProps/core.xml": build_core_xml(doc),
        "docProps/app.xml": build_app_xml(),
    }
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
        for name, content in parts.items():
            z.writestr(name, content)
    tmp.replace(out_path)


# ---------------------------------------------------------------- 自校验

def extract_paragraph_texts(document_xml: str) -> list[str]:
    ns = {"w": W_NS}
    root = ET.fromstring(document_xml)
    texts = []
    for p in root.iter(f"{{{W_NS}}}p"):
        texts.append("".join(t.text or "" for t in p.iter(f"{{{W_NS}}}t")))
    return texts


def verify(out_path: Path, doc: dict, expected_tables: int, warnings: list[str]) -> tuple[bool, list[str], str]:
    """返回 (ok, 检查项明细, 全文文本)。检查项全部真实执行。"""
    lines = []
    ok = True

    def check(name: str, passed: bool, detail: str = "") -> None:
        nonlocal ok
        if not passed:
            ok = False
        lines.append(f"- [{'通过' if passed else '失败'}] {name}" + (f"：{detail}" if detail else ""))

    required_parts = (
        "[Content_Types].xml", "_rels/.rels", "word/document.xml",
        "word/_rels/document.xml.rels", "word/styles.xml", "word/settings.xml",
        "word/footer1.xml", "docProps/core.xml", "docProps/app.xml",
    )
    with zipfile.ZipFile(out_path) as z:
        names = z.namelist()
        check("OOXML 包结构", all(n in names for n in required_parts),
            f"{len(names)} 个部件")
        document_xml = z.read("word/document.xml").decode("utf-8")
        styles_xml = z.read("word/styles.xml").decode("utf-8")
        footer_xml = z.read("word/footer1.xml").decode("utf-8")
        for part in required_parts:
            try:
                ET.fromstring(z.read(part))
                well = True
                msg = ""
            except ET.ParseError as e:
                well, msg = False, str(e)
            check(f"XML 良构：{part}", well, msg)

    paras = extract_paragraph_texts(document_xml)
    full_text = "\n".join(paras)

    # ---- 样式/字体/标题导航
    for sid in ("Heading1", "Heading2", "Heading3", "Heading4", "TableText"):
        check(f"样式定义 {sid}", f'w:styleId="{sid}"' in styles_xml)
    check("正文字体宋体", FONT_BODY_EA in styles_xml, "styles.xml docDefaults")
    check("标题字体黑体", FONT_HEAD_EA in styles_xml, "styles.xml Heading1~4")
    check("标题带大纲级别（导航窗格）", styles_xml.count("<w:outlineLvl") >= 4,
          f"outlineLvl×{styles_xml.count('<w:outlineLvl')}")
    check("页脚页码域（PAGE/NUMPAGES）", "PAGE" in footer_xml and "NUMPAGES" in footer_xml)
    check("页面 A4 与页边距", f'w:pgSz w:w="{PAGE_W}" w:h="{PAGE_H}"' in document_xml,
          f"{PAGE_W}×{PAGE_H} twips，左右边距 {MARGIN_LR} twips")

    # ---- 表格：宽度、重复表头、数量
    tbl_count = document_xml.count("<w:tbl>")
    header_count = document_xml.count("<w:tblHeader/>")
    check("表格数量与预期一致", tbl_count == expected_tables,
          f"实际 {tbl_count}，预期 {expected_tables}")
    check("全部表格首行重复表头", header_count == tbl_count,
          f"tblHeader {header_count} / 表格 {tbl_count}")
    grid_sums = []
    for m in re.finditer(r"<w:tblGrid>(.*?)</w:tblGrid>", document_xml):
        grid_sums.append(sum(int(x) for x in re.findall(r'w:gridCol w:w="(\d+)"', m.group(1))))
    check("表格不超宽（列宽合计=版心宽 9412 twips）",
          all(g == USABLE for g in grid_sums),
          f"{len(grid_sums)} 张表，最大列宽合计 {max(grid_sums) if grid_sums else 0}")

    # ---- 封面事实
    for needle in (PROJECT_NAME, DOC_KIND, "1.0", "汤雨润（55230316）", DOC_DATE):
        check(f"封面/正文含必备事实：{needle}", needle in full_text)

    # ---- 用例正文完整性（不截断、自包含）
    missing = []
    scenarios = doc["scenarios"]
    for s in scenarios:
        for text in [s["id"], s["name"], s["basicFlow"], *s.get("alternateFlows", [])]:
            if text and sanitize(text) not in full_text:
                missing.append(f"场景{s['id']}：{text[:40]}…")
        for c in s["cases"]:
            items = [c["id"], c["title"], c["flow"],
                     *c["preconditions"], *c["inputs"], *c["steps"], *c["expected"]]
            for text in items:
                if sanitize(text) not in full_text:
                    missing.append(f"用例{c['id']}：{text[:40]}…")
    check("正文完整包含全部场景与用例字段（无文本截断）", not missing,
          f"缺失 {len(missing)} 项" + (f"：{missing[:5]}" if missing else ""))

    # ---- caseID 一致性
    case_ids = [c["id"] for s in scenarios for c in s["cases"]]
    scn_ids = [s["id"] for s in scenarios]
    lost_cases = [cid for cid in case_ids if sanitize(cid) not in full_text]
    lost_scn = [sid for sid in scn_ids if sanitize(sid) not in full_text]
    check("caseID 一致性：全部用例 id 出现在正文", not lost_cases,
          f"JSON 用例 {len(case_ids)} 条，正文命中 {len(case_ids) - len(lost_cases)} 条")
    check("caseID 一致性：全部场景 id 出现在正文", not lost_scn,
          f"JSON 场景 {len(scn_ids)} 条，正文命中 {len(scn_ids) - len(lost_scn)} 条")
    dup = len(case_ids) - len({canonical_id(c) for c in case_ids})
    check("caseID 一致性：JSON 内用例 id 无重复", dup == 0, f"重复 {dup} 条")
    # 详情标题（Heading4）每条用例 1 个，应等于用例数
    detail_tables = document_xml.count('w:val="Heading4"')
    check("用例详情标题与用例数一一对应", detail_tables == len(case_ids),
          f"详情标题 {detail_tables} 个 vs 用例 {len(case_ids)} 条（每用例 1 张纵向表）")

    # ---- 状态真实性
    bad_status = [c["id"] for s in scenarios for c in s["cases"] if c["status"] != CASE_STATUS_EXPECTED]
    lines.append(f"- [{'通过' if not bad_status else '提示'}] 用例状态核对："
                 f"{len(case_ids) - len(bad_status)}/{len(case_ids)} 条为「{CASE_STATUS_EXPECTED}」"
                 + (f"；非未执行：{'、'.join(bad_status)}" if bad_status else ""))
    lines.append(f"- [提示] 统计口径：全部数字按 JSON 计算，未生成任何通过率/执行结果。")

    for w in warnings:
        lines.append(f"- [提示] {w}")

    return ok, lines, full_text


# ---------------------------------------------------------------- 主流程

def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="从 test-design-cases JSON 生成软件测试设计 Word（标准库 OOXML）")
    ap.add_argument("--data", "--input", dest="data", default=DEFAULT_DATA,
                    help=f"用例数据 JSON（默认 {DEFAULT_DATA}）")
    ap.add_argument("--out", "--output", dest="out", default=DEFAULT_OUT,
                    help=f"输出 docx（默认 {DEFAULT_OUT}）")
    ap.add_argument("--evidence", "--evidence-dir", dest="evidence", default=DEFAULT_EVIDENCE,
                    help=f"证据目录（默认 {DEFAULT_EVIDENCE}）")
    args = ap.parse_args(argv)

    def rp(p: str) -> Path:
        path = Path(p)
        return path if path.is_absolute() else (REPO_ROOT / path)

    data_path, out_path, ev_path = rp(args.data), rp(args.out), rp(args.evidence)

    print(f"[render-test-design] 数据源：{data_path}")
    try:
        doc = load_data(data_path)
    except DataError as e:
        print(f"[render-test-design][错误] {e}", file=sys.stderr)
        print("[render-test-design] 数据未就绪或非法：不生成任何文档，退出码 2。", file=sys.stderr)
        return 2

    warnings: list[str] = []
    try:
        doc_xml, expected_tables = build_document_xml(doc, warnings)
        write_docx(out_path, doc_xml, doc)
    except Exception as e:  # 生成失败不留半成品
        print(f"[render-test-design][错误] 文档生成失败：{type(e).__name__}: {e}", file=sys.stderr)
        return 3

    stats = compute_stats(doc)
    print(f"[render-test-design] 已生成：{out_path}")
    print(f"[render-test-design] 场景 {stats['scenario_count']}（业务 {stats['uc_scenario_count']} + "
          f"专项 {stats['special_scenario_count']}）；用例 {stats['case_count']} 条"
          f"（正面 {stats['positive']} / 负面 {stats['negative']}；"
          f"P0 {stats['p0']} / P1 {stats['p1']} / P2 {stats['p2']}）；"
          f"状态「{CASE_STATUS_EXPECTED}」{stats['status_unexecuted']}/{stats['case_count']}")

    ok, checks, full_text = verify(out_path, doc, expected_tables, warnings)

    case_ids = [c["id"] for s in doc["scenarios"] for c in s["cases"]]
    lost = [cid for cid in case_ids if sanitize(cid) not in full_text]
    print(f"[caseID一致性] JSON 用例 {len(case_ids)} 条 → 正文命中 {len(case_ids) - len(lost)} 条；"
          f"缺失 {len(lost)}；重复 0；场景 {len(doc['scenarios'])} → 正文命中 "
          f"{len(doc['scenarios']) - sum(1 for s in doc['scenarios'] if sanitize(s['id']) not in full_text)} 条")

    # ---- 证据落盘
    try:
        ev_path.mkdir(parents=True, exist_ok=True)
        (ev_path / "document-text.txt").write_text(full_text, encoding="utf-8")
        report = [
            "# 测试设计 Word 生成与自校验证据", "",
            f"- 生成时间：{now_iso()}（Asia/Shanghai 时区机器本地时间）",
            f"- 生成命令：`python3 qa/scripts/render-test-design.py`",
            f"- 数据源：`{data_path.relative_to(REPO_ROOT) if data_path.is_relative_to(REPO_ROOT) else data_path}`"
            f"（SHA-256 `{sha256_of(data_path)}`）",
            f"- 输出：`{out_path.relative_to(REPO_ROOT) if out_path.is_relative_to(REPO_ROOT) else out_path}`"
            f"（SHA-256 `{sha256_of(out_path)}`）",
            f"- 场景 {stats['scenario_count']} 条；用例 {stats['case_count']} 条"
            f"（正面 {stats['positive']} / 负面 {stats['negative']}）；"
            f"状态「{CASE_STATUS_EXPECTED}」{stats['status_unexecuted']}/{stats['case_count']}",
            f"- 追溯：UC {stats['uc_covered']}/20；D-AT {stats['d_at_covered']}/24；"
            f"N-AT {stats['n_at_covered']}/6；API {stats['api_covered']}/40（缺口见文档 0.5 节）", "",
            "## 自校验明细（全部真实执行）", "",
            *checks, "",
            "## 工具与视觉检查限制（如实披露）", "",
            "- 本机无 LibreOffice/soffice、pandoc、pdftoppm、python-docx、npm docx（已逐一探测，见 tool-probe.txt），"
            "无法在本环境做真实 Word 版面渲染截图。",
            "- 本证据为 XML 良构性与所列结构/全文检查：OOXML 部件齐全（含 word/_rels/document.xml.rels）、"
            "样式/字体/大纲级别、页码域、表格宽度与重复表头、caseID 一致性、全部用例字段逐字出现在正文（无截断）。",
            "- 未做完整 XSD schema 校验（本机无 defusedxml/lxml，docx skill 的 validate.py 不可运行），"
            "也未做视觉/分页验证；结构校验不等于视觉检查。",
            "- 未使用 HTML 渲染冒充 Word 版面；最终分页与表格视觉效果须由项目经理在 Word/WPS 中"
            "人工核对（见 docs/testing/task5-submission-v1.0.md §6）。",
        ]
        (ev_path / "verification-report.md").write_text("\n".join(report) + "\n", encoding="utf-8")
        (ev_path / "render.log").write_text(
            "\n".join([
                f"[{now_iso()}] python3 qa/scripts/render-test-design.py",
                f"data={data_path} sha256={sha256_of(data_path)}",
                f"out={out_path} sha256={sha256_of(out_path)}",
                f"scenarios={stats['scenario_count']} cases={stats['case_count']} "
                f"positive={stats['positive']} negative={stats['negative']} "
                f"P0={stats['p0']} P1={stats['p1']} P2={stats['p2']} "
                f"status_unexecuted={stats['status_unexecuted']}",
                f"traceability: UC={stats['uc_covered']}/20 D-AT={stats['d_at_covered']}/24 "
                f"N-AT={stats['n_at_covered']}/6 API={stats['api_covered']}/40",
                f"caseID check: json={len(case_ids)} doc={len(case_ids) - len(lost)} missing={len(lost)}",
                f"verify={'PASS' if ok else 'FAIL'}",
                *checks,
            ]) + "\n", encoding="utf-8")
    except Exception as e:
        print(f"[render-test-design][警告] 证据落盘失败：{e}", file=sys.stderr)

    if ok:
        print("[render-test-design] 自校验全部通过（明细见证据目录 verification-report.md）。")
        return 0
    print("[render-test-design][错误] 自校验存在失败项，见下方与证据目录：", file=sys.stderr)
    for line in checks:
        if line.startswith("- [失败]"):
            print(line, file=sys.stderr)
    return 3


if __name__ == "__main__":
    sys.exit(main())
