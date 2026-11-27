import { TestBed } from '@angular/core/testing';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApiService } from './api.service';
import { DOCUMENT_PAGE_CACHE_SIZE, DocumentPageCache } from './document-page-cache';
import { DocumentPage } from './models';

function page(documentId: string, pageNumber: number): DocumentPage {
  return { documentId, filename: 'f.pdf', kind: 'SYLLABUS', courseCode: null, courseRequired: null, pageNumber, pageCount: 99, text: `${documentId} ${pageNumber}` };
}

/** Records every request; each one completes only when the test says so. */
class FakeApi {
  readonly requests: { key: string; response: Subject<DocumentPage> }[] = [];
  documentPage(documentId: string, pageNumber: number): Observable<DocumentPage> {
    const response = new Subject<DocumentPage>();
    this.requests.push({ key: `${documentId}:${pageNumber}`, response });
    return response;
  }
  answer(index: number): void {
    const { key, response } = this.requests[index];
    const [documentId, pageNumber] = key.split(':');
    response.next(page(documentId, Number(pageNumber)));
    response.complete();
  }
  count(key: string): number {
    return this.requests.filter((r) => r.key === key).length;
  }
}

describe('DocumentPageCache', () => {
  let api: FakeApi;
  let cache: DocumentPageCache;

  beforeEach(() => {
    api = new FakeApi();
    TestBed.configureTestingModule({ providers: [{ provide: ApiService, useValue: api }] });
    cache = TestBed.inject(DocumentPageCache);
  });

  /** Asks for a page that must not be cached: exactly one new request goes out, and it is answered. */
  async function load(documentId: string, pageNumber: number): Promise<DocumentPage> {
    const before = api.requests.length;
    const pending = firstValueFrom(cache.page(documentId, pageNumber));
    expect(api.requests).toHaveLength(before + 1);
    api.answer(before);
    return pending;
  }

  it('shares a request that is still in flight: the same observable, one request', async () => {
    const first = cache.page('d', 1);
    const second = cache.page('d', 1);
    expect(second).toBe(first);
    const both = Promise.all([firstValueFrom(first), firstValueFrom(second)]);
    api.answer(0);
    const [a, b] = await both;
    expect(a.text).toBe('d 1');
    expect(b).toBe(a);
    expect(api.requests).toHaveLength(1);
  });

  it('answers a loaded page from memory without a new request', async () => {
    await load('d', 1);
    expect((await firstValueFrom(cache.page('d', 1))).text).toBe('d 1');
    expect(api.count('d:1')).toBe(1);
  });

  it('keeps one entry per document and page', async () => {
    await load('d', 1);
    await load('d', 2);
    await load('e', 1);
    expect(api.requests.map((r) => r.key)).toEqual(['d:1', 'd:2', 'e:1']);
  });

  it(`evicts the least recently used page once more than ${DOCUMENT_PAGE_CACHE_SIZE} are held`, async () => {
    for (let n = 1; n <= DOCUMENT_PAGE_CACHE_SIZE; n++) {
      await load('d', n);
    }
    // Reading page 1 makes it the most recently used, so page 2 is now the oldest.
    await firstValueFrom(cache.page('d', 1));
    await load('d', DOCUMENT_PAGE_CACHE_SIZE + 1);

    await firstValueFrom(cache.page('d', 1));
    expect(api.count('d:1')).toBe(1);
    await load('d', 2);
    // Reloading page 2 pushed out the next oldest, page 3; page 4 is still held.
    await firstValueFrom(cache.page('d', 4));
    expect(api.count('d:4')).toBe(1);
    await load('d', 3);
  });

  it('forgets a failed request so the next ask tries again', async () => {
    const failing = firstValueFrom(cache.page('d', 1));
    api.requests[0].response.error(new Error('offline'));
    await expect(failing).rejects.toThrow('offline');
    expect((await load('d', 1)).text).toBe('d 1');
  });
});
