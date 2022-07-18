(ns generator.main
  (:require [cryogen-core.compiler :as comp]
            [cryogen-core.plugins :refer [load-plugins]])
  (:import (java.time LocalDateTime ZoneId)
           (java.time.format DateTimeFormatter)
           java.util.Date))

; Hacks

(.bindRoot
 #'comp/compile-archives
 (fn [{:keys [blog-prefix] :as params} posts]
   (println (text-decoration.core/blue "compiling blog index"))
   (let [uri (comp/page-uri "blog/index.html" params)]
     (comp/write-html
      uri params
      (comp/render-file "/html/blog-index.html"
                        (merge params
                               {:posts           posts
                                :selmer/context  (cryogen-core.io/path "/" blog-prefix "/")
                                :uri             uri}))))))

(.bindRoot #'comp/compile-tags (fn [& _] :nop))
(.bindRoot #'comp/compile-tags-page (fn [& _] :nop))

(def date-format (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss"))

(defn article-date->date-published [article]
  (if-let [dp (:date-published article)]
    (assoc article :date (-> (cond-> dp
                               (not (.contains dp " ")) (str " 12:00:00"))
                             (LocalDateTime/parse date-format)
                             (.atZone (ZoneId/systemDefault))
                             .toInstant Date/from))
    article))

(defn strip-date-from-post-url [article]
  (if (= (:type article) :post)
    (update article :uri
            #(let [[_ pre post] (re-matches #"(.+/)\d{4}-\d{2}-\d{2}-(.+)" %)]
               (str pre post)))
    article))

(defn build* [opts changeset]
  (comp/compile-assets-timed
   {:update-article-fn
    (fn [article _]
      (when (or (not (:draft article)) (:draft opts))
        (-> article
            strip-date-from-post-url
            article-date->date-published)))}
   changeset))

(defn build [opts]
  (load-plugins)
  (build* opts {}))
