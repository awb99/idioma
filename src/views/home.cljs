(ns views.home
  (:require [router :as router]
            [data :as data]
            [views.shared :as shared]))

(defn- step-groups [lessons]
  (let [steps (reduce (fn [acc lesson]
                        (let [step (:step lesson)]
                          (if (some (fn [s] (= (:number s) (:number step))) acc)
                            acc
                            (conj acc step))))
                      []
                      lessons)]
    (map (fn [step]
           {:number (:number step)
            :title (:title step)
            :lessons (filter (fn [lesson]
                               (= (:number (:step lesson)) (:number step)))
                             lessons)})
         steps)))

(defn- lesson-card [state lesson]
  (let [stats (data/lesson-stats (:progress state) lesson)
        next-ex (or (data/first-incomplete-exercise (:progress state) lesson)
                    (data/first-exercise lesson))]
    [:article.lesson-card
     [:div.lesson-card-head
      [:span.lesson-label (:label lesson)]
      (when (:title lesson)
        [:h3.lesson-card-title (:title lesson)])
      [:p.lesson-spanish {:lang "es"} (:spanish-title lesson)]]
     [:div.lesson-card-progress
      [:span.lesson-card-count (str (:done stats) " of " (:total stats) " answered")]
      [shared/progress-bar stats]]
     [:div.lesson-card-links
      [:a {:href (router/lesson-href (:number lesson))} "Open lesson"]
      (when next-ex
        [:a.continue-link {:href (router/exercise-href (:number lesson) (:id next-ex))}
         "Continue"])]]))

(defn view [state]
  (let [lessons (:lessons state)
        overall (data/overall-stats (:progress state) lessons)]
    [:div.home
     [:header.app-header
      [:h1 "Synergy Spanish"]
      [:p.tagline "25 lessons from the Synergy Spanish book"]]
     [:section.overall-progress
      [:div.overall-head
       [:h2 "Your progress"]
       [:span.overall-count (str (:done overall) " / " (:total overall) " answers correct")]]
      [shared/progress-bar overall]]
     (map (fn [group]
            [:section.step
             [:h2.step-title (str "Step " (:number group) " · " (:title group))]
             [:div.lesson-grid
              (map (fn [lesson] (lesson-card state lesson)) (:lessons group))]])
          (step-groups lessons))
     [:footer.app-footer
      [:p "Progress is stored in this browser only."]]]))