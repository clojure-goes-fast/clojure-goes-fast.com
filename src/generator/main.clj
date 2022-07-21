(ns generator.main
  (:require [cryogen-core.compiler :as comp]
            cryogen-core.config
            cryogen-core.io
            cryogen-core.markup
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

(defn update-article [article]
  (-> article
      strip-date-from-post-url
      article-date->date-published))

(defn read-draft-preview-posts [config]
  (->> (cryogen-core.markup/markups)
       (mapcat
        (fn [mu]
          (->>
           (comp/find-posts config mu)
           (pmap #(comp/parse-post % config mu))
           (filter #(= (:draft %) "preview")))))
       (sort-by :date)
       reverse))

(defn compile-draft-preview-posts []
  (let [{:keys [^String site-url blog-prefix rss-name recent-posts keep-files ignored-files previews? author-root-uri theme]
         :as   config} (cryogen-core.config/resolve-config)
        posts (->> (read-draft-preview-posts config)
                   (map (partial comp/add-description config))
                   (map update-article)
                   (remove nil?))
        params0 (merge
                 config
                 {:today         (Date.)
                  :title         (:site-title config)
                  :archives-uri  (comp/page-uri "archives.html" config)
                  :index-uri     (comp/page-uri "index.html" config)
                  :tags-uri      (comp/page-uri "tags.html" config)
                  :rss-uri       (cryogen-core.io/path "/" blog-prefix rss-name)
                  :site-url      (if (.endsWith site-url "/") (.substring site-url 0 (dec (count site-url))) site-url)})
        params params0]
    (when-not (empty? posts)
      (println (text-decoration.core/blue "compiling draft preview posts..."))
      (comp/compile-posts params posts))))

(defn build* [opts changeset]
  (let [gen-drafts? (:draft opts)]
   (comp/compile-assets-timed
    {:update-article-fn
     (fn [article _]
       (when (or gen-drafts? (not (:draft article)))
         (update-article article)))}
    changeset)
   (when-not gen-drafts?
     (compile-draft-preview-posts))))

(defn build [opts]
  (load-plugins)
  (build* opts {}))
