# Spanish

Browser app for the 25 Spanish lessons. ClojureScript syntax is
compiled to JavaScript by Squint, the UI is rendered with Reagami, and
Babashka drives install, development, build, and serving.

## Requirements

- Babashka 1.13 or newer
- Node.js 20.19+ (or 22.12+) and npm
- No global Clojure/JVM installation is needed for the app

## Live demo

<https://awb99.github.io/idioma/>

## Quick start

```bash
bb install
bb dev
```

Then open <http://localhost:5173>.

`bb dev` installs npm dependencies, validates the lesson EDN files, starts the
lesson watcher, and runs Vite with Squint compilation and hot reload.

## Production build and serve

```bash
bb build
bb serve
```

Open <http://localhost:8080>. `bb serve` requires a completed build in `dist/`.

Pushing to `main` builds `dist/` and deploys it to GitHub Pages via the
workflow in `.github/workflows/pages.yml`; `bb serve` remains the local static
server. The repository must have **Settings → Pages → Build and deployment →
Source** set to **GitHub Actions** for the deploy job to succeed.

Use `bb preview` to build and serve in one command, or change the port:

```bash
bb preview
bb serve --port 8099
```

## Other Babashka tasks

```bash
bb test                    # transform tests
bb check                   # validate lessons + compile all Squint sources
bb lessons                 # regenerate public/lessons.json
bb lessons --check         # validate without writing
```

Fixture-only transform:

```bash
bb lessons --allow-partial --dir test/fixtures/lessons --out /tmp/lessons.json
```

## Lesson data

The source of truth is `lesson/spanish/1.edn` through `lesson/spanish/25.edn`.
`bb lessons` transforms them into `public/lessons.json`, which is generated and
gitignored. Edit the EDN files, then rerun `bb lessons` or keep `bb dev`
running so its watcher regenerates the JSON.

Test fixtures live in `test/fixtures/lessons/` and are not part of the app data.

## App notes

- Routes are hash-based: `#/`, `#/lesson/N`, `#/lesson/N/exercise/ID`.
- Progress is stored in this browser under `idioma/progress/v1`.
- Generated files and directories: `out/`, `dist/`, `public/lessons.json`.
- Deployment artifact is `dist/`; the static `bb serve` server needs no SPA
  fallback because routing is hash-based.

## Project layout

```text
bb.edn
package.json
squint.edn
vite.config.js
index.html
README.md
lesson/spanish/*.edn       # lesson source data
public/
  styles.css
  lessons.json             # generated
out/                       # generated Squint output
dist/                      # generated production build
script/
  lessons.clj              # EDN -> JSON transform and validation
  serve.clj                # production static server
src/
  app.cljs
  router.cljs
  data.cljs
  progress.cljs
  answer.cljs
  views/
test/
  fixtures/lessons/*.edn
  lessons_test.clj
```

## Troubleshooting

- Vite 7 requires Node 20.19+ or 22.12+. On an older Node, pin
  `vite` to `^5.4.0` in `package.json`.
- `missing lessons` from `bb lessons`: confirm `lesson/spanish/1.edn` through
  `25.edn` exist, or pass `--dir`.
- `bb serve` fails when `dist/index.html` is missing: run `bb build` first.
- Port already in use: pass `--port` to `bb serve`, or stop the other process.