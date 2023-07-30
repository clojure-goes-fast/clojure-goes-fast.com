(ns generator.main
  (:require [clojure.string :as str]
            [cryogen-core.compiler :as comp]
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
  (letfn [(fix-date [date]
            (-> (cond-> date
                  (not (.contains date " ")) (str " 12:00:00"))
                (LocalDateTime/parse date-format)
                (.atZone (ZoneId/systemDefault))
                .toInstant Date/from))]
    (let [dp (or (:date-updated article)
                 (:date-published article))]
      (cond-> article
        (:date-published article) (update :date-published fix-date)
        dp (assoc :date (fix-date dp))))))

(defn strip-date-from-post-url [article]
  (if (= (:type article) :post)
    (update article :uri
            #(let [[_ pre post] (re-matches #"(.+/)\d{4}-\d{2}-\d{2}-(.+)" %)]
               (str pre post)))
    article))

(defn remove-newlines-from-description [article]
  (update article :description #(str/replace % #"\n" " ")))

(defn update-article [article]
  (-> article
      strip-date-from-post-url
      remove-newlines-from-description
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

(defn assoc-in-kb-tree [tree url-path page]
  (let [k (vec (interpose :children url-path))]
    (update-in tree k #(assoc % :page page))))

(defn attach-knowledge-base-sidebar-fn [{:keys [sidebar-pages] :as result} _]
  (let [uri->page (->> sidebar-pages
                       (map (fn [page] [(:uri page) page]))
                       (into {}))
        tree (reduce (fn [tree page]
                       (let [uri (:uri page)
                             parts (rest (remove str/blank? (str/split uri #"/")))
                             partitions (rest (reductions #(str %1 %2 "/") "/kb/" parts))]
                         (if (seq parts)
                           (assoc-in-kb-tree tree partitions page)
                           tree)))
                     {}
                     sidebar-pages)
        sort-tree (fn sort-tree [subtree]
                    (for [[k v] subtree]
                      (update v :children sort-tree)))
        tree (sort-tree tree)
        generate-nav
        (fn generate-nav [tree current-uri]
          (format "<ul class=\"nav\">%s</ul>"
                  (->> (for [child tree]
                         (format
                          "<li class=\"nav-item%s\"><a href=\"%s\">%s</a></li>%s"
                          (if (= (:uri (:page child)) current-uri) " active" "")
                          (:uri (:page child))
                          (:title (:page child))
                          (if (seq (:children child))
                            (generate-nav (:children child) current-uri)
                            "")))
                       str/join)))]
    #_(clojure.pprint/pprint tree)
    (assoc result :sidebar-fn (fn [params]
                                (generate-nav tree (:uri params))))))

;; Also a hack, too lazy to do it properly.

(alter-var-root
 #'comp/render-file
 (fn [og]
   (fn [file-path {:keys [sidebar-fn] :as params}]
     (og file-path (if sidebar-fn
                     (assoc params :sidebar (sidebar-fn params))
                     params)))))

(alter-var-root
 #'cryogen-core.sitemap/loc
 (fn [og]
   (fn [file]
     (let [loc (og file)]
       (if-let [[_ wo-index] (re-matches #"(.*)index\.html" loc)]
         wo-index
         loc)))))

(defn build* [opts changeset]
  (let [gen-drafts? (:draft opts)]
    (comp/compile-assets-timed
     {:update-article-fn
      (fn [article _]
        (when (or gen-drafts? (not (:draft article)))
          (update-article article)))
      :extend-params-fn attach-knowledge-base-sidebar-fn}
     changeset)
    (when-not gen-drafts?
      (compile-draft-preview-posts))))

(defn build [opts]
  (load-plugins)
  (build* opts {}))
