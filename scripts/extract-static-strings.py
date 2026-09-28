"""One-time/resource maintenance helper; preserves visible English wording exactly."""
from pathlib import Path
import re
from xml.sax.saxutils import escape

root = Path(__file__).resolve().parents[1]
resource = root / 'app/src/main/res/values/ui_strings.xml'
pattern = re.compile(r'\bText\("([^"$\\\n]*)"(?=[,)])')
strings = {}
used = set()
if resource.exists():
    raise SystemExit('Resources already extracted; edit the resource file normally.')
for path in sorted((root / 'app/src/main/java/app/moodiary').rglob('*.kt')):
    source = path.read_text(encoding='utf-8-sig')
    def replace(match):
        value = match[1]
        if value not in strings:
            base = 'ui_' + re.sub(r'[^a-z0-9]+', '_', value.lower()).strip('_')[:60]
            if base == 'ui_': base = 'ui_symbol'
            key = base
            counter = 2
            while key in used:
                key = f'{base}_{counter}'; counter += 1
            used.add(key); strings[value] = key
        return f'Text(stringResource(R.string.{strings[value]})'
    updated = pattern.sub(replace, source)
    if updated != source:
        position = updated.index('\n') + 1
        updated = updated[:position] + '\nimport androidx.compose.ui.res.stringResource\nimport app.moodiary.R\n' + updated[position:]
        path.write_text(updated, encoding='utf-8')
lines = ['<resources>']
for value, key in strings.items():
    value = escape(value).replace("'", "\\'")
    lines.append(f'    <string name="{key}" formatted="false">{value}</string>')
lines.append('</resources>')
resource.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'Extracted {len(strings)} static UI strings. Dynamic/error strings remain beside their logic.')
