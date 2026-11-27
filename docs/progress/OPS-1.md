# OPS-1 progress: image build and smoke in CI

Branch `ops-ci-image`, PR #20, 2026-11-27. Decisions: `docs/decisions/OPS-1.md` (D-240 to D-244).

## What changed

- `.github/workflows/ci.yml`: job `image` (`needs: test`, 30 minute timeout, same triggers and concurrency group).
  Buildx build with the GitHub Actions cache, tag `lexvay:ci-<sha>`, `load: true`, `push: false`, empty license;
  image size to `$GITHUB_STEP_SUMMARY`; then `scripts/smoke.sh lexvay:ci-<sha>` on slot 0.
- `scripts/smoke.sh [image-tag]`: tests an existing image, or builds `lexvay:local` when given none. Slot, compose
  project, cleanup and temporary directory come from `scripts/lib/stack.sh`. `docker-compose.yml` reads the image
  name from `LEXVAY_IMAGE`.
- `scripts/lib/stack.sh`: was not on `main`; copied from F7's branch (PR #17) with only the header comment changed.
- `docs/OPERATIONS.md`: section "Continuous integration" names the job and the tag convention.

## Evidence

Local, Colima, slot 11 (`LEXVAY_E2E_SLOT=11`): `make smoke` (builds `lexvay:local`) and
`scripts/smoke.sh lexvay:ci-local` (existing image) both ended `smoke: PASSED`, 31 checks each, image 216 MiB.

CI, run 45946473005 on PR #20: `test` pass (3m58s), `infra` pass (21s), `image` pass (3m41s). In the `image` job log:
`smoke: testing the existing image lexvay:ci-<sha>`, `smoke: image size 218 MiB`, 31 `smoke: ok` lines,
`Started LexvayApplication in 4.869 seconds`, `smoke: ok: shutdown completed`, `smoke: PASSED`. The job summary
line reads `Image lexvay:ci-<sha>: 218 MiB` (the same `docker image inspect` value the smoke script prints).

## Not verified

`actionlint` is not installed on this machine, so the workflow was validated only by its own run.
