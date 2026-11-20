(ns app
  (:require ["reagami" :as r]
            [router :as router]
            [progress :as progress]
            [views.home :as home]
            [views.lesson :as lesson]
            [views.exercise :as exercise]))

(defonce !state
  (atom {:route (router/parse (.-hash js/location))
         :lessons nil
         :error nil
         :progress (progress/load)
         :feedback {}}))

(defn view [state]
  (let [state (assoc state :state-atom !state)
        route (:route state)
        lessons (:lessons state)
        error (:error state)]
    (cond
      error
      [:div.error
       [:h1 "Something went wrong"]
       [:p error]]

      (nil? lessons)
      [:div.loading "Loading lessons…"]

      :else
      (case (:name route)
        :home (home/view state)
        :lesson (lesson/view state)
        :exercise (exercise/view state)
        [:div.not-found
         [:h1 "Page not found"]
         [:p [:a {:href (router/home-href)} "Back to all lessons"]]]))))

(defn render! []
  (r/render (js/document.getElementById "app") (view @!state)))

(defn ^:async load! []
  (try
    (let [res (await (js/fetch "lessons.json"))
          _ (when-not (.-ok res)
              (throw (js/Error. (str "HTTP " (.-status res)))))
          body (await (.json res))
          lessons (.-lessons body)]
      (when-not (js/Array.isArray lessons)
        (throw (js/Error. "lessons.json has no lessons array")))
      (swap! !state assoc :lessons (vec lessons)))
    (catch :default e
      (swap! !state assoc :error
             (str "Could not load lessons: " (or (.-message e) (str e)))))))

(defn ^:dev/after-load re-render []
  (render!))

(add-watch !state ::render (fn [_ _ _ _] (render!)))

(.addEventListener js/window "hashchange"
                   (fn [_]
                     (swap! !state assoc
                            :route (router/parse (.-hash js/location))
                            :feedback {})))

(render!)
(load!)