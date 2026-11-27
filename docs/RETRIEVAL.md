# Retrieval quality

Written by `make eval` (`RetrievalService`); do not edit by hand. Fictional data: the corpus is the demo seed plus the syllabi in
`eval/retrieval/docs`, and the questions are in `eval/retrieval/queries.yml`. A query is recalled when its expected page is among
the first eight pages the ranker returns for the requirement's title and text. "BM25 + terms" also searches the requirement's
search terms (F7c), which the file holds per query, hand-written for the requirement alone and never adjusted after a run (D-258). Excerpt coverage is the
share of recalled queries whose expected evidence is inside the excerpt that would be shown to the model (D-174).

Corpus: 27 documents. Queries: 29 counted and 6 lexical gaps.

## Queries that count toward the limits

| Metric | ts_rank (baseline) | BM25 | BM25 + terms |
|---|---|---|---|
| Recall@8 | 96.6 % (28 / 29) | 96.6 % (28 / 29) | 96.6 % (28 / 29) |
| Recall@3 | 86.2 % (25 / 29) | 89.7 % (26 / 29) | 93.1 % (27 / 29) |
| Mean reciprocal rank | 0.796 | 0.766 | 0.839 |
| Excerpt coverage | 100.0 % (28 / 28) | 100.0 % (28 / 28) | 100.0 % (28 / 28) |

Limits (`eval/thresholds.yml`): recall@8 at least 95.0 % and, for BM25, not below the baseline and, for BM25 + terms, not below BM25; recall@3 likewise; excerpt coverage 100.0 %.

## Lexical gaps

The document says it in other words than the requirement does, so a search on the requirement's words cannot be expected to
find it. These numbers show what lexical search does not reach and what the search terms recover. Only the recall@8 of
BM25 + terms is limited: at least 66.0 % (4 of 6).

| Metric | ts_rank (baseline) | BM25 | BM25 + terms |
|---|---|---|---|
| Recall@8 | 33.3 % (2 / 6) | 33.3 % (2 / 6) | 100.0 % (6 / 6) |
| Recall@3 | 16.7 % (1 / 6) | 16.7 % (1 / 6) | 100.0 % (6 / 6) |
| Mean reciprocal rank | 0.111 | 0.076 | 0.917 |
| Excerpt coverage | 100.0 % (2 / 2) | 100.0 % (2 / 2) | 100.0 % (6 / 6) |

## Every query

Rank is the position of the expected page in the first eight, `-` when it is not there.

| Query | Kind | Expected page | ts_rank | BM25 | BM25 + terms | Excerpt covers (BM25 + terms) | Search terms |
|---|---|---|---|---|---|---|---|
| R01 Multicultural counseling | counted | `coun705-syllabus.pdf` p. 2 | 2 | 2 | 2 | yes | cultural diversity, cross-cultural, cultural competence, oppression, diverse populations |
| R02 Counseling theories | counted | `coun710-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | theoretical orientations, cognitive behavioral, psychodynamic, humanistic, person-centered, therapeutic approaches |
| R03 Family systems | counted | `coun715-syllabus.pdf` p. 2 | 2 | 2 | 3 | yes | family therapy, marriage and family, couples, systemic, genogram |
| R04 Children and adolescents | counted | `coun720-syllabus.pdf` p. 2 | 1 | 1 | 1 | yes | child development, youth, play therapy, developmental, school-age |
| R05 Counseling skills | counted | `coun725-syllabus.pdf` p. 3 | 2 | 2 | 1 | yes | skills lab, role play, microskills, video recording, observation and feedback |
| R06 Clinical supervision | counted | `coun730-syllabus.pdf` p. 2 | 2 | 2 | 1 | yes | supervisory relationship, supervisor, supervisee, case consultation, live observation |
| R07 Grief and loss | counted | `coun735-syllabus.pdf` p. 2 | 1 | 1 | 1 | yes | mourning, death and dying, end of life, complicated grief, hospice |
| R08 Human sexuality | counted | `coun740-syllabus.pdf` p. 2 | 2 | 4 | 3 | yes | sexual orientation, gender diversity, sexual health, sex therapy, lgbtq |
| R09 Consultation | counted | `coun755-syllabus.pdf` p. 2 | 1 | 2 | 1 | yes | consultant, collaboration, systems change, community outreach, case management |
| R10 Statistics | counted | `coun760-syllabus.pdf` p. 2 | 1 | 1 | 1 | yes | quantitative methods, hypothesis testing, regression, descriptive statistics, data analysis |
| R11 Psychopathology | counted | `coun701-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | abnormal psychology, mental illness, dsm, psychiatric disorders, diagnostic criteria |
| R12 Supervised practice | counted | `coun725-syllabus.pdf` p. 2 | 7 | 7 | 6 | yes | practicum, role play, skills lab, observation, feedback, live supervision |
| R13 Research literacy | counted | `coun760-syllabus.pdf` p. 3 | - | - | - | - | evidence-based practice, research methods, journal articles, empirical studies, critical appraisal, outcome research |
| R14 Diverse clients | counted | `coun705-syllabus.pdf` p. 1 | 4 | 2 | 1 | yes | multicultural, cultural diversity, cross-cultural, cultural competence, ethnicity, culturally responsive |
| R15 Death and loss | counted | `coun735-syllabus.pdf` p. 1 | 1 | 2 | 2 | yes | grief, bereavement, mourning, end of life, complicated grief, survivors |
| S01 Ethics coursework | counted | `coun605-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | code of ethics, confidentiality, informed consent, professional conduct, licensure law, liability |
| S02 Human growth | counted | `coun610-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | developmental psychology, aging, life stages, cognitive development, moral development |
| S03 Diagnosis | counted | `coun615-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | dsm, psychopathology, case conceptualization, clinical assessment, diagnostic criteria, mental illness |
| S04 Accreditation | counted | `accreditation-letter.pdf` p. 1 | 1 | 1 | 1 | yes | cacrep, accreditation letter, program approval, self-study |
| S05 Group counseling | counted | `coun625-syllabus.pdf` p. 1 | 5 | 3 | 2 | yes | group therapy, group dynamics, group process, psychoeducational groups, group leadership, facilitation |
| S06 Addictions | counted | `coun650-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | addiction counseling, chemical dependency, substance abuse, alcohol and drugs, recovery, relapse prevention |
| S07 Crisis and trauma | counted | `coun620-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | disaster response, suicide prevention, ptsd, emergency, trauma-informed care, risk assessment |
| S08 Career development | counted | `coun630-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | vocational, occupational information, employment, career assessment, life planning, job search |
| S09 Research | counted | `coun635-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | statistics, outcome research, evidence-based practice, data analysis, needs assessment, qualitative methods |
| S10 Assessment | counted | `coun640-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | psychometrics, appraisal, standardized tests, validity and reliability, measurement, psychological testing |
| S11 Psychopharmacology | counted | `coun655-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | medication, psychotropic drugs, pharmacology, prescribers, neurobiology, side effects |
| S12 Ethics and law | counted | `coun605-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | legal issues, code of ethics, professional responsibility, malpractice, confidentiality, licensure regulations |
| S13 Crisis and trauma | counted | `coun620-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | crisis response, ptsd, suicide risk, disaster mental health, trauma-informed care, safety planning |
| S14 Assessment | counted | `coun640-syllabus.pdf` p. 1 | 1 | 1 | 1 | yes | psychometrics, psychological testing, standardized instruments, measurement, evaluation, diagnostic interviewing |
| G01 Mental and emotional disorders | lexical gap | `coun701-syllabus.pdf` p. 1 | 6 | - | 1 | yes | psychopathology, abnormal psychology, psychiatric disorders, dsm, clinical assessment, mental illness, diagnostic criteria |
| G02 Marital counseling | lexical gap | `coun715-syllabus.pdf` p. 1 | - | - | 1 | yes | couples therapy, marriage and family, family systems, intimate relationships, premarital, family therapy |
| G03 Chemical dependency | lexical gap | `coun650-syllabus.pdf` p. 1 | 2 | 3 | 1 | yes | addiction counseling, substance abuse, substance use, alcohol and drug, recovery, relapse prevention, co-occurring disorders |
| G04 Minors and youth | lexical gap | `coun720-syllabus.pdf` p. 1 | - | - | 2 | yes | children, adolescents, child development, play therapy, school counseling, young clients |
| G05 Telehealth | lexical gap | `coun750-syllabus.pdf` p. 1 | - | 8 | 1 | yes | online counseling, distance counseling, videoconferencing, virtual sessions, telemental health, remote delivery |
| G06 Documentation | lexical gap | `coun765-syllabus.pdf` p. 1 | - | - | 1 | yes | case notes, progress notes, treatment plans, charting, hipaa, clinical records |
