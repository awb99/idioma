(ns lessons-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [lessons :as lessons]))

(def fixture-dir "test/fixtures/lessons")
(def lesson-root "lesson")

(defn- fixture-lesson []
  (edn/read-string (slurp (str (fs/path fixture-dir "1.edn")))))

(defn- fixture-course []
  (lessons/fixture-course fixture-dir))

(defn- validate-quiet
  "validate-corpus prints a summary; keep test output clean."
  [entries opts]
  (let [result (atom nil)]
    (with-out-str
      (reset! result (lessons/validate-corpus entries opts)))
    @result))

(defn- errors-for
  ([lesson] (errors-for lesson 1))
  ([lesson number]
   (:errors (lessons/validate-lesson number lesson))))

(defn- any-error-contains? [errors needle]
  (some #(str/includes? % needle) errors))

(defn- write-lesson! [dir n lesson]
  (spit (str (fs/path dir (str n ".edn"))) (pr-str lesson)))

(defn- lesson-entries [dir course]
  (lessons/collect dir false (:course/lesson-count course)))

(defn- course-dirs []
  (lessons/course-dirs lesson-root))

(defn- course-for [dir]
  (lessons/load-course dir))

(defn- ui-pack [code]
  (edn/read-string (slurp (str (fs/path lesson-root "ui" (str (name code) ".edn"))))))

(defn- key-set [x]
  (cond
    (map? x) (reduce (fn [acc [k v]] (into (conj acc k) (key-set v))) #{} x)
    (vector? x) (reduce (fn [acc v] (into acc (key-set v))) #{} x)
    :else #{}))

(defn- text-words [s]
  (->> (-> (str s)
           str/lower-case
           (str/replace #"[^a-záéíóúüñäöß ]" " ")
           (str/split #"\s+"))
       (remove str/blank?)
       set))

(def english-stopwords
  #{"the" "and" "to" "of" "is" "are" "am" "was" "were" "an" "in" "on"
    "for" "with" "you" "i" "we" "they" "do" "does" "did" "have" "has" "had"
    "can" "could" "would" "will" "want" "needs" "need" "going" "not" "my"
    "your" "his" "her" "their" "our" "them" "him" "us" "this" "that" "there"
    "here" "because" "but" "or" "if" "it" "she" "from" "at" "by" "so"})

(def spanish-stopwords
  #{"el" "la" "los" "las" "un" "una" "unos" "unas" "de" "del" "que" "y" "a"
    "al" "en" "es" "son" "era" "por" "para" "con" "sin" "yo" "tú" "nosotros"
    "ellos" "ellas" "él" "ella" "usted" "ustedes" "quiero" "quiere" "queremos"
    "quieren" "necesito" "necesita" "necesitan" "tengo" "tiene" "tienen"
    "puedo" "puede" "pueden" "voy" "va" "van" "vamos" "mi" "mis" "tu" "tus"
    "su" "sus" "nuestro" "nuestra" "este" "esta" "estos" "estas" "ese" "esa"
    "esos" "esas" "esto" "eso" "pero" "o" "si" "sí" "porque" "como" "más"
    "muy" "también" "tampoco" "aquí" "allí" "allá" "hoy" "mañana" "ahora"
    "nunca" "siempre" "casi" "solo" "sólo" "mucho" "mucha" "muchos" "muchas"
    "poco" "poca" "bien" "tan" "tanto" "tanta" "me" "te" "le" "les" "nos"
    "lo" "se" "hay" "gusta" "gustan" "gustaría" "hace" "hacen" "hacer"
    "donde" "dónde" "quien" "quién" "qué" "cuando" "cuándo" "cómo" "cuál"
    "invitar" "viajar" "tomar" "probar" "comida" "leer" "lee" "veces"
    "diferente" "alcohol" "tequila" "café" "fútbol" "dinero"})

(def german-stopwords
  #{"der" "die" "das" "den" "dem" "des" "ein" "eine" "einen" "ist" "sind"
    "war" "bin" "bist" "ich" "du" "er" "sie" "es" "wir" "ihr" "mich" "dich"
    "sich" "uns" "euch" "mein" "dein" "sein" "unser" "kann" "können" "will"
    "wollen" "muss" "müssen" "möchte" "möchten" "habe" "hast" "haben" "hat"
    "werde" "wirst" "wird" "werden" "wo" "wann" "wie" "warum" "was" "wer"
    "wen" "wem" "dass" "ob" "nicht" "kein" "keine" "zu" "zum" "zur" "mit"
    "von" "für" "auf" "in" "im" "an" "am" "als" "auch" "noch" "schon" "sehr"
    "mehr" "viel" "nur" "so" "dann" "dort" "hier" "jetzt" "heute" "morgen"
    "gestern" "bitte" "danke"})

(defn- stopword-count [language text]
  (let [words (text-words text)
        stopwords (case language
                    "en" english-stopwords
                    "es" spanish-stopwords
                    "de" german-stopwords
                    #{})]
    (count (set/intersection words stopwords))))

(defn- answer-language [course direction]
  (if (= :to-base direction)
    (get-in course [:course/languages :base :code])
    (get-in course [:course/languages :target :code])))

(defn- prompt-language [course direction]
  (if (= :to-base direction)
    (get-in course [:course/languages :target :code])
    (get-in course [:course/languages :base :code])))

(defn- language-complaint [course exercise item]
  (let [direction (:exercise/direction exercise)
        answer-lang (answer-language course direction)
        prompt-lang (prompt-language course direction)
        answer (str/join " " (:item/answer item))
        prompt (str/join " " (:item/prompt item))]
    (for [[lang label text] [[answer-lang "answer" answer]
                             [prompt-lang "prompt" prompt]]
          :let [other (if (= lang answer-lang) prompt-lang answer-lang)
                expected (stopword-count lang text)
                wrong (stopword-count other text)]
          :when (and (>= wrong 2) (> wrong expected))]
      (format "%s %s: %s looks like %s, not %s"
              (:exercise/id exercise) label text other lang))))

;; ---------------------------------------------------------------------------

(deftest normalize-test
  (testing "namespaced keys become unqualified strings"
    (is (= {"number" 1}
           (lessons/normalize {:lesson/number 1}))))
  (testing "keyword values become unqualified strings"
    (is (= {"kind" "synergy-verb"
            "direction" "to-base"
            "dialog?" false}
           (lessons/normalize {:vocab/kind :synergy-verb
                               :exercise/direction :to-base
                               :exercise/dialog? false}))))
  (testing "nested vectors and maps recurse"
    (is (= {"items" [{"n" 1 "prompt" ["a" "b"]}]}
           (lessons/normalize {:exercise/items [{:item/n 1
                                                 :item/prompt ["a" "b"]}]}))))
  (testing "nil, numbers, booleans and strings are preserved"
    (is (= {"x" nil "y" 3 "z" true "s" "hola"}
           (lessons/normalize {:a/x nil :a/y 3 :a/z true :a/s "hola"})))))

(deftest fixture-transform-test
  (let [course (fixture-course)
        {:keys [entries problems missing]} (lessons/collect fixture-dir true
                                                            (:course/lesson-count course))
        {:keys [errors]} (validate-quiet entries {:allow-partial true :course course})
        parsed (json/parse-string (lessons/render-json course entries))
        out-lessons (get parsed "lessons")]
    (is (empty? problems))
    (is (empty? missing))
    (is (empty? errors) (pr-str errors))
    (testing "envelope"
      (is (= 2 (get parsed "version")))
      (is (= 2 (get parsed "count")))
      (is (= "lessons" (get-in parsed ["course" "slug"])))
      (is (= "English" (get-in parsed ["course" "languages" "base" "name"])))
      (is (= "Spanish" (get-in parsed ["course" "languages" "target" "name"])))
      (is (map? (get-in parsed ["course" "ui"]))))
    (testing "lessons are sorted by number"
      (is (= [1 2] (mapv #(get % "number") out-lessons))))
    (testing "output has unqualified string keys"
      (is (contains? (first out-lessons) "target-title"))
      (is (contains? (first out-lessons) "source-pages"))
      (is (not (contains? (first out-lessons) "lesson/number"))))
    (testing "keyword values became strings"
      (is (= "synergy-verb"
             (get-in out-lessons [0 "vocabulary" 0 "kind"])))
      (is (= "to-base"
             (get-in out-lessons [0 "exercises" 0 "direction"])))
      (is (= false
             (get-in out-lessons [0 "exercises" 0 "dialog?"]))))
    (testing "nested arrays/objects"
      (is (vector? (get-in out-lessons [0 "vocabulary"])))
      (is (vector? (get-in out-lessons [0 "exercises" 0 "items"])))
      (is (map? (get-in out-lessons [0 "step"]))))))

(deftest allow-partial-test
  (let [course (fixture-course)
        {:keys [entries missing]} (lessons/collect fixture-dir true
                                                   (:course/lesson-count course))
        {:keys [errors]} (validate-quiet entries {:allow-partial true :course course})]
    (is (empty? missing))
    (is (= [1 2] (mapv :number entries)))
    (is (empty? errors) (pr-str errors))))

(deftest missing-files-test
  (let [dir (fs/create-temp-dir)
        _ (write-lesson! dir 1 (fixture-lesson))
        {:keys [missing entries]} (lessons/collect dir false 25)
        {:keys [errors]} (validate-quiet entries {:allow-partial false
                                                  :course (fixture-course)})]
    (is (= [1] (mapv :number entries)))
    (is (= (vec (range 2 26)) (vec missing)))
    (is (any-error-contains? errors "missing lessons"))))

(deftest duplicate-lesson-number-test
  (let [dir (fs/create-temp-dir)
        lesson (fixture-lesson)
        _ (write-lesson! dir 1 lesson)
        _ (write-lesson! dir 2 lesson)
        {:keys [entries]} (lessons/collect dir true 25)
        {:keys [errors]} (validate-quiet entries {:allow-partial true
                                                  :course (fixture-course)})]
    (testing "the duplicate number is reported"
      (is (any-error-contains? errors "duplicate lesson numbers")))
    (testing "the filename mismatch is reported too"
      (is (any-error-contains? errors "but filename is 2.edn")))))

(deftest lesson-level-validation-test
  (testing "filename/number mismatch"
    (is (any-error-contains? (errors-for (assoc (fixture-lesson) :lesson/number 9) 1)
                             "filename is 1.edn")))
  (testing "missing top-level key"
    (is (any-error-contains? (errors-for (dissoc (fixture-lesson) :lesson/intro))
                             "missing :lesson/intro")))
  (testing "source-pages must be two integers"
    (is (any-error-contains? (errors-for (assoc (fixture-lesson) :lesson/source-pages [10]))
                             ":lesson/source-pages")))
  (testing "source-pages is optional"
    (is (empty? (errors-for (dissoc (fixture-lesson) :lesson/source-pages)))))
  (testing "step must have its two keys"
    (is (any-error-contains? (errors-for (assoc (fixture-lesson) :lesson/step {}))
                             ":step/number")))
  (testing "exercise with no items"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/exercises 0 :exercise/items] [])]
      (is (any-error-contains? (errors-for lesson)
                               ":exercise/items must be a non-empty vector"))))
  (testing "item prompt must be a non-empty vector"
    (let [lesson (assoc-in (fixture-lesson)
                           [:lesson/exercises 0 :exercise/items 0 :item/prompt] [])]
      (is (any-error-contains? (errors-for lesson) ":item/prompt"))))
  (testing "duplicate exercise ids"
    (let [lesson (assoc-in (fixture-lesson)
                           [:lesson/exercises 1 :exercise/id]
                           (get-in (fixture-lesson) [:lesson/exercises 0 :exercise/id]))]
      (is (any-error-contains? (errors-for lesson) "not unique"))))
  (testing "unknown section type"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/sections 0 :section/type] :mystery)]
      (is (any-error-contains? (errors-for lesson) "unknown :section/type"))))
  (testing "directions use the generic vocabulary"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/exercises 0 :exercise/direction]
                           :to-english)]
      (is (any-error-contains? (errors-for lesson) "unknown :exercise/direction"))))
  (testing "vocabulary target and base are non-empty"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/vocabulary 0 :vocab/target] "")]
      (is (any-error-contains? (errors-for lesson) ":vocab/target"))))
  (testing "vocabulary IPA is optional and nil-safe"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/vocabulary 0 :vocab/ipa] nil)]
      (is (empty? (errors-for lesson)))))
  (testing "alternatives must match the answer turn count"
    (let [lesson (assoc-in (fixture-lesson)
                           [:lesson/exercises 0 :exercise/items 0 :item/alternatives]
                           [["one" "two"]])]
      (is (any-error-contains? (errors-for lesson) ":item/alternatives")))))

(deftest vocabulary-contiguity-test
  (testing "non-contiguous ids fail the corpus check"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/vocabulary 0 :vocab/id] 99)
          {:keys [errors]} (validate-quiet [{:number 1 :lesson lesson}]
                                           {:allow-partial true
                                            :course (fixture-course)})]
      (is (any-error-contains? errors "vocabulary ids are not globally contiguous"))))
  (testing "duplicate vocabulary ids are named"
    (let [lesson (assoc-in (fixture-lesson)
                           [:lesson/vocabulary 1 :vocab/id]
                           (get-in (fixture-lesson) [:lesson/vocabulary 0 :vocab/id]))
          {:keys [errors]} (validate-quiet [{:number 1 :lesson lesson}]
                                           {:allow-partial true
                                            :course (fixture-course)})]
      (is (any-error-contains? errors "duplicates")))))

(deftest empty-vocabulary-test
  (testing "lessons 21 and 24 may have no vocabulary"
    (doseq [n [21 24]]
      (let [lesson (-> (fixture-lesson)
                       (assoc :lesson/number n)
                       (assoc :lesson/label (str "Lesson " n))
                       (assoc :lesson/vocabulary []))
            {:keys [errors warnings]} (validate-quiet [{:number n :lesson lesson}]
                                                      {:allow-partial true
                                                       :course (fixture-course)})]
        (is (empty? errors) (pr-str errors))
        (is (not-any? #(str/includes? % "vocabulary is empty") warnings)))))
  (testing "other lessons with no vocabulary warn but do not fail"
    (let [lesson (-> (fixture-lesson) (assoc :lesson/vocabulary []))
          {:keys [errors warnings]} (validate-quiet [{:number 1 :lesson lesson}]
                                                    {:allow-partial true
                                                     :course (fixture-course)})]
      (is (empty? errors) (pr-str errors))
      (is (any-error-contains? warnings "vocabulary is empty")))))

(deftest dialog-length-warning-test
  (testing "differing dialog turn counts warn instead of failing"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/exercises 0 :exercise/dialog?] true)
          lesson (assoc-in lesson [:lesson/exercises 0 :exercise/items 0 :item/prompt]
                           ["one" "two"])
          {:keys [errors warnings]} (validate-quiet [{:number 1 :lesson lesson}]
                                                    {:allow-partial true
                                                     :course (fixture-course)})]
      (is (empty? errors) (pr-str errors))
      (is (any-error-contains? warnings "dialog lengths differ")))))

(deftest title-may-be-nil-test
  (is (empty? (:errors (lessons/validate-lesson 1 (assoc (fixture-lesson) :lesson/title nil))))))

(deftest minimal-course-optional-keys-test
  (let [dir (fs/create-temp-dir)
        course {:course/slug (str (fs/file-name dir))
                :course/ui :en
                :course/title "Minimal course"
                :course/tagline nil
                :course/languages {:base {:code "en"}
                                   :target {:code "es"}}
                :course/lesson-count 25}
        result (atom nil)]
    (spit (str (fs/path dir "course.edn")) (pr-str course))
    (write-lesson! dir 1 (fixture-lesson))
    (with-out-str
      (reset! result (lessons/run {:dir dir :check true :allow-partial true})))
    (is (true? (:ok @result)))))

(deftest course-slug-must-match-directory-test
  (let [dir (fs/create-temp-dir)
        course (assoc (fixture-course)
                      :course/slug "wrong-slug"
                      :course/title "Wrong slug")
        result (atom nil)]
    (spit (str (fs/path dir "course.edn")) (pr-str course))
    (write-lesson! dir 1 (fixture-lesson))
    (binding [*err* (java.io.StringWriter.)]
      (with-out-str
        (reset! result (lessons/run {:dir dir :check true :allow-partial true}))))
    (is (= false (:ok @result)))))

(deftest multi-course-discovery-test
  (let [dirs (course-dirs)
        slugs (set (map (comp :course/slug course-for) dirs))]
    (is (<= 3 (count dirs)))
    (is (contains? slugs "learn-spanish"))
    (is (contains? slugs "aprende-ingles"))
    (is (contains? slugs "aprende-aleman"))))

(deftest courses-manifest-test
  (let [manifest (lessons/courses-envelope (course-dirs))
        by-slug (into {} (map (fn [course] [(get course "slug") course])
                              (get manifest "courses")))]
    (testing "site chrome comes from the English pack"
      (is (= "Idioma" (get-in manifest ["site" "title"])))
      (is (= "Choose a course" (get-in manifest ["site" "chooseCourse"])))
      (is (map? (get manifest "ui"))))
    (testing "summaries carry routing metadata"
      (is (true? (get-in by-slug ["learn-spanish" "legacyHash"])))
      (is (true? (get-in by-slug ["learn-spanish" "legacyProgress"])))
      (is (false? (get-in by-slug ["aprende-ingles" "legacyHash"])))
      (is (= "es" (get-in by-slug ["aprende-ingles" "htmlLang"])))
      (is (= "inglés" (get-in by-slug ["aprende-ingles" "languages" "target" "name"])))
      (is (= "Spanish" (get-in by-slug ["learn-spanish" "languages" "target" "name"])))
      (is (= 25 (get-in by-slug ["learn-spanish" "lessonCount"])))
      (is (= "es" (get-in by-slug ["aprende-aleman" "htmlLang"])))
      (is (= "español" (get-in by-slug ["aprende-aleman" "languages" "base" "name"])))
      (is (= "alemán" (get-in by-slug ["aprende-aleman" "languages" "target" "name"])))
      (is (= 25 (get-in by-slug ["aprende-aleman" "lessonCount"])))
      (is (false? (get-in by-slug ["aprende-aleman" "legacyHash"])))
      (is (false? (get-in by-slug ["aprende-aleman" "legacyProgress"]))))))

(deftest real-corpus-test
  (doseq [dir (course-dirs)]
    (let [course (course-for dir)
          {:keys [entries problems missing]} (lesson-entries dir course)
          {:keys [errors warnings]} (validate-quiet entries {:allow-partial false :course course})
          vocab (reduce + (map (comp count :lesson/vocabulary) (map :lesson entries)))
          exercises (reduce + (map (comp count :lesson/exercises) (map :lesson entries)))
          items (reduce + (mapcat (fn [{:keys [lesson]}]
                                    (map (comp count :exercise/items) (:lesson/exercises lesson)))
                                  entries))
          known-course? (contains? #{"learn-spanish" "aprende-ingles" "aprende-aleman"}
                                   (:course/slug course))]
      (testing (str "course " (:course/slug course))
        (is (empty? problems))
        (is (empty? missing))
        (is (empty? errors) (pr-str errors))
        (is (empty? warnings) (pr-str warnings))
        (is (= (:course/lesson-count course) (count entries)))
        (is (= (vec (range 1 (inc (:course/lesson-count course))))
               (mapv :number entries)))
        (when known-course?
          (is (= 138 vocab))
          (is (= 98 exercises))
          (is (= 1702 items)))
        (is (= (set (:course/empty-vocabulary-lessons course))
               (set (keep (fn [{:keys [lesson]}]
                            (when (empty? (:lesson/vocabulary lesson))
                              (:lesson/number lesson)))
                          entries)))
            (str "empty vocabulary lessons in " (:course/slug course)))))))

(defn- assert-mirrored
  [a-label a-lessons b-label b-lessons]
  (is (= (mapv :lesson/number a-lessons) (mapv :lesson/number b-lessons))
      (str a-label " vs " b-label " lesson numbers"))
  (doseq [[a b] (map vector a-lessons b-lessons)]
    (testing (str a-label " vs " b-label " lesson " (:lesson/number a))
      (is (= (count (:lesson/vocabulary a)) (count (:lesson/vocabulary b))))
      (is (= (mapv :exercise/id (:lesson/exercises a))
             (mapv :exercise/id (:lesson/exercises b))))
      (is (= (mapv (comp count :exercise/items) (:lesson/exercises a))
             (mapv (comp count :exercise/items) (:lesson/exercises b))))
      (doseq [[ea eb] (map vector (:lesson/exercises a) (:lesson/exercises b))]
        (is (= (mapv (fn [i] [(count (:item/prompt i)) (count (:item/answer i))])
                     (:exercise/items ea))
               (mapv (fn [i] [(count (:item/prompt i)) (count (:item/answer i))])
                     (:exercise/items eb)))
            (str (:exercise/id ea) " turn counts"))))))

(defn- course-lessons [dir]
  (->> (lesson-entries dir (course-for dir)) :entries (map :lesson)))

(deftest mirrored-course-parity-test
  (let [dirs (into {} (map (fn [dir] [(:course/slug (course-for dir)) dir]) (course-dirs)))
        learn-dir (get dirs "learn-spanish")
        english-dir (get dirs "aprende-ingles")
        german-dir (get dirs "aprende-aleman")]
    (is learn-dir)
    (is english-dir)
    (is german-dir)
    (when (and learn-dir english-dir)
      (assert-mirrored "learn-spanish" (course-lessons learn-dir)
                       "aprende-ingles" (course-lessons english-dir)))
    (when (and english-dir german-dir)
      (assert-mirrored "aprende-ingles" (course-lessons english-dir)
                       "aprende-aleman" (course-lessons german-dir)))))

(deftest direction-language-sanity-test
  (doseq [dir (course-dirs)]
    (let [course (course-for dir)
          {:keys [entries]} (lesson-entries dir course)
          complaints (for [{:keys [lesson]} entries
                           exercise (:lesson/exercises lesson)
                           :when (= :translate (:exercise/skill exercise))
                           item (:exercise/items exercise)
                           complaint (language-complaint course exercise item)]
                       complaint)]
      (testing (str "direction language sanity for " (:course/slug course))
        (is (empty? complaints) (str/join "\n" (take 20 complaints)))))))

(deftest alternatives-test
  (let [items (for [dir (course-dirs)
                    :let [course (course-for dir)
                          {:keys [entries]} (lesson-entries dir course)]
                    {:keys [lesson]} entries
                    exercise (:lesson/exercises lesson)
                    item (:exercise/items exercise)
                    :when (:item/alternatives item)]
                {:course (:course/slug course)
                 :exercise exercise
                 :item item})]
    (is (seq items) "the mirrored English course should provide contraction alternatives")
    (doseq [{:keys [exercise item]} items]
      (let [turns (count (:item/answer item))]
        (is (vector? (:item/alternatives item)))
        (doseq [alternative (:item/alternatives item)]
          (is (vector? alternative))
          (is (= turns (count alternative))
              (str (:exercise/id exercise) " item " (:item/n item))))))))

(deftest ui-pack-parity-test
  (let [en (ui-pack :en)
        es (ui-pack :es)]
    (testing "both packs define the same key tree"
      (is (= (key-set en) (key-set es))))
    (testing "Spanish chrome is actually Spanish"
      (is (= "Comprobar" (:check es)))
      (is (= "Mostrar respuesta" (:show-answer es)))
      (is (= "Anterior" (:previous es)))
      (is (= "Siguiente" (:next es)))
      (is (str/starts-with? (:correct es) "¡"))
      (is (= "Traducir" (:skill-translate es)))
      (is (= "Recordar" (:skill-recall es))))
    (testing "language names cover all course pairs"
      (is (= #{"en" "es" "de"} (set (keys (:language-names en)))))
      (is (= #{"en" "es" "de"} (set (keys (:language-names es)))))
      (is (= "German" (get-in en [:language-names "de"])))
      (is (= "alemán" (get-in es [:language-names "de"]))))))

(deftest spanish-course-keeps-brand-and-readme-legacy-test
  (testing "learn-spanish keeps its original legacy flags"
    (let [course (course-for (fs/path lesson-root "learn-spanish"))]
      (is (true? (:course/legacy-hash? course)))
      (is (true? (:course/legacy-progress? course)))))
  (testing "aprende-ingles does not claim legacy routes or progress"
    (let [course (course-for (fs/path lesson-root "aprende-ingles"))]
      (is (false? (:course/legacy-hash? course)))
      (is (false? (:course/legacy-progress? course))))))