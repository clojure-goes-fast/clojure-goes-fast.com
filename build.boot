(set-env!
 :source-paths #{"src" "content"}
 :resource-paths #{"res"}
 :dependencies '[[perun "0.3.0" :scope "test"]
                 [hiccup "LATEST"]
                 [criterium "0.4.4"]
                 [pandeiro/boot-http "0.7.0"]])

(require '[io.perun :refer :all]
         'io.perun.core
         '[pandeiro.boot-http :as jetty]
         '[clojure.string :as str]
         '[boot.core :as boot])

(defn my-permalink [m]
  (-> (cond
        (.startsWith (:filename m) "index") "/index.html" ;; Top-level index
        (seq (:slug m)) (str (:parent-path m) (:slug m) "/index.html") ;; A post
        :else (str (re-find #"^[^\.]+" (:filename m)) "/index.html")) ;; Site page
      io.perun.core/absolutize-url))

(deftask clean-url
  "Removes all \"index.html\" from permalinks."
  []
  (boot/with-pre-wrap fileset
    (let [files         (io.perun.core/get-meta fileset)
          clean-url-fn  #(let [clean-link (str/replace (:permalink %) #"index.html$" "")]
                           (-> %
                               (assoc :permalink clean-link)
                               (assoc :clean-url clean-link)))
          updated-files (map clean-url-fn files)]
      (io.perun.core/merge-meta fileset updated-files))))

(task-options!
 jetty/serve {:dir "target/public/"}  ;; {:resource-root "public"}
 permalink {:permalink-fn my-permalink})

(defn blog? [entry] (= (:parent-path entry) "blog/"))

(deftask render-all []
  (comp (sift :move {#"((?:css|js|img).*)" "public/$1"})
        (markdown)
        (global-metadata :filename "base-meta.edn")
        (slug)
        (permalink)
        (clean-url)
        (canonical-url)
        ;; (print-meta :map-fn #(dissoc % :content))

        ;; Site
        (collection :renderer 'pages.index/index
                    :page "index.html")

        ;; Blog
        (render :filterer blog?
                :renderer 'pages.blog/blog-post)
        (collection :filterer blog?
                    :renderer 'pages.blog/blog-index
                    :page "blog/index.html")))

(deftask live []
  (comp (jetty/serve)
        (watch)
        (render-all)
        (target)))

(deftask build []
  (comp (render-all)
        (atom-feed :filterer blog? :filename "blog/atom.xml")
        (target)))

#_(boot (live))

#_(boot (build))

#_(boot (jetty/serve))
