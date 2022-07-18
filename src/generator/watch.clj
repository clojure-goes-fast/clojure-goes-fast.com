(ns generator.watch
  (:require [cryogen-core.config :refer [resolve-config]]
            [cryogen-core.plugins :refer [load-plugins]]
            [cryogen-core.watcher :as watcher]
            [generator.main :as main]))

(def resolved-config (delay (resolve-config)))

(defn init [opts]
  (load-plugins)
  (main/build* opts {})
  (let [ignored-files (-> @resolved-config :ignored-files)]
    (doseq [dir ["content" "themes"]]
      (if (:fast opts)
        (watcher/start-watcher-for-changes! dir ignored-files #(main/build* opts %1))
        (watcher/start-watcher! dir ignored-files #(main/build* opts {}))))))
