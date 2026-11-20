(ns views.lesson
  (:require [router :as router]
            [data :as data]
            [ui :as i18n]
            [views.shared :as shared]))

(defn- section-block [ui section]
  [:section.lesson-section
   [:h2.section-type (shared/section-label ui (:type section))
    (when (:title section)
      [:span.section-title (str " — " (:title section))])]
   (shared/prose (:body section))])

(defn- vocabulary-table [course items]
  (let [ui (:ui course)
        target-code (get-in course [:languages :target :code])
        base-code (get-in course [:languages :base :code])
        target-name (i18n/language-name course target-code)
        base-name (i18n/language-name course base-code)
        show-ipa? (some #(not-empty (:ipa %)) items)]
    [:div.table-wrap
     [:table.vocab-table
      [:thead
       [:tr
        [:th {:scope "col"} target-name]
        [:th {:scope "col"} base-name]
        [:th {:scope "col"} (i18n/t ui :vocab-pronunciation)]
        (when show-ipa?
          [:th {:scope "col"} (i18n/t ui :vocab-ipa)])
        [:th {:scope "col"} (i18n/t ui :vocab-mnemonic)]]]
      [:tbody
       (map (fn [item]
              [:tr {:key (str (:id item))}
               [:td.vocab-target {:lang target-code} (or (not-empty (:target item)) "—")]
               [:td {:lang base-code} (or (not-empty (:base item)) "—")]
               [:td.vocab-pronunciation (or (:pronunciation item) "")]
               (when show-ipa?
                 [:td.vocab-ipa (or (:ipa item) "—")])
               [:td.vocab-mnemonic (:mnemonic item)]])
            items)]]]))

(defn- vocabulary-section [course kind items]
  [:section.vocab-group
   [:h3 (shared/vocab-kind-label (:ui course) kind)]
   (vocabulary-table course items)])

(defn- exercise-row [state lesson exercise]
  (let [ui (get-in state [:course :ui])
        progress (:progress state)
        stats (data/exercise-stats progress (:number lesson) exercise)]
    [:li.exercise-row {:key (:id exercise)}
     [:a.exercise-link {:href (router/exercise-href (:number lesson) (:id exercise))}
      [:span.exercise-id (:id exercise)]
      [:span.exercise-meta
       (str (shared/skill-label ui (:skill exercise))
            " · "
            (shared/direction-label ui (:course state) (:direction exercise))
            " · "
            (i18n/t ui :item-count {:count (:total stats)}))]]
     [:span.exercise-progress (str (:done stats) "/" (:total stats))]
     [shared/completion-chip ui (:done stats) (:total stats)]]))

(defn- not-found [ui]
  [:div.not-found
   [:h1 (i18n/t ui :lesson-not-found)]
   [:p [:a {:href (router/home-href)} (i18n/t ui :back-home)]]])

(defn view [state]
  (let [route (:route state)
        course (:course state)
        ui (:ui course)
        lesson (data/lesson-by-number (:lessons state) (:lesson route))]
    (if-not lesson
      (not-found ui)
      (let [progress (:progress state)
            stats (data/lesson-stats progress lesson)
            vocabulary (:vocabulary lesson)
            grouped (group-by (fn [item] (:kind item)) vocabulary)
            target-code (get-in course [:languages :target :code])]
        [:div.lesson {:key (str "lesson-" (:number lesson))}
         [:nav.breadcrumbs
          [:a {:href (router/home-href)} (i18n/t ui :all-lessons)]]
         [:header.lesson-header
          [:p.step-label
           (i18n/t ui :step-title {:number (get-in lesson [:step :number])
                                 :title (get-in lesson [:step :title])})]
          [:h1 (:label lesson)]
          (when (:title lesson)
            [:p.lesson-title (:title lesson)])
          [:p.lesson-target-title {:lang target-code} (:target-title lesson)]
          [:div.lesson-progress
           [:span (i18n/t ui :lesson-answers-correct {:done (:done stats)
                                                    :total (:total stats)})]
           [shared/progress-bar ui stats]]]
         (when (seq (:intro lesson))
           [:section.lesson-intro
            (shared/prose (:intro lesson))])
         (map (fn [section] (section-block ui section)) (:sections lesson))
         (when (seq vocabulary)
           (map (fn [kind]
                  (let [items (get grouped kind)]
                    (when (seq items)
                      (vocabulary-section course kind items))))
                shared/vocab-kind-order))
         [:section.exercise-block
          [:h2 (i18n/t ui :exercises)]
          [:ul.exercise-list
           (map (fn [exercise] (exercise-row state lesson exercise))
                (:exercises lesson))]]
         (when (seq (:closing lesson))
           [:section.lesson-closing
            [:h2 (i18n/t ui :closing)]
            (shared/prose (:closing lesson))])]))))