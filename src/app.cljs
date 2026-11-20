(ns app
  (:require ["reagami" :as r]
            [router :as router]
            [progress :as progress]
            [ui :as i18n]
            [views.courses :as courses]
            [views.home :as home]
            [views.lesson :as lesson]
            [views.exercise :as exercise]))

(defn- base []
  (router/normalized-base (or (.-__idiomaBase js/window) "/")))

(defonce !state
  (atom {:base (base)
         :slug (router/course-slug (.-pathname js/location) (base))
         :route (router/parse (.-hash js/location))
         :manifest nil
         :course nil
         :course-not-found false
         :lessons nil
         :error nil
         :progress-store (progress/load)
         :progress {}
         :feedback {}}))

(defn- current-ui [state]
  (or (get-in state [:course :ui])
      (get-in state [:manifest :ui])
      {}))

(defn- loading-view [state]
  [:div.loading (i18n/t (current-ui state) :loading)])

(defn- error-view [state]
  [:div.error
   [:h1 (i18n/t (current-ui state) :something-wrong)]
   [:p (:error state)]])

(defn view [state]
  (let [state (assoc state :state-atom !state)
        route (:route state)
        slug (:slug state)
        manifest (:manifest state)]
    (cond
      (:error state)
      (error-view state)

      (nil? slug)
      (if manifest
        (courses/view state)
        (loading-view state))

      (:course-not-found state)
      (courses/course-not-found state)

      (or (nil? (:course state)) (nil? (:lessons state)))
      (loading-view state)

      :else
      (case (:name route)
        :home (home/view state)
        :lesson (lesson/view state)
        :exercise (exercise/view state)
        (let [ui (:ui (:course state))]
          [:div.not-found
           [:h1 (i18n/t ui :page-not-found)]
           [:p [:a {:href (router/home-href)} (i18n/t ui :back-home)]]])))))

(defn- apply-document! [state]
  (let [course (:course state)
        manifest (:manifest state)
        title (if course (:htmlTitle course) (get-in manifest [:site :title]))
        lang (if course (:htmlLang course) "en")]
    (when title
      (set! (.-title js/document) title))
    (set! (.-lang js/document.documentElement) lang)))

(defn render! []
  (let [state @!state]
    (apply-document! state)
    (r/render (js/document.getElementById "app") (view state))))

(defn ^:async fetch-json [url]
  (let [res (await (js/fetch url))]
    (when-not (.-ok res)
      (throw (js/Error. (str "HTTP " (.-status res)))))
    (await (.json res))))

(defn- load-error-text [state message]
  (let [ui (get-in state [:manifest :ui])
        template (i18n/t ui :load-error)]
    (if (= template "load-error")
      (str "Could not load the course: " message)
      (i18n/t ui :load-error {:message message}))))

(defn- legacy-redirect! [manifest]
  (let [{:keys [slug route]} @!state
        legacy (when (and (nil? slug)
                          (contains? #{:lesson :exercise} (:name route)))
                 (first (filter (fn [course] (:legacyHash course))
                                (:courses manifest))))]
    (when legacy
      (.replace js/location
                (str (router/course-href (:base @!state) (:slug legacy))
                     (.-hash js/location)))
      true)))

(defn ^:async load-course! [manifest slug]
  (let [summary (first (filter (fn [course] (= slug (:slug course)))
                               (:courses manifest)))]
    (if-not summary
      (swap! !state assoc :course-not-found true)
      (try
        (let [body (await (fetch-json (str (:base @!state) "lessons/" slug ".json")))
              lessons (.-lessons body)
              course (or (.-course body) summary)
              store (:progress-store @!state)]
          (when-not (js/Array.isArray lessons)
            (throw (js/Error. (str "lessons/" slug ".json has no lessons array"))))
          (swap! !state assoc
                 :course course
                 :lessons (vec lessons)
                 :progress (progress/course-progress store slug)
                 :course-not-found false))
        (catch :default e
          (swap! !state assoc
                 :error (load-error-text @!state
                                         (or (.-message e) (str e)))))))))

(defn ^:async load! []
  (try
    (let [manifest (await (fetch-json (str (:base @!state) "courses.json")))]
      (swap! !state assoc :manifest manifest)
      (when-not (legacy-redirect! manifest)
        (let [slug (:slug @!state)]
          (when slug
            (await (load-course! manifest slug))))))
    (catch :default e
      (swap! !state assoc
             :error (str "Could not load courses.json: "
                         (or (.-message e) (str e)))))))

(defn ^:dev/after-load re-render []
  (render!))

(add-watch !state ::render (fn [_ _ _ _] (render!)))

(.addEventListener js/window "hashchange"
                   (fn [_]
                     (swap! !state assoc
                            :route (router/parse (.-hash js/location))
                            :feedback {})
                     (let [{:keys [slug manifest]} @!state]
                       (when (and (nil? slug) manifest)
                         (legacy-redirect! manifest)))))

(render!)
(load!)