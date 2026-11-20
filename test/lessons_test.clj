(ns lessons-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [lessons :as lessons]))

(def fixture-dir "test/fixtures/lessons")

(def corpus-dir "lesson/spanish")

(defn- fixture-lesson []
  (edn/read-string (slurp (str (fs/path fixture-dir "1.edn")))))

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

;; ---------------------------------------------------------------------------

(deftest normalize-test
  (testing "namespaced keys become unqualified strings"
    (is (= {"number" 1}
           (lessons/normalize {:lesson/number 1}))))
  (testing "keyword values become unqualified strings"
    (is (= {"kind" "synergy-verb"
            "direction" "to-english"
            "dialog?" false}
           (lessons/normalize {:vocab/kind :synergy-verb
                               :exercise/direction :to-english
                               :exercise/dialog? false}))))
  (testing "nested vectors and maps recurse"
    (is (= {"items" [{"n" 1 "prompt" ["a" "b"]}]}
           (lessons/normalize {:exercise/items [{:item/n 1
                                                 :item/prompt ["a" "b"]}]}))))
  (testing "nil, numbers, booleans and strings are preserved"
    (is (= {"x" nil "y" 3 "z" true "s" "hola"}
           (lessons/normalize {:a/x nil :a/y 3 :a/z true :a/s "hola"})))))

(deftest fixture-transform-test
  (let [{:keys [entries problems missing]} (lessons/collect fixture-dir true)
        {:keys [errors]} (validate-quiet entries {:allow-partial true})
        parsed (json/parse-string (lessons/render-json entries))
        out-lessons (get parsed "lessons")]
    (is (empty? problems))
    (is (empty? missing))
    (is (empty? errors) (pr-str errors))
    (testing "envelope"
      (is (= 1 (get parsed "version")))
      (is (= 2 (get parsed "count"))))
    (testing "lessons are sorted by number"
      (is (= [1 2] (mapv #(get % "number") out-lessons))))
    (testing "output has unqualified string keys"
      (is (contains? (first out-lessons) "spanish-title"))
      (is (not (contains? (first out-lessons) "lesson/number"))))
    (testing "keyword values became strings"
      (is (= "synergy-verb"
             (get-in out-lessons [0 "vocabulary" 0 "kind"])))
      (is (= "to-english"
             (get-in out-lessons [0 "exercises" 0 "direction"])))
      (is (= false
             (get-in out-lessons [0 "exercises" 0 "dialog?"]))))
    (testing "nested arrays/objects"
      (is (vector? (get-in out-lessons [0 "vocabulary"])))
      (is (vector? (get-in out-lessons [0 "exercises" 0 "items"])))
      (is (map? (get-in out-lessons [0 "step"]))))))

(deftest allow-partial-test
  (let [{:keys [entries missing]} (lessons/collect fixture-dir true)
        {:keys [errors]} (validate-quiet entries {:allow-partial true})]
    (is (empty? missing))
    (is (= [1 2] (mapv :number entries)))
    (is (empty? errors) (pr-str errors))))

(deftest missing-files-test
  (let [dir (fs/create-temp-dir)
        _ (write-lesson! dir 1 (fixture-lesson))
        {:keys [missing entries]} (lessons/collect dir false)
        {:keys [errors]} (validate-quiet entries {:allow-partial false})]
    (is (= [1] (mapv :number entries)))
    (is (= (vec (range 2 26)) (vec missing)))
    (is (any-error-contains? errors "missing lessons"))))

(deftest duplicate-lesson-number-test
  (let [dir (fs/create-temp-dir)
        lesson (fixture-lesson)
        _ (write-lesson! dir 1 lesson)
        _ (write-lesson! dir 2 lesson)
        {:keys [entries]} (lessons/collect dir true)
        {:keys [errors]} (validate-quiet entries {:allow-partial true})]
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
  (testing "pages must be two integers"
    (is (any-error-contains? (errors-for (assoc (fixture-lesson) :lesson/pages [10]))
                             ":lesson/pages")))
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
      (is (any-error-contains? (errors-for lesson) "unknown :section/type")))))

(deftest vocabulary-contiguity-test
  (testing "non-contiguous ids fail the corpus check"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/vocabulary 0 :vocab/id] 99)
          {:keys [errors]} (validate-quiet [{:number 1 :lesson lesson}]
                                           {:allow-partial true})]
      (is (any-error-contains? errors "vocabulary ids are not globally contiguous"))))
  (testing "duplicate vocabulary ids are named"
    (let [lesson (assoc-in (fixture-lesson)
                           [:lesson/vocabulary 1 :vocab/id]
                           (get-in (fixture-lesson) [:lesson/vocabulary 0 :vocab/id]))
          {:keys [errors]} (validate-quiet [{:number 1 :lesson lesson}]
                                           {:allow-partial true})]
      (is (any-error-contains? errors "duplicates")))))

(deftest empty-vocabulary-test
  (testing "lessons 21 and 24 may have no vocabulary"
    (doseq [n [21 24]]
      (let [lesson (-> (fixture-lesson)
                       (assoc :lesson/number n)
                       (assoc-in [:lesson/label] (str "Lesson " n))
                       (assoc :lesson/vocabulary []))
            {:keys [errors warnings]} (validate-quiet [{:number n :lesson lesson}]
                                                      {:allow-partial true})]
        (is (empty? errors) (pr-str errors))
        (is (not-any? #(str/includes? % "vocabulary is empty") warnings)))))
  (testing "other lessons with no vocabulary warn but do not fail"
    (let [lesson (-> (fixture-lesson) (assoc :lesson/vocabulary []))
          {:keys [errors warnings]} (validate-quiet [{:number 1 :lesson lesson}]
                                                    {:allow-partial true})]
      (is (empty? errors) (pr-str errors))
      (is (any-error-contains? warnings "vocabulary is empty")))))

(deftest dialog-length-warning-test
  (testing "differing dialog turn counts warn instead of failing"
    (let [lesson (assoc-in (fixture-lesson) [:lesson/exercises 0 :exercise/dialog?] true)
          lesson (assoc-in lesson [:lesson/exercises 0 :exercise/items 0 :item/prompt]
                           ["one" "two"])
          {:keys [errors warnings]} (validate-quiet [{:number 1 :lesson lesson}]
                                                    {:allow-partial true})]
      (is (empty? errors) (pr-str errors))
      (is (any-error-contains? warnings "dialog lengths differ")))))

(deftest title-may-be-nil-test
  (is (empty? (:errors (lessons/validate-lesson 1 (assoc (fixture-lesson) :lesson/title nil))))))

(deftest real-corpus-test
  (when (and (fs/exists? (fs/path corpus-dir "1.edn"))
             (fs/exists? (fs/path corpus-dir "25.edn")))
    (let [{:keys [entries problems missing]} (lessons/collect corpus-dir false)
          {:keys [errors]} (validate-quiet entries {:allow-partial false})]
      (is (empty? problems))
      (is (empty? missing))
      (is (empty? errors) (pr-str errors))
      (is (= 25 (count entries)))
      (is (= (vec (range 1 26)) (mapv :number entries)))
      (is (= lessons/expected-vocabulary-count
             (reduce + (map (comp count :lesson/vocabulary) (map :lesson entries))))))))