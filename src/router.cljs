(ns router
  "Course path + hash routes.

  Path: <base><course-slug>/ ; empty slug is the chooser.
  Hash: #/, #/lesson/N, #/lesson/N/exercise/ID."
  (:require [clojure.string :as str]))

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

(defn normalized-base [base]
  (let [b (or base "/")]
    (if (str/ends-with? b "/") b (str b "/"))))

(defn- relative-path [pathname base]
  (let [b (normalized-base base)
        b-no-slash (subs b 0 (dec (count b)))
        p (if (or (= pathname b-no-slash) (str/starts-with? pathname b))
            (subs pathname (if (= pathname b-no-slash)
                             (count b-no-slash)
                             (count b)))
            pathname)]
    (str/replace p #"^/+" "")))

(defn path-segments [pathname base]
  (vec (remove empty? (.split (relative-path pathname base) "/"))))

(defn course-slug [pathname base]
  (first (path-segments pathname base)))

(defn course-href [base slug]
  (str (normalized-base base) slug "/"))

(defn chooser-href [base]
  (normalized-base base))

(defn home-href []
  "#/")

(defn lesson-href [lesson-number]
  (str "#/lesson/" lesson-number))

(defn exercise-href [lesson-number exercise-id]
  (str "#/lesson/" lesson-number "/exercise/" exercise-id))