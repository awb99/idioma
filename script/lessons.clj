(ns lessons
  "Transform course EDN files into JSON for the browser app.

  Courses live in `lesson/<slug>/`: a `course.edn` marker plus lesson files
  `1.edn` .. `N.edn`. UI language packs live in `lesson/ui/<lang>.edn`.

  `bb lessons` writes `public/courses.json` and `public/lessons/<slug>.json`.
  `--dir` keeps the single-course mode used by tests and one-off transforms.

  Every map key is normalized with `name` (namespace stripped), every keyword
  value is normalized to its unqualified name, and maps/vectors become JSON
  objects/arrays. Validation runs before writing so a bad file fails the build
  with a readable report instead of shipping broken data."
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def default-lesson-count 25)
(def default-empty-vocabulary-lessons [21 24])

(def course-required-keys
  #{:course/slug :course/ui :course/title :course/tagline
    :course/languages :course/lesson-count})

(def course-optional-keys
  #{:course/html-title :course/vocabulary-count
    :course/empty-vocabulary-lessons :course/legacy-hash?
    :course/legacy-progress?})

(def lesson-required-keys
  #{:lesson/number :lesson/label :lesson/title :lesson/target-title
    :lesson/step :lesson/intro :lesson/sections :lesson/vocabulary
    :lesson/exercises :lesson/closing})

(def lesson-optional-keys
  #{:lesson/source-pages})

(def step-keys
  #{:step/number :step/title})

(def section-keys
  #{:section/type :section/id :section/title :section/body})

(def vocab-required-keys
  #{:vocab/id :vocab/kind :vocab/target :vocab/base
    :vocab/pronunciation :vocab/mnemonic})

(def vocab-optional-keys
  #{:vocab/ipa})

(def exercise-keys
  #{:exercise/id :exercise/skill :exercise/direction :exercise/dialog?
    :exercise/instructions :exercise/hints :exercise/items :exercise/notes})

(def item-required-keys
  #{:item/n :item/prompt :item/answer})

(def item-optional-keys
  #{:item/alternatives})

(def vocab-kinds
  #{:synergy-verb :brick-verb :mortar :sentence-starter :other})

(def exercise-skills
  #{:recall :translate})

(def exercise-directions
  #{:to-base :to-target})

(def section-types
  #{:speaking-hints :communication-multiplied :accuracy-pointer
    :sentence-builder :sentence-starter :multipurpose-word :step-intro
    :closing})

(def ui-required-keys
  #{:site-title :site-tagline :choose-course :courses-label :open-course
    :back-all-lessons :back-to-lesson :back-home :back-to-courses
    :all-lessons :your-progress :answers-correct :answered-count
    :lesson-answers-correct :step-title :open-lesson :continue
    :progress-browser-only :exercises :closing :lesson-not-found
    :item-count :hints :check :show-answer :i-was-right :reset-exercise
    :previous :next :exercise-not-found :exercise-heading :items-correct
    :correct :correct-accent :wrong :revealed :empty :you :expected
    :marked-correct :status-correct :status-correct-accent :status-wrong
    :status-revealed :status-empty :status-unseen :complete :in-progress
    :not-started :percent-complete :vocab-pronunciation :vocab-mnemonic
    :vocab-ipa :skill-recall :skill-translate :section-labels
    :vocab-kind-labels :direction-template
    :language-names :page-not-found :course-not-found :loading :load-error
    :something-wrong :answer-aria :course-lessons})

;; ---------------------------------------------------------------------------
;; Reading

(defn- lesson-number-from-file [file]
  (some-> (fs/file-name file)
          (str/replace #"\.edn$" "")
          parse-long))

(defn- lesson-file? [file]
  (and (fs/regular-file? file)
       (re-matches #"\d+\.edn" (str (fs/file-name file)))))

(defn lesson-files
  "Return all `N.edn` files in dir, sorted by numeric lesson number."
  [dir]
  (try
    (->> (fs/list-dir dir)
         (filter lesson-file?)
         (sort-by lesson-number-from-file))
    (catch Exception _
      [])))

(defn read-lesson [file]
  (edn/read-string (slurp (str file))))

(defn- parse-problem [file]
  (try
    (read-lesson file)
    (catch Exception e
      {:error (str (fs/file-name file) ": cannot read EDN: "
                   (or (ex-message e) (str e)))})))

(defn expected-lesson-numbers [lesson-count]
  (range 1 (inc (or lesson-count default-lesson-count))))

(defn collect
  "Read lesson files from dir. Returns
  {:entries [{:file f :number n :lesson m}] :problems [str ...] :missing [n ...]}.

  In full mode every lesson 1..lesson-count must exist. With --allow-partial any
  readable N.edn file is accepted."
  [dir allow-partial? lesson-count]
  (let [lesson-count (or lesson-count default-lesson-count)
        files (lesson-files dir)
        present (set (map lesson-number-from-file files))
        missing (when-not allow-partial?
                  (remove present (expected-lesson-numbers lesson-count)))
        problems (atom [])
        entries (reduce
                 (fn [acc file]
                   (let [number (lesson-number-from-file file)
                         parsed (parse-problem file)]
                     (if (and (map? parsed) (:error parsed))
                       (do (swap! problems conj (:error parsed)) acc)
                       (conj acc {:file file :number number :lesson parsed}))))
                 []
                 files)]
    {:entries entries
     :problems @problems
     :missing (vec missing)}))

(defn course-dirs
  "Directories under root that contain a course.edn marker."
  [root]
  (try
    (->> (fs/list-dir root)
         (filter fs/directory?)
         (filter (fn [dir] (fs/regular-file? (fs/path dir "course.edn"))))
         (sort-by str))
    (catch Exception _
      [])))

(defn read-course [dir]
  (edn/read-string (slurp (str (fs/path dir "course.edn")))))

(defn fixture-course
  "Synthetic course used by --dir mode when the directory has no course.edn."
  [dir]
  {:course/slug (str (fs/file-name dir))
   :course/ui :en
   :course/title "Fixture course"
   :course/tagline "Fixture lessons"
   :course/languages {:base {:code "en"}
                      :target {:code "es"}}
   :course/lesson-count default-lesson-count
   :course/empty-vocabulary-lessons default-empty-vocabulary-lessons
   :course/legacy-hash? false
   :course/legacy-progress? false})

(defn load-course
  "Read course.edn from dir, or synthesize one for fixture directories."
  [dir]
  (if (fs/regular-file? (fs/path dir "course.edn"))
    (read-course dir)
    (fixture-course dir)))

(defn read-ui-pack [ui-root code]
  (edn/read-string (slurp (str (fs/path ui-root (str (name code) ".edn"))))))

;; ---------------------------------------------------------------------------
;; Normalization

(defn normalize
  "Recursively strip namespaces from map keys, convert keyword values to
  strings, keep vectors as vectors and maps as maps."
  [x]
  (cond
    (map? x)
    (reduce-kv (fn [m k v]
                 (assoc m (if (keyword? k) (name k) (str k)) (normalize v)))
               {}
               x)

    (vector? x) (mapv normalize x)
    (sequential? x) (mapv normalize x)
    (keyword? x) (name x)
    :else x))

(defn- normalized-lessons [entries]
  (->> entries
       (map (fn [{:keys [lesson]}] (normalize lesson)))
       (sort-by #(get % "number"))
       vec))

(defn- ui-pack-name [code]
  (if (keyword? code) (name code) (str code)))

(defn resolve-course
  "Build the JSON-friendly course object from course.edn and its UI pack."
  [course]
  (let [ui-code (:course/ui course)
        ui (normalize (read-ui-pack "lesson/ui" ui-code))
        language-name (fn [code] (or (get-in ui ["language-names" code]) code))
        base-code (get-in course [:course/languages :base :code])
        target-code (get-in course [:course/languages :target :code])
        title (:course/title course)]
    {"slug" (:course/slug course)
     "title" title
     "tagline" (:course/tagline course)
     "htmlTitle" (or (:course/html-title course) title)
     "htmlLang" (ui-pack-name ui-code)
     "lessonCount" (or (:course/lesson-count course) default-lesson-count)
     "vocabularyCount" (:course/vocabulary-count course)
     "emptyVocabularyLessons" (vec (or (:course/empty-vocabulary-lessons course)
                                       default-empty-vocabulary-lessons))
     "legacyHash" (boolean (:course/legacy-hash? course))
     "legacyProgress" (boolean (:course/legacy-progress? course))
     "languages" {"base" {"code" base-code
                          "name" (language-name base-code)}
                  "target" {"code" target-code
                            "name" (language-name target-code)}}
     "ui" ui}))

(defn envelope
  "JSON envelope for one course."
  [course entries]
  {"version" 2
   "course" (resolve-course course)
   "count" (count entries)
   "lessons" (normalized-lessons entries)})

(defn render-json
  ([entries] (render-json (fixture-course "fixture") entries))
  ([course entries] (str (json/generate-string (envelope course entries)) "\n")))

(defn courses-envelope
  "JSON manifest for the course chooser."
  [course-dirs]
  (let [ui (normalize (read-ui-pack "lesson/ui" :en))]
    {"version" 2
     "site" {"title" (get ui "site-title")
             "tagline" (get ui "site-tagline")
             "chooseCourse" (get ui "choose-course")}
     "ui" ui
     "courses" (vec (for [dir course-dirs
                          :let [course (load-course dir)]]
                      (let [resolved (resolve-course course)]
(select-keys resolved
                                      ["slug" "title" "tagline" "htmlTitle"
                                       "htmlLang" "lessonCount" "languages"
                                       "legacyHash" "legacyProgress"]))))}))

;; ---------------------------------------------------------------------------
;; Validation

(defn- non-empty-string? [x]
  (and (string? x) (not (str/blank? x))))

(defn- string-or-nil? [x]
  (or (nil? x) (string? x)))

(defn- vector-of-strings? [x]
  (and (vector? x) (every? string? x)))

(defn- vector-of-non-empty-strings? [x]
  (and (vector? x) (seq x) (every? non-empty-string? x)))

(defn- vector-of-vectors? [x]
  (and (vector? x) (every? vector? x)))

(defn- key-problems
  ([m required path] (key-problems m required #{} path))
  ([m required optional path]
   (let [allowed (into required optional)
         missing (remove #(contains? m %) required)
         extra (remove #(contains? allowed %) (keys m))]
     (cond-> []
       (seq missing) (conj (format "%s: missing %s" path (str/join ", " (map pr-str missing))))
       (seq extra) (conj (format "%s: unexpected %s" path (str/join ", " (map pr-str extra))))))))

(defn- validate-course-map [dir course]
  (let [path "course.edn"
        slug (:course/slug course)
        languages (:course/languages course)
        ui (:course/ui course)
        lesson-count (:course/lesson-count course)]
    (cond-> (key-problems course course-required-keys course-optional-keys path)
      (not (non-empty-string? slug))
      (conj (format "%s: :course/slug must be a non-empty string" path))

      (and (non-empty-string? slug)
           (not= slug (str (fs/file-name dir))))
      (conj (format "%s: :course/slug %s must equal the directory name %s"
                    path (pr-str slug) (pr-str (str (fs/file-name dir)))))

      (not (keyword? ui))
      (conj (format "%s: :course/ui must be a keyword" path))

      (not (non-empty-string? (:course/title course)))
      (conj (format "%s: :course/title must be a non-empty string" path))

      (not (string-or-nil? (:course/tagline course)))
      (conj (format "%s: :course/tagline must be a string or nil" path))

      (not (string-or-nil? (:course/html-title course)))
      (conj (format "%s: :course/html-title must be a string or nil" path))

      (not (integer? lesson-count))
      (conj (format "%s: :course/lesson-count must be an integer" path))

      (not (and (map? languages)
                (non-empty-string? (get-in languages [:base :code]))
                (non-empty-string? (get-in languages [:target :code]))))
      (conj (format "%s: :course/languages must provide :base/:code and :target/:code strings" path))

      (and (map? languages)
           (= (get-in languages [:base :code]) (get-in languages [:target :code])))
      (conj (format "%s: :course/languages :base and :target must differ" path))

      (and (some? (:course/vocabulary-count course))
           (not (and (integer? (:course/vocabulary-count course))
                     (pos? (:course/vocabulary-count course)))))
      (conj (format "%s: :course/vocabulary-count must be a positive integer" path))

      (and (contains? course :course/empty-vocabulary-lessons)
           (not (and (vector? (:course/empty-vocabulary-lessons course))
                     (every? integer? (:course/empty-vocabulary-lessons course)))))
      (conj (format "%s: :course/empty-vocabulary-lessons must be a vector of integers" path))

      (and (contains? course :course/legacy-hash?)
           (not (boolean? (:course/legacy-hash? course))))
      (conj (format "%s: :course/legacy-hash? must be a boolean" path))

      (and (contains? course :course/legacy-progress?)
           (not (boolean? (:course/legacy-progress? course))))
      (conj (format "%s: :course/legacy-progress? must be a boolean" path)))))

(defn- validate-ui-pack [code ui]
  (let [path (str "ui/" (name code) ".edn")]
    (cond-> (key-problems ui ui-required-keys path)
      (not (map? (:section-labels ui)))
      (conj (format "%s: :section-labels must be a map" path))

      (and (map? (:section-labels ui))
           (not (every? #(contains? (:section-labels ui) %) section-types)))
      (conj (format "%s: :section-labels must label all section types" path))

      (not (map? (:vocab-kind-labels ui)))
      (conj (format "%s: :vocab-kind-labels must be a map" path))

      (and (map? (:vocab-kind-labels ui))
           (not (every? #(contains? (:vocab-kind-labels ui) %) vocab-kinds)))
      (conj (format "%s: :vocab-kind-labels must label all vocabulary kinds" path))

      (not (map? (:language-names ui)))
      (conj (format "%s: :language-names must be a map" path)))))

(defn- validate-section [idx section]
  (let [path (format "section %d" idx)]
    (cond-> (key-problems section section-keys path)
      (not (contains? section-types (:section/type section)))
      (conj (format "%s: unknown :section/type %s" path (pr-str (:section/type section))))

      (not (or (nil? (:section/id section)) (integer? (:section/id section))))
      (conj (format "%s: :section/id must be an integer or nil" path))

      (not (string-or-nil? (:section/title section)))
      (conj (format "%s: :section/title must be a string or nil" path))

      (not (vector-of-strings? (:section/body section)))
      (conj (format "%s: :section/body must be a vector of strings" path)))))

(defn- validate-vocab-item [idx item]
  (let [path (format "vocabulary %d" idx)]
    (cond-> (key-problems item vocab-required-keys vocab-optional-keys path)
      (not (integer? (:vocab/id item)))
      (conj (format "%s: :vocab/id must be an integer" path))

      (not (contains? vocab-kinds (:vocab/kind item)))
      (conj (format "%s: unknown :vocab/kind %s" path (pr-str (:vocab/kind item))))

      (not (non-empty-string? (:vocab/target item)))
      (conj (format "%s: :vocab/target must be a non-empty string" path))

      (not (non-empty-string? (:vocab/base item)))
      (conj (format "%s: :vocab/base must be a non-empty string" path))

      (not (non-empty-string? (:vocab/pronunciation item)))
      (conj (format "%s: :vocab/pronunciation must be a non-empty string" path))

      (not (string-or-nil? (:vocab/mnemonic item)))
      (conj (format "%s: :vocab/mnemonic must be a string or nil" path))

      (and (contains? item :vocab/ipa)
           (not (string-or-nil? (:vocab/ipa item))))
      (conj (format "%s: :vocab/ipa must be a string or nil" path)))))

(defn- validate-alternatives [exercise-path idx item]
  (let [alts (:item/alternatives item)
        expected-turns (count (:item/answer item))
        path (format "%s item %d" exercise-path idx)]
    (cond-> []
      (and (some? alts) (not (vector-of-vectors? alts)))
      (conj (format "%s: :item/alternatives must be a vector of vectors" path))

      (and (vector-of-vectors? alts)
           (not (every? vector-of-non-empty-strings? alts)))
      (conj (format "%s: every :item/alternatives entry must be a non-empty vector of strings" path))

      (and (vector-of-vectors? alts)
           (not (every? #(= expected-turns (count %)) alts)))
      (conj (format "%s: every :item/alternatives entry must have %d turn(s)"
                    path expected-turns)))))

(defn- validate-item [exercise-path idx item]
  (let [path (format "%s item %d" exercise-path idx)]
    (cond-> (key-problems item item-required-keys item-optional-keys path)
      (not (integer? (:item/n item)))
      (conj (format "%s: :item/n must be an integer" path))

      (not (vector-of-non-empty-strings? (:item/prompt item)))
      (conj (format "%s: :item/prompt must be a non-empty vector of non-empty strings" path))

      (not (vector-of-non-empty-strings? (:item/answer item)))
      (conj (format "%s: :item/answer must be a non-empty vector of non-empty strings" path))

      :always
      (into (validate-alternatives exercise-path idx item)))))

(defn- validate-exercise [idx exercise]
  (let [path (format "exercise %d (%s)" idx (pr-str (:exercise/id exercise)))
        items (:exercise/items exercise)]
    (cond-> (key-problems exercise exercise-keys path)
      (not (non-empty-string? (:exercise/id exercise)))
      (conj (format "%s: :exercise/id must be a non-empty string" path))

      (not (contains? exercise-skills (:exercise/skill exercise)))
      (conj (format "%s: unknown :exercise/skill %s" path (pr-str (:exercise/skill exercise))))

      (not (contains? exercise-directions (:exercise/direction exercise)))
      (conj (format "%s: unknown :exercise/direction %s" path (pr-str (:exercise/direction exercise))))

      (not (boolean? (:exercise/dialog? exercise)))
      (conj (format "%s: :exercise/dialog? must be a boolean" path))

      (not (string? (:exercise/instructions exercise)))
      (conj (format "%s: :exercise/instructions must be a string" path))

      (not (vector-of-strings? (:exercise/hints exercise)))
      (conj (format "%s: :exercise/hints must be a vector of strings" path))

      (not (vector-of-strings? (:exercise/notes exercise)))
      (conj (format "%s: :exercise/notes must be a vector of strings" path))

      (not (and (vector? items) (seq items)))
      (conj (format "%s: :exercise/items must be a non-empty vector" path))

      :always
      (into (mapcat (fn [i item] (validate-item path i item)) (range) items)))))

(defn validate-lesson
  ([number lesson] (validate-lesson number lesson {}))
  ([number lesson {:keys [empty-vocabulary-lessons]
                   :or {empty-vocabulary-lessons (set default-empty-vocabulary-lessons)}}]
   (if-not (map? lesson)
     {:errors [(format "%d.edn: top-level form is not a map" number)] :warnings []}
     (let [errors (atom [])
           warnings (atom [])
           add! (fn [& msgs] (doseq [m msgs] (swap! errors conj (str number ".edn: " m))))
           warn! (fn [& msgs] (doseq [m msgs] (swap! warnings conj (str number ".edn: " m))))]
       (doseq [p (key-problems lesson lesson-required-keys lesson-optional-keys (str number ".edn"))]
         (swap! errors conj p))

       (when-not (= number (:lesson/number lesson))
         (add! (format ":lesson/number is %s but filename is %d.edn"
                       (pr-str (:lesson/number lesson)) number)))

       (when-not (non-empty-string? (:lesson/label lesson))
         (add! ":lesson/label must be a non-empty string"))

       (when-not (string-or-nil? (:lesson/title lesson))
         (add! ":lesson/title must be a string or nil"))

       (when-not (non-empty-string? (:lesson/target-title lesson))
         (add! ":lesson/target-title must be a non-empty string"))

       (let [step (:lesson/step lesson)]
         (when-not (map? step)
           (add! ":lesson/step must be a map"))
         (when (map? step)
           (doseq [p (key-problems step step-keys (str number ".edn step"))]
             (swap! errors conj p))
           (when-not (integer? (:step/number step))
             (add! ":step/number must be an integer"))
           (when-not (non-empty-string? (:step/title step))
             (add! ":step/title must be a non-empty string"))))

       (when (contains? lesson :lesson/source-pages)
         (let [pages (:lesson/source-pages lesson)]
           (when-not (and (vector? pages)
                          (= 2 (count pages))
                          (every? integer? pages)
                          (<= (first pages) (second pages)))
             (add! (format ":lesson/source-pages must be two integers [start end], got %s"
                           (pr-str pages))))))

       (when-not (vector-of-strings? (:lesson/intro lesson))
         (add! ":lesson/intro must be a vector of strings"))

       (let [sections (:lesson/sections lesson)]
         (when-not (vector? sections)
           (add! ":lesson/sections must be a vector"))
         (when (vector? sections)
           (doseq [p (mapcat (fn [i s] (validate-section i s)) (range) sections)]
             (swap! errors conj (str number ".edn: " p)))))

       (let [vocabulary (:lesson/vocabulary lesson)]
         (when-not (vector? vocabulary)
           (add! ":lesson/vocabulary must be a vector"))
         (when (vector? vocabulary)
           (doseq [p (mapcat (fn [i v] (validate-vocab-item i v)) (range) vocabulary)]
             (swap! errors conj (str number ".edn: " p)))
           (when (and (empty? vocabulary)
                      (not (contains? empty-vocabulary-lessons number)))
             (warn! ":lesson/vocabulary is empty (not in :course/empty-vocabulary-lessons)"))))

       (let [exercises (:lesson/exercises lesson)]
         (when-not (vector? exercises)
           (add! ":lesson/exercises must be a vector"))
         (when (vector? exercises)
           (doseq [p (mapcat (fn [i e] (validate-exercise i e)) (range) exercises)]
             (swap! errors conj (str number ".edn: " p)))
           (let [ids (map :exercise/id exercises)
                 duplicates (->> ids frequencies (filter #(> (val %) 1)) keys vec)]
             (when (seq duplicates)
               (add! (str ":exercise/id values are not unique: " (pr-str duplicates)))))
           (doseq [exercise exercises
                   :when (:exercise/dialog? exercise)
                   item (:exercise/items exercise)
                   :let [p (count (:item/prompt item))
                         a (count (:item/answer item))]
                   :when (not= p a)]
             (warn! (format "exercise %s item %s: %d prompt turns but %d answer turns (dialog lengths differ)"
                            (pr-str (:exercise/id exercise)) (pr-str (:item/n item)) p a)))))

       (when-not (vector-of-strings? (:lesson/closing lesson))
         (add! ":lesson/closing must be a vector of strings"))

       {:errors @errors :warnings @warnings}))))

(defn- validate-lesson-numbers [entries course]
  (let [expected (set (expected-lesson-numbers (:course/lesson-count course)))
        numbers (set (map :number entries))
        out-of-range (remove expected numbers)]
    (cond-> []
      (seq out-of-range)
      (conj (str "lesson numbers out of range: " (pr-str (vec (sort out-of-range)))
                 " (expected 1.." (:course/lesson-count course) ")")))))

(defn validate-corpus
  "Validate parsed lesson entries for one course.
  Returns {:errors [...] :warnings [...]}."
  [entries {:keys [allow-partial course]
            :or {course (fixture-course "fixture")}}]
  (let [course (merge {:course/lesson-count default-lesson-count
                       :course/empty-vocabulary-lessons default-empty-vocabulary-lessons}
                      course)
        empty-lessons (set (:course/empty-vocabulary-lessons course))
        expected-numbers (vec (expected-lesson-numbers (:course/lesson-count course)))
        errors (atom [])
        warnings (atom [])]
    (doseq [{:keys [number lesson]} entries]
      (let [lesson-report (validate-lesson number lesson
                                           {:empty-vocabulary-lessons empty-lessons})]
        (swap! errors into (:errors lesson-report))
        (swap! warnings into (:warnings lesson-report))))

    (swap! errors into (validate-lesson-numbers entries course))

    (let [by-number (group-by :number entries)]
      (when (some #(> (count %) 1) (vals by-number))
        (swap! errors conj (str "duplicate lesson numbers: "
                                (pr-str (mapv key (filter #(> (count (val %)) 1) by-number))))))
      (let [declared (group-by (comp :lesson/number :lesson) entries)
            duplicates (->> declared
                            (filter #(> (count (val %)) 1))
                            (mapv key))]
        (when (seq duplicates)
          (swap! errors conj (str "duplicate lesson numbers: " (pr-str duplicates))))))
    (when-not allow-partial
      (let [numbers (set (map :number entries))
            missing (remove numbers expected-numbers)]
        (when (seq missing)
          (swap! errors conj (str "missing lessons: " (str/join ", " missing)
                                  " (expected 1.edn through "
                                  (:course/lesson-count course) ".edn)")))))
    (let [ids (mapcat (fn [{:keys [lesson]}]
                        (map :vocab/id (:lesson/vocabulary lesson)))
                      entries)
          sorted-ids (vec (sort ids))
          expected (vec (range 1 (inc (count ids))))]
      (when (not= sorted-ids expected)
        (let [dupes (->> ids frequencies (filter #(> (val %) 1)) keys vec)]
          (swap! errors conj
                 (str "vocabulary ids are not globally contiguous from 1"
                      (when (seq dupes) (str "; duplicates: " (pr-str dupes)))
                      "; got " (pr-str sorted-ids)))))
      (when (and (not allow-partial)
                 (:course/vocabulary-count course)
                 (= (count entries) (:course/lesson-count course))
                 (not= (count ids) (:course/vocabulary-count course)))
        (swap! errors conj
               (str "expected " (:course/vocabulary-count course)
                    " vocabulary entries across the corpus, got " (count ids)))))
    (let [numbers (vec (sort (map :number entries)))]
      (println "Course:    " (or (:course/slug course) "(fixture)"))
      (println "Lessons:   " (count entries)
               (if allow-partial
                 "(partial)"
                 (str "(expected 1.." (:course/lesson-count course) ": "
                      (if (= numbers expected-numbers) "all present" "gaps")
                      ")")))
      (println "Vocabulary:" (count (mapcat (fn [{:keys [lesson]}]
                                              (:lesson/vocabulary lesson)) entries)))
      (println "Exercises: " (count (mapcat (fn [{:keys [lesson]}]
                                              (:lesson/exercises lesson)) entries)))
      (println "Items:     " (count (mapcat (fn [{:keys [lesson]}]
                                              (mapcat :exercise/items (:lesson/exercises lesson)))
                                            entries)))
      (println))
    {:errors @errors :warnings @warnings}))

;; ---------------------------------------------------------------------------
;; Commands

(def cli-spec
  {:dir {:desc "Single course directory (fixture mode)"}
   :out {:desc "Output JSON file in --dir mode"}
   :out-dir {:default "public" :desc "Directory for courses.json and lessons/<slug>.json"}
   :check {:default false :coerce :boolean :desc "Validate and summarize without writing"}
   :allow-partial {:default false :coerce :boolean :desc "Accept whatever N.edn files exist"}
   :watch {:default false :coerce :boolean :desc "Poll EDN mtimes and regenerate on change"}})

(defn parse-opts [args]
  (let [opts (cli/parse-opts args {:spec cli-spec})]
    (cond-> opts
      (:dir opts) (update :dir str)
      (:out opts) (update :out str))))

(defn- print-report! [{:keys [errors warnings]}]
  (doseq [w warnings]
    (binding [*out* *err*]
      (println "warning:" w)))
  (doseq [e errors]
    (binding [*out* *err*]
      (println "error:  " e))))

(defn- write-json! [path value]
  (let [parent (fs/parent path)]
    (when parent (fs/create-dirs parent))
    (spit (str path) (str (json/generate-string value) "\n"))
    (println "Wrote" (str path) (str "(" (fs/size path) " bytes)"))))

(defn- run-single!
  "Transform one directory. Used by --dir, fixtures and tests."
  [{:keys [dir out check allow-partial]}]
  (let [course (load-course dir)
        lesson-count (:course/lesson-count course)
        {course-errors :errors} (let [report (atom nil)]
                                  (with-out-str
                                    (reset! report (validate-course-map dir course)))
                                  {:errors @report})
        {ui-errors :errors :as ui-report}
        (try
          {:errors (validate-ui-pack (:course/ui course)
                                     (read-ui-pack "lesson/ui" (:course/ui course)))}
          (catch Exception e
            {:errors [(format "ui/%s.edn: cannot read UI pack: %s"
                              (name (:course/ui course))
                              (or (ex-message e) (str e)))]}))
        {entries :entries problems :problems missing :missing}
        (collect dir allow-partial lesson-count)
        load-errors (cond-> (vec problems)
                      (seq missing)
                      (conj (str "missing lessons: " (str/join ", " missing)
                                 " (expected 1.edn through " lesson-count ".edn)"))

                      (and (empty? entries) (empty? missing))
                      (conj (str "no lesson files (N.edn) found in " dir)))]
    (if (or (seq load-errors) (seq course-errors) (seq ui-errors))
      (do (print-report! {:errors (concat course-errors ui-errors load-errors)
                          :warnings []})
          {:ok false})
      (let [{:keys [errors warnings] :as report}
            (validate-corpus entries {:allow-partial allow-partial :course course})]
        (print-report! report)
        (if (seq errors)
          {:ok false}
          (do
            (when check
              (println "Check OK:" (count entries) "lesson(s) for" (:course/slug course)))
            (when-not check
              (let [out (or out
                            (fs/path "public" "lessons" (str (:course/slug course) ".json")))]
                (write-json! out (envelope course entries))))
            {:ok true}))))))

(defn- run-all!
  "Discover and transform every course under lesson/."
  [{:keys [out-dir check allow-partial]}]
  (let [dirs (course-dirs "lesson")]
    (if (empty? dirs)
      (do (binding [*out* *err*]
            (println "error:  no course directories with course.edn found under lesson/"))
          {:ok false})
      (let [results (atom [])
            slugs (atom [])]
        (doseq [dir dirs]
          (let [{:keys [ok] :as result}
                (run-single! {:dir dir
                              :out nil
                              :check true
                              :allow-partial allow-partial})]
            (swap! results conj result)
            (when ok
              (swap! slugs conj (:course/slug (load-course dir))))))
        (let [duplicate-slugs (->> @slugs frequencies (filter #(> (val %) 1)) keys vec)
              ok? (and (every? :ok @results) (empty? duplicate-slugs))]
          (when (seq duplicate-slugs)
            (binding [*out* *err*]
              (println "error:  duplicate course slugs:" (pr-str duplicate-slugs))))
          (if-not ok?
            {:ok false}
            (do
              (when-not check
                (let [out-dir (fs/path (or out-dir "public"))
                      courses (courses-envelope dirs)]
                  (write-json! (fs/path out-dir "courses.json") courses)
                  (doseq [dir dirs]
                    (let [course (load-course dir)
                          {entries :entries} (collect dir allow-partial
                                                      (:course/lesson-count course))
                          out (fs/path out-dir "lessons"
                                       (str (:course/slug course) ".json"))]
                      (write-json! out (envelope course entries))))))
              (println "Check OK:" (count dirs) "course(s)")
              {:ok true})))))))

(defn run
  "Load, validate, then either check or write. Returns {:ok bool}."
  [opts]
  (if (:dir opts)
    (run-single! opts)
    (run-all! opts)))

(defn- edns-under [root]
  (try
    (->> (fs/glob root "**.edn") (map str) sort vec)
    (catch Exception _
      [])))

(defn- signature [root]
  (mapv (fn [path]
          [path
           (some-> (fs/last-modified-time path) str)
           (fs/size path)])
        (edns-under root)))

(defn watch! [opts]
  (println "Watching lesson/ for changes...")
  (loop [previous nil]
    (let [current (signature "lesson")]
      (when (not= previous current)
        (try
          (let [{:keys [ok]} (run (assoc opts :watch false))]
            (when-not ok
              (binding [*out* *err*]
                (println "Not writing JSON until the errors above are fixed."))))
          (catch Exception e
            (binding [*out* *err*]
              (println "error:  " (or (ex-message e) (str e)))))))
      (Thread/sleep 1000)
      (recur current))))

(defn -main [& args]
  (let [opts (parse-opts args)]
    (if (:watch opts)
      (watch! opts)
      (let [{:keys [ok]} (run opts)]
        (when-not ok
          (System/exit 1))))))