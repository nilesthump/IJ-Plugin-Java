"""Export the non-frozen F0 plan, with rendered UML and independent PDF checks.

Authoring dependency: ReportLab. Verification: pypdf + pdfplumber + Poppler.
This document export does not create a formal architecture candidate or freeze.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import subprocess
import unicodedata
from pathlib import Path
from xml.sax.saxutils import escape

from PIL import Image as PILImage
import pdfplumber
from pypdf import PdfReader
from reportlab import Version as reportlab_version
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Frame, Image, KeepTogether, PageBreak, PageTemplate,
    Paragraph, Spacer, Table, TableStyle,
)
from reportlab.platypus.tableofcontents import TableOfContents


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'spec/architecture/F0_DEVELOPMENT_PLAN.md'
OUTPUT = ROOT / 'output/pdf'
WORK = ROOT / 'tmp/pdfs'
IDENTITY = 'edu.courseassist.f0.development-plan'
EDITION = '2026-10-18-export-01'
BLUE = colors.HexColor('#173D61')
MUTED = colors.HexColor('#60738A')
INK = colors.HexColor('#203248')


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(args: list[str], **kwargs):
    return subprocess.run(args, check=True, timeout=120, **kwargs)


def plain(text: str) -> str:
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)
    text = text.replace('**', '').replace('`', '')
    return html.unescape(text)


def normal(text: str) -> str:
    return re.sub(r'\s+', '', unicodedata.normalize('NFKC', text)).replace('\u00ad', '')


def rich(text: str) -> str:
    text = escape(text)

    def link(match):
        label, target = match.groups()
        if target.startswith('#'):
            return f'<link href="{target}" color="#246598">{label}</link>'
        # Public reading links point to existing plan/rule pages. Diagram sources
        # are shipped alongside the local PDF, so no unpublished web URL is made.
        if target.endswith('.puml'):
            return label
        if not target.startswith(('http://', 'https://')):
            resolved = (SOURCE.parent / target).resolve()
            target = ('https://github.com/nilesthump/IJ-Plugin-Java/blob/main/'
                      + resolved.relative_to(ROOT).as_posix())
        return f'<link href="{target}" color="#246598">{label}</link>'

    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', link, text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    text = re.sub(r'`([^`]+)`', r'<font color="#155A70">\1</font>', text)
    return text


def parse_markdown(source: str) -> dict:
    lines = source.splitlines()
    title = lines[0][2:]
    intro = []
    sections = []
    blocks = intro
    anchor = None
    i = 1
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        if line == '## 目录':
            i += 1
            while i < len(lines) and not lines[i].startswith('<a id='):
                i += 1
            continue
        if line.startswith('<a id='):
            anchor = re.fullmatch(r'<a id="([^"]+)"></a>', line).group(1)
            i += 1
            continue
        if line.startswith('## '):
            if not anchor:
                raise ValueError('Every section requires a stable source anchor')
            section = {'id': anchor, 'title': line[3:], 'blocks': []}
            sections.append(section)
            blocks = section['blocks']
            anchor = None
            i += 1
            continue
        if line.startswith('!['):
            match = re.fullmatch(r'!\[([^\]]+)\]\(([^)]+)\)', line)
            blocks.append({'kind': 'diagram', 'alt': match[1], 'path': match[2]})
            i += 1
            continue
        if line.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                cells = [c.strip() for c in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', c) for c in cells):
                    rows.append(cells)
                i += 1
            blocks.append({'kind': 'table', 'rows': rows})
            continue
        item = re.match(r'^(?:- |(\d+)\. )(.*)', line)
        if item:
            blocks.append({'kind': 'list', 'number': item[1], 'text': item[2]})
            i += 1
            continue
        chunks = [line]
        i += 1
        while i < len(lines) and lines[i].strip():
            if lines[i].startswith(('## ', '<a id=', '![', '|', '- ', '```')):
                break
            chunks.append(lines[i].strip())
            i += 1
        blocks.append({'kind': 'paragraph', 'text': ' '.join(chunks)})
    if len(sections) != 11:
        raise ValueError(f'Expected 11 plan sections, got {len(sections)}')
    return {'identity': IDENTITY, 'edition': EDITION, 'status': 'development-plan-not-frozen',
            'title': title, 'source': SOURCE.relative_to(ROOT).as_posix(),
            'sourceSha256': sha(SOURCE), 'intro': intro, 'sections': sections}


def render_diagrams(model: dict, java: str, plantuml: Path) -> list[dict]:
    records = []
    for section in model['sections']:
        for block in section['blocks']:
            if block['kind'] != 'diagram':
                continue
            image = SOURCE.parent / block['path']
            source = image.with_suffix('.puml')
            result = run([java, '-DPLANTUML_SECURITY_PROFILE=SECURE',
                          '-Djava.awt.headless=true', '-jar', str(plantuml),
                          '-charset', 'UTF-8', '-pipe', '-tpng'],
                         input=source.read_bytes(), capture_output=True)
            if not result.stdout.startswith(b'\x89PNG\r\n\x1a\n'):
                raise ValueError(f'PlantUML did not render {source.name}')
            image.write_bytes(result.stdout)
            records.append({'id': 'D01' if section['id'] == 'architecture' else 'D02',
                            'sectionIds': [section['id']],
                            'source': source.relative_to(ROOT).as_posix(),
                            'image': image.relative_to(ROOT).as_posix(),
                            'sourceSha256': sha(source), 'imageSha256': sha(image)})
    if len(records) != 2:
        raise ValueError('Expected exactly two rendered diagrams')
    model['diagrams'] = records
    return records


class PlanDocument(BaseDocTemplate):
    def __init__(self, path: Path, **kwargs):
        # Reserve a small right gutter for CJK hanging punctuation.
        super().__init__(str(path), pagesize=A4, leftMargin=48, rightMargin=60,
                         topMargin=56, bottomMargin=48, **kwargs)
        self.addPageTemplates(PageTemplate(
            id='plan', frames=[Frame(self.leftMargin, self.bottomMargin,
                                    self.width, self.height, id='body',
                                    leftPadding=0, rightPadding=0,
                                    topPadding=0, bottomPadding=0)],
            onPage=self.page_header_footer))

    def page_header_footer(self, canvas, doc):
        canvas.saveState()
        canvas.setStrokeColor(colors.HexColor('#D5E1EC'))
        canvas.setLineWidth(.5)
        canvas.line(48, 42, A4[0] - 48, 42)
        canvas.setFont('Deng', 8)
        canvas.setFillColor(MUTED)
        canvas.drawString(48, 28, 'F0 · 开发方案 · 未正式冻结')
        canvas.drawRightString(A4[0] - 48, 28, str(doc.page))
        if doc.page > 1:
            canvas.drawString(48, A4[1] - 30, 'IJ-Plugin-Java / F0 插件综合开发方案')
        canvas.restoreState()

    def afterFlowable(self, flowable):
        if getattr(flowable, 'section_id', None):
            self.canv.bookmarkPage(flowable.section_id)
            outline_level = 1 if flowable.section_id.endswith('-diagram') else 0
            self.canv.addOutlineEntry(flowable.getPlainText(), flowable.section_id, outline_level)
            if hasattr(flowable, 'toc_level'):
                self.notify('TOCEntry', (flowable.toc_level, flowable.getPlainText(), self.page,
                                         flowable.section_id))


def build_pdf(model: dict, pdf: Path, font_dir: Path):
    pdfmetrics.registerFont(TTFont('Deng', str(font_dir / 'Deng.ttf')))
    pdfmetrics.registerFont(TTFont('DengBold', str(font_dir / 'Dengb.ttf')))
    pdfmetrics.registerFontFamily('Deng', normal='Deng', bold='DengBold',
                                 italic='Deng', boldItalic='DengBold')
    styles = getSampleStyleSheet()
    body = ParagraphStyle('PlanBody', fontName='Deng', fontSize=10.3, leading=16.5,
                          textColor=INK, spaceAfter=9, wordWrap='CJK',
                          splitLongWords=True, allowWidows=0, allowOrphans=0)
    section_style = ParagraphStyle('Section', parent=body, fontName='DengBold',
                                   fontSize=20, leading=27, textColor=BLUE,
                                   spaceBefore=2, spaceAfter=18, keepWithNext=True)
    small = ParagraphStyle('Small', parent=body, fontSize=8.7, leading=13.5,
                           textColor=MUTED)
    table_body = ParagraphStyle('Cell', parent=body, fontSize=9.2, leading=14,
                                spaceAfter=0, allowWidows=1, allowOrphans=1)
    table_head = ParagraphStyle('TableHead', parent=table_body, fontName='DengBold',
                                textColor=BLUE)
    story = []
    title = Paragraph('F0 插件<br/>综合开发方案', ParagraphStyle(
        'Cover', parent=body, fontName='DengBold', fontSize=34, leading=46,
        textColor=BLUE, spaceAfter=25))
    title.section_id = 'cover'
    story.extend([Spacer(1, 55), Paragraph('ARCHITECTURE / DEVELOPMENT PLAN', small),
                  Spacer(1, 10), title,
                  Paragraph('IntelliJ IDEA 内嵌 · 纯 Java · Spring AI · H2 · JVector', body),
                  Spacer(1, 18)])
    for block in model['intro']:
        story.append(Paragraph(rich(block['text']), body))
    status = Table([[Paragraph(
        '<b>文件状态</b><br/>按现有开发基线导出，保留已确认要求、实现提案和待验证项。'
        '本次只生成阅读产物，不创建架构候选、不冻结、不改变版本指针。', body)]],
        colWidths=[A4[0] - 108])
    status.setStyle(TableStyle([('BACKGROUND', (0, 0), (-1, -1), colors.HexColor('#EDF5FA')),
                               ('BOX', (0, 0), (-1, -1), .6, colors.HexColor('#B7CCDC')),
                               ('LEFTPADDING', (0, 0), (-1, -1), 14),
                               ('RIGHTPADDING', (0, 0), (-1, -1), 14),
                               ('TOPPADDING', (0, 0), (-1, -1), 12),
                               ('BOTTOMPADDING', (0, 0), (-1, -1), 4)]))
    story.extend([Spacer(1, 14), status, Spacer(1, 22),
                  Paragraph(f'文档标识：{IDENTITY}<br/>导出版次：{EDITION}', small),
                  Paragraph('该版次仅标识阅读产物，不是正式架构版本。', small), PageBreak()])
    toc_heading = Paragraph('目录', section_style)
    toc_heading.section_id = 'contents'
    toc = TableOfContents()
    toc.levelStyles = [
        ParagraphStyle('Toc', parent=body, fontSize=12, leading=21,
                       spaceBefore=8, spaceAfter=8, firstLineIndent=0, leftIndent=0),
        ParagraphStyle('TocDiagram', parent=small, fontSize=10, leading=17,
                       spaceBefore=0, spaceAfter=6, firstLineIndent=0, leftIndent=18),
    ]
    toc.dotsMinLevel = 0
    story.extend([toc_heading, toc, Spacer(1, 25),
                  Paragraph('阅读依据：BASELINE.md → 本方案 → API.md / 当前 Java 契约。'
                            '初始化状态以 CURRENT.md 为准；旧细则的历史表述不覆盖当前基线。', small)])
    width = A4[0] - 108
    for section in model['sections']:
        story.append(PageBreak())
        heading = Paragraph(escape(section['title']), section_style)
        heading.section_id = section['id']
        heading.toc_level = 0
        story.append(heading)
        diagrams = []
        for block in section['blocks']:
            if block['kind'] == 'diagram':
                diagrams.append(block)
            elif block['kind'] == 'table':
                rows = [[Paragraph(rich(c), table_head if i == 0 else table_body)
                         for c in row] for i, row in enumerate(block['rows'])]
                ncols = len(rows[0])
                if ncols == 2:
                    ratios = [.23, .77]
                elif block['rows'][0][1] == '权重':
                    ratios = [.24, .09, .67]
                else:
                    ratios = [.40, .22, .38]
                table = Table(rows, colWidths=[width * r for r in ratios],
                              repeatRows=1, hAlign='LEFT')
                table.setStyle(TableStyle([
                    ('VALIGN', (0, 0), (-1, -1), 'TOP'),
                    ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#E4EEF6')),
                    ('ROWBACKGROUNDS', (0, 1), (-1, -1),
                     [colors.white, colors.HexColor('#F6F9FC')]),
                    ('LINEBELOW', (0, 0), (-1, 0), .7, colors.HexColor('#ACC1D4')),
                    ('LINEBELOW', (0, 1), (-1, -1), .3, colors.HexColor('#DEE7EF')),
                    ('LEFTPADDING', (0, 0), (-1, -1), 9),
                    ('RIGHTPADDING', (0, 0), (-1, -1), 9),
                    ('TOPPADDING', (0, 0), (-1, -1), 8),
                    ('BOTTOMPADDING', (0, 0), (-1, -1), 8),
                ]))
                story.extend([table, Spacer(1, 12)])
            elif block['kind'] == 'list':
                marker = block['number'] + '.' if block['number'] else '•'
                list_style = ParagraphStyle('List', parent=body, leftIndent=14,
                                             firstLineIndent=-14)
                story.append(Paragraph(escape(marker) + ' ' + rich(block['text']), list_style))
            else:
                story.append(Paragraph(rich(block['text']), body))
        for block in diagrams:
            story.append(PageBreak())
            diagram_heading = Paragraph(escape(block['alt']), section_style)
            diagram_heading.section_id = section['id'] + '-diagram'
            diagram_heading.toc_level = 1
            story.append(diagram_heading)
            image = SOURCE.parent / block['path']
            with PILImage.open(image) as source_image:
                iw, ih = source_image.size
            scale = min(width / iw, 590 / ih)
            picture = Image(str(image), width=iw * scale, height=ih * scale)
            picture.hAlign = 'CENTER'
            story.extend([picture, Spacer(1, 13),
                          Paragraph('图源为同名 .puml；组件和流程保留开发方案语义。'
                                    '节点表示目标职责，不表示业务模块已实现。', small)])
    doc = PlanDocument(pdf, title=model['title'], author='IJ-Plugin-Java',
                       subject='F0 development baseline; not formally frozen',
                       creator='Architecture artifacts / ReportLab')
    doc.multiBuild(story)


def verify(model: dict, pdf: Path) -> dict:
    reader = PdfReader(pdf)
    extracted = '\n'.join(page.extract_text() or '' for page in reader.pages)
    normalized = normal(extracted)
    fragments = [model['title']]
    fragments.extend(b['text'] for b in model['intro'])
    for section in model['sections']:
        fragments.append(section['title'])
        for block in section['blocks']:
            if block['kind'] == 'diagram':
                fragments.append(block['alt'])
            elif block['kind'] == 'table':
                fragments.extend(cell for row in block['rows'] for cell in row)
            else:
                fragments.append(block['text'])
    missing = [plain(f) for f in fragments if normal(plain(f)) not in normalized]
    if missing:
        raise ValueError('PDF text parity failure: ' + json.dumps(missing, ensure_ascii=False))
    outlines = []

    def walk(items):
        for item in items:
            if isinstance(item, list):
                walk(item)
            else:
                outlines.append(str(item.title))

    walk(reader.outline)
    for section in model['sections']:
        if section['title'] not in outlines:
            raise ValueError(f'Missing bookmark: {section["id"]}')
    layout_issues = []
    with pdfplumber.open(pdf) as document:
        for number, page in enumerate(document.pages, 1):
            for char in page.chars:
                if char['x0'] < 43 or char['x1'] > page.width - 43:
                    layout_issues.append({'page': number, 'char': char['text'], 'axis': 'x'})
                if char['top'] < 20 or char['bottom'] > page.height - 20:
                    layout_issues.append({'page': number, 'char': char['text'], 'axis': 'y'})
    if layout_issues:
        raise ValueError('Text outside page safe area: ' + str(layout_issues[:10]))
    if '\ufffd' in extracted or '\x00' in extracted:
        raise ValueError('PDF contains replacement or null characters')
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / 'extracted.txt').write_text(extracted, encoding='utf-8')
    return {'textParity': 'PASS', 'independentExtractor': 'pypdf',
            'verifiedFragments': len(fragments), 'pages': len(reader.pages),
            'bookmarks': len(outlines), 'sectionBookmarks': 11,
            'pageSafeArea': 'PASS', 'layoutChecker': 'pdfplumber',
            'visualReview': 'PENDING'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plantuml-jar', required=True, type=Path)
    parser.add_argument('--java', default='java')
    parser.add_argument('--font-dir', type=Path, default=Path('C:/Windows/Fonts'))
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    model = parse_markdown(SOURCE.read_text(encoding='utf-8-sig'))
    diagrams = render_diagrams(model, args.java, args.plantuml_jar.resolve())
    model_path = OUTPUT / 'F0_DEVELOPMENT_PLAN.model.json'
    model_path.write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    pdf = OUTPUT / 'F0_DEVELOPMENT_PLAN.pdf'
    build_pdf(model, pdf, args.font_dir)
    verification = verify(model, pdf)
    verification_path = OUTPUT / 'F0_DEVELOPMENT_PLAN.verification.json'
    verification_path.write_text(json.dumps(verification, ensure_ascii=False, indent=2) + '\n',
                                 encoding='utf-8')
    manifest = {'schema': 'f0-plan-pdf-export-1', 'identity': IDENTITY, 'edition': EDITION,
                'status': model['status'], 'formalCandidateCreated': False,
                'architectureFrozen': False, 'statePointerChanged': False,
                'source': model['source'], 'sourceSha256': sha(SOURCE),
                'model': model_path.relative_to(ROOT).as_posix(), 'modelSha256': sha(model_path),
                'pdf': pdf.relative_to(ROOT).as_posix(), 'pdfSha256': sha(pdf),
                'sectionIds': [s['id'] for s in model['sections']], 'diagrams': diagrams,
                'tooling': {'pdf': 'ReportLab ' + reportlab_version,
                            'plantumlJarSha256': sha(args.plantuml_jar),
                            'fontSha256': sha(args.font_dir / 'Deng.ttf')},
                'verification': verification_path.relative_to(ROOT).as_posix()}
    (OUTPUT / 'F0_DEVELOPMENT_PLAN.manifest.json').write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'pdf': str(pdf), **verification}, ensure_ascii=False))


if __name__ == '__main__':
    main()
