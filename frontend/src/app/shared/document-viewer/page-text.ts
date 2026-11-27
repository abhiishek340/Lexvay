/** Page-relative character offsets, end exclusive, exactly as `Evidence` carries them. */
export interface CharRange { charStart: number; charEnd: number; }

/** A passage a person chose on a page: the offsets and the stored text between them. */
export interface PassageSelection extends CharRange { pageNumber: number; quote: string; }

/** The page text cut around the highlighted span. */
export interface HighlightParts { before: string; quote: string; after: string; }

/**
 * Cuts the page text at the stored offsets. Returns null when the offsets do not describe a non-empty span
 * inside the text, so the caller shows the page without a mark rather than marking the wrong words.
 * The browser never searches for the quote: the server verified it, the viewer only shows where it is.
 */
export function splitForHighlight(text: string, start: number, end: number): HighlightParts | null {
  if (!isRange(text, start, end)) {
    return null;
  }
  return { before: text.slice(0, start), quote: text.slice(start, end), after: text.slice(end) };
}

/** True when start and end are whole offsets with 0 <= start < end <= text.length. */
export function isRange(text: string, start: number, end: number): boolean {
  return Number.isInteger(start) && Number.isInteger(end) && start >= 0 && start < end && end <= text.length;
}

/**
 * The character offsets of a DOM range within the element that holds the page text, or null when the range
 * is collapsed or not entirely inside that element. Offsets count the characters of the text nodes before
 * each boundary, so they are measured on the text exactly as stored, whitespace included, and a range that
 * starts or ends inside the highlight mark counts the same as one in plain text.
 */
export function rangeOffsets(container: Node, range: AbstractRange): CharRange | null {
  if (range.collapsed || !container.contains(range.startContainer) || !container.contains(range.endContainer)) {
    return null;
  }
  const charStart = offsetOf(container, range.startContainer, range.startOffset);
  const charEnd = offsetOf(container, range.endContainer, range.endOffset);
  return charStart < charEnd ? { charStart, charEnd } : null;
}

/** Characters of text from the start of the container to a boundary point inside it. */
function offsetOf(container: Node, node: Node, offset: number): number {
  const before = container.ownerDocument!.createRange();
  before.setStart(container, 0);
  before.setEnd(node, offset);
  return before.toString().length;
}
