(ns pages.index
  (:require [hiccup.page :as hp]
            [views :refer :all]))

(defn- index-card [& {:keys [icon title link link-blank? body disabled?]}]
  [:div.column.col-4.col-xs-12
   [:div.card.text-center
    [:div {:class (str "card-header" (when disabled? " disabled"))}
     (conj (if disabled?
             [:div.tooltip {:data-tooltip "Under construction."}]
             [:a (cond-> {:href link}
                   link-blank? (assoc :target "_blank"))])
           [:img {:src icon :alt title}]
           [:br]
           [:span.card-title title])]
    [:div.card-body body]]])

(defn index [_]
  (wrap
   "Clojure Goes Fast"
   [:div.section.section-hero
    [:div.container.grid-hero.grid-lg.text-center
     [:img.big-logo {:src "/img/logo.png"}]
     [:h1 "Clojure goes fast!"]
     [:h2 "A central hub for news, docs, and guides about Clojure and high
      performance."]

     [:div.columns
      (index-card :title "Documentation" :icon "/img/icons/book.svg"
                  :link "#" :disabled? true
                  :body "Study comprehensive and up-to-date guides about what
                   makes Clojure slow and how to make it fast.")
      (index-card :title "Blog posts" :icon "/img/icons/script.svg"
                  :link "blog/"
                  :body "Follow the latest news and articles about tools and
                   techniques for achieving high performance in Clojure.")
      (index-card :title "Source code" :icon "/img/icons/github.svg"
                  :link "https://github.com/clojure-goes-fast/"
                  :link-blank? true
                  :body "View the code for this site and other
                   performance-related projects. Contribute to help Clojure go
                   faster.")]]]))
