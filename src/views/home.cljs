(ns views.home
  (:require [router :as router]
            [data :as data]
            [ui :as i18n]
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
  (let [course (:course state)
        ui (:ui course)
        progress (:progress state)
        stats (data/lesson-stats progress lesson)
        next-ex (or (data/first-incomplete-exercise progress lesson)
                    (data/first-exercise lesson))]
    [:article.lesson-card
     [:div.lesson-card-head
      [:span.lesson-label (:label lesson)]
      (when (:title lesson)
        [:h3.lesson-card-title (:title lesson)])
      [:p.lesson-target {:lang (get-in course [:languages :target :code])}
       (:target-title lesson)]]
     [:div.lesson-card-progress
      [:span.lesson-card-count
       (i18n/t ui :answered-count {:done (:done stats) :total (:total stats)})]
      [shared/progress-bar ui stats]]
     [:div.lesson-card-links
      [:a {:href (router/lesson-href (:number lesson))} (i18n/t ui :open-lesson)]
      (when next-ex
        [:a.continue-link {:href (router/exercise-href (:number lesson) (:id next-ex))}
         (i18n/t ui :continue)])]]))

(defn view [state]
  (let [course (:course state)
        ui (:ui course)
        lessons (:lessons state)
        overall (data/overall-stats (:progress state) lessons)]
    [:div.home
     [:header.app-header
      [:h1 (:title course)]
      [:p.tagline (:tagline course)]]
     [:section.overall-progress
      [:div.overall-head
       [:h2 (i18n/t ui :your-progress)]
       [:span.overall-count
        (i18n/t ui :answers-correct {:done (:done overall) :total (:total overall)})]]
      [shared/progress-bar ui overall]]
     (map (fn [group]
            [:section.step
             [:h2.step-title
              (i18n/t ui :step-title {:number (:number group) :title (:title group)})]
             [:div.lesson-grid
              (map (fn [lesson] (lesson-card state lesson)) (:lessons group))]])
          (step-groups lessons))
     [:footer.app-footer
      [:p (i18n/t ui :progress-browser-only)]]]))