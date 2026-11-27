import { NewRuleVersion } from '../../core/models';

/** The server's limit on rule text (CreateRuleVersionRequest: at most 500 000 characters). */
export const MAX_RULE_TEXT = 500_000;

export interface RuleVersionForm {
  jurisdiction: string | null;
  licenseType: string;
  label: string;
  sourceUrl: string;
  /** ISO date (yyyy-MM-dd) from the date picker. */
  effectiveFrom: string | null;
  supersedesId: string | null;
  text: string;
}

export type RuleVersionField = keyof RuleVersionForm;
export type FieldProblems = Partial<Record<RuleVersionField, string>>;

export function emptyForm(licenseType: string): RuleVersionForm {
  return { jurisdiction: null, licenseType, label: '', sourceUrl: '', effectiveFrom: null, supersedesId: null, text: '' };
}

/** What can be told before asking the server: required fields, the URL scheme and the text limit. */
export function formProblems(form: RuleVersionForm): FieldProblems {
  const problems: FieldProblems = {};
  if (!form.jurisdiction) { problems.jurisdiction = 'Choose a jurisdiction.'; }
  if (!form.licenseType.trim()) { problems.licenseType = 'Enter the license type.'; }
  if (!form.label.trim()) { problems.label = 'Enter a label.'; }
  if (!/^https?:\/\/\S+/.test(form.sourceUrl.trim())) { problems.sourceUrl = 'Enter the address of the source, starting with https:// or http://.'; }
  if (!form.effectiveFrom) { problems.effectiveFrom = 'Choose the date the rule takes effect.'; }
  if (!form.text.trim()) { problems.text = 'Paste the rule text.'; } else if (form.text.length > MAX_RULE_TEXT) { problems.text = 'The rule text is longer than 500 000 characters.'; }
  return problems;
}

export function toNewRuleVersion(form: RuleVersionForm): NewRuleVersion {
  return {
    jurisdiction: form.jurisdiction ?? '',
    licenseType: form.licenseType.trim(),
    label: form.label.trim(),
    sourceUrl: form.sourceUrl.trim(),
    effectiveFrom: form.effectiveFrom ?? '',
    supersedesId: form.supersedesId,
    text: form.text,
  };
}

/** Which field a problem detail from POST /rule-versions is about, when the server's words name one. */
const FIELD_WORDS: { field: RuleVersionField; pattern: RegExp }[] = [
  { field: 'supersedesId', pattern: /supersede/i },
  { field: 'jurisdiction', pattern: /jurisdiction code/i },
  { field: 'text', pattern: /same content|rule text/i },
  { field: 'sourceUrl', pattern: /source ?url/i },
  { field: 'effectiveFrom', pattern: /effective ?from/i },
  { field: 'licenseType', pattern: /license ?type/i },
  { field: 'label', pattern: /\blabel\b/i },
];

export function problemField(detail: string): RuleVersionField | null {
  return FIELD_WORDS.find(({ pattern }) => pattern.test(detail))?.field ?? null;
}
