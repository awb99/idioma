(ns data
  "Pure lookups and progress arithmetic over the lessons JSON.")

(defn lesson-by-number [lessons n]
  (when lessons
    (first (filter (fn [lesson] (= (:number lesson) n)) lessons))))

(defn exercise-by-id [lesson id]
  (when lesson
    (first (filter (fn [ex] (= (:id ex) id)) (:exercises lesson)))))

(defn exercise-index [lesson id]
  (when lesson
    (first (keep-indexed (fn [idx ex] (when (= (:id ex) id) idx))
                         (:exercises lesson)))))

(defn prev-exercise [lesson id]
  (let [idx (exercise-index lesson id)]
    (when (and idx (pos? idx))
      (nth (:exercises lesson) (dec idx)))))

(defn next-exercise [lesson id]
  (let [idx (exercise-index lesson id)]
    (when (and idx (< (inc idx) (count (:exercises lesson))))
      (nth (:exercises lesson) (inc idx)))))

(defn exercise-total [exercise]
  (count (:items exercise)))

(defn lesson-total [lesson]
  (reduce + (map exercise-total (:exercises lesson))))

(defn item-status [progress lesson-number exercise-id item-number]
  (get-in progress [(str lesson-number) exercise-id (str item-number) "status"]))

(defn item-correct? [progress lesson-number exercise-id item-number]
  (= "correct" (item-status progress lesson-number exercise-id item-number)))

(defn exercise-done [progress lesson-number exercise]
  (count (filter (fn [item]
                   (item-correct? progress lesson-number (:id exercise) (:n item)))
                 (:items exercise))))

(defn exercise-stats [progress lesson-number exercise]
  (let [total (exercise-total exercise)
        done (exercise-done progress lesson-number exercise)]
    {:total total
     :done done
     :pct (if (pos? total) (js/Math.round (* 100 (/ done total))) 0)}))

(defn lesson-done [progress lesson]
  (reduce + (map (fn [ex] (exercise-done progress (:number lesson) ex))
                 (:exercises lesson))))

(defn lesson-stats [progress lesson]
  (let [total (lesson-total lesson)
        done (lesson-done progress lesson)]
    {:total total
     :done done
     :pct (if (pos? total) (js/Math.round (* 100 (/ done total))) 0)}))

(defn overall-stats [progress lessons]
  (let [total (reduce + (map lesson-total lessons))
        done (reduce + (map (fn [lesson] (lesson-done progress lesson)) lessons))]
    {:total total
     :done done
     :pct (if (pos? total) (js/Math.round (* 100 (/ done total))) 0)}))

(defn first-incomplete-exercise [progress lesson]
  (first (filter (fn [ex]
                   (< (exercise-done progress (:number lesson) ex)
                      (exercise-total ex)))
                 (:exercises lesson))))

(defn first-exercise [lesson]
  (first (:exercises lesson)))