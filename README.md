# Idioma

Browser app for short language courses. Two courses ship today:

- **Aprende inglés** — 25 Spanish-language lessons for learning English.
- **Synergy Spanish** — the original 25 English-language Spanish lessons.

ClojureScript syntax is compiled to JavaScript by Squint, the UI is rendered
with Reagami, and Babashka drives install, validation, development, build, and
serving.

Live site: <https://awb99.github.io/idioma/>

## Requirements

- Babashka 1.13 or newer
- Node.js 20.19+ (or 22.12+) and npm
- No global Clojure/JVM installation is needed for the app

## Quick start

```bash
bb install
bb dev
```

Then open <http://localhost:5173>. The root page is the course chooser.

`bb dev` installs npm dependencies, validates the lesson EDN files, starts the
lessons watcher, and runs Vite with Squint compilation and hot reload.

## Production build and serve

```bash
bb build
bb serve
```

Open <http://localhost:8080>. `bb serve` requires a completed build in `dist/`.
It resolves `/`, `/learn-spanish/`, `/aprende-ingles/`, and the same paths
without a trailing slash, and serves `dist/404.html` for unknown paths.

Pushing to `main` builds `dist/` and deploys it to GitHub Pages via the
workflow in `.github/workflows/pages.yml`; `bb serve` remains the local static
server. The repository must have **Settings → Pages → Build and deployment →
Source** set to **GitHub Actions** for the deploy job to succeed.

Use `bb preview` to build and serve in one command, or change the port:

```bash
bb preview
bb serve --port 8099
```

## Course data

Each course lives in its own directory:

```text
lesson/
  aprende-ingles/
    course.edn
    1.edn ... 25.edn
  learn-spanish/
    course.edn
    1.edn ... 25.edn
  ui/
    en.edn
    es.edn
```

- `course.edn` is the marker and metadata: slug, UI language pack, title,
  tagline, languages, lesson count, optional vocabulary count, empty-vocabulary
  allowlist, and legacy flags.
- `N.edn` files hold one lesson each. Keys are namespaced (`:lesson/number`,
  `:vocab/target`, `:exercise/direction`, ...). The transform strips
  namespaces for the browser.
- `ui/<lang>.edn` is shared chrome for one UI language. A course points to it
  with `:course/ui :en` or `:course/ui :es`. All user-visible chrome comes from
  the UI pack; views do not hardcode language-pair text.
- Directions are relative to the course: `:to-base` answers in the base
  language, `:to-target` answers in the target language. `course.edn` declares
  which language is which.
- `:item/alternatives` is optional and holds vectors of alternative answers
  with the same turn structure as `:item/answer`.

Generated browser data:

- `public/courses.json` — site chrome from the English pack plus course summaries.
- `public/lessons/<slug>.json` — version 2 envelope: full course object,
  resolved UI pack, and normalized lessons.

Both are gitignored. Edit the EDN, then rerun `bb lessons` or keep `bb dev`
running so its watcher regenerates the JSON. The watcher rescans directories,
so adding or removing a course is picked up without restarting.

Test fixtures live in `test/fixtures/lessons/` and are not part of the app data.
`--dir` keeps the single-course transform mode:

```bash
bb lessons --allow-partial --dir test/fixtures/lessons --out /tmp/lessons.json
```

## Other Babashka tasks

```bash
bb test                    # transform, corpus, parity, and UI-pack tests
bb check                   # validate all courses + compile all Squint sources
bb lessons                 # regenerate courses.json and per-course JSON
bb lessons --check         # validate without writing
bb lessons --allow-partial # accept whatever N.edn files exist (drafts)
bb lessons --watch         # poll EDN mtimes and regenerate
```

Warnings are printed but do not fail the build. Errors stop the write and exit
non-zero. Treat warnings as errors while authoring.

## Adding a course

1. Create `lesson/<slug>/course.edn`. `:course/slug` must equal the directory
   name. Choose an existing `:course/ui` pack or add a new one.
2. Add `1.edn` through `N.edn` with the schema described above.
3. Run `bb check`. The validator reports missing or unexpected keys, unknown
   enum values, duplicate IDs, non-contiguous vocabulary IDs, dialog turn-count
   mismatches, and vocabulary-count mismatches.
4. Run `bb build` and open `/<slug>/` locally.

The UI pack key set is enforced for every course. When adding a new chrome
string, add it to every pack and to `ui-required-keys` in
`script/lessons.clj`; the tests also check that the packs have the same key tree.

## Routes

Course selection lives in the **path**; lesson and exercise selection stays in
the **hash**:

- `/` — course chooser
- `/<slug>/` — course home
- `/<slug>/#/lesson/N`
- `/<slug>/#/lesson/N/exercise/ID`

The build sets `window.__idiomaBase` from Vite's `base`, and the app uses it for
course links, JSON fetches, and legacy redirects. GitHub Pages uses
`/idioma/`; local development and `bb serve` use `/`.

Old hash-only lesson links (`#/lesson/N`, `#/lesson/N/exercise/ID`) redirect to
the course marked `:course/legacy-hash? true` (currently `learn-spanish`) before
the course data is fetched.

## Progress

Progress is stored in this browser under `idioma/progress/v2`:

```json
{"version":2,"courses":{"learn-spanish":{"3":{"3A":{"1":{"status":"correct","attempts":2}}}}}}
```

Courses are isolated by slug. On first load, if the legacy
`idioma/progress/v1` value parses, it is imported into
`courses.learn-spanish` once. The v1 key is deliberately kept for rollback and
is never overwritten by the app.

`correct` always wins over later wrong/revealed attempts; `correct-accent` is
stored as `correct`; `revealed` only applies when the item is not already
correct. The app never deletes either progress key automatically.

## App notes

- Reagami has no auto-rerender: every mutation goes through the single state
  atom and its `add-watch`.
- Exercise inputs are uncontrolled and keyed by DOM id
  `ans-<lesson>-<exerciseID>-<itemN>-<turn>`. Keep that format stable.
- Answer checking folds accents, ignores punctuation/case/spacing, folds curly
  apostrophes to straight ones, and accepts any `:item/alternatives` entry.
- `document.title` and `documentElement.lang` follow the selected course's
  `htmlTitle`/`htmlLang`; the chooser and not-found views use the site chrome.

## Project layout

```text
bb.edn
package.json
squint.edn
vite.config.js
index.html
README.md
lesson/
  <course>/course.edn
  <course>/*.edn           # lesson source data
  ui/*.edn                 # UI language packs
public/
  styles.css
  courses.json             # generated
  lessons/*.json           # generated
out/                       # generated Squint output
dist/                      # generated production build
script/
  lessons.clj              # EDN -> JSON transform and validation
  site.clj                 # per-course entry pages and 404.html
  serve.clj                # production static server
src/
  app.cljs
  router.cljs
  data.cljs
  progress.cljs
  answer.cljs
  ui.cljs                  # UI pack helpers
  views/
test/
  fixtures/lessons/*.edn
  lessons_test.clj
```

## Troubleshooting

- Vite 7 requires Node 20.19+ or 22.12+. On an older Node, pin
  `vite` to `^5.4.0` in `package.json`.
- `unexpected :key` from `bb lessons`: the validator rejects unknown keys as
  well as missing ones. Rename the key in the EDN or add it to the allowed key
  sets in `script/lessons.clj` intentionally.
- `no course directories with course.edn found`: run `bb lessons --dir <dir>`
  for a fixture directory, or add `lesson/<slug>/course.edn`.
- `:course/slug ... must equal the directory name`: rename the directory or the
  slug; they are intentionally coupled.
- `bb serve` returns 404 for a course path: run `bb build`; the per-course
  `dist/<slug>/index.html` files are emitted by `script/site.clj`.
- Port already in use: pass `--port` to `bb serve`, or stop the other process.