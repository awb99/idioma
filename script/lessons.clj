(ns lessons
  "Transform extracted Synergy Spanish lesson EDN files into public/lessons.json.

  Every map key is normalized with `name` (namespace stripped), every keyword
  value is normalized to its unqualified name, and maps/vectors become JSON
  objects/arrays. The transform validates the extraction schema first so a bad
  lesson file fails the build with a readable report instead of shipping broken
  data."
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def expected-lesson-numbers
  (range 1 26))

(def expected-vocabulary-count
  138)

(def top-keys
  #{:lesson/number :lesson/label :lesson/title :lesson/spanish-title
    :lesson/step :lesson/pages :lesson/intro :lesson/sections
    :lesson/vocabulary :lesson/exercises :lesson/closing})

(def step-keys
  #{:step/number :step/title})

(def section-keys
  #{:section/type :section/id :section/title :section/body})

(def vocab-keys
  #{:vocab/id :vocab/kind :vocab/spanish :vocab/english
    :vocab/pronunciation :vocab/mnemonic})

(def exercise-keys
  #{:exercise/id :exercise/skill :exercise/direction :exercise/dialog?
    :exercise/instructions :exercise/hints :exercise/items :exercise/notes})

(def item-keys
  #{:item/n :item/prompt :item/answer})

(def vocab-kinds
  #{:synergy-verb :brick-verb :mortar :sentence-starter :other})

(def exercise-skills
  #{:recall :translate})

(def exercise-directions
  #{:to-english :to-spanish})

(def section-types
  #{:speaking-hints :communication-multiplied :accuracy-pointer
    :sentence-builder :sentence-starter :multipurpose-word :step-intro
    :closing})

(def empty-vocabulary-lessons
  #{21 24})

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

(defn collect
  "Read the lesson files from dir. Returns
  {:entries [{:file f :number n :lesson m}] :problems [str ...] :missing [n ...]}.

  In full mode exactly 1..25 must exist. With --allow-partial every readable
  N.edn file is accepted."
  [dir allow-partial?]
  (let [files (lesson-files dir)
        files (if allow-partial?
                files
                (filterv (fn [f] (contains? (set expected-lesson-numbers)
                                            (lesson-number-from-file f)))
                         files))
        present (set (map lesson-number-from-file files))
        missing (when-not allow-partial?
                  (remove present expected-lesson-numbers))
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

(defn envelope [entries]
  {"version" 1
   "count" (count entries)
   "lessons" (normalized-lessons entries)})

(defn render-json [entries]
  (str (json/generate-string (envelope entries)) "\n"))

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

(defn- key-problems [m expected path]
  (let [missing (remove #(contains? m %) expected)
        extra (remove #(contains? expected %) (keys m))]
    (cond-> []
      (seq missing) (conj (format "%s: missing %s" path (str/join ", " (map pr-str missing))))
      (seq extra) (conj (format "%s: unexpected %s" path (str/join ", " (map pr-str extra)))))))

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
    (cond-> (key-problems item vocab-keys path)
      (not (integer? (:vocab/id item)))
      (conj (format "%s: :vocab/id must be an integer" path))

      (not (contains? vocab-kinds (:vocab/kind item)))
      (conj (format "%s: unknown :vocab/kind %s" path (pr-str (:vocab/kind item))))

      (not (string? (:vocab/spanish item)))
      (conj (format "%s: :vocab/spanish must be a string" path))

      (not (string? (:vocab/english item)))
      (conj (format "%s: :vocab/english must be a string" path))

      (not (string-or-nil? (:vocab/pronunciation item)))
      (conj (format "%s: :vocab/pronunciation must be a string or nil" path))

      (not (string-or-nil? (:vocab/mnemonic item)))
      (conj (format "%s: :vocab/mnemonic must be a string or nil" path)))))

(defn- validate-item [exercise-path idx item]
  (let [path (format "%s item %d" exercise-path idx)]
    (cond-> (key-problems item item-keys path)
      (not (integer? (:item/n item)))
      (conj (format "%s: :item/n must be an integer" path))

      (not (vector-of-non-empty-strings? (:item/prompt item)))
      (conj (format "%s: :item/prompt must be a non-empty vector of non-empty strings" path))

      (not (vector-of-non-empty-strings? (:item/answer item)))
      (conj (format "%s: :item/answer must be a non-empty vector of non-empty strings" path)))))

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

(defn- validate-lesson [number lesson]
  (if-not (map? lesson)
    {:errors [(format "%d.edn: top-level form is not a map" number)] :warnings []}
    (let [errors (atom [])
          warnings (atom [])
          add! (fn [& msgs] (doseq [m msgs] (swap! errors conj (str number ".edn: " m))))
          warn! (fn [& msgs] (doseq [m msgs] (swap! warnings conj (str number ".edn: " m))))]
      (doseq [p (key-problems lesson top-keys (str number ".edn"))]
        (swap! errors conj p))

      (when-not (= number (:lesson/number lesson))
        (add! (format ":lesson/number is %s but filename is %d.edn"
                      (pr-str (:lesson/number lesson)) number)))

      (when-not (non-empty-string? (:lesson/label lesson))
        (add! ":lesson/label must be a non-empty string"))

      (when-not (string-or-nil? (:lesson/title lesson))
        (add! ":lesson/title must be a string or nil"))

      (when-not (non-empty-string? (:lesson/spanish-title lesson))
        (add! ":lesson/spanish-title must be a non-empty string"))

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

      (let [pages (:lesson/pages lesson)]
        (when-not (and (vector? pages)
                       (= 2 (count pages))
                       (every? integer? pages)
                       (<= (first pages) (second pages)))
          (add! (format ":lesson/pages must be two integers [start end], got %s"
                        (pr-str pages)))))

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
            (warn! ":lesson/vocabulary is empty (only lessons 21 and 24 may be empty)"))))

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

      {:errors @errors :warnings @warnings})))

(defn validate-corpus
  "Validate parsed lesson entries. Returns {:errors [...] :warnings [...]}."
  [entries {:keys [allow-partial]}]
  (let [errors (atom [])
        warnings (atom [])]
    (doseq [{:keys [number lesson]} entries]
      (let [lesson-report (validate-lesson number lesson)]
        (swap! errors into (:errors lesson-report))
        (swap! warnings into (:warnings lesson-report))))

    (let [by-number (group-by :number entries)
          numbers (set (map :number entries))]
      (when (some #(> (count %) 1) (vals by-number))
        (swap! errors conj (str "duplicate lesson numbers: "
                                (pr-str (mapv key (filter #(> (count (val %)) 1) by-number))))))
      (let [declared (group-by (comp :lesson/number :lesson) entries)
            duplicates (->> declared
                            (filter #(> (count (val %)) 1))
                            (mapv key))]
        (when (seq duplicates)
          (swap! errors conj (str "duplicate lesson numbers: " (pr-str duplicates)))))
      (when-not allow-partial
        (let [missing (remove numbers expected-lesson-numbers)]
          (when (seq missing)
            (swap! errors conj (str "missing lessons: " (str/join ", " missing)
                                    " (expected 1.edn through 25.edn)")))))
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
                   (= (count entries) 25)
                   (not= (count ids) expected-vocabulary-count))
          (swap! errors conj
                 (str "expected " expected-vocabulary-count
                      " vocabulary entries across the corpus, got " (count ids)))))
      (let [numbers (vec (sort (map :number entries)))]
        (println "Lessons:   " (count entries)
                 (if allow-partial
                   "(partial)"
                   (str "(expected 1..25: "
                        (if (= numbers (vec expected-lesson-numbers)) "all present" "gaps")
                        ")")))
        (println "Vocabulary:" (count (mapcat (fn [{:keys [lesson]}]
                                                (:lesson/vocabulary lesson)) entries)))
        (println "Exercises: " (count (mapcat (fn [{:keys [lesson]}]
                                                (:lesson/exercises lesson)) entries)))
        (println "Items:     " (count (mapcat (fn [{:keys [lesson]}]
                                                (mapcat :exercise/items (:lesson/exercises lesson)))
                                              entries)))))
    {:errors @errors :warnings @warnings}))

;; ---------------------------------------------------------------------------
;; Commands

(def cli-spec
  {:dir {:default "lesson/spanish" :desc "Directory containing 1.edn .. 25.edn"}
   :out {:default "public/lessons.json" :desc "Output JSON file"}
   :check {:default false :coerce :boolean :desc "Validate and summarize without writing"}
   :allow-partial {:default false :coerce :boolean :desc "Accept whatever N.edn files exist"}
   :watch {:default false :coerce :boolean :desc "Poll mtimes and regenerate on change"}})

(defn parse-opts [args]
  (let [opts (cli/parse-opts args {:spec cli-spec})]
    (update opts :dir str)))

(defn- print-report! [{:keys [errors warnings]}]
  (doseq [w warnings]
    (binding [*out* *err*]
      (println "warning:" w)))
  (doseq [e errors]
    (binding [*out* *err*]
      (println "error:  " e))))

(defn run
  "Load, validate, then either check or write. Returns {:ok bool}."
  [{:keys [dir out check allow-partial]}]
  (let [{:keys [entries problems missing]} (collect dir allow-partial)
        load-errors (cond-> (vec problems)
                      (seq missing)
                      (conj (str "missing lessons: " (str/join ", " missing)
                                 " (expected 1.edn through 25.edn)"))

                      (and (empty? entries) (empty? missing))
                      (conj (str "no lesson files (N.edn) found in " dir)))]
    (if (seq load-errors)
      (do (doseq [e load-errors]
            (binding [*out* *err*] (println "error:  " e)))
          {:ok false})
      (let [{:keys [errors warnings] :as report} (validate-corpus entries {:allow-partial allow-partial})]
        (print-report! report)
        (if (seq errors)
          {:ok false}
          (do
            (when check
              (println "Check OK:" (count entries) "lesson(s)"))
            (when-not check
              (let [parent (fs/parent out)]
                (when parent (fs/create-dirs parent))
                (spit (str out) (render-json entries))
                (println "Wrote" out
                         (str "(" (count entries) " lesson(s), "
                              (fs/size out) " bytes)"))))
            {:ok true}))))))

(defn- signature [dir]
  (mapv (fn [f]
          [(str (fs/file-name f))
           (some-> (fs/last-modified-time f) str)
           (fs/size f)])
        (lesson-files dir)))

(defn watch! [{:keys [dir] :as opts}]
  (println "Watching" (str dir) "for changes...")
  (loop [previous nil]
    (let [current (signature dir)]
      (when (not= previous current)
        (try
          (let [{:keys [ok]} (run (assoc opts :watch false))]
            (when-not ok
              (binding [*out* *err*]
                (println "Not writing lessons.json until the errors above are fixed."))))
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