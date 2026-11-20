(ns views.shared)

(def section-labels
  {:speaking-hints "Speaking hints"
   :communication-multiplied "Communication multiplied"
   :accuracy-pointer "Accuracy pointer"
   :sentence-builder "Sentence builder"
   :sentence-starter "Sentence starter"
   :multipurpose-word "Multipurpose word"
   :step-intro "Step intro"
   :closing "Closing"})

(def vocab-kind-labels
  {:synergy-verb "Synergy verbs"
   :brick-verb "Brick verbs"
   :mortar "Mortar"
   :sentence-starter "Sentence starters"
   :other "Other words"})

(def vocab-kind-order
  [:synergy-verb :brick-verb :mortar :sentence-starter :other])

(defn section-label [type]
  (get section-labels type type))

(defn vocab-kind-label [kind]
  (get vocab-kind-labels kind kind))

(defn skill-label [skill]
  (case skill
    :recall "Recall"
    :translate "Translate"
    (str skill)))

(defn direction-label [direction]
  (case direction
    :to-english "into English"
    :to-spanish "into Spanish"
    (str direction)))

(defn status-label [status]
  (case status
    :correct "Correct"
    :correct-accent "Correct (watch accents)"
    :wrong "Not quite"
    :revealed "Answer shown"
    :empty "No answer"
    "Unseen"))

(defn status-chip [status]
  (let [status-name (if status (name status) "unseen")]
    [:span {:class (str "chip chip-" status-name)}
     (status-label status)]))

(defn completion-chip [done total]
  (let [status (cond
                 (zero? total) "unseen"
                 (>= done total) "correct"
                 (pos? done) "partial"
                 :else "unseen")]
    [:span {:class (str "chip chip-" status)}
     (cond
       (>= done total) "Complete"
       (pos? done) "In progress"
       :else "Not started")]))

(defn progress-bar [stats]
  [:div.progress-bar
   {:role "progressbar"
    :aria-valuenow (:pct stats)
    :aria-valuemin 0
    :aria-valuemax 100
    :aria-label (str (:pct stats) " percent complete")}
   [:div.progress-fill {:style {:width (str (:pct stats) "%")}}]])

(defn prose [lines]
  (map (fn [line] [:p line]) lines))