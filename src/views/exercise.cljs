(ns views.exercise
  (:require [clojure.string :as str]
            [router :as router]
            [answer :as answer]
            [data :as data]
            [progress :as progress]
            [ui :as i18n]
            [views.shared :as shared]))

(defn- input-id [lesson-number exercise-id item-number turn]
  (str "ans-" lesson-number "-" exercise-id "-" item-number "-" turn))

(defn- read-entered [lesson-number exercise-id item]
  (let [turns (max 1 (count (:answer item)))]
    (mapv (fn [turn]
            (let [el (js/document.getElementById
                      (input-id lesson-number exercise-id (:n item) turn))]
              (if el (.-value el) "")))
          (range turns))))

(defn- apply-progress [state next-store slug]
  (-> state
      (assoc :progress-store next-store)
      (assoc :progress (progress/course-progress next-store slug))))

(defn- store-feedback! [!state item status entered]
  (swap! !state
         (fn [state]
           (-> state
               (assoc-in [:feedback (str (:n item))]
                         {:status status
                          :entered entered
                          :expected (:answer item)})))))

(defn- check-item! [!state lesson exercise item]
  (let [entered (read-entered (:number lesson) (:id exercise) item)
        result (answer/check-any entered (:answer item) (:alternatives item))]
    (if (= :empty result)
      (store-feedback! !state item :empty entered)
      (swap! !state
             (fn [state]
               (let [requested (if (= :correct-accent result)
                                 "correct"
                                 (name result))
                     next-store (progress/record! (:progress-store state)
                                                  (get-in state [:course :slug])
                                                  (:number lesson)
                                                  (:id exercise)
                                                  (:n item)
                                                  requested)]
                 (-> state
                     (apply-progress next-store (get-in state [:course :slug]))
                     (assoc-in [:feedback (str (:n item))]
                               {:status result
                                :entered entered
                                :expected (:answer item)}))))))))

(defn- reveal-item! [!state lesson exercise item]
  (let [entered (read-entered (:number lesson) (:id exercise) item)]
    (swap! !state
           (fn [state]
             (let [slug (get-in state [:course :slug])
                   next-store (progress/record! (:progress-store state)
                                                slug
                                                (:number lesson)
                                                (:id exercise)
                                                (:n item)
                                                "revealed")]
               (-> state
                   (apply-progress next-store slug)
                   (assoc-in [:feedback (str (:n item))]
                             {:status :revealed
                              :entered entered
                              :expected (:answer item)})))))))

(defn- override-item! [!state lesson exercise item]
  (let [entered (read-entered (:number lesson) (:id exercise) item)]
    (swap! !state
           (fn [state]
             (let [slug (get-in state [:course :slug])
                   next-store (progress/record! (:progress-store state)
                                                slug
                                                (:number lesson)
                                                (:id exercise)
                                                (:n item)
                                                "correct")]
               (-> state
                   (apply-progress next-store slug)
                   (assoc-in [:feedback (str (:n item))]
                             {:status :correct
                              :override? true
                              :entered entered
                              :expected (:answer item)})))))))

(defn- reset-exercise! [!state lesson exercise]
  (swap! !state
         (fn [state]
           (let [slug (get-in state [:course :slug])
                 next-store (progress/reset-exercise! (:progress-store state)
                                                      slug
                                                      (:number lesson)
                                                      (:id exercise))]
             (-> state
                 (apply-progress next-store slug)
                 (assoc :feedback {}))))))

(defn- feedback-view [ui feedback]
  (let [status (:status feedback)
        entered (:entered feedback)
        expected (:expected feedback)]
    [:div {:class (str "feedback feedback-" (name status))
           :aria-live "polite"}
     [:p.feedback-status
      (case status
        :correct (i18n/t ui :correct)
        :correct-accent (i18n/t ui :correct-accent)
        :wrong (i18n/t ui :wrong)
        :revealed (i18n/t ui :revealed)
        :empty (i18n/t ui :empty)
        "")]
     (when (and (seq entered) (not= status :empty))
       [:p.feedback-entered
        [:span.feedback-label (i18n/t ui :you)]
        (str/join " / " entered)])
     (when (or (= status :wrong) (= status :revealed) (= status :correct-accent))
       [:p.feedback-expected
        [:span.feedback-label (i18n/t ui :expected)]
        (str/join " / " expected)])
     (when (:override? feedback)
       [:p.feedback-hint (i18n/t ui :marked-correct)])]))

(defn- item-view [state !state lesson exercise item]
  (let [ui (get-in state [:course :ui])
        course (:course state)
        lesson-number (:number lesson)
        exercise-id (:id exercise)
        item-number (:n item)
        feedback (get-in (:feedback state) [(str item-number)])
        status (:status feedback)
        persisted (progress/item-entry (:progress state)
                                       lesson-number exercise-id item-number)
        display-status (or status
                           (case (:status persisted)
                             "correct" :correct
                             "revealed" :revealed
                             "wrong" :wrong
                             nil))
        turns (max 1 (count (:answer item)))
        base-code (get-in course [:languages :base :code])
        target-code (get-in course [:languages :target :code])
        direction (:direction exercise)
        source-lang (if (= :to-base direction) target-code base-code)
        answer-lang (if (= :to-base direction) base-code target-code)]
    [:div {:class (str "item" (when display-status
                                (str " item-" (name display-status))))
           :key (str item-number)}
     [:div.item-head
      [:span.item-number (str item-number ".")]
      (when display-status
        [shared/status-chip ui display-status])]
     [:div.item-prompt
      (map (fn [turn] [:p.prompt-turn {:lang source-lang} turn]) (:prompt item))]
     [:div.item-inputs
      (map (fn [turn]
             [:input {:id (input-id lesson-number exercise-id item-number turn)
                      :key (str turn)
                      :type "text"
                      :class "answer-input"
                      :default-value ""
                      :autocomplete "off"
                      :autocapitalize "off"
                      :spellcheck "false"
                      :lang answer-lang
                      :aria-label (i18n/t ui :answer-aria {:item item-number
                                                         :turn (inc turn)})
                      :on-keydown (fn [e]
                                    (when (= (.-key e) "Enter")
                                      (.preventDefault e)
                                      (check-item! !state lesson exercise item)))}])
           (range turns))]
     [:div.item-actions
      [:button {:type "button"
                :on-click (fn [_] (check-item! !state lesson exercise item))}
       (i18n/t ui :check)]
      [:button.secondary {:type "button"
                          :on-click (fn [_] (reveal-item! !state lesson exercise item))}
       (i18n/t ui :show-answer)]
      (when (= status :wrong)
        [:button.link {:type "button"
                       :on-click (fn [_] (override-item! !state lesson exercise item))}
         (i18n/t ui :i-was-right)])]
     (when feedback
       (feedback-view ui feedback))]))

(defn- not-found [ui]
  [:div.not-found
   [:h1 (i18n/t ui :exercise-not-found)]
   [:p [:a {:href (router/home-href)} (i18n/t ui :back-home)]]])

(defn view [state]
  (let [route (:route state)
        course (:course state)
        ui (:ui course)
        lesson (data/lesson-by-number (:lessons state) (:lesson route))
        exercise (data/exercise-by-id lesson (:exercise route))]
    (cond
      (nil? lesson) (not-found ui)
      (nil? exercise) (not-found ui)
      :else
      (let [!state (:state-atom state)
            stats (data/exercise-stats (:progress state) (:number lesson) exercise)
            previous (data/prev-exercise lesson (:id exercise))
            following (data/next-exercise lesson (:id exercise))]
        [:div.exercise {:key (str (:number lesson) "-" (:id exercise))}
         [:nav.breadcrumbs
          [:a {:href (router/lesson-href (:number lesson))}
           (str "← " (:label lesson))]]
         [:header.exercise-header
          [:p.step-label
           (i18n/t ui :step-title {:number (get-in lesson [:step :number])
                                 :title (get-in lesson [:step :title])})]
          [:h1 (i18n/t ui :exercise-heading {:id (:id exercise)})]
          [:p.exercise-meta
           (str (shared/skill-label ui (:skill exercise))
                " · "
                (shared/direction-label ui course (:direction exercise)))]
          (when (not-empty (:instructions exercise))
            [:p.instructions (:instructions exercise)])
          [:div.exercise-progress
           [:span (i18n/t ui :items-correct {:done (:done stats) :total (:total stats)})]
           [shared/progress-bar ui stats]]]
         (when (seq (:hints exercise))
           [:details.hints
            [:summary (i18n/t ui :hints)]
            [:ul (map (fn [hint] [:li {:key hint} hint]) (:hints exercise))]])
         (when (seq (:notes exercise))
           [:div.exercise-notes
            (shared/prose (:notes exercise))])
         [:div.items
          (map (fn [item] (item-view state !state lesson exercise item))
               (:items exercise))]
         [:footer.exercise-footer
          [:div.footer-actions
           [:button.secondary {:type "button"
                               :on-click (fn [_] (reset-exercise! !state lesson exercise))}
            (i18n/t ui :reset-exercise)]
           [:a.back-link {:href (router/lesson-href (:number lesson))}
            (i18n/t ui :back-to-lesson)]
           (when previous
             [:a {:href (router/exercise-href (:number lesson) (:id previous))}
              (str "← " (:id previous))])
           (when following
             [:a {:href (router/exercise-href (:number lesson) (:id following))}
              (str (:id following) " →")])]
          [:p.footer-progress
           (i18n/t ui :lesson-answers-correct {:done (:done stats) :total (:total stats)})]]]))))