(ns play
  (:require [clj-async-profiler.core :as prof])
  (:import raytracer.Render))

(prof/set-default-profiling-options
 {:predefined-transforms
  [{:type :replace
    :what #";nrepl.+;nrepl[^;]+"
    :replacement "$1"}]
  [{:type :replace
    :what #"(Lambda)[^;]+(\.run)"
    :replacement "$1$2"}]})

(comment
  (require 'play)
  (in-ns 'play)
  (prof/profile (Render/render 500 20 "out2.png"))
  (prof/profile {:event :alloc} (Render/render 500 20 "out2.ong"))

  (prof/generate-diffgraph "/tmp/clj-async-profiler/results/02-alloc-2023-06-25-20-08-36.txt"
                           "/tmp/clj-async-profiler/results/03-alloc-2023-06-25-20-38-47.txt"
                           {})
  (prof/serve-ui 8085))
