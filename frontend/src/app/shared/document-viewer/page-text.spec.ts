import { beforeEach, describe, expect, it } from 'vitest';
import { isRange, ocrNote, rangeOffsets, softWrap, splitForHighlight } from './page-text';

/** A fictional page with a line break, a double space and a tab, so whitespace must be counted as stored. */
const PAGE = 'COUN 605 Ethics\nStudents complete  40 hours of\tsupervised practice.\nFictional syllabus.';
const QUOTE = 'complete  40 hours';
const START = PAGE.indexOf(QUOTE);
const END = START + QUOTE.length;

describe('splitForHighlight', () => {
  it('cuts the text into before, quote and after at the stored offsets', () => {
    expect(splitForHighlight(PAGE, START, END)).toEqual({ before: PAGE.slice(0, START), quote: QUOTE, after: PAGE.slice(END) });
  });

  it('handles a span at the very start and at the very end of the page', () => {
    expect(splitForHighlight(PAGE, 0, 4)).toEqual({ before: '', quote: 'COUN', after: PAGE.slice(4) });
    expect(splitForHighlight(PAGE, PAGE.length - 9, PAGE.length)).toEqual({ before: PAGE.slice(0, -9), quote: 'syllabus.', after: '' });
  });

  it('refuses offsets that do not describe a non-empty span inside the text', () => {
    expect(splitForHighlight(PAGE, -1, 4)).toBeNull();
    expect(splitForHighlight(PAGE, 4, 4)).toBeNull();
    expect(splitForHighlight(PAGE, 8, 4)).toBeNull();
    expect(splitForHighlight(PAGE, 0, PAGE.length + 1)).toBeNull();
    expect(splitForHighlight(PAGE, 1.5, 4)).toBeNull();
    expect(splitForHighlight('', 0, 0)).toBeNull();
  });

  it('agrees with isRange', () => {
    expect(isRange(PAGE, START, END)).toBe(true);
    expect(isRange(PAGE, END, START)).toBe(false);
  });
});

describe('ocrNote', () => {
  it('names OCR and its rounded confidence, and says nothing for a text layer', () => {
    expect(ocrNote({ textSource: 'OCR', ocrConfidence: 71.25 })).toBe('Read by OCR, 71 % confidence');
    expect(ocrNote({ textSource: 'OCR', ocrConfidence: 93.5 })).toBe('Read by OCR, 94 % confidence');
    expect(ocrNote({ textSource: 'TEXT_LAYER', ocrConfidence: null })).toBeNull();
  });
});

describe('rangeOffsets', () => {
  let pre: HTMLPreElement;
  let markText: Text;
  let beforeText: Text;
  let afterText: Text;

  /** The fixture page as the viewer renders it: text node, mark around the quote, text node. */
  beforeEach(() => {
    document.body.replaceChildren();
    pre = document.createElement('pre');
    beforeText = document.createTextNode(PAGE.slice(0, START));
    const mark = document.createElement('mark');
    markText = document.createTextNode(QUOTE);
    mark.append(markText);
    afterText = document.createTextNode(PAGE.slice(END));
    pre.append(beforeText, mark, afterText);
    document.body.append(pre);
  });

  function range(startNode: Node, startOffset: number, endNode: Node, endOffset: number): Range {
    const r = document.createRange();
    r.setStart(startNode, startOffset);
    r.setEnd(endNode, endOffset);
    return r;
  }

  it('measures a selection inside one text node against the page text', () => {
    const at = PAGE.indexOf('Students');
    const offsets = rangeOffsets(pre, range(beforeText, at, beforeText, at + 'Students'.length));
    expect(offsets).toEqual({ charStart: at, charEnd: at + 8 });
    expect(PAGE.slice(offsets!.charStart, offsets!.charEnd)).toBe('Students');
  });

  it('counts whitespace exactly as stored, line breaks and tabs included', () => {
    const at = PAGE.indexOf('of\t');
    const end = PAGE.indexOf('Fictional') + 'Fictional'.length;
    const offsets = rangeOffsets(pre, range(afterText, at - END, afterText, end - END));
    expect(offsets).toEqual({ charStart: at, charEnd: end });
    expect(PAGE.slice(offsets!.charStart, offsets!.charEnd)).toBe('of\tsupervised practice.\nFictional');
  });

  it('measures a selection that crosses the mark boundary in either direction', () => {
    const from = PAGE.indexOf('Students');
    const into = range(beforeText, from, markText, 'complete'.length);
    expect(PAGE.slice(...bounds(rangeOffsets(pre, into)))).toBe('Students complete');

    const to = PAGE.indexOf('supervised') + 'supervised'.length;
    const outOf = range(markText, 'complete  '.length, afterText, to - END);
    expect(PAGE.slice(...bounds(rangeOffsets(pre, outOf)))).toBe('40 hours of\tsupervised');

    const across = range(beforeText, from, afterText, to - END);
    expect(PAGE.slice(...bounds(rangeOffsets(pre, across)))).toBe('Students complete  40 hours of\tsupervised');
  });

  it('measures boundaries given as element positions, as a triple click produces', () => {
    expect(rangeOffsets(pre, range(pre, 0, pre, pre.childNodes.length))).toEqual({ charStart: 0, charEnd: PAGE.length });
    expect(rangeOffsets(pre, range(pre, 1, pre, 2))).toEqual({ charStart: START, charEnd: END });
  });

  it('ignores a collapsed selection', () => {
    expect(rangeOffsets(pre, range(beforeText, 3, beforeText, 3))).toBeNull();
  });

  it('ignores a selection that reaches outside the page text', () => {
    const outside = document.createElement('p');
    outside.textContent = 'Page 1 of 3';
    document.body.prepend(outside);
    expect(rangeOffsets(pre, range(outside.firstChild!, 0, beforeText, 4))).toBeNull();
  });
});

function bounds(offsets: { charStart: number; charEnd: number } | null): [number, number] {
  expect(offsets).not.toBeNull();
  return [offsets!.charStart, offsets!.charEnd];
}

/** A fictional PDF page the way text extraction stores it: one line per printed line. */
const WRAPPED = 'Course description\nStudents examine the ACA Code of\nEthics, state licensing law,\nconfidentiality, duty to warn,\nand the legal standards (with\n(case studies) in 3\n12 weeks. Required course.\nfinal line\n\nnew paragraph';

describe('softWrap', () => {
  it('joins a line that continues in lower case, a digit or a parenthesis, and keeps every other break', () => {
    expect(softWrap(WRAPPED)).toBe('Course description\nStudents examine the ACA Code of\nEthics, state licensing law, confidentiality, duty to warn, and the legal standards (with (case studies) in 3 12 weeks. Required course.\nfinal line\n\nnew paragraph');
  });

  it('keeps a break after sentence-final punctuation even when the next line starts in lower case', () => {
    expect(softWrap('One.\ntwo! three?\nfour')).toBe('One.\ntwo! three?\nfour');
  });

  it('keeps a break before an upper-case letter, at either end of the text and after another break', () => {
    expect(softWrap('Code of\nEthics')).toBe('Code of\nEthics');
    expect(softWrap('\nstart')).toBe('\nstart');
    expect(softWrap('end\n')).toBe('end\n');
    expect(softWrap('a\n\nb')).toBe('a\n\nb');
  });

  it('treats lower-case letters beyond ASCII as continuing the sentence', () => {
    expect(softWrap('la\nédition')).toBe('la édition');
  });

  it('is one character for one character: same length, and only joined breaks differ', () => {
    const shown = softWrap(WRAPPED);
    expect(shown.length).toBe(WRAPPED.length);
    for (let i = 0; i < WRAPPED.length; i++) {
      if (shown[i] !== WRAPPED[i]) {
        expect([WRAPPED[i], shown[i]]).toEqual(['\n', ' ']);
      }
    }
  });

  it('maps a selection on the shown text back to the stored text exactly, across the mark and joined breaks', () => {
    const quote = 'state licensing law,\nconfidentiality';
    const start = WRAPPED.indexOf(quote);
    const end = start + quote.length;
    const cut = splitForHighlight(softWrap(WRAPPED), start, end)!;
    expect(cut.quote).toBe('state licensing law, confidentiality');
    // The viewer renders the shown text: text node, mark, text node.
    document.body.replaceChildren();
    const pre = document.createElement('pre');
    const mark = document.createElement('mark');
    mark.append(cut.quote);
    pre.append(cut.before, mark, cut.after);
    document.body.append(pre);
    const [before, , after] = Array.from(pre.childNodes);
    const range = document.createRange();
    range.setStart(before, WRAPPED.indexOf('Ethics'));
    range.setEnd(after, WRAPPED.indexOf('warn') + 'warn'.length - end);
    const offsets = rangeOffsets(pre, range)!;
    expect(WRAPPED.slice(offsets.charStart, offsets.charEnd)).toBe('Ethics, state licensing law,\nconfidentiality, duty to warn');
    expect(rangeOffsets(pre, rangeOver(mark.firstChild!))).toEqual({ charStart: start, charEnd: end });
  });
});

function rangeOver(node: Node): Range {
  const r = document.createRange();
  r.selectNodeContents(node);
  return r;
}
