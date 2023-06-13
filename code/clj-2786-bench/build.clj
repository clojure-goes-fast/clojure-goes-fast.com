(ns build
  (:require [clojure.tools.build.api :as b]))

(def default-opts
  {:basis (b/create-basis {})
   :src-dirs ["src"]
   :class-dir "target/classes"})

(defn javac [opts]
  (b/delete {:path "target"})
  (b/delete {:path "BenchmarkList"})
  (b/delete {:path "CompilerHints"})
  (b/javac (merge default-opts opts)))
