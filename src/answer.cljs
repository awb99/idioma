(ns answer
  "Answer normalization and checking. Accent-only differences count as correct
  with a hint; punctuation, case, and spacing differences are ignored."
  (:require [clojure.string :as str]))

(def punctuation #"[\u00bf\u00a1!?.,;:]+")
(def combining-marks #"[\u0300-\u036f]")

(defn normalize [s]
  (-> (or s "")
      (str/lower-case)
      (str/trim)
      (str/replace punctuation " ")
      (str/replace #"\s+" " ")
      (str/trim)))

(defn fold-accents [s]
  (-> (or s "")
      (.normalize "NFD")
      (str/replace combining-marks "")))

(defn- check-one [entered expected]
  (let [e (normalize entered)
        x (normalize expected)]
    (cond
      (str/blank? e) :empty
      (= e x) :correct
      (= (fold-accents e) (fold-accents x)) :correct-accent
      :else :wrong)))

(defn- all-blank? [values]
  (every? (fn [v] (str/blank? (normalize v))) values))

(defn check
  "Compare entered turns against expected turns.
  Returns :empty, :correct, :correct-accent, or :wrong."
  [entered expected]
  (let [entered (vec entered)
        expected (vec expected)]
    (cond
      (or (empty? entered) (all-blank? entered)) :empty
      (not= (count entered) (count expected))
      (check-one (str/join " " entered) (str/join " " expected))
      :else
      (let [results (mapv check-one entered expected)]
        (cond
          (every? (fn [r] (= :correct r)) results) :correct
          (every? (fn [r] (or (= :correct r) (= :correct-accent r))) results) :correct-accent
          :else :wrong)))))