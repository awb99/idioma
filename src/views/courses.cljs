(ns views.courses
  (:require [router :as router]
            [ui :as i18n]))

(defn- course-card [state course]
  (let [ui (get-in state [:manifest :ui])
        base (get-in course [:languages :base :name])
        target (get-in course [:languages :target :name])
        href (router/course-href (:base state) (:slug course))]
    [:article.course-card {:key (:slug course)}
     [:h3.course-title (:title course)]
     (when (:tagline course)
       [:p.course-tagline (:tagline course)])
     [:p.course-languages (str base " → " target)]
     [:p.course-lesson-count
      (i18n/t ui :course-lessons {:count (:lessonCount course)})]
     [:a.course-link {:href href} (i18n/t ui :open-course)]]))

(defn view [state]
  (let [manifest (:manifest state)
        ui (get-in manifest [:ui])
        site (:site manifest)
        courses (:courses manifest)]
    [:div.chooser
     [:header.app-header
      [:h1 (or (:title site) (i18n/t ui :site-title))]
      (when (:tagline site)
        [:p.tagline (:tagline site)])]
     [:section.course-block
      [:h2 (i18n/t ui :choose-course)]
      (if (seq courses)
        [:div.course-grid
         (map (fn [course] (course-card state course)) courses)]
        [:p (i18n/t ui :course-not-found)])]
     [:footer.app-footer
      [:p (i18n/t ui :progress-browser-only)]]]))

(defn course-not-found [state]
  (let [ui (get-in state [:manifest :ui])]
    [:div.not-found
     [:h1 (i18n/t ui :course-not-found)]
     [:p [:a {:href (router/chooser-href (:base state))}
          (i18n/t ui :back-to-courses)]]]))