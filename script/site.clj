(ns site
  "Post-build step: emit per-course entry pages and a 404 page.

  GitHub Pages serves `dist/<slug>/index.html` for both `/idioma/<slug>` and
  `/idioma/<slug>/`, but only if that directory exists. The SPA uses the same
  `index.html` for every course and reads the course slug from the path."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]))

(defn- read-json [path]
  (json/parse-string (slurp (str path))))

(defn- copy-file! [from to]
  (fs/create-dirs (fs/parent to))
  (spit (str to) (slurp (str from)))
  (println "Wrote" (str to)))

(defn emit-course-pages
  "Copy dist/index.html to dist/<slug>/index.html for every course and to
  dist/404.html. Returns the list of emitted paths."
  ([] (emit-course-pages "dist"))
  ([dist]
   (let [dist (fs/path dist)
         index (fs/path dist "index.html")
         manifest (fs/path dist "courses.json")]
     (when-not (fs/regular-file? index)
       (throw (ex-info (str "missing " index "; run `npx vite build` first") {})))
     (when-not (fs/regular-file? manifest)
       (throw (ex-info (str "missing " manifest "; run `bb lessons` first") {})))
     (let [courses (get (read-json manifest) "courses")
           pages (vec (for [{:strs [slug]} courses]
                        (let [target (fs/path dist slug "index.html")]
                          (copy-file! index target)
                          target)))
           not-found (fs/path dist "404.html")]
       (copy-file! index not-found)
       (conj pages not-found)))))

(defn -main [& args]
  (let [dist (or (first args) "dist")]
    (emit-course-pages dist)
    (println "Course pages emitted under" dist)))