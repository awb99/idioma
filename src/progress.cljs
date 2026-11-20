(ns progress
  "localStorage-backed progress store, version 2.

  Shape:
    {\"version\" 2
     \"courses\" {\"learn-spanish\"
                 {\"3\" {\"3A\" {\"1\" {\"status\" \"correct\", \"attempts\" 2,
                                     \"updatedAt\" \"...\"}}}}}}

  Version 1 lived at `idioma/progress/v1` as the inner lesson map. It is
  imported into `courses.learn-spanish` once and kept for rollback."
  (:require [data :as data]))

(def storage-key "idioma/progress/v2")
(def legacy-key "idioma/progress/v1")
(def legacy-slug "learn-spanish")

(defonce !memory (atom {"version" 2 "courses" {}}))

(defn- usable-object? [x]
  (and x
       (= "object" (js/typeof x))
       (not (js/Array.isArray x))))

(defn- empty-store []
  {"version" 2 "courses" {}})

(defn- read-json [key]
  (try
    (let [raw (.getItem js/localStorage key)]
      (when raw (js/JSON.parse raw)))
    (catch :default _ nil)))

(defn- write! [store]
  (reset! !memory store)
  (try
    (.setItem js/localStorage storage-key (js/JSON.stringify store))
    (catch :default _ nil))
  store)

(defn- migrate-legacy [store]
  (if (contains? (get store "courses") legacy-slug)
    store
    (let [legacy (read-json legacy-key)]
      (if (usable-object? legacy)
        (write! (assoc-in store ["courses" legacy-slug] legacy))
        store))))

(defn load []
  (try
    (let [parsed (read-json storage-key)
          store (if (and (usable-object? parsed)
                         (= 2 (get parsed "version")))
                  (update parsed "courses"
                          (fn [courses] (if (usable-object? courses) courses {})))
                  (empty-store))]
      (migrate-legacy store))
    (catch :default _ @!memory)))

(defn save! [store]
  (write! store))

(defn course-progress [store slug]
  (get (get store "courses") slug {}))

(defn lesson-progress [p lesson-number]
  (get p (str lesson-number) {}))

(defn exercise-progress [p lesson-number exercise-id]
  (get (lesson-progress p lesson-number) exercise-id {}))

(defn item-entry [p lesson-number exercise-id item-number]
  (get (exercise-progress p lesson-number exercise-id) (str item-number)))

(defn- record-in [p lesson-number exercise-id item-number requested]
  (let [prev (item-entry p lesson-number exercise-id item-number)
        prev-status (:status prev)
        next-status (cond
                      (= "correct" prev-status) "correct"
                      (or (= "correct" requested) (= "correct-accent" requested)) "correct"
                      :else requested)
        entry {:status next-status
               :attempts (inc (or (:attempts prev) 0))
               :updatedAt (.toISOString (js/Date.))}]
    (assoc-in p [(str lesson-number) exercise-id (str item-number)] entry)))

(defn record!
  "Record an attempt. requested is \"correct\", \"wrong\", or \"revealed\".
  Correct always wins; revealed only applies when the item is not correct."
  [store slug lesson-number exercise-id item-number requested]
  (let [p (course-progress store slug)
        next-p (record-in p lesson-number exercise-id item-number requested)]
    (write! (assoc-in store ["courses" slug] next-p))))

(defn reset-exercise! [store slug lesson-number exercise-id]
  (let [p (course-progress store slug)
        lesson (lesson-progress p lesson-number)
        next-p (assoc p (str lesson-number) (dissoc lesson exercise-id))]
    (write! (assoc-in store ["courses" slug] next-p))))

(defn reset-lesson! [store slug lesson-number]
  (let [p (course-progress store slug)
        next-p (dissoc p (str lesson-number))]
    (write! (assoc-in store ["courses" slug] next-p))))

(defn lesson-stats [p lesson]
  (data/lesson-stats p lesson))

(defn overall-stats [p lessons]
  (data/overall-stats p lessons))