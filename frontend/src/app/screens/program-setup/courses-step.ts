import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, computed, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon } from '@ng-icons/core';
import { ButtonDirective, ButtonLabel } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { Tag } from 'primeng/tag';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Course } from '../../core/models';
import { problemDetail } from '../../core/problem';
import { Enter } from '../../ui/enter';
import { InfoTip } from '../../ui/info-tip';
import { currentVersion } from './setup-model';
import { SetupNotice, SetupStore } from './setup-store';

interface CourseRow { course: Course; catalogYear: string; required: boolean; versions: number }
interface CourseForm { code: string; title: string; required: boolean }
interface VersionForm { catalogYear: string; required: boolean }

/**
 * The Courses step: the program's courses with their current catalog year, a dialog to add one (its first version is
 * the program's catalog year, D-039) and one to add a new catalog year, which supersedes the current version.
 */
@Component({
  selector: 'lx-setup-courses',
  imports: [FormsModule, NgIcon, ButtonDirective, ButtonLabel, Dialog, InputText, Message, Skeleton, TableModule, Tag, ToggleSwitch, Enter, InfoTip],
  templateUrl: './courses-step.html',
  styleUrl: './steps.css',
})
export class CoursesStep {
  readonly store = inject(SetupStore);
  readonly announce = output<SetupNotice>();

  readonly rows = computed<CourseRow[] | null>(() => this.store.courses()?.map((course) => {
    const current = currentVersion(course);
    return { course, catalogYear: current?.catalogYear ?? '', required: current?.required ?? false, versions: course.versions.length };
  }) ?? null);

  readonly addOpen = signal(false);
  readonly course = signal<CourseForm>({ code: '', title: '', required: true });
  readonly courseProblems = signal<Partial<Record<keyof CourseForm, string>>>({});
  /** The course whose new catalog year is being entered. */
  readonly versionOf = signal<Course | null>(null);
  readonly version = signal<VersionForm>({ catalogYear: '', required: true });
  readonly versionProblem = signal<string | null>(null);
  readonly general = signal<string | null>(null);
  readonly busy = signal(false);
  private readonly addButton = viewChild<ElementRef<HTMLButtonElement>>('addButton');
  private opener: HTMLElement | null = null;

  openAdd(): void {
    this.course.set({ code: '', title: '', required: true });
    this.courseProblems.set({});
    this.general.set(null);
    this.addOpen.set(true);
  }

  setCourse<K extends keyof CourseForm>(field: K, value: CourseForm[K]): void {
    this.course.update((c) => ({ ...c, [field]: value }));
    this.courseProblems.update((p) => ({ ...p, [field]: undefined }));
  }

  async addCourse(): Promise<void> {
    const c = this.course();
    const problems: Partial<Record<keyof CourseForm, string>> = {};
    if (!c.code.trim()) { problems.code = 'Enter the course code.'; } else if (c.code.trim().length > 30) { problems.code = 'The code has at most 30 characters.'; }
    if (!c.title.trim()) { problems.title = 'Enter the course title.'; } else if (c.title.trim().length > 200) { problems.title = 'The title has at most 200 characters.'; }
    this.courseProblems.set(problems);
    if (Object.keys(problems).length > 0 || this.busy()) { return; }
    this.busy.set(true);
    this.general.set(null);
    try {
      const added = await this.store.addCourse({ code: c.code.trim(), title: c.title.trim(), required: c.required });
      this.addOpen.set(false);
      this.announce.emit({ summary: 'Course added', detail: `${added.code}, catalog year ${currentVersion(added)?.catalogYear ?? ''}.` });
    } catch (e: unknown) {
      // The only conflict a new course can meet is a code the program already has.
      if (e instanceof HttpErrorResponse && e.status === 409) {
        this.courseProblems.set({ code: `${c.code.trim()} already exists in this program.` });
      } else {
        this.general.set(problemDetail(e, 'The course could not be added.'));
      }
    } finally {
      this.busy.set(false);
    }
  }

  addClosed(): void {
    requestAnimationFrame(() => this.addButton()?.nativeElement.focus());
  }

  openVersion(course: Course, trigger: EventTarget | null): void {
    this.opener = trigger instanceof HTMLElement ? trigger.closest('button') : null;
    this.version.set({ catalogYear: '', required: currentVersion(course)?.required ?? true });
    this.versionProblem.set(null);
    this.general.set(null);
    this.versionOf.set(course);
  }

  setVersion<K extends keyof VersionForm>(field: K, value: VersionForm[K]): void {
    this.version.update((v) => ({ ...v, [field]: value }));
    this.versionProblem.set(null);
  }

  async addVersion(): Promise<void> {
    const course = this.versionOf();
    const v = this.version();
    if (!course || this.busy()) { return; }
    if (!v.catalogYear.trim()) { this.versionProblem.set('Enter the catalog year.'); return; }
    if (v.catalogYear.trim().length > 20) { this.versionProblem.set('The catalog year has at most 20 characters.'); return; }
    this.busy.set(true);
    this.general.set(null);
    try {
      await this.store.addVersion(course.id, { catalogYear: v.catalogYear.trim(), required: v.required, supersedesId: currentVersion(course)?.id ?? null });
      this.versionOf.set(null);
      this.announce.emit({ summary: 'Catalog year added', detail: `${course.code} now follows catalog year ${v.catalogYear.trim()}.` });
    } catch (e: unknown) {
      this.general.set(problemDetail(e, 'The catalog year could not be added.'));
    } finally {
      this.busy.set(false);
    }
  }

  versionClosed(): void {
    const opener = this.opener;
    this.opener = null;
    requestAnimationFrame(() => opener?.focus());
  }

  currentYear(course: Course | null): string {
    return course ? (currentVersion(course)?.catalogYear ?? '') : '';
  }
}
