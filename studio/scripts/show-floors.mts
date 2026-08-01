import { readFileSync } from 'node:fs';
import { join } from 'node:path';
const report = JSON.parse(readFileSync(join(process.cwd(), 'scripts/roundtrip_report.json'), 'utf8'));
for (const f of report) {
  console.log('===', f.name);
  for (const fl of f.plan.floors) console.log('  ', JSON.stringify(fl));
  console.log('  warnings:', f.warnings.length);
  for (const w of f.warnings.slice(0, 4)) console.log('   -', w.message);
}
