import { useState } from 'react';
import { CircleCheck, CircleX, ChevronRight, X, ShieldCheck, ShieldAlert } from 'lucide-react';
import type { FullReport, CheckResult } from '../engine';

type Props = {
  report: FullReport | null;
  onClose: () => void;
};

const CATEGORY_LABELS: Record<string, string> = {
  integrity: 'Integrity',
  fabric: 'Fabric',
  stray: 'Stray',
  stairs: 'Stairs',
};

const CATEGORY_ORDER = ['integrity', 'fabric', 'stray', 'stairs'];

export function CheckerPanel({ report, onClose }: Props) {
  return (
    <aside className="checker-panel">
      <div className="checker-header">
        <h3><ShieldCheck className="icon" />Checks</h3>
        <button
          className="checker-close"
          onClick={onClose}
          aria-label="Close checker panel"
        >
          <X className="icon" />
        </button>
      </div>

      {!report && <p className="checker-empty">Click "Check" to run.</p>}

      {report && (
        <>
          <div className={`checker-summary ${report.ok ? 'ok' : 'fail'}`}>
            {report.ok
              ? <CircleCheck className="icon" />
              : <ShieldAlert className="icon" />}
            <span>{report.summary}</span>
          </div>
          <div className="checker-results">
            {CATEGORY_ORDER.map(cat => {
              const items = report.results.filter(r => r.category === cat);
              if (items.length === 0) return null;
              return (
                <div key={cat} className="checker-category">
                  <div className="checker-cat-label">{CATEGORY_LABELS[cat]}</div>
                  {items.map(r => <CheckRow key={r.label} result={r} />)}
                </div>
              );
            })}
          </div>
        </>
      )}
    </aside>
  );
}

function CheckRow({ result }: { result: CheckResult }) {
  const [expanded, setExpanded] = useState(false);
  const hasFindings = result.findings.length > 0;

  return (
    <div className={`check-row ${result.ok ? 'pass' : 'fail'}`}>
      <button
        className="check-row-header"
        onClick={() => hasFindings && setExpanded(!expanded)}
        disabled={!hasFindings}
      >
        <span className="check-status">
          {result.ok
            ? <CircleCheck className="icon" />
            : <CircleX className="icon" />}
        </span>
        <span className="check-label">{result.label}</span>
        <span className="check-count">{result.count}</span>
        {hasFindings && (
          <ChevronRight className={`check-arrow ${expanded ? '' : 'collapsed'}`} />
        )}
      </button>
      {expanded && hasFindings && (
        <ul className="check-findings">
          {result.findings.slice(0, 50).map((f, i) => (
            <li key={i}>{f}</li>
          ))}
          {result.findings.length > 50 && (
            <li className="check-more">… and {result.findings.length - 50} more</li>
          )}
        </ul>
      )}
    </div>
  );
}
