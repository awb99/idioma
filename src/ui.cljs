(ns ui
  "UI language pack helpers. Every user-visible chrome string lives in the
  course's UI pack; views never hardcode language-pair text."
  (:require [clojure.string :as str]))

(defn template
  "Replace `{{name}}` placeholders in s with values from replacements."
  [s replacements]
  (reduce (fn [acc [k v]]
            (str/replace acc (str "{{" (name k) "}}") (str v)))
          (or s "")
          replacements))

(defn t
  "Look up a chrome string. With replacements, interpolate placeholders."
  ([ui k] (or (get ui (name k)) (name k)))
  ([ui k replacements] (template (t ui k) replacements)))

(defn language-name [course code]
  (or (get-in course [:ui :language-names code]) code))

(defn direction-label [ui course direction]
  (let [code (if (= direction :to-base)
               (get-in course [:languages :base :code])
               (get-in course [:languages :target :code]))
        language (language-name course code)]
    (t ui :direction-template {:language language})))

(defn section-label [ui type]
  (or (get-in ui [:section-labels type]) (name type)))

(defn vocab-kind-label [ui kind]
  (or (get-in ui [:vocab-kind-labels kind]) (name kind)))

(def vocab-kind-order
  [:synergy-verb :brick-verb :mortar :sentence-starter :other])

(defn skill-label [ui skill]
  (case skill
    :recall (t ui :skill-recall)
    :translate (t ui :skill-translate)
    (name skill)))

(defn status-label [ui status]
  (case status
    :correct (t ui :status-correct)
    :correct-accent (t ui :status-correct-accent)
    :wrong (t ui :status-wrong)
    :revealed (t ui :status-revealed)
    :empty (t ui :status-empty)
    (t ui :status-unseen)))

(defn completion-label [ui done total]
  (cond
    (>= done total) (t ui :complete)
    (pos? done) (t ui :in-progress)
    :else (t ui :not-started)))