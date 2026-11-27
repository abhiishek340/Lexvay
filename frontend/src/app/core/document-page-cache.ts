import { Injectable, inject } from '@angular/core';
import { Observable, catchError, shareReplay, throwError } from 'rxjs';
import { ApiService } from './api.service';
import { DocumentPage } from './models';

/** How many pages stay in memory. A page is a few kilobytes; the browser cache keeps the rest (D-112). */
export const DOCUMENT_PAGE_CACHE_SIZE = 40;

/**
 * Document pages in front of GET /documents/{id}/pages/{n}, least recently used first out. Pages never
 * change, so a cached page is never stale. Asking for a page that is still loading returns the same
 * observable, so two viewers on one page cost one request. A failed request is forgotten, so the next
 * ask tries again.
 */
@Injectable({ providedIn: 'root' })
export class DocumentPageCache {
  private readonly api = inject(ApiService);
  /** Map iteration order is insertion order: the first key is the least recently used. */
  private readonly pages = new Map<string, Observable<DocumentPage>>();

  page(documentId: string, pageNumber: number): Observable<DocumentPage> {
    const key = `${documentId}:${pageNumber}`;
    const cached = this.pages.get(key);
    if (cached) {
      this.pages.delete(key);
      this.pages.set(key, cached);
      return cached;
    }
    const page: Observable<DocumentPage> = this.api.documentPage(documentId, pageNumber).pipe(
      catchError((error: unknown) => {
        if (this.pages.get(key) === page) {
          this.pages.delete(key);
        }
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.pages.set(key, page);
    if (this.pages.size > DOCUMENT_PAGE_CACHE_SIZE) {
      this.pages.delete(this.pages.keys().next().value!);
    }
    return page;
  }
}
