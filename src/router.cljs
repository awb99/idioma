(ns router
  "Hash routes: #/, #/lesson/N, #/lesson/N/exercise/ID.")

(defn- segments [hash]
  (let [h (or hash "")
        h (if (.startsWith h "#") (subs h 1) h)]
    (vec (remove empty? (.split h "/")))))

(defn parse [hash]
  (let [parts (segments hash)]
    (cond
      (empty? parts)
      {:name :home}

      (and (= 2 (count parts))
           (= "lesson" (nth parts 0)))
      (let [n (js/parseInt (nth parts 1) 10)]
        (if (js/isNaN n)
          {:name :not-found}
          {:name :lesson :lesson n}))

      (and (= 4 (count parts))
           (= "lesson" (nth parts 0))
           (= "exercise" (nth parts 2)))
      (let [n (js/parseInt (nth parts 1) 10)
            id (nth parts 3)]
        (if (or (js/isNaN n) (empty? id))
          {:name :not-found}
          {:name :exercise :lesson n :exercise id}))

      :else
      {:name :not-found})))

(defn home-href []
  "#/")

(defn lesson-href [lesson-number]
  (str "#/lesson/" lesson-number))

(defn exercise-href [lesson-number exercise-id]
  (str "#/lesson/" lesson-number "/exercise/" exercise-id))