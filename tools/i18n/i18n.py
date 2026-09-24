#!/usr/bin/env python3
"""Tooling for the OpenPnP translation workflow described in TRANSLATIONS.md.

Subcommands:

  gap        what is left to translate for one language, graded by difficulty
  batch      write the gap out as small tab separated files for a translator to fill in
  import     validate a filled in batch against its contract and merge it into the bundle
  reuse      fill entries whose English is already translated under another key
  check      the invariants that fail silently at runtime: placeholders, duplicates, orphans
  normalize  rewrite a bundle in the escaped form Properties.store produces
  hardcoded  English still baked into the Java sources, which no bundle can reach
  unused     keys no Java source reads, left behind when a class was removed

Run any subcommand with -h for its options.
"""

import argparse
import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RESOURCES = REPO / "src" / "main" / "resources" / "org" / "openpnp"
SOURCES = REPO / "src" / "main" / "java"

# The bundle families. "translations" is keyed by identifier, "texts" is keyed by the English prose
# itself - see the note on Translations.translateText - and "patterns" holds templates, keyed by
# identifier like translations but matched against prose the sources assemble at runtime.
FAMILIES = ("translations", "texts", "patterns")

# Keys under this prefix in the patterns family are single words a template may substitute rather
# than templates in their own right.
VOCABULARY_PREFIX = "Word."


# ---------------------------------------------------------------------------
# .properties reading and writing
#
# Java's Properties.store(Writer) is the reference: the repo's LocalisationTest compares the
# English bundle against exactly its output, so the escaping below has to match it. Note that the
# Writer overload leaves non-ASCII alone, which is why these files are readable UTF-8.
# ---------------------------------------------------------------------------

def load_convert(text):
    """Undo Java's .properties escaping."""
    out = []
    i = 0
    while i < len(text):
        c = text[i]
        if c != "\\":
            out.append(c)
            i += 1
            continue
        i += 1
        if i >= len(text):
            break
        c = text[i]
        i += 1
        if c == "u":
            out.append(chr(int(text[i:i + 4], 16)))
            i += 4
        elif c == "t":
            out.append("\t")
        elif c == "n":
            out.append("\n")
        elif c == "r":
            out.append("\r")
        elif c == "f":
            out.append("\f")
        else:
            out.append(c)
    return "".join(out)


def save_convert(text, escape_space):
    """Apply Java's .properties escaping, as Properties.store(Writer) does it."""
    out = []
    for index, c in enumerate(text):
        if c == "\\":
            out.append("\\\\")
        elif c == " ":
            # In values only a leading space needs escaping; in keys, every one does.
            out.append("\\ " if escape_space or index == 0 else " ")
        elif c == "\t":
            out.append("\\t")
        elif c == "\n":
            out.append("\\n")
        elif c == "\r":
            out.append("\\r")
        elif c == "\f":
            out.append("\\f")
        elif c in "=:#!":
            out.append("\\" + c)
        else:
            out.append(c)
    return "".join(out)


def sort_key(key):
    """Java's String.compareTo order, which is by UTF-16 code unit."""
    return key.encode("utf-16-be")


def _key_of_raw(line):
    stripped = line.strip()
    if not stripped or stripped[0] in "#!":
        return None
    return Bundle._split(line)[0]


def strip_unescaped(raw):
    """Drop padding around a raw key, keeping a space that was deliberately escaped.

    Properties.store escapes every space in a key, so a key that genuinely ends in one is written
    as "...Rate.\\ ". Unescaping first and stripping afterwards would silently shorten it, and the
    entry would then never match its lookup - which is exactly how a translation goes dead.
    """
    end = len(raw)
    while end > 0 and raw[end - 1] in " \t":
        backslashes = 0
        scan = end - 1
        while scan > 0 and raw[scan - 1] == "\\":
            backslashes += 1
            scan -= 1
        if backslashes % 2 == 1:
            break
        end -= 1
    return raw[:end].lstrip()


class Bundle:
    """One .properties file: its entries, its leading comment block, and any duplicate keys."""

    def __init__(self, path):
        self.path = path
        self.entries = OrderedDict()
        self.header = []
        self.duplicates = []
        self.lines = []
        self.line_of = {}
        self.original = {}
        self.has_bom = False
        self.crlf = False
        if path.exists():
            self._read()

    def _read(self):
        raw = self.path.read_bytes()
        if raw.startswith(b"\xef\xbb\xbf"):
            raw = raw[3:]
            self.has_bom = True
        text = raw.decode("utf-8")
        self.crlf = "\r\n" in text
        text = text.replace("\r\n", "\n")
        if text.endswith("\n"):
            text = text[:-1]

        in_header = True
        for line in text.split("\n"):
            self.lines.append(line)
            stripped = line.strip()
            if not stripped or stripped[0] in "#!":
                if in_header:
                    self.header.append(line)
                continue
            in_header = False
            key, value = self._split(line)
            if key is None:
                continue
            if key in self.entries:
                self.duplicates.append(key)
            else:
                self.line_of[key] = len(self.lines) - 1
            self.entries[key] = value
        # Keeps save() from reformatting lines nobody edited. Several bundles carry values whose
        # = and : are not escaped, which is legal to read but would all change on a rewrite.
        self.original = dict(self.entries)

    def key_at(self, index):
        line = self.lines[index]
        stripped = line.strip()
        if not stripped or stripped[0] in "#!":
            return None
        return self._split(line)[0]

    @staticmethod
    def _split(line):
        # The separator is the first unescaped = or :, or else the first unescaped whitespace.
        i = 0
        while i < len(line):
            c = line[i]
            if c == "\\":
                i += 2
                continue
            if c in "=:":
                # Properties.load drops unescaped whitespace after the separator, so a value
                # written as "key= text" reaches the program as "text". Reading it any other way
                # would have the tool reporting a string the user never sees.
                return load_convert(strip_unescaped(line[:i])), load_convert(
                    line[i + 1:].lstrip(" \t\f"))
            i += 1
        return None, None

    @staticmethod
    def format_entry(key, value):
        return save_convert(key, True) + "=" + save_convert(value, False)

    def save(self):
        """Write back, leaving untouched entries on the lines they were already on.

        Re-sorting the whole file instead would be a legitimate normalisation, but several of
        these bundles are not in Java's order to begin with, so it would turn a five line
        translation batch into a fifteen hundred line diff that nobody can review. Full
        normalisation is what the normalize command is for.
        """
        lines = list(self.lines)
        for key, index in self.line_of.items():
            if key in self.entries and self.entries[key] != self.original.get(key):
                lines[index] = self.format_entry(key, self.entries[key])

        for key in [k for k in self.entries if k not in self.line_of]:
            entry = self.format_entry(key, self.entries[key])
            at = len(lines)
            for i in range(len(lines)):
                existing = self.key_at(i) if i < len(self.lines) else None
                if existing is None:
                    existing = _key_of_raw(lines[i])
                if existing is not None and sort_key(existing) > sort_key(key):
                    at = i
                    break
            lines.insert(at, entry)
            # The cached positions past the insert are stale now; rebuild rather than adjust.
            self.lines = lines
            self.line_of = {}
            for i, line in enumerate(lines):
                k = _key_of_raw(line)
                if k is not None and k not in self.line_of:
                    self.line_of[k] = i

        self._write_lines(lines)

    def normalize(self):
        """Rewrite fully sorted and re-escaped, the way Properties.store would."""
        lines = list(self.header)
        for key in sorted(self.entries, key=sort_key):
            lines.append(self.format_entry(key, self.entries[key]))
        self._write_lines(lines)

    def _write_lines(self, lines):
        # LF and no BOM: checkstyle enforces the line endings, and the loader assumes UTF-8.
        self.path.write_bytes(("\n".join(lines) + "\n").encode("utf-8"))


def bundle_path(family, lang=None):
    name = family if lang in (None, "en") else "{}_{}".format(family, lang)
    return RESOURCES / (name + ".properties")


def discover_languages():
    """The language codes that have a translations bundle, which is what the menu offers."""
    found = []
    for path in sorted(RESOURCES.glob("translations_*.properties")):
        found.append(path.stem[len("translations_"):])
    return found


# ---------------------------------------------------------------------------
# Invariants that fail silently at runtime
# ---------------------------------------------------------------------------

# %[index$][flags][width][.precision]conversion.
#
# Java also allows a space flag, but accepting it here would read "% Roundness" and "Minimum
# Speed % supported by the driver" - ordinary labels that never reach String.format - as format
# specifiers. A literal percent in prose is common, "% d" is not.
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z%])")
HTML_TAG = re.compile(r"</?([a-zA-Z][a-zA-Z0-9]*)")


def argument_map(text):
    """Which format argument each specifier consumes, so a reordered translation still matches.

    "%s and %s" and "%2$s then %1$s" both consume arguments 1 and 2 as strings, so they compare
    equal; dropping or adding one does not.
    """
    consumed = {}
    mixed = False
    seen_indexed = seen_positional = False
    position = 0
    for match in FORMAT.finditer(text):
        index, conversion = match.group(1), match.group(2)
        if conversion == "%":
            continue
        if index is None:
            seen_positional = True
            position += 1
            consumed[position] = conversion
        else:
            seen_indexed = True
            consumed[int(index)] = conversion
    if seen_indexed and seen_positional:
        mixed = True
    return consumed, mixed


def markers(text):
    """Counts of things that shape the message and must survive translation.

    Compared strictly only when importing a fresh batch. Auditing bundles with it is too noisy:
    translators legitimately restructure long tooltips, and the Chinese ones for instance add
    <ul><li> explanations the English does not have. A dropped <br/> in a batch, on the other
    hand, is far more likely a mistake than an intention.
    """
    counts = Counter()
    counts["\\n"] = text.count("\n")
    counts["\\t"] = text.count("\t")
    for tag in HTML_TAG.findall(text):
        counts["<" + tag.lower() + ">"] += 1
    return counts


def is_html(text):
    """Whether Swing will render the value as HTML rather than as literal characters."""
    return text.lstrip().lower().startswith("<html")


# Deliberately a fixed list rather than "anything in angle brackets": labels like "Value <unset>"
# are not markup.
HTML_TAGS = ("html", "br", "p", "b", "i", "u", "span", "div", "ul", "ol", "li", "strong", "em",
             "font", "table", "tr", "td", "th", "h1", "h2", "h3", "h4", "h5", "h6", "a", "hr",
             "pre", "code", "center", "sub", "sup", "blockquote", "body")
KNOWN_TAG = re.compile(r"</?(" + "|".join(HTML_TAGS) + r")\b", re.I)


def markup_shown_literally(text):
    """Markup that Swing will print instead of render, because the value is not in HTML mode.

    Whether a translation uses HTML at all is not something both sides have to agree on: the
    Chinese labels deliberately wrap plain English labels in HTML to put a smaller gloss on a
    second line. What is always wrong is carrying tags without the <html> that turns them on,
    because then the user reads "<br/>".
    """
    return bool(KNOWN_TAG.search(text)) and not is_html(text)


def difficulty(english):
    """Rough grading so easy work can be handed to a weaker translator than hard work."""
    consumed, _ = argument_map(english)
    if "<html" in english.lower() or "<br" in english.lower() or len(english) > 120:
        return "hard"
    if len(consumed) >= 3:
        return "hard"
    if len(english) > 40 or consumed:
        return "medium"
    return "easy"


def java_string_literals():
    """Every standalone string literal in the Java sources.

    Used to spot texts_<lang> keys whose English original has since been reworded: the lookup is
    by the English text, so a one word edit orphans the translation with no error anywhere.
    """
    literal = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
    found = set()
    for path in SOURCES.rglob("*.java"):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for match in literal.finditer(text):
            found.add(load_java_literal(match.group(1)))
    return found


def load_java_literal(body):
    return (body.replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r")
            .replace('\\"', '"').replace("\\\\", "\\"))


# Any class whose name ends in Issue or Choice, not just the ones declared in Solutions:
# VisionFeatureIssue and friends are subclasses whose descriptions reach translateText too.
CONSTRUCTION = re.compile(r"new\s+(?:[\w.]+\.)?\w*(?:Issue|Choice)\s*\(")
ONLY_LITERAL = re.compile(r'^"((?:[^"\\]|\\.)*)"$')


def split_top_level(text, separators):
    """Split an argument list on separators that are not inside quotes, parens or brackets."""
    parts = []
    depth = 0
    in_string = False
    escaped = False
    current = []
    for c in text:
        if in_string:
            current.append(c)
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
            continue
        if c == '"':
            in_string = True
            current.append(c)
        elif c in "([{":
            depth += 1
            current.append(c)
        elif c in ")]}":
            depth -= 1
            current.append(c)
        elif depth == 0 and c in separators:
            parts.append("".join(current))
            current = []
        else:
            current.append(c)
    parts.append("".join(current))
    return parts


"""How many strings one argument may yield before it is not worth enumerating. Reached only by
a concatenation of several ternaries, which is not prose anyone would translate as a whole."""
MAX_ALTERNATIVES = 16


def whole_literals(expression):
    """The strings an argument can evaluate to, if it can only ever be a literal.

    A bare literal yields itself, and a ternary between literals yields both branches, because at
    runtime the whole string arrives at translateText either way. A concatenation counts too when
    every part is itself a literal: the result is a compile time constant, and long prose in these
    sources is routinely split across lines that way. One part that is not a literal makes the
    whole string unknowable, and it is then dropped: what arrives at translateText would not be
    what the source says, so no key could ever match it.
    """
    expression = " ".join(expression.split())
    while expression.startswith("(") and expression.endswith(")"):
        inner = expression[1:-1]
        if len(split_top_level(inner, ")")) > 1:
            break
        expression = " ".join(inner.split())

    match = ONLY_LITERAL.match(expression)
    if match:
        return [match.group(1)]

    # The ternary has to be split before looking for concatenation: + binds tighter than ?:, so
    # a + in one branch says nothing about the other. Checking + first loses the whole branch of
    # "cond ? \"a complete sentence\" : \"a\" + variable".
    branches = split_top_level(expression, "?")
    if len(branches) == 2:
        alternatives = split_top_level(branches[1], ":")
        if len(alternatives) == 2:
            return whole_literals(alternatives[0]) + whole_literals(alternatives[1])

    parts = split_top_level(expression, "+")
    if len(parts) > 1:
        combinations = [""]
        for part in parts:
            alternatives = whole_literals(part)
            if not alternatives:
                return []
            combinations = [prefix + alternative
                            for prefix in combinations
                            for alternative in alternatives]
            if len(combinations) > MAX_ALTERNATIVES:
                return []
        return combinations
    return []


# The longer explanation shown under a solution. It is not a constructor argument but the return
# of an overridden method, so it has to be gathered separately from the descriptions beside it.
EXTENDED_DESCRIPTION = re.compile(
    r"protected\s+String\s+extendedDescription\s*\(\s*\)\s*\{\s*return\b")


def extended_descriptions(text):
    """The expressions the extendedDescription() overrides in one file return."""
    found = []
    for match in EXTENDED_DESCRIPTION.finditer(text):
        start = match.end()
        depth = 0
        in_string = False
        escaped = False
        i = start
        while i < len(text):
            c = text[i]
            if in_string:
                if escaped:
                    escaped = False
                elif c == "\\":
                    escaped = True
                elif c == '"':
                    in_string = False
            elif c == '"':
                in_string = True
            elif c in "([{":
                depth += 1
            elif c in ")]}":
                depth -= 1
            elif c == ";" and depth == 0:
                break
            i += 1
        found.append(text[start:i])
    return found


def solutions_literals():
    """The English prose that reaches Translations.translateText, which is what texts_* is keyed by.

    There is no base bundle listing these: the key is the English itself, so the manifest has to
    be recovered from the sources.
    """
    found = {}
    for path in sorted(SOURCES.rglob("*.java")):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        # A getString argument is a lookup key, not prose. It often sits on a line of its own
        # inside these very constructions, so without this the keys of the first mechanism get
        # collected as if they were text for the second.
        text = re.sub(r'Translations\.getString\(\s*"[^"]*"', "KEY(", text)

        for expression in extended_descriptions(text):
            for literal in whole_literals(expression):
                if len(literal) < 3:
                    continue
                found.setdefault(load_java_literal(literal), path.name)

        for match in CONSTRUCTION.finditer(text):
            start = match.end()
            depth = 1
            in_string = False
            escaped = False
            i = start
            while i < len(text) and depth > 0:
                c = text[i]
                if in_string:
                    if escaped:
                        escaped = False
                    elif c == "\\":
                        escaped = True
                    elif c == '"':
                        in_string = False
                elif c == '"':
                    in_string = True
                elif c == "(":
                    depth += 1
                elif c == ")":
                    depth -= 1
                i += 1
            for argument in split_top_level(text[start:i - 1], ","):
                for literal in whole_literals(argument):
                    if literal.startswith("http") or len(literal) < 3:
                        continue
                    found.setdefault(load_java_literal(literal), path.name)
    return found


# The role an actuator plays, which these helpers paste into a sentence they build. The word is
# English in the source, so a template that lifts it back out needs it translated - see the Word.
# entries in the patterns bundle.
ACTUATOR_QUALIFIER = re.compile(
    r"find(?:Actuate|ActuatorRead)Issues\s*\([^;]*?,\s*\"((?:[^\"\\]|\\.)*)\"\s*,")


def actuator_qualifiers():
    """The English role words the actuator solutions build their wording around."""
    found = set()
    for path in sorted(SOURCES.rglob("*.java")):
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for match in ACTUATOR_QUALIFIER.finditer(text):
            found.add(load_java_literal(match.group(1)))
    return found


# ---------------------------------------------------------------------------
# gap
# ---------------------------------------------------------------------------

def source_entries(family):
    """What a language is expected to cover, as key -> English.

    For translations that is the base bundle. For texts the base bundle is empty on purpose - the
    key is the English prose - so the expectation comes from the sources instead.
    """
    if family == "texts":
        return OrderedDict((text, text) for text in sorted(solutions_literals()))
    return Bundle(bundle_path(family)).entries


def untranslated(lang, family="translations"):
    """Entries a language still needs: absent, or present but identical to the English.

    An entry that repeats the English is ambiguous - it can be an untouched copy or a deliberate
    decision to keep a term in English - so it is reported separately rather than silently
    counted as work.
    """
    expected = source_entries(family)
    other = Bundle(bundle_path(family, lang))
    missing, same = [], []
    for key, value in expected.items():
        if key not in other.entries:
            missing.append((key, value))
        elif other.entries[key] == value and re.search(r"[A-Za-z]{3}", value):
            same.append((key, value))
    return expected, other, missing, same


def cmd_gap(args):
    total_shown = 0
    for family in FAMILIES:
        expected, other, missing, same = untranslated(args.lang, family)
        if not expected:
            continue

        print("=== {} : {} translatable, {} in {} ===".format(
            bundle_path(family, args.lang).name, len(expected), len(other.entries), args.lang))

        graded = Counter(difficulty(v) for _, v in missing)
        print("    missing            : {}  (easy {}, medium {}, hard {})".format(
            len(missing), graded["easy"], graded["medium"], graded["hard"]))
        print("    present as English : {}".format(len(same)))
        print()

        rows = missing if not args.include_same else missing + same
        if args.difficulty:
            rows = [r for r in rows if difficulty(r[1]) == args.difficulty]
        rows.sort(key=lambda r: (difficulty(r[1]), r[0]))
        for key, value in rows[:args.limit]:
            print("  [{}] {}".format(difficulty(value)[0].upper(), key))
            print("        {}".format(value.replace("\n", "\\n")[:150]))
            total_shown += 1
        if len(rows) > args.limit:
            print("  ... {} more".format(len(rows) - args.limit))
        print()
    return 0


# ---------------------------------------------------------------------------
# batch / import
#
# Translators do not edit .properties directly. They get a tab separated batch and fill in one
# column; every structural concern - escaping, sort order, encoding, line endings - stays here.
# ---------------------------------------------------------------------------

BATCH_HEADER = "# key\tenglish\ttranslation"
SKIP = "SKIP"


def batch_encode(text):
    """Make a value safe to carry in one tab separated line, and visible while it is there.

    A raw carriage return or tab inside a cell splits or silently reshapes the row, and a
    translator cannot see it to reproduce it, so every character that is not printable on one line
    is spelled out. The backslash goes first, so that decoding is unambiguous.
    """
    return (text.replace("\\", "\\\\").replace("\t", "\\t")
            .replace("\r", "\\r").replace("\n", "\\n"))


def batch_decode(text):
    """The inverse of batch_encode, in a single left to right pass."""
    out = []
    i = 0
    escapes = {"n": "\n", "r": "\r", "t": "\t", "\\": "\\"}
    while i < len(text):
        if text[i] == "\\" and i + 1 < len(text) and text[i + 1] in escapes:
            out.append(escapes[text[i + 1]])
            i += 2
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def cmd_batch(args):
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    _, _, missing, same = untranslated(args.lang, args.family)
    rows = missing if not args.include_same else missing + same
    if args.difficulty:
        rows = [r for r in rows if difficulty(r[1]) == args.difficulty]
    rows.sort(key=lambda r: (difficulty(r[1]), r[0]))

    if not rows:
        print("nothing to do for {}".format(args.lang))
        return 0

    written = []
    for number, start in enumerate(range(0, len(rows), args.size), start=1):
        chunk = rows[start:start + args.size]
        path = out_dir / "{}-{}-{:03d}.tsv".format(args.family, args.lang, number)
        lines = [
            "# Fill in the third column only. Do not change the first two.",
            "# Keep every %s / %1$s and every <br/> and \\n and \\r that the English has.",
            "# Put {} in the third column for anything you are unsure of; it stays English.".format(SKIP),
            BATCH_HEADER,
        ]
        for key, value in chunk:
            lines.append("{}\t{}\t".format(key, batch_encode(value)))
        path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        written.append(path)

    print("wrote {} batches of up to {} rows to {}".format(len(written), args.size, out_dir))
    for path in written:
        print("  {}".format(path.name))
    return 0


def cmd_import(args):
    batch = Path(args.batch)
    lines = batch.read_text(encoding="utf-8").split("\n")
    rows = []
    for line in lines:
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.rstrip("\r").split("\t")
        if len(parts) < 3:
            print("REJECTED: a row does not have three columns: {!r}".format(line[:80]))
            return 1
        rows.append((parts[0], parts[1], "\t".join(parts[2:])))

    # For texts the expectation comes from the sources, not from the base bundle, which is empty
    # there because the key is the English itself.
    english = source_entries(args.family)
    problems = []
    accepted = OrderedDict()
    skipped = 0

    for key, shown_english, translation in rows:
        if key not in english:
            problems.append("{}: not a translatable string in this family".format(key[:70]))
            continue
        actual = batch_encode(english[key])
        if shown_english != actual:
            problems.append("{}: the English column was altered".format(key))
            continue
        if translation.strip() == SKIP:
            skipped += 1
            continue
        if not translation.strip():
            problems.append("{}: translation is empty (use {} to leave it English)".format(key, SKIP))
            continue
        if "\t" in translation:
            problems.append("{}: translation contains a tab".format(key))
            continue

        value = batch_decode(translation)
        english_value = english[key]

        want, want_mixed = argument_map(english_value)
        got, got_mixed = argument_map(value)
        if want != got:
            problems.append("{}: format arguments differ, English uses {} and the translation {}"
                            .format(key, sorted(want.items()), sorted(got.items())))
            continue
        if got_mixed and not want_mixed:
            problems.append("{}: mixes %s and %1$s forms".format(key))
            continue
        if markers(english_value) != markers(value):
            difference = markers(english_value) - markers(value)
            extra = markers(value) - markers(english_value)
            problems.append("{}: markup differs, missing {} extra {}"
                            .format(key, dict(difference), dict(extra)))
            continue

        accepted[key] = value

    if problems:
        # The whole batch is rejected rather than partly applied: picking the good rows out of a
        # bad batch costs more than translating it again.
        print("REJECTED {} ({} problems, {} rows would have been accepted)"
              .format(batch.name, len(problems), len(accepted)))
        for problem in problems[:20]:
            print("  {}".format(problem))
        if len(problems) > 20:
            print("  ... {} more".format(len(problems) - 20))
        return 1

    target = Bundle(bundle_path(args.family, args.lang))
    target.entries.update(accepted)
    target.save()
    print("{}: merged {} translations, {} left English by request, {} entries now"
          .format(bundle_path(args.family, args.lang).name, len(accepted), skipped,
                  len(target.entries)))
    return 0


# ---------------------------------------------------------------------------
# reuse
# ---------------------------------------------------------------------------

def cmd_reuse(args):
    """Fill entries whose English already has a translation under a different key.

    The bundles repeat themselves heavily - the same "Rotation in Tape" explanation appears under
    four keys, "Feed Count" under six - and translating each copy again is both wasted work and a
    way for the copies to drift apart. Only unambiguous English is filled: if two keys share the
    English but were translated differently, the text is left alone rather than guessed at.
    """
    english = source_entries(args.family)
    target = Bundle(bundle_path(args.family, args.lang))

    by_english = {}
    for key, value in english.items():
        if key in target.entries and target.entries[key] != value:
            by_english.setdefault(value, set()).add(target.entries[key])

    filled = 0
    ambiguous = []
    for key, value in english.items():
        if key in target.entries:
            continue
        candidates = by_english.get(value)
        if not candidates:
            continue
        if len(candidates) > 1:
            ambiguous.append(key)
            continue
        target.entries[key] = next(iter(candidates))
        filled += 1

    if args.apply:
        target.save()
    print("{}: {} entries {} from an identical English string, {} ambiguous and left alone"
          .format(bundle_path(args.family, args.lang).name, filled,
                  "filled" if args.apply else "fillable", len(ambiguous)))
    for key in ambiguous[:args.limit]:
        print("  ambiguous: {}".format(key[:90]))
    return 0


# ---------------------------------------------------------------------------
# check
# ---------------------------------------------------------------------------

def swallowed_leading_space(bundle):
    """Keys written with a leading space that Properties.load throws away.

    The space is invisible in the file and gone at runtime, so it looks deliberate to whoever wrote
    it and does nothing. Where the padding is wanted - the status bar counters use it - the space
    has to be escaped as `\\ `. Worth an error rather than a warning: the two known cases were both
    cosmetic padding that had silently stopped working.
    """
    found = []
    for key, index in bundle.line_of.items():
        raw = bundle.lines[index]
        i = 0
        while i < len(raw):
            if raw[i] == "\\":
                i += 2
                continue
            if raw[i] in "=:":
                if raw[i + 1:i + 2] in (" ", "\t", "\f"):
                    found.append(key)
                break
            i += 1
    return found


def check_language(lang, literals, strict_normalisation):
    """Returns (errors, warnings).

    An error is something a user will see go wrong: a crash from mismatched format arguments, a
    translation silently dropped by a duplicate key, markup printed as text. A warning is dead
    weight that costs nothing at runtime, so it must not fail a gate that is meant to stay green.
    """
    problems = []
    warnings = []
    for family in FAMILIES:
        base_path = bundle_path(family)
        path = bundle_path(family, lang)
        if not path.exists():
            continue
        bundle = Bundle(path)
        english = Bundle(base_path)

        if bundle.has_bom:
            problems.append("{}: has a UTF-8 BOM".format(path.name))
        if bundle.crlf:
            problems.append("{}: has CRLF line endings".format(path.name))
        for key in sorted(set(bundle.duplicates)):
            problems.append("{}: key appears more than once, the later one silently wins: {}"
                            .format(path.name, key))
        for key in swallowed_leading_space(bundle):
            problems.append("{}: the space after the separator is dropped at runtime, escape it "
                            "as \\\\ or remove it: {}".format(path.name, key))

        for key, value in bundle.entries.items():
            if family == "texts":
                # Keyed by the English prose, so the key itself has to still exist in the source.
                # Either as a standalone literal, or as one the sources build by concatenating
                # literals across lines, which is still a constant and still reaches translateText.
                if key not in literals:
                    warnings.append("{}: no source string matches this key any more, so the "
                                    "translation is dead: {!r}".format(path.name, key[:70]))
                english_value = key
            else:
                if key not in english.entries:
                    warnings.append("{}: key is not in the English bundle, so nothing reads it: {}"
                                    .format(path.name, key))
                    continue
                english_value = english.entries[key]

            want, _ = argument_map(english_value)
            got, _ = argument_map(value)
            if want != got:
                problems.append("{}: {} format arguments differ, English {} translation {}"
                                .format(path.name, key[:50], sorted(want.items()), sorted(got.items())))
            if markup_shown_literally(value) and not markup_shown_literally(english_value):
                problems.append("{}: {} carries HTML tags without an <html> prefix, so they will "
                                "be shown as text".format(path.name, key[:50]))

        if strict_normalisation:
            normalised = normalised_body(bundle)
            if normalised != current_body(path):
                problems.append("{}: not in normalised form, run: i18n.py normalize --lang {}"
                                .format(path.name, lang))
    return problems, warnings


def current_body(path):
    text = path.read_bytes().decode("utf-8-sig")
    return [l for l in text.replace("\r\n", "\n").split("\n") if l.strip() and l.strip()[0] not in "#!"]


def normalised_body(bundle):
    return [save_convert(k, True) + "=" + save_convert(bundle.entries[k], False)
            for k in sorted(bundle.entries, key=lambda k: k.encode("utf-16-be"))]


def cmd_check(args):
    langs = discover_languages() if args.lang in (None, "all") else [args.lang]
    literals = set()
    if any(bundle_path("texts", l).exists() for l in langs):
        literals = java_string_literals() | set(source_entries("texts"))

    english = Bundle(bundle_path("translations"))
    problems = []
    warnings = []
    for key in sorted(set(english.duplicates)):
        problems.append("translations.properties: duplicate key: {}".format(key))
    for key in swallowed_leading_space(english):
        problems.append("translations.properties: the space after the separator is dropped at "
                        "runtime, escape it as \\\\ or remove it: {}".format(key))

    for lang in langs:
        errors, warns = check_language(lang, literals, args.strict)
        problems.extend(errors)
        warnings.extend(warns)

    if warnings and not args.quiet:
        print("{} warnings (dead entries; harmless at runtime)".format(len(warnings)))
        for warning in warnings[:args.limit]:
            print("  {}".format(warning))
        if len(warnings) > args.limit:
            print("  ... {} more".format(len(warnings) - args.limit))
        print()

    if problems:
        print("{} errors".format(len(problems)))
        for problem in problems:
            print("  {}".format(problem))
        return 1
    print("checked {}: no errors".format(", ".join(langs)))
    return 0


# ---------------------------------------------------------------------------
# consistency
# ---------------------------------------------------------------------------

# A short label rendered one way on one panel and another way on the next is invisible to a
# reviewer working entry by entry, because each rendering reads correctly on its own. Long values
# are excluded: a sentence legitimately varies with its surroundings, and reporting those buries
# the labels that matter. Markup is excluded for the same reason.
def consistency_report(lang, family, max_len):
    english = Bundle(bundle_path(family)).entries
    target_path = bundle_path(family, lang)
    if not target_path.exists():
        return []
    target = Bundle(target_path).entries

    by_english = {}
    for key, value in english.items():
        if key not in target or len(value) > max_len or "<" in value:
            continue
        by_english.setdefault(value.strip(), {})[key] = target[key].strip()

    findings = []
    for source, renderings in sorted(by_english.items()):
        if len(set(renderings.values())) > 1:
            findings.append((source, renderings))
    return findings


def collision_report(lang, family, max_len):
    """The inverse of consistency: distinct English strings that share one rendering.

    Consistency asks whether a language says one thing two ways. This asks whether it says two
    things the same way, which is the worse defect of the pair: the user is looking at two controls
    that do different things under one label. It found the Italian jog increment buttons, where
    Second and Fourth both read "Incrementa di Quattro".
    """
    english = Bundle(bundle_path(family)).entries
    target_path = bundle_path(family, lang)
    if not target_path.exists():
        return []
    target = Bundle(target_path).entries

    by_rendering = {}
    for key, value in english.items():
        if key not in target or len(value) > max_len or "<" in value:
            continue
        rendering = target[key].strip()
        if not rendering:
            continue
        by_rendering.setdefault(rendering, {})[key] = value.strip()

    findings = []
    for rendering, sources in sorted(by_rendering.items()):
        if len({_collision_form(v) for v in sources.values()}) > 1:
            findings.append((rendering, sources))
    return findings


def _collision_form(english):
    """Reduce English to the distinction a translation is obliged to keep.

    Most collisions are not defects. Chinese marks no plural, so Offset and Offsets must land on
    the same word; "Assigned To" and "Assigned to" differ only in how someone capitalised them.
    Reporting those buries the real finding, which is two unrelated strings sharing a rendering.
    """
    form = english.lower().rstrip(":?.").strip()
    if form.endswith("(s)"):
        form = form[:-3]
    elif form.endswith("es") and not form.endswith("ses"):
        form = form[:-2]
    elif form.endswith("s") and not form.endswith("ss"):
        form = form[:-1]
    return form.strip()


def cmd_collisions(args):
    langs = discover_languages() if args.lang in (None, "all") else [args.lang]
    total = 0
    for lang in langs:
        for family in FAMILIES:
            findings = collision_report(lang, family, args.max_len)
            total += len(findings)
            if not findings or args.quiet:
                continue
            print("{}_{}.properties".format(family, lang))
            for rendering, sources in findings[:args.limit]:
                print("  {!r} stands for {} different English strings".format(
                    rendering, len(set(sources.values()))))
                for key, value in sorted(sources.items(), key=lambda kv: kv[1]):
                    print("    {:32s} {}".format(value, key))
            if len(findings) > args.limit:
                print("  ... {} more".format(len(findings) - args.limit))
            print()
    print("{}: {} renderings cover more than one English string".format(", ".join(langs), total))
    return 0


# ---------------------------------------------------------------------------
# unused
# ---------------------------------------------------------------------------

def unused_report():
    """English keys that no Java source reads.

    A class can be deleted upstream while its keys stay behind in every bundle. Nothing breaks -
    the entries are simply never looked up - but they are not harmless either, because the
    consistency report groups keys by their English text, so a stale key carrying an older
    rendering is indistinguishable from a live disagreement.

    Keys are also assembled at run time ("Placement.Side." + side), so a key with no literal
    occurrence is only a candidate. Every dotted prefix that appears as a literal is treated as a
    possible builder, and a candidate matching one is left alone.
    """
    source = "\n".join(p.read_text(encoding="utf-8", errors="replace")
                       for p in SOURCES.rglob("*.java"))
    literals = set(re.findall(r'"([A-Za-z][\w.]*?)"', source))
    builders = tuple(sorted(set(re.findall(r'"([A-Za-z][\w.]*?\.)"', source))
                            | set(re.findall(r'"([A-Za-z]\w*\.[\w.]*)"\s*\+', source))))
    # And the other way round: a literal with a suffix put after it, key + ".Title".
    suffixes = set(re.findall(r'\+\s*"\.([A-Za-z][\w.]*)"', source))

    dead = []
    for key in sorted(Bundle(RESOURCES / "translations.properties").entries):
        if key in literals:
            continue
        if any(key.startswith(prefix) for prefix in builders):
            continue
        if any(key.endswith("." + suffix) and key[:-len(suffix) - 1] in literals for suffix in suffixes):
            continue
        dead.append(key)
    return dead


def cmd_unused(args):
    dead = unused_report()
    if not dead:
        print("every key in translations.properties is read somewhere")
        return 0

    owners = OrderedDict()
    for key in dead:
        owners.setdefault(key.split(".")[0], []).append(key)

    for owner, members in sorted(owners.items(), key=lambda kv: (-len(kv[1]), kv[0])):
        print("{} ({})".format(owner, len(members)))
        for key in members:
            print("    " + key)
    print()
    print("{} keys are never read; delete them from every bundle, not just English".format(len(dead)))
    return 1 if args.strict else 0


# ---------------------------------------------------------------------------
# patterns
# ---------------------------------------------------------------------------

PATTERN_PLACEHOLDER = re.compile(r"%(?:\d+\$)?s")

# Mirrors Translations.MINIMUM_LITERAL_RUN: a template with no distinctive phrase in it would
# match far more than it was written for, so the runtime refuses to load one.
MINIMUM_LITERAL_RUN = 6

# How much of a template's longest literal run has to still be in the sources for it to count as
# live. Enough to be unique, short enough not to run into the next branch of whatever assembled it.
DISTINCTIVE_PREFIX = 40


def template_regex(english):
    """The matcher Translations builds for a template, and the literal text it rests on."""
    regex = ["^"]
    literals = []
    consumed = 0
    for placeholder in PATTERN_PLACEHOLDER.finditer(english):
        literal = english[consumed:placeholder.start()]
        regex.append(re.escape(literal))
        regex.append("(.+?)")
        literals.append(literal)
        consumed = placeholder.end()
    tail = english[consumed:]
    regex.append(re.escape(tail))
    regex.append("$")
    literals.append(tail)
    return re.compile("".join(regex), re.DOTALL), literals


def cmd_patterns(args):
    """Report what the templates would and would not recognise.

    The risk in matching prose by template is silent on both sides: a reworded source stops being
    recognised, and a loose template starts recognising things it was never meant to. Feed this the
    strings a real machine produced and it says which is happening.
    """
    english = Bundle(bundle_path("patterns")).entries
    templates = OrderedDict((k, v) for k, v in english.items()
                            if not k.startswith(VOCABULARY_PREFIX))
    vocabulary = {v: k for k, v in english.items() if k.startswith(VOCABULARY_PREFIX)}

    problems = 0
    compiled = []
    for key, template in templates.items():
        matcher, literals = template_regex(template)
        if len(literals) == 1:
            print("{}: no placeholder, so this is whole prose and belongs in texts".format(key))
            problems += 1
            continue
        longest = max(len(literal) for literal in literals)
        if longest < MINIMUM_LITERAL_RUN:
            print("{}: longest literal run is {} characters, under the {} the runtime requires, "
                  "so it is ignored".format(key, longest, MINIMUM_LITERAL_RUN))
            problems += 1
            continue
        compiled.append((key, template, matcher, sum(len(l) for l in literals)))
    compiled.sort(key=lambda entry: -entry[3])

    # A template whose English no longer appears in the sources cannot fire, silently. What the
    # sources assemble cannot be reconstructed from here, so the template's most distinctive run of
    # literal text is looked for instead. Two things have to be undone first: these descriptions are
    # written as a literal per source line, and their quotes are escaped for Java.
    if not args.against:
        source = "\n".join(p.read_text(encoding="utf-8", errors="replace")
                           for p in SOURCES.rglob("*.java"))
        source = re.sub(r'"\s*\+\s*"', "", source).replace('\\"', '"')
        for key, template, _, _ in compiled:
            _, literals = template_regex(template)
            # Any run long enough to be distinctive will do, and only its opening. A run reaches
            # past a branch in the source as often as not, where the text stops being contiguous
            # through no fault of the template, and the run that does so is frequently the longest
            # one. This says the wording is still there; --against says the template still reaches
            # it, which is the question that actually matters.
            candidates = sorted((l for l in literals if len(l) >= MINIMUM_LITERAL_RUN),
                                key=len, reverse=True)
            if candidates and not any(l[:DISTINCTIVE_PREFIX] in source for l in candidates):
                print("{}: no source builds this any more, the wording {!r} is gone"
                      .format(key, candidates[0][:60]))
                problems += 1
        for word, key in sorted(vocabulary.items()):
            if '"{}"'.format(word) not in source:
                print("{}: no source passes {!r} any more".format(key, word))
                problems += 1
        for qualifier in sorted(actuator_qualifiers() - set(vocabulary)):
            print("no Word. entry for the actuator role {!r}, so a template that lifts it out "
                  "would leave it English".format(qualifier))
            problems += 1
        print()
        print("{} templates, {} words, {} problems".format(len(compiled), len(vocabulary), problems))
        return 1 if problems and args.strict else 0

    lines = [l.strip() for l in Path(args.against).read_text(encoding="utf-8").split("\n")
             if l.strip() and not l.startswith("#")]
    # Prose another mechanism already covers needs no template, and saying so would bury the
    # strings that do. Two of them cover prose: texts, keyed by the English itself, and ordinary
    # keys, whose English sits in translations.properties and sometimes has a name appended to it.
    # Ambiguity is still worth reporting for these, since a template that reaches one is too loose.
    whole = set(source_entries("texts"))
    keyed = [v for v in Bundle(bundle_path("translations")).entries.values() if len(v) >= 10]

    def covered_elsewhere(line):
        return line in whole or any(line.startswith(v) for v in keyed)

    uncovered = []
    ambiguous = []
    matched = 0
    for line in lines:
        hits = [key for key, _, matcher, _ in compiled if matcher.match(line)]
        if len(hits) > 1:
            ambiguous.append((line, hits))
        if hits:
            matched += 1
        elif not covered_elsewhere(line):
            uncovered.append(line)

    for line, hits in ambiguous:
        print("matched by {}, the first wins: {!r}".format(", ".join(hits), line[:80]))
    if ambiguous:
        print()
    for line in uncovered:
        print("nothing covers this: {!r}".format(line[:110]))
    if uncovered:
        print()
    print("{} strings: {} by template, {} by whole prose, {} covered by nothing, {} ambiguous"
          .format(len(lines), matched, len(lines) - matched - len(uncovered), len(uncovered),
                  len(ambiguous)))
    return 1 if (uncovered or ambiguous or problems) and args.strict else 0


def cmd_consistency(args):
    langs = discover_languages() if args.lang in (None, "all") else [args.lang]
    total = 0
    for lang in langs:
        for family in FAMILIES:
            findings = consistency_report(lang, family, args.max_len)
            total += len(findings)
            if not findings or args.quiet:
                continue
            print("{}_{}.properties".format(family, lang))
            for source, renderings in findings[:args.limit]:
                print("  {!r} is rendered {} ways".format(source, len(set(renderings.values()))))
                for key, value in sorted(renderings.items(), key=lambda kv: kv[1]):
                    print("    {:24s} {}".format(value, key))
            if len(findings) > args.limit:
                print("  ... {} more".format(len(findings) - args.limit))
            print()
    print("{}: {} English strings have more than one rendering".format(", ".join(langs), total))
    return 0


# ---------------------------------------------------------------------------
# normalize
# ---------------------------------------------------------------------------

def cmd_normalize(args):
    langs = discover_languages() if args.lang in (None, "all") else [args.lang]
    targets = []
    if args.lang == "en":
        targets = [bundle_path(f) for f in FAMILIES if bundle_path(f).exists()]
    else:
        for lang in langs:
            targets.extend(bundle_path(f, lang) for f in FAMILIES if bundle_path(f, lang).exists())

    for path in targets:
        bundle = Bundle(path)
        stranded = sum(1 for i in range(len(bundle.lines))
                       if bundle.lines[i].strip().startswith("#") and i >= len(bundle.header))
        if stranded:
            print("{}: refusing, {} comment lines sit between entries and sorting would strand "
                  "them; move them into the header block first".format(path.name, stranded))
            continue
        before = path.read_bytes()
        bundle.normalize()
        after = path.read_bytes()
        print("{}: {}".format(path.name, "rewritten" if before != after else "already normalised"))
    return 0


# ---------------------------------------------------------------------------
# hardcoded
# ---------------------------------------------------------------------------

# Requiring a space is what separates display text from a dotted lookup key: without it, the key
# inside Translations.getString("JobPanel.SaveJob.Error") counts as untranslated English and the
# totals come out roughly a third too high.
WIDGET_PATTERNS = {
    "JLabel": re.compile(r'new JLabel\("([A-Za-z][^"]{3,})"\)'),
    "JButton": re.compile(r'new JButton\("([A-Za-z][^"]{3,})"\)'),
    "JCheckBox": re.compile(r'new JCheckBox\("([A-Za-z][^"]{3,})"\)'),
    # Menus are built by hand rather than by WindowBuilder, which is why they were missed for so
    # long: nothing in the form editor ever shows them as externalisable strings.
    "JMenu": re.compile(r'new JMenu\("([A-Za-z][^"]{1,})"\)'),
    "JMenuItem": re.compile(r'new J(?:RadioButton|CheckBox)?MenuItem\("([A-Za-z][^"]{1,})"\)'),
    "setToolTip": re.compile(r'setToolTipText\("([A-Za-z<][^"]*\s[^"]*)"\)'),
    "TitledBorder": re.compile(r'TitledBorder\([^;]*?"([A-Za-z][^"]*\s[^"]*)"', re.S),
    "errorBox": re.compile(r'MessageBoxes\.errorBox\s*\([^;]*?"([A-Za-z][^"]*\s[^"]*)"', re.S),
    "JOptionPane": re.compile(r'JOptionPane\.show[A-Za-z]*Dialog\s*\([^;]*?"([A-Za-z][^"]*\s[^"]*)"', re.S),
}

AREAS = (
    ("vendor", re.compile(r"/machine/(neoden4|photon|rapidplacer|pandaplacer)/")),
    ("feeder-wizards", re.compile(r"/feeder/")),
    ("machine-wizards", re.compile(r"/machine/reference/")),
    ("common-ui", re.compile(r"/openpnp/gui/|/vision/pipeline/ui/")),
)


def area_of(path):
    text = str(path).replace("\\", "/")
    for name, pattern in AREAS:
        if pattern.search(text):
            return name
    return "other"


def cmd_hardcoded(args):
    by_kind = Counter()
    by_area = Counter()
    per_file = Counter()
    samples = []

    for path in SOURCES.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        # A getString call holds a key, not display text; drop those before matching.
        text = re.sub(r'Translations\.getString\(\s*"[^"]*"', "Translations.getString(KEY", text)
        for kind, pattern in WIDGET_PATTERNS.items():
            hits = pattern.findall(text)
            if hits:
                by_kind[kind] += len(hits)
                by_area[area_of(path)] += len(hits)
                per_file[path.name] += len(hits)
                if args.area in (None, area_of(path)):
                    samples.extend((path.name, kind, h) for h in hits)

    print("=== by area ===")
    for area, count in by_area.most_common():
        print("  {:<18} {}".format(area, count))
    print()
    print("=== by widget ===")
    for kind, count in by_kind.most_common():
        print("  {:<14} {}".format(kind, count))
    print()
    print("total {}".format(sum(by_kind.values())))

    if args.files:
        print()
        print("=== per file ===")
        for name, count in per_file.most_common(args.files):
            print("  {:>4}  {}".format(count, name))
    if args.area:
        print()
        print("=== literals in {} ===".format(args.area))
        for name, kind, hit in samples[:args.limit]:
            print("  {:<44} {:<12} {}".format(name, kind, hit[:70]))
    return 0


# ---------------------------------------------------------------------------
# externalize
# ---------------------------------------------------------------------------

# Text that is in the source in English but is not display text, so moving it into a bundle would
# invite someone to translate a filename or a layout measurement.
NOT_TRANSLATABLE = (
    (re.compile(r"^[\w.-]+\.(xml|log|txt|json|properties|js|py|bsh)$", re.I), "a filename"),
    (re.compile(r"^(New label|New JLabel|New check ?box|New JCheckBox|New button|New JButton"
                r"|newLabel|None|N/A|\.\.\.)$", re.I), "a placeholder left by the form editor"),
    (re.compile(r"^[A-Za-z]\s*[\d.]{4,}[\s,]"), "a template used to size a field, not shown"),
    # A label bound to a numeric property carries this only until the binding first fires, which
    # is before the wizard is visible, so the word itself never reaches a user.
    (re.compile(r"^(min|max|def)$"), "a design time placeholder for a bound value"),
    (re.compile(r"^(OpenPnP|Gcode|G-code|TCP|UDP|HTTP|USB|CSV|XML|JSON|OpenCV|Java)$"), "a name"),
    (re.compile(r"^[^A-Za-z]*$"), "no letters"),
    (re.compile(r"©|\(c\)\s*\d{4}", re.I), "a copyright notice"),
)

# new JLabel("x") / new JButton("x") / setToolTipText("x"), with what precedes them so the widget
# can be named after the variable it is assigned to.
EXTERNALIZE_PATTERNS = (
    ("text", re.compile(r'(?P<lead>(?:(?P<var>\w+)\s*=\s*)?'
                        r'new (?:JLabel|JButton|JCheckBox'
                        r'|JMenu|J(?:RadioButton|CheckBox)?MenuItem)\()'
                        r'"(?P<text>[A-Za-z][^"]{2,})"\)')),
    # A tooltip may open with <html>, and the long explanatory ones usually do, so the first
    # character cannot be required to be a letter the way a label's can.
    ("toolTipText", re.compile(r'(?P<lead>(?P<var>\w+)\.setToolTipText\()'
                               r'"(?P<text>[A-Za-z<](?:[^"\\]|\\.)*\s(?:[^"\\]|\\.)*)"\)')),
    # The title is the second argument, and the panel it belongs to names the key, which is the
    # convention the existing Border.title keys follow.
    ("Border.title", re.compile(r'(?P<lead>(?P<var>\w+)\.setBorder\(new TitledBorder\(\s*[^,]*,\s*)'
                                r'"(?P<text>[A-Za-z][^"]{2,})"(?P<tail>)')),
)

# WindowBuilder names a widget it was given no name for, and those names say nothing about the
# text. Keys built from them read as noise, so the text itself is the better identifier.
#
# The menu variables are here for a different reason: a hand-built menu reassigns one variable for
# every item in turn, so naming keys after it yields menuItem, menuItem2, menuItem3 - which say
# nothing, and shift the moment an item is inserted above.
GENERATED_NAME = re.compile(
    r"^(lbl|label|btn|button|chckbx|checkBox|txt|textField|panel|separator"
    r"|menu|menuItem|subMenu)?"
    r"(New\w*)?[_]?\d*$", re.I)

CAMEL = re.compile(r"[^A-Za-z0-9]+")

# Placeholder for "a non-NLS marker belongs on this line", resolved once the line is complete.
MARK = "\x00"


def place_nls_markers(line):
    """Append the Eclipse non-NLS markers a line's new getString calls need, at its end.

    The marker says "the string literal on this line is deliberately not translated", which is
    true of the lookup key itself. It has to sit after everything else on the line, including the
    semicolon.
    """
    count = line.count(MARK)
    if not count:
        return line
    line = line.replace(MARK, "")
    existing = line.count("$NON-NLS-")
    markers = " ".join("//$NON-NLS-{}$".format(existing + i + 1) for i in range(count))
    return line.rstrip() + " " + markers


def key_fragment(text):
    """A stable identifier built from the text, for widgets held in no variable."""
    words = [w for w in CAMEL.split(text) if w]
    fragment = "".join(w[:1].upper() + w[1:] for w in words[:4])
    return fragment[:40] or "Text"


def why_not_translatable(text):
    for pattern, reason in NOT_TRANSLATABLE:
        if pattern.search(text):
            return reason
    return None


def ensure_import(text):
    """Add the Translations import in sorted position, if the file does not have it."""
    if re.search(r"(?m)^import org\.openpnp\.Translations;", text):
        return text
    if re.search(r"(?m)^package org\.openpnp;", text):
        return text
    imports = list(re.finditer(r"(?m)^import org\.openpnp\.[^;]+;", text))
    line = "import org.openpnp.Translations;\n"
    if imports:
        for match in imports:
            name = match.group(0)[len("import org.openpnp."):-1]
            if name > "Translations":
                return text[:match.start()] + line + text[match.start():]
        last = imports[-1]
        return text[:last.end() + 1] + line + text[last.end() + 1:]
    anchor = list(re.finditer(r"(?m)^import [^;]+;\n", text))
    if not anchor:
        return None
    return text[:anchor[-1].end()] + "\n" + line + text[anchor[-1].end():]


def cmd_externalize(args):
    english = Bundle(bundle_path("translations"))
    added = OrderedDict()
    edits = 0
    files_touched = 0
    skipped = []

    for path in sorted(SOURCES.rglob("*.java")):
        if args.area and area_of(path) != args.area:
            continue
        if args.file and args.file not in path.name:
            continue
        text = path.read_text(encoding="utf-8")
        original = text
        class_name = path.stem
        # key -> the English it was given. A hand-built menu repeats words like "Options" and
        # "Units" across its submenus; those want one key, not Options2 and Units2, both so that
        # each language translates the word once and so that the copies cannot drift apart.
        used = {}

        for prop, pattern in EXTERNALIZE_PATTERNS:
            def replace(match):
                nonlocal edits
                # The source form still carries Java's escapes; the bundle needs the characters
                # they stand for, so that save_convert can escape them the way Java's Properties
                # does. Storing "\\r\\n" verbatim would put a literal backslash in the tooltip.
                body = load_java_literal(match.group("text"))
                reason = why_not_translatable(body)
                if reason:
                    skipped.append((path.name, body, reason))
                    return match.group(0)

                variable = match.group("var")
                if variable and GENERATED_NAME.match(variable):
                    variable = None
                name = variable if variable else key_fragment(body)
                key = "{}.{}.{}".format(class_name, name, prop)
                suffix = 2
                while ((key in used and used[key] != body)
                        or (key in english.entries and english.entries[key] != body)):
                    key = "{}.{}{}.{}".format(class_name, name, suffix, prop)
                    suffix += 1
                used[key] = body
                added[key] = body
                edits += 1
                # The marker cannot go here: the statement's semicolon comes after this match, so
                # an inline comment would swallow it. Mark the spot and place it at end of line.
                closing = "" if "tail" in match.groupdict() else ")"
                return '{}Translations.getString("{}"){}{}'.format(
                    match.group("lead"), key, closing, MARK)

            text = pattern.sub(replace, text)

        if MARK in text:
            text = "\n".join(place_nls_markers(line) for line in text.split("\n"))

        if text != original:
            text = ensure_import(text)
            if text is None:
                print("  cannot place an import in {}, skipping the file".format(path.name))
                continue
            files_touched += 1
            if args.apply:
                path.write_text(text, encoding="utf-8", newline="\n")

    if skipped and not args.quiet:
        print("=== left alone ({}) ===".format(len(skipped)))
        for name, body, reason in skipped[:args.limit]:
            print("  {:<40} {:<26} {}".format(name, reason, body[:46]))
        if len(skipped) > args.limit:
            print("  ... {} more".format(len(skipped) - args.limit))
        print()

    print("=== {} {} strings in {} files ===".format(
        "externalised" if args.apply else "would externalise", edits, files_touched))
    for key, value in list(added.items())[:args.limit]:
        print("  {} = {}".format(key, value[:60]))
    if len(added) > args.limit:
        print("  ... {} more".format(len(added) - args.limit))

    if args.apply and added:
        english.entries.update(added)
        english.save()
        print()
        print("translations.properties: {} keys added, {} entries now"
              .format(len(added), len(english.entries)))
    return 0


# ---------------------------------------------------------------------------

def main():
    # These commands print translated text, so the console encoding has to cope with it. A
    # Windows console defaults to a local code page and would otherwise abort the whole run on
    # the first character it cannot represent.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("gap", help="what is left to translate for one language")
    p.add_argument("--lang", required=True)
    p.add_argument("--limit", type=int, default=40)
    p.add_argument("--difficulty", choices=("easy", "medium", "hard"))
    p.add_argument("--include-same", action="store_true",
                   help="also list entries that are present but still the English")
    p.set_defaults(func=cmd_gap)

    p = sub.add_parser("batch", help="write the gap out as batches for a translator")
    p.add_argument("--lang", required=True)
    p.add_argument("--out", default="build/i18n-batches")
    p.add_argument("--size", type=int, default=50)
    p.add_argument("--family", choices=FAMILIES, default="translations")
    p.add_argument("--difficulty", choices=("easy", "medium", "hard"))
    p.add_argument("--include-same", action="store_true")
    p.set_defaults(func=cmd_batch)

    p = sub.add_parser("import", help="validate a filled in batch and merge it")
    p.add_argument("--lang", required=True)
    p.add_argument("--batch", required=True)
    p.add_argument("--family", choices=FAMILIES, default="translations")
    p.set_defaults(func=cmd_import)

    p = sub.add_parser("reuse", help="fill entries whose English is already translated elsewhere")
    p.add_argument("--lang", required=True)
    p.add_argument("--family", choices=FAMILIES, default="translations")
    p.add_argument("--apply", action="store_true", help="write; otherwise only count")
    p.add_argument("--limit", type=int, default=20)
    p.set_defaults(func=cmd_reuse)

    p = sub.add_parser("check", help="the invariants that otherwise fail silently")
    p.add_argument("--lang", default="all")
    p.add_argument("--strict", action="store_true",
                   help="also require the normalised form, as the English bundle must be")
    p.add_argument("--quiet", action="store_true", help="errors only, no dead entry warnings")
    p.add_argument("--limit", type=int, default=15)
    p.set_defaults(func=cmd_check)

    p = sub.add_parser("consistency", help="short labels one language renders more than one way")
    p.add_argument("--lang", default="all")
    p.add_argument("--max-len", type=int, default=40,
                   help="ignore English longer than this; sentences vary with context")
    p.add_argument("--quiet", action="store_true", help="the count only")
    p.add_argument("--limit", type=int, default=40)
    p.set_defaults(func=cmd_consistency)

    p = sub.add_parser("collisions", help="one rendering standing for several English strings")
    p.add_argument("--lang", default="all")
    p.add_argument("--max-len", type=int, default=40,
                   help="ignore English longer than this; sentences vary with context")
    p.add_argument("--quiet", action="store_true", help="the count only")
    p.add_argument("--limit", type=int, default=40)
    p.set_defaults(func=cmd_collisions)

    p = sub.add_parser("patterns", help="what the runtime templates do and do not recognise")
    p.add_argument("--against", metavar="FILE",
                   help="a file of assembled English, one string per line, to match the templates "
                        "against; without it the templates are checked against the sources")
    p.add_argument("--strict", action="store_true",
                   help="exit non-zero when anything is unmatched or unsound")
    p.set_defaults(func=cmd_patterns)

    p = sub.add_parser("unused", help="keys no Java source reads")
    p.add_argument("--strict", action="store_true",
                   help="exit non-zero when any are found, for use in a pre-commit run")
    p.set_defaults(func=cmd_unused)

    p = sub.add_parser("normalize", help="rewrite bundles in normalised escaped form")
    p.add_argument("--lang", default="all", help="a language code, en, or all")
    p.set_defaults(func=cmd_normalize)

    p = sub.add_parser("externalize", help="move hardcoded English into the English bundle")
    p.add_argument("--area", choices=[a for a, _ in AREAS] + ["other"])
    p.add_argument("--file", help="restrict to files whose name contains this")
    p.add_argument("--apply", action="store_true", help="write; otherwise only report")
    p.add_argument("--quiet", action="store_true")
    p.add_argument("--limit", type=int, default=30)
    p.set_defaults(func=cmd_externalize)

    p = sub.add_parser("hardcoded", help="English baked into the Java sources")
    p.add_argument("--area", choices=[a for a, _ in AREAS] + ["other"])
    p.add_argument("--files", type=int, default=0, help="also list the top N files")
    p.add_argument("--limit", type=int, default=60)
    p.set_defaults(func=cmd_hardcoded)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
