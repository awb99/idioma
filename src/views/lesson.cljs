(ns views.lesson
  (:require [router :as router]
            [data :as data]
            [views.shared :as shared]))

(defn- section-block [section]
  [:section.lesson-section
   [:h2.section-type (shared/section-label (:type section))
    (when (:title section)
      [:span.section-title (str " — " (:title section))])]
   (shared/prose (:body section))])

(defn- vocabulary-table [items]
  [:div.table-wrap
   [:table.vocab-table
    [:thead
     [:tr
      [:th {:scope "col"} "Spanish"]
      [:th {:scope "col"} "English"]
      [:th {:scope "col"} "Pronunciation"]
      [:th {:scope "col"} "Mnemonic"]]]
    [:tbody
     (map (fn [item]
            [:tr {:key (str (:id item))}
             [:td.vocab-spanish {:lang "es"} (or (not-empty (:spanish item)) "—")]
             [:td (or (not-empty (:english item)) "—")]
             [:td.vocab-pronunciation (or (:pronunciation item) "")]
             [:td.vocab-mnemonic (:mnemonic item)]])
          items)]]])

(defn- vocabulary-section [kind items]
  [:section.vocab-group
   [:h3 (shared/vocab-kind-label kind)]
   (vocabulary-table items)])

(defn- exercise-row [progress lesson exercise]
  (let [stats (data/exercise-stats progress (:number lesson) exercise)]
    [:li.exercise-row {:key (:id exercise)}
     [:a.exercise-link {:href (router/exercise-href (:number lesson) (:id exercise))}
      [:span.exercise-id (:id exercise)]
      [:span.exercise-meta
       (str (shared/skill-label (:skill exercise))
            " · "
            (shared/direction-label (:direction exercise))
            " · "
            (:total stats) " items")]]
     [:span.exercise-progress (str (:done stats) "/" (:total stats))]
     [shared/completion-chip (:done stats) (:total stats)]]))

(defn view [state]
  (let [route (:route state)
        lesson (data/lesson-by-number (:lessons state) (:lesson route))]
    (if-not lesson
      [:div.not-found
       [:h1 "Lesson not found"]
       [:p [:a {:href (router/home-href)} "Back to all lessons"]]]
      (let [progress (:progress state)
            stats (data/lesson-stats progress lesson)
            vocabulary (:vocabulary lesson)
            grouped (group-by (fn [item] (:kind item)) vocabulary)]
        [:div.lesson {:key (str "lesson-" (:number lesson))}
         [:nav.breadcrumbs
          [:a {:href (router/home-href)} "← All lessons"]]
         [:header.lesson-header
          [:p.step-label (str "Step " (:number (:step lesson)) " · " (:title (:step lesson)))]
          [:h1 (:label lesson)]
          (when (:title lesson)
            [:p.lesson-title (:title lesson)])
          [:p.lesson-spanish-title {:lang "es"} (:spanish-title lesson)]
          [:div.lesson-progress
           [:span (str (:done stats) " of " (:total stats) " answers correct")]
           [shared/progress-bar stats]]]
         (when (seq (:intro lesson))
           [:section.lesson-intro
            (shared/prose (:intro lesson))])
         (map section-block (:sections lesson))
         (when (seq vocabulary)
           (map (fn [kind]
                  (let [items (get grouped kind)]
                    (when (seq items)
                      (vocabulary-section kind items))))
                shared/vocab-kind-order))
         [:section.exercise-block
          [:h2 "Exercises"]
          [:ul.exercise-list
           (map (fn [exercise] (exercise-row progress lesson exercise))
                (:exercises lesson))]]
         (when (seq (:closing lesson))
           [:section.lesson-closing
            [:h2 "Closing"]
            (shared/prose (:closing lesson))])]))))