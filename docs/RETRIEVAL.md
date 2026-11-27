# Retrieval quality

Written by `make eval` (`RetrievalService`); do not edit by hand. Fictional data: the corpus is the demo seed plus the syllabi in
`eval/retrieval/docs`, and the questions are in `eval/retrieval/queries.yml`. A query is recalled when its expected page is among
the first eight pages the ranker returns for the requirement's title and text. Excerpt coverage is the share of recalled queries
whose expected evidence is inside the excerpt that would be shown to the model (D-174).

Corpus: 27 documents. Queries: 29 counted and 6 lexical gaps.

## Queries that count toward the limits

| Metric | ts_rank (baseline) | BM25 |
|---|---|---|
| Recall@8 | 96.6 % (28 / 29) | 96.6 % (28 / 29) |
| Recall@3 | 86.2 % (25 / 29) | 89.7 % (26 / 29) |
| Mean reciprocal rank | 0.796 | 0.766 |
| Excerpt coverage | 100.0 % (28 / 28) | 100.0 % (28 / 28) |

Limits (`eval/thresholds.yml`): recall@8 at least 95.0 % and not below the baseline, recall@3 not below the baseline, excerpt coverage 100.0 %.

## Lexical gaps (reported, never thresholded)

The document says it in other words than the requirement does, so a search on the requirement's words cannot be expected to
find it. These numbers show what lexical search does not reach.

| Metric | ts_rank (baseline) | BM25 |
|---|---|---|
| Recall@8 | 33.3 % (2 / 6) | 33.3 % (2 / 6) |
| Recall@3 | 16.7 % (1 / 6) | 16.7 % (1 / 6) |
| Mean reciprocal rank | 0.111 | 0.076 |
| Excerpt coverage | 100.0 % (2 / 2) | 100.0 % (2 / 2) |

## Every query

Rank is the position of the expected page in the first eight, `-` when it is not there.

| Query | Kind | Expected page | ts_rank | BM25 | Excerpt covers (BM25) |
|---|---|---|---|---|---|
| R01 Multicultural counseling | counted | `coun705-syllabus.pdf` p. 2 | 2 | 2 | yes |
| R02 Counseling theories | counted | `coun710-syllabus.pdf` p. 1 | 1 | 1 | yes |
| R03 Family systems | counted | `coun715-syllabus.pdf` p. 2 | 2 | 2 | yes |
| R04 Children and adolescents | counted | `coun720-syllabus.pdf` p. 2 | 1 | 1 | yes |
| R05 Counseling skills | counted | `coun725-syllabus.pdf` p. 3 | 2 | 2 | yes |
| R06 Clinical supervision | counted | `coun730-syllabus.pdf` p. 2 | 2 | 2 | yes |
| R07 Grief and loss | counted | `coun735-syllabus.pdf` p. 2 | 1 | 1 | yes |
| R08 Human sexuality | counted | `coun740-syllabus.pdf` p. 2 | 2 | 4 | yes |
| R09 Consultation | counted | `coun755-syllabus.pdf` p. 2 | 1 | 2 | yes |
| R10 Statistics | counted | `coun760-syllabus.pdf` p. 2 | 1 | 1 | yes |
| R11 Psychopathology | counted | `coun701-syllabus.pdf` p. 1 | 1 | 1 | yes |
| R12 Supervised practice | counted | `coun725-syllabus.pdf` p. 2 | 7 | 7 | yes |
| R13 Research literacy | counted | `coun760-syllabus.pdf` p. 3 | - | - | - |
| R14 Diverse clients | counted | `coun705-syllabus.pdf` p. 1 | 4 | 2 | yes |
| R15 Death and loss | counted | `coun735-syllabus.pdf` p. 1 | 1 | 2 | yes |
| S01 Ethics coursework | counted | `coun605-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S02 Human growth | counted | `coun610-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S03 Diagnosis | counted | `coun615-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S04 Accreditation | counted | `accreditation-letter.pdf` p. 1 | 1 | 1 | yes |
| S05 Group counseling | counted | `coun625-syllabus.pdf` p. 1 | 5 | 3 | yes |
| S06 Addictions | counted | `coun650-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S07 Crisis and trauma | counted | `coun620-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S08 Career development | counted | `coun630-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S09 Research | counted | `coun635-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S10 Assessment | counted | `coun640-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S11 Psychopharmacology | counted | `coun655-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S12 Ethics and law | counted | `coun605-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S13 Crisis and trauma | counted | `coun620-syllabus.pdf` p. 1 | 1 | 1 | yes |
| S14 Assessment | counted | `coun640-syllabus.pdf` p. 1 | 1 | 1 | yes |
| G01 Mental and emotional disorders | lexical gap | `coun701-syllabus.pdf` p. 1 | 6 | - | - |
| G02 Marital counseling | lexical gap | `coun715-syllabus.pdf` p. 1 | - | - | - |
| G03 Chemical dependency | lexical gap | `coun650-syllabus.pdf` p. 1 | 2 | 3 | yes |
| G04 Minors and youth | lexical gap | `coun720-syllabus.pdf` p. 1 | - | - | - |
| G05 Telehealth | lexical gap | `coun750-syllabus.pdf` p. 1 | - | 8 | yes |
| G06 Documentation | lexical gap | `coun765-syllabus.pdf` p. 1 | - | - | - |
