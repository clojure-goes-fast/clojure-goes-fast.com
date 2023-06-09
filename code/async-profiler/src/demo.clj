(ns demo
  (:require [clj-async-profiler.core :as prof]))

(defn test-sum []
  (reduce + (map inc (range 1000))))

(defn test-div []
  (reduce / (map inc (range 1000))))

(defn burn-cpu [secs]
  (let [start (System/nanoTime)]
    (while (< (/ (- (System/nanoTime) start) 1e9) secs)
      (test-sum)
      (test-div))))

;; Profile the invocation.
(prof/profile (burn-cpu 10))
