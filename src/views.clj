(ns views
  (:require [hiccup.page :as hp]
            [clojure.java.io :as io]))

(defn google-analytics []
  [:script
   "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
})(window,document,'script','https://www.google-analytics.com/analytics.js','ga');

ga('create', 'UA-106863357-1', 'auto');
ga('send', 'pageview');
"])

(defn head [title]
  [:head
   [:meta {:name "viewport" :content "width=device-width"}]
   [:meta {:http-equiv "Content-Type" :content "text/html; charset=UTF-8"} ]
   [:link {:rel "icon" :type "image/png" :href "/img/favicon.png"}]
   [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/spectre.css/0.4.2/spectre.min.css"}]
   [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/spectre.css/0.4.2/spectre-exp.min.css"}]
   [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/spectre.css/0.4.2/spectre-icons.min.css"}]
   [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/styles/github.min.css"}]
   [:link {:rel "stylesheet" :href "/css/custom.css"}]
   (google-analytics)
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/highlight.min.js"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/clojure.min.js"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/clojure-repl.min.js"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/java.min.js"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/bash.min.js"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/shell.min.js"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/x86asm.min.js"}]
   [:script "hljs.initHighlightingOnLoad();"]
   [:title title]])

(defn navbar []
  [:header.navbar
   [:section.navbar-section]
   [:section.navbar-center
    [:div.brand [:a.btn.btn-link.logo {:href "/"} [:img {:src "/img/logo.png"}] [:h2 "Clojure Goes Fast"]]]]
   [:section.navbar-section]])

(defn footer []
  [:footer.text-center
   [:div.divider]
   [:div.footer-content
    [:span "© 2017 " [:a {:href "mailto:alex@bytopia.org"} "Alexander Yakushev"] ". "]
    [:span "Made with " [:a {:href "https://perun.io/" :target "_blank"} "Perun"] ". "]
    [:span "Logo is derived from "
     [:a {:href "https://commons.wikimedia.org/wiki/File:Clojure_logo.svg" :target "_blank"} "Clojure logo"]
     ", copyright by Rich Hickey. "]
    [:span "Icons from " [:a {:href "https://iconmonstr.com/"} "Iconmonstr"] "."]]])

(defn wrap [title & content]
  (hp/html5
   (head title)
   (conj (into [:body] content) (footer))))

;; [:section.navbar-section
;;  [:a.btn.btn-link {:href ""} [:img {:src "/img/icons/rss.svg"}]]]
