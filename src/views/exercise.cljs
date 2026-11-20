(ns views.exercise
  (:require [clojure.string :as str]
            [router :as router]
            [answer :as answer]
            [data :as data]
            [progress :as progress]
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

(defn- store-feedback! [!state lesson-number exercise-id item status entered]
  (swap! !state
         (fn [state]
           (-> state
               (assoc-in [:feedback (str (:n item))]
                         {:status status
                          :entered entered
                          :expected (:answer item)})))))

(defn- check-item! [!state lesson exercise item]
  (let [entered (read-entered (:number lesson) (:id exercise) item)
        result (answer/check entered (:answer item))]
    (if (= :empty result)
      (store-feedback! !state (:number lesson) (:id exercise) item :empty entered)
      (swap! !state
             (fn [state]
               (let [requested (if (= :correct-accent result)
                                 "correct"
                                 (name result))
                     next-progress (progress/record! (:progress state)
                                                     (:number lesson)
                                                     (:id exercise)
                                                     (:n item)
                                                     requested)]
                 (-> state
                     (assoc :progress next-progress)
                     (assoc-in [:feedback (str (:n item))]
                               {:status result
                                :entered entered
                                :expected (:answer item)}))))))))

(defn- reveal-item! [!state lesson exercise item]
  (let [entered (read-entered (:number lesson) (:id exercise) item)]
    (swap! !state
           (fn [state]
             (let [next-progress (progress/record! (:progress state)
                                                   (:number lesson)
                                                   (:id exercise)
                                                   (:n item)
                                                   "revealed")]
               (-> state
                   (assoc :progress next-progress)
                   (assoc-in [:feedback (str (:n item))]
                             {:status :revealed
                              :entered entered
                              :expected (:answer item)})))))))

(defn- override-item! [!state lesson exercise item]
  (let [entered (read-entered (:number lesson) (:id exercise) item)]
    (swap! !state
           (fn [state]
             (let [next-progress (progress/record! (:progress state)
                                                   (:number lesson)
                                                   (:id exercise)
                                                   (:n item)
                                                   "correct")]
               (-> state
                   (assoc :progress next-progress)
                   (assoc-in [:feedback (str (:n item))]
                             {:status :correct
                              :override? true
                              :entered entered
                              :expected (:answer item)})))))))

(defn- reset-exercise! [!state lesson exercise]
  (swap! !state
         (fn [state]
           (-> state
               (assoc :progress (progress/reset-exercise! (:progress state)
                                                          (:number lesson)
                                                          (:id exercise)))
               (assoc :feedback {})))))

(defn- feedback-view [feedback]
  (let [status (:status feedback)
        entered (:entered feedback)
        expected (:expected feedback)]
    [:div {:class (str "feedback feedback-" (name status))
           :aria-live "polite"}
     [:p.feedback-status
      (case status
        :correct "Correct!"
        :correct-accent "Correct — check the accents."
        :wrong "Not quite."
        :revealed "Here is the answer."
        :empty "Type an answer first."
        "")]
     (when (and (seq entered) (not= status :empty))
       [:p.feedback-entered
        [:span.feedback-label "You: "]
        (str/join " / " entered)])
     (when (or (= status :wrong) (= status :revealed) (= status :correct-accent))
       [:p.feedback-expected
        [:span.feedback-label "Expected: "]
        (str/join " / " expected)])
     (when (:override? feedback)
       [:p.feedback-hint "Marked correct by you."])]))

(defn- item-view [state !state lesson exercise item]
  (let [lesson-number (:number lesson)
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
        source-lang (if (= :to-english (:direction exercise)) "es" nil)
        answer-lang (if (= :to-spanish (:direction exercise)) "es" nil)]
    [:div {:class (str "item" (when display-status
                                (str " item-" (name display-status))))
           :key (str item-number)}
     [:div.item-head
      [:span.item-number (str item-number ".")]
      (when display-status
        [shared/status-chip display-status])]
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
                      :aria-label (str "Answer for item " item-number " turn " (inc turn))
                      :on-keydown (fn [e]
                                    (when (= (.-key e) "Enter")
                                      (.preventDefault e)
                                      (check-item! !state lesson exercise item)))}])
           (range turns))]
     [:div.item-actions
      [:button {:type "button"
                :on-click (fn [_] (check-item! !state lesson exercise item))}
       "Check"]
      [:button.secondary {:type "button"
                          :on-click (fn [_] (reveal-item! !state lesson exercise item))}
       "Show answer"]
      (when (= status :wrong)
        [:button.link {:type "button"
                       :on-click (fn [_] (override-item! !state lesson exercise item))}
         "I was right"])]
     (when feedback
       (feedback-view feedback))]))

(defn- not-found []
  [:div.not-found
   [:h1 "Exercise not found"]
   [:p [:a {:href (router/home-href)} "Back to all lessons"]]])

(defn view [state]
  (let [route (:route state)
        lesson (data/lesson-by-number (:lessons state) (:lesson route))
        exercise (data/exercise-by-id lesson (:exercise route))]
    (cond
      (nil? lesson) (not-found)
      (nil? exercise) (not-found)
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
          [:p.step-label (str "Step " (:number (:step lesson)) " · " (:title (:step lesson)))]
          [:h1 (str "Exercise " (:id exercise))]
          [:p.exercise-meta
           (str (shared/skill-label (:skill exercise))
                " · "
                (shared/direction-label (:direction exercise)))]
          (when (not-empty (:instructions exercise))
            [:p.instructions (:instructions exercise)])
          [:div.exercise-progress
           [:span (str (:done stats) " of " (:total stats) " items correct")]
           [shared/progress-bar stats]]]
         (when (seq (:hints exercise))
           [:details.hints
            [:summary "Hints"]
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
            "Reset exercise"]
           [:a.back-link {:href (router/lesson-href (:number lesson))} "Back to lesson"]
           (when previous
             [:a {:href (router/exercise-href (:number lesson) (:id previous))}
              (str "← " (:id previous))])
           (when following
             [:a {:href (router/exercise-href (:number lesson) (:id following))}
              (str (:id following) " →")])]
          [:p.footer-progress
           (str (:done stats) " of " (:total stats) " answers correct")]]]))))