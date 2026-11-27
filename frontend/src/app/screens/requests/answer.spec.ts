import { describe, expect, it } from 'vitest';
import { responseBody } from './answer';

describe('responseBody: the selection becomes the request', () => {
  const selection = { pageNumber: 2, charStart: 14, charEnd: 52, quote: 'Week 3: Legal standards\nand duty to warn' };

  it('sends the document, the page and the quote exactly as stored, with the trimmed comment', () => {
    expect(responseBody('d1', selection, '  See week 3.  ')).toEqual({ documentId: 'd1', pageNumber: 2, quote: 'Week 3: Legal standards\nand duty to warn', comment: 'See week 3.' });
  });

  it('sends a blank comment as null and never sends the offsets (the server finds them again)', () => {
    const body = responseBody('d1', selection, '   ');
    expect(body.comment).toBeNull();
    expect(Object.keys(body).sort()).toEqual(['comment', 'documentId', 'pageNumber', 'quote']);
  });
});
