/**
 * Minimal YAML subset parser for the build-plan DSL.
 *
 * Supports the subset we need:
 *   - top-level scalars (string, number, boolean)
 *   - nested mappings via indentation
 *   - block sequences (`- item`) of scalars or mappings
 *   - inline list literals: `[a, b, c]`
 *   - comments starting with `#`
 *   - quoted and unquoted strings
 *
 * Not supported (intentional — the schema is closed, we can extend):
 *   - flow mappings (`{ a: 1 }`)
 *   - anchors / references (`&foo`, `*foo`)
 *   - multi-line scalars (`|` / `>`)
 *   - tags (`!!str` etc.)
 *
 * If the schema grows beyond this, swap in `js-yaml` and remove this file.
 * Keeping the parser in-tree today means one less dependency and one fewer
 * thing to break at install time.
 */

export class YamlParseError extends Error {
  constructor(
    message: string,
    public readonly line: number,
    public readonly column: number,
  ) {
    super(`YAML parse error at line ${line + 1}:${column + 1} — ${message}`);
    this.name = 'YamlParseError';
  }
}

export type YamlValue =
  | string
  | number
  | boolean
  | null
  | YamlValue[]
  | { [key: string]: YamlValue };

/** Parse a YAML document into a JS value. Returns `null` for an empty doc. */
export function parseYaml(text: string): YamlValue {
  const lines = preprocess(text);
  if (lines.length === 0) return null;

  const ctx: ParseContext = { lines, index: 0 };
  // Start at the indent of the first line so the top-level mapping tolerates
  // leading indentation in the source. Sub-blocks must still be deeper.
  const firstIndent = lines[0].indent;
  const result = parseNode(ctx, firstIndent);
  return result;
}

type Line = {
  /** Indent in spaces (tabs are forbidden). */
  indent: number;
  /** The trimmed content after indent, stripped of trailing comments. */
  content: string;
  /** 0-based line index in the source. */
  lineNo: number;
};

type ParseContext = {
  lines: Line[];
  index: number;
};

function preprocess(text: string): Line[] {
  const rawLines = text.split(/\r?\n/);
  const out: Line[] = [];
  for (let i = 0; i < rawLines.length; i++) {
    const raw = rawLines[i];
    // Skip blank lines and pure-comment lines, but track them for error messages.
    const stripped = raw.replace(/\t/g, '  ');
    if (/^\s*(#.*)?$/.test(stripped)) continue;
    const m = /^(\s*)(.*)$/.exec(stripped)!;
    const indent = m[1].length;
    const content = stripTrailingComment(m[2]).trim();
    if (content === '') continue;
    out.push({ indent, content, lineNo: i });
  }
  return out;
}

function stripTrailingComment(s: string): string {
  // Strip a `# ...` comment that follows whitespace, but only when the `#`
  // is not inside a quoted string. A simple state walk handles the common case.
  let inSingle = false;
  let inDouble = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === "'" && !inDouble) inSingle = !inSingle;
    else if (c === '"' && !inSingle) inDouble = !inDouble;
    else if (c === '#' && !inSingle && !inDouble) {
      return s.slice(0, i);
    }
  }
  return s;
}

function parseNode(ctx: ParseContext, indent: number): YamlValue {
  if (ctx.index >= ctx.lines.length) return null;
  const line = ctx.lines[ctx.index];
  if (line.indent < indent) return null;
  if (line.indent > indent) {
    throw new YamlParseError(
      `unexpected indent (${line.indent} > ${indent})`,
      line.lineNo,
      line.indent,
    );
  }
  if (line.content.startsWith('- ')) return parseList(ctx, indent);
  return parseMapping(ctx, indent);
}

function parseMapping(ctx: ParseContext, indent: number): { [k: string]: YamlValue } {
  const obj: { [k: string]: YamlValue } = {};
  while (ctx.index < ctx.lines.length) {
    const line = ctx.lines[ctx.index];
    if (line.indent < indent) break;
    if (line.indent > indent) {
      throw new YamlParseError(
        `unexpected indent (${line.indent} > ${indent})`,
        line.lineNo,
        line.indent,
      );
    }
    if (line.content.startsWith('- ')) break; // a list under the current key
    const colon = findUnquotedColon(line.content);
    if (colon < 0) {
      throw new YamlParseError(
        `expected "key: value" but got "${line.content}"`,
        line.lineNo,
        line.indent,
      );
    }
    const key = unquote(line.content.slice(0, colon).trim());
    const rest = line.content.slice(colon + 1).trim();
    ctx.index++;
    if (rest === '') {
      // Nested node — child must be deeper indented.
      const child = ctx.lines[ctx.index];
      if (!child || child.indent <= indent) {
        obj[key] = null;
        continue;
      }
      obj[key] = parseNode(ctx, child.indent);
    } else if (rest.startsWith('[')) {
      obj[key] = parseInlineList(rest, line);
    } else {
      obj[key] = parseScalar(rest, line);
    }
  }
  return obj;
}

function parseList(ctx: ParseContext, indent: number): YamlValue[] {
  const list: YamlValue[] = [];
  while (ctx.index < ctx.lines.length) {
    const line = ctx.lines[ctx.index];
    if (line.indent < indent) break;
    if (line.indent > indent) {
      throw new YamlParseError(
        `unexpected indent in list (${line.indent} > ${indent})`,
        line.lineNo,
        line.indent,
      );
    }
    if (!line.content.startsWith('- ')) break;
    const itemContent = line.content.slice(2).trim();
    ctx.index++;
    if (itemContent === '') {
      // The list item is a mapping or sub-list on the next lines, indented further.
      const child = ctx.lines[ctx.index];
      if (child && child.indent > indent) {
        list.push(parseNode(ctx, child.indent));
      } else {
        list.push(null);
      }
    } else if (itemContent.startsWith('[')) {
      list.push(parseInlineList(itemContent, line));
    } else if (findUnquotedColon(itemContent) >= 0) {
      // `- key: scalar` — first key/value pair of a list-item mapping, OR a
      // single-line inline mapping. If the next line is deeper-indented, the
      // mapping continues on subsequent lines; otherwise it's a one-shot.
      const next = ctx.lines[ctx.index];
      if (next && next.indent > indent) {
        const obj = parseMappingStartingWith(ctx, indent, itemContent, line);
        list.push(obj);
      } else {
        const obj = parseInlineMapping(itemContent, line);
        list.push(obj);
      }
    } else {
      list.push(parseScalar(itemContent, line));
    }
  }
  return list;
}

/** Parse a mapping that begins on a list-item line whose `- key: value`
 *  prefix has already been consumed. `firstContent` is what came after `- `. */
function parseMappingStartingWith(
  ctx: ParseContext,
  parentIndent: number,
  firstContent: string,
  firstLine: Line,
): { [k: string]: YamlValue } {
  const colon = findUnquotedColon(firstContent);
  const key = unquote(firstContent.slice(0, colon).trim());
  const rest = firstContent.slice(colon + 1).trim();
  const obj: { [k: string]: YamlValue } = {};
  if (rest === '') {
    const child = ctx.lines[ctx.index];
    if (!child || child.indent <= parentIndent) {
      obj[key] = null;
      return obj;
    }
    obj[key] = parseNode(ctx, child.indent);
  } else if (rest.startsWith('[')) {
    obj[key] = parseInlineList(rest, firstLine);
  } else {
    obj[key] = parseScalar(rest, firstLine);
  }
  // Continue reading sibling keys from deeper-indented lines.
  // The remaining lines share the deeper indent we just consumed.
  const childIndent = ctx.index < ctx.lines.length ? ctx.lines[ctx.index].indent : -1;
  while (ctx.index < ctx.lines.length) {
    const line = ctx.lines[ctx.index];
    if (line.indent !== childIndent) break;
    if (line.content.startsWith('- ')) break;
    const c = findUnquotedColon(line.content);
    if (c < 0) {
      throw new YamlParseError(
        `expected "key: value" but got "${line.content}"`,
        line.lineNo,
        line.indent,
      );
    }
    const k = unquote(line.content.slice(0, c).trim());
    const r = line.content.slice(c + 1).trim();
    ctx.index++;
    if (r === '') {
      const grandchild = ctx.lines[ctx.index];
      if (grandchild && grandchild.indent > childIndent) {
        obj[k] = parseNode(ctx, grandchild.indent);
      } else {
        obj[k] = null;
      }
    } else if (r.startsWith('[')) {
      obj[k] = parseInlineList(r, line);
    } else {
      obj[k] = parseScalar(r, line);
    }
  }
  return obj;
}

function parseInlineMapping(content: string, line: Line): { [k: string]: YamlValue } {
  // We expect exactly one `key: value` pair per inline list item. More would
  // need a richer grammar; the DSL keeps devices to a single key:value line.
  const colon = findUnquotedColon(content);
  if (colon < 0) {
    throw new YamlParseError(
      `inline mapping needs "key: value" but got "${content}"`,
      line.lineNo,
      line.indent,
    );
  }
  const key = unquote(content.slice(0, colon).trim());
  const value = parseScalar(content.slice(colon + 1).trim(), line);
  return { [key]: value };
}

function parseInlineList(content: string, line: Line): YamlValue[] {
  if (!content.endsWith(']')) {
    throw new YamlParseError(
      `inline list must end with "]" but got "${content}"`,
      line.lineNo,
      line.indent,
    );
  }
  const inner = content.slice(1, -1).trim();
  if (inner === '') return [];
  const parts = splitTopLevel(inner, ',');
  return parts.map((p) => parseScalar(p.trim(), line));
}

function splitTopLevel(s: string, sep: string): string[] {
  const out: string[] = [];
  let depth = 0;
  let inSingle = false;
  let inDouble = false;
  let start = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === "'" && !inDouble) inSingle = !inSingle;
    else if (c === '"' && !inSingle) inDouble = !inDouble;
    else if (!inSingle && !inDouble) {
      if (c === '[' || c === '{') depth++;
      else if (c === ']' || c === '}') depth--;
      else if (c === sep && depth === 0) {
        out.push(s.slice(start, i));
        start = i + 1;
      }
    }
  }
  out.push(s.slice(start));
  return out;
}

function findUnquotedColon(s: string): number {
  let inSingle = false;
  let inDouble = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === "'" && !inDouble) inSingle = !inSingle;
    else if (c === '"' && !inSingle) inDouble = !inDouble;
    else if (c === ':' && !inSingle && !inDouble) return i;
  }
  return -1;
}

function parseScalar(s: string, _line: Line): YamlValue {
  if (s === 'null' || s === '~') return null;
  if (s === 'true') return true;
  if (s === 'false') return false;
  if (/^-?\d+$/.test(s)) return parseInt(s, 10);
  if (/^-?\d+\.\d+$/.test(s)) return parseFloat(s);
  return unquote(s);
}

function unquote(s: string): string {
  if (s.length >= 2 && ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith("'") && s.endsWith("'")))) {
    return s.slice(1, -1);
  }
  return s;
}
