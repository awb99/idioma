(ns serve
  "Small static file server for the production build in dist/."
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [org.httpkit.server :as http]))

(def content-types
  {"html" "text/html; charset=utf-8"
   "js" "text/javascript; charset=utf-8"
   "mjs" "text/javascript; charset=utf-8"
   "css" "text/css; charset=utf-8"
   "json" "application/json; charset=utf-8"
   "map" "application/json; charset=utf-8"
   "svg" "image/svg+xml"
   "png" "image/png"
   "jpg" "image/jpeg"
   "jpeg" "image/jpeg"
   "webp" "image/webp"
   "woff2" "font/woff2"
   "ico" "image/x-icon"
   "txt" "text/plain; charset=utf-8"})

(def cli-spec
  {:port {:default 8080 :coerce :long :desc "Port to listen on"}
   :dir {:default "dist" :desc "Directory to serve"}})

(defn- content-type [path]
  (let [name (str (fs/file-name path))
        ext (some-> (str/last-index-of name ".")
                    (as-> i (when i (subs name (inc i))))
                    str/lower-case)]
    (get content-types ext "application/octet-stream")))

(defn- percent-decode [s]
  (try
    ;; URLDecoder treats + as space, which is wrong for path segments.
    (java.net.URLDecoder/decode (str/replace s "+" "%2B") "UTF-8")
    (catch Exception _ nil)))

(defn- safe-relative
  "Map a request URI to a path below the root. A trailing slash means a
  directory index; a path without an extension that resolves to a directory is
  treated the same way by `resolve-request`."
  [uri]
  (when (string? uri)
    (let [decoded (percent-decode uri)]
      (when (and decoded (not (str/includes? decoded "\u0000")))
        (let [rel (-> decoded
                      (str/replace #"^/+" "")
                      (str/replace #"^\./" ""))]
          (cond
            (str/blank? rel) "index.html"
            (str/ends-with? rel "/") (str rel "index.html")
            :else rel))))))

(defn- inside-root? [root-str path]
  (let [p (str path)]
    (or (= p root-str)
        (str/starts-with? p (str root-str java.io.File/separator)))))

(defn- resolve-request [root root-str uri]
  (when-let [rel (safe-relative uri)]
    (let [candidate (fs/path root rel)
          candidate (if (fs/directory? candidate)
                      (fs/path candidate "index.html")
                      candidate)]
      (try
        (let [canonical (fs/canonicalize candidate)]
          {:path canonical
           :rel rel
           :inside? (inside-root? root-str canonical)})
        (catch Exception _
          {:path candidate
           :rel rel
           :inside? false})))))

(defn- response [status headers body]
  {:status status
   :headers headers
   :body body})

(defn- text-response [status body]
  (response status {"content-type" "text/plain; charset=utf-8"
                    "cache-control" "no-cache"}
               body))

(defn- not-found [root body?]
  (let [page (fs/path root "404.html")]
    (if (and body? (fs/regular-file? page))
      (response 404 {"content-type" "text/html; charset=utf-8"
                     "cache-control" "no-cache"
                     "content-length" (str (fs/size page))}
                (fs/read-all-bytes page))
      (text-response 404 "not found"))))

(defn handler
  "Ring handler serving files below root. GET and HEAD only."
  [root]
  (let [root (fs/canonicalize root)
        root-str (str root)]
    (fn [req]
      (let [method (:request-method req)]
        (if-not (contains? #{:get :head} method)
          (response 405 {"content-type" "text/plain; charset=utf-8"
                         "allow" "GET, HEAD"}
                    "method not allowed")
          (let [{:keys [path rel inside?]} (resolve-request root root-str (:uri req))]
            (cond
              (nil? path) (text-response 400 "bad request")
              (not inside?) (not-found root (= :get method))
              (not (fs/regular-file? path)) (not-found root (= :get method))
              :else
              (let [immutable? (str/starts-with? rel "assets/")
                    headers {"content-type" (content-type path)
                             "cache-control" (if immutable?
                                               "public, max-age=31536000, immutable"
                                               "no-cache")
                             "content-length" (str (fs/size path))}]
                (response 200 headers
                          (when (= :get method)
                            (fs/read-all-bytes path)))))))))))

(defn- parse-opts [args]
  (let [opts (cli/parse-opts args {:spec cli-spec})
        port (:port opts)]
    (when-not (and (integer? port) (< 0 port 65536))
      (binding [*out* *err*]
        (println "error:  --port must be an integer between 1 and 65535"))
      (System/exit 1))
    opts))

(defn -main [& args]
  (let [{:keys [port dir]} (parse-opts args)
        root (fs/path dir)]
    (when-not (fs/regular-file? (fs/path root "index.html"))
      (binding [*out* *err*]
        (println "error:  " (str root) "does not contain index.html.")
        (println "        Run `bb build` (or `npx vite build`) before `bb serve`."))
      (System/exit 1))
    (println "Serving" (str (fs/canonicalize root)) "on" (str "http://localhost:" port))
    (http/run-server (handler root) {:port port :ip "127.0.0.1"})
    (deref (promise))))