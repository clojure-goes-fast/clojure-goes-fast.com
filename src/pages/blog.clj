(ns pages.blog
  (:require [hiccup.page :as hp]
            [views :refer :all])
  (:import java.text.SimpleDateFormat))

(def ^:private date-format (SimpleDateFormat. "MMM dd, YYYY"))
(defn- fmt-date [date] (.format date-format date))

(defn blog-post [{:keys [entry]}]
  (wrap
   (str (:name entry) " - Clojure Goes Fast")
   (navbar)
   [:div.container
    [:div.content.blog-post
     [:div.back-link [:a {:href "/blog/"} "↰ Back to list"]]
     [:span [:i (fmt-date (:date-published entry))]]
     [:h1 (:name entry)]
     (:content entry)]]))

(defn blog-index [{:keys [meta entries]}]
  (wrap
   "Blog - Clojure Goes Fast"
   (navbar)
   [:div.container.flex-container
    [:div.blog
     [:h3 "Blog posts"
      [:div.float-right
       [:a.btn.btn-link {:href "/blog/atom.xml"
                         :target "_blank"}
        [:img {:src "/img/icons/rss.svg" :alt "Atom feed"}]]
       [:a.btn.btn-link {:href "https://github.com/clojure-goes-fast/clojure-goes-fast.com"
                         :target "_blank"}
        [:img {:src "/img/icons/github.svg" :alt "Github"}]]]]
     [:div.divider]
     (for [post entries]
       [:div.p-1
        [:a.h6 {:href (:clean-url post)} (:name post)]
        [:span.float-right (fmt-date (:date-published post))]])]]))
