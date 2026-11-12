import argparse
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('directory', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--pattern', action='append', help='Only reports executed in a targeted run')
args = parser.parse_args()
files = sorted({file for pattern in (args.pattern or ['TEST-*.xml'])
                for file in args.directory.glob(pattern)})
if not files:
    raise SystemExit('没有实际 JUnit 测试报告')
totals = dict(total=0, passed=0, failed=0, errors=0, skipped=0)
suites = []
failures = []
for file in files:
    suite = ET.parse(file).getroot()
    row = {'class': suite.get('name'), 'total': int(suite.get('tests', 0)),
           'failed': int(suite.get('failures', 0)), 'errors': int(suite.get('errors', 0)),
           'skipped': int(suite.get('skipped', 0))}
    row['passed'] = row['total'] - row['failed'] - row['errors'] - row['skipped']
    suites.append(row)
    for key in totals:
        totals[key] += row[key]
    for case in suite.findall('testcase'):
        for tag in ('failure', 'error', 'skipped'):
            detail = case.find(tag)
            if detail is not None:
                failures.append({'class': case.get('classname'), 'test': case.get('name'),
                                 'result': tag, 'message': detail.get('message', ''),
                                 'detail': (detail.text or '')[:3000]})
result = json.dumps({'totals': totals, 'suites': suites, 'failures': failures}, ensure_ascii=False, indent=2)
for key in ('TEST_DB_PASSWORD', 'DEMO_PASSWORD'):
    value = os.getenv(key)
    if value:
        result = result.replace(value, '[已隐藏测试口令]')
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(result + '\n', encoding='utf-8')
print(json.dumps(totals, ensure_ascii=False))
