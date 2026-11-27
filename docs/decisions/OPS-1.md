# OPS-1 decisions: image build and smoke in CI (D-240 to D-244)

**D-240 — A third job, `image`, after `test`.** `.github/workflows/ci.yml` gains `image` with `needs: test`, so an
image is only built for a change whose suites already passed. It runs on the same triggers (pull requests and pushes
to `main`), inherits the workflow's `concurrency` group (a newer commit cancels the older run, image job included),
runs on `ubuntu-latest` and has `timeout-minutes: 30` like `test`. The smoke script fails the job on the first
failed check (`set -e` and `fail`).

**D-241 — Action pins.** `docker/setup-buildx-action@v4` and `docker/build-push-action@v7`, pinned by major tag like
the other actions in the file. Latest releases verified from the GitHub API on 2026-11-26: setup-buildx-action
v4.4.1 (published 2026-09-16), build-push-action v7.4.0 (published 2026-09-15). A major tag follows minor releases
by design; to move to a new major, change the tag and re-read the action's release notes.

**D-242 — One build, tagged by commit, never pushed.** `build-push-action` builds the root `Dockerfile` with
`load: true` (the image lands in the runner's Docker daemon), `push: false`, tag `lexvay:ci-<full sha>`,
`cache-from: type=gha` and `cache-to: type=gha,mode=max` (every stage is cached, including the Node and Maven
stages that never reach the final image). `build-args: LEXVAY_PRIMEUI_LICENSE=` passes an empty license key: CI has
no key and the app then shows its license notice (Dockerfile comment; D-140).

**D-243 — `scripts/smoke.sh` tests an image it is given.** `scripts/smoke.sh <image-tag>` runs against an existing
image and builds nothing, so CI tests exactly the cached build above and not a second, uncached one; without an
argument it builds `lexvay:local` through compose as before (`make smoke`). `docker-compose.yml` reads the image name
from `LEXVAY_IMAGE` (default `lexvay:local`). The script has no Colima dependency: it uses whatever daemon the
`docker` CLI already points at (`DOCKER_HOST` when set, otherwise the default socket). The slot, the PostgreSQL port,
the compose project (`lexvay-smoke-<slot>`), the cleanup trap and the temporary directory come from
`scripts/lib/stack.sh`. That file did not exist on `main`; it is the same file F7 (PR #17) adds, copied unchanged
except for the header comment, so F7 merges onto it without a conflict in the code. `scripts/e2e.sh` and
`scripts/eval.sh` adopt it in F7. Slot 0 is free on a CI runner (ports 5433, 8081, 8091).

**D-244 — Image size in the job summary.** A step after the build writes one line to `$GITHUB_STEP_SUMMARY`:
`Image lexvay:ci-<sha>: <n> MiB`, taken from `docker image inspect --format '{{.Size}}'`. `docker image ls` has no
content-size field on the runner's Docker (the field exists only with the containerd image store), and `inspect`
reports the same number the smoke script already prints, so one definition is used everywhere. On the containerd
store this is the compressed content size (227 MB locally, matching `docs/OPERATIONS.md`); on a classic store it is
the unpacked size, so compare numbers from the same environment only. The first CI run's value is recorded in
`docs/progress/OPS-1.md`.
