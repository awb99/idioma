(ns progress
  "localStorage-backed progress store.

  Shape:
    {\"3\" {\"3A\" {\"1\" {\"status\" \"correct\", \"attempts\" 2, \"updatedAt\" \"...\"}}}}"
  (:require [data :as data]))

(def storage-key "idioma/progress/v1")

(defonce !memory (atom {}))

(defn- usable-object? [x]
  (and x
       (= "object" (js/typeof x))
       (not (js/Array.isArray x))))

(defn load []
  (try
    (let [raw (.getItem js/localStorage storage-key)
          parsed (when raw (js/JSON.parse raw))]
      (if (usable-object? parsed)
        (do (reset! !memory parsed) parsed)
        (do (reset! !memory {}) {})))
    (catch :default _ @!memory)))

(defn save! [p]
  (reset! !memory p)
  (try
    (.setItem js/localStorage storage-key (js/JSON.stringify p))
    (catch :default _ nil))
  p)

(defn lesson-progress [p lesson-number]
  (get p (str lesson-number) {}))

(defn exercise-progress [p lesson-number exercise-id]
  (get (lesson-progress p lesson-number) exercise-id {}))

(defn item-entry [p lesson-number exercise-id item-number]
  (get (exercise-progress p lesson-number exercise-id) (str item-number)))

(defn record!
  "Record an attempt. requested is \"correct\", \"wrong\", or \"revealed\".
  Correct always wins; revealed only applies when the item is not correct."
  [p lesson-number exercise-id item-number requested]
  (let [prev (item-entry p lesson-number exercise-id item-number)
        prev-status (:status prev)
        next-status (cond
                      (= "correct" prev-status) "correct"
                      (or (= "correct" requested) (= "correct-accent" requested)) "correct"
                      :else requested)
        entry {:status next-status
               :attempts (inc (or (:attempts prev) 0))
               :updatedAt (.toISOString (js/Date.))}
        next (assoc-in p [(str lesson-number) exercise-id (str item-number)] entry)]
    (save! next)))

(defn reset-exercise! [p lesson-number exercise-id]
  (let [lesson (lesson-progress p lesson-number)
        next (assoc p (str lesson-number) (dissoc lesson exercise-id))]
    (save! next)))

(defn reset-lesson! [p lesson-number]
  (save! (dissoc p (str lesson-number))))

(defn lesson-stats [p lesson]
  (data/lesson-stats p lesson))

(defn overall-stats [p lessons]
  (data/overall-stats p lessons))