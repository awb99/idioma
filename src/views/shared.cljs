(ns views.shared
  (:require [ui :as i18n]))

(def vocab-kind-order i18n/vocab-kind-order)

(defn section-label [ui type]
  (i18n/section-label ui type))

(defn vocab-kind-label [ui kind]
  (i18n/vocab-kind-label ui kind))

(defn skill-label [ui skill]
  (i18n/skill-label ui skill))

(defn direction-label [ui course direction]
  (i18n/direction-label ui course direction))

(defn status-label [ui status]
  (i18n/status-label ui status))

(defn status-chip [ui status]
  (let [status-name (if status (name status) "unseen")]
    [:span {:class (str "chip chip-" status-name)}
     (status-label ui status)]))

(defn completion-chip [ui done total]
  (let [status (cond
                 (zero? total) "unseen"
                 (>= done total) "correct"
                 (pos? done) "partial"
                 :else "unseen")]
    [:span {:class (str "chip chip-" status)}
     (i18n/completion-label ui done total)]))

(defn progress-bar [ui stats]
  [:div.progress-bar
   {:role "progressbar"
    :aria-valuenow (:pct stats)
    :aria-valuemin 0
    :aria-valuemax 100
    :aria-label (i18n/t ui :percent-complete {:percent (:pct stats)})}
   [:div.progress-fill {:style {:width (str (:pct stats) "%")}}]])

(defn prose [lines]
  (map (fn [line] [:p line]) lines))