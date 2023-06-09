(ns demo
  (:require [criterium.core :as criterium]))

;; How not to do
(time (reduce + (map #(/ % 100.0) (range 100))))

;; Slightly better how not to do
(time (dotimes [_ 1e6] (reduce + (map #(/ % 100.0) (range 100)))))

;; Proper way to benchmark
(criterium/quick-bench (reduce + (map #(/ % 100.0) (range 100))))

;; Benchmarking Thread/sleep
(criterium/quick-bench (Thread/sleep 10))

;; Laziness pitfall. Don't do this!
(criterium/quick-bench (map #(Math/tan %) (range 1000)))

;; Forcing lazy sequence yields a correct result
(criterium/quick-bench (doall (map #(Math/tan %) (range 1000))))

;; Example with nested lazy sequences.
(defn full-lazy []
  (repeatedly 10 (fn [] (map #(Math/tan %) (range 1000)))))

(defn outer-doall []
  (doall
   (repeatedly 10 (fn [] (map #(Math/tan %) (range 1000))))))

(defn full-eager []
  (doall
   (repeatedly 10 (fn [] (doall
                          (map #(Math/tan %) (range 1000)))))))

(doseq [f [#'full-lazy #'outer-doall #'full-eager]]
  (let [result (criterium/quick-benchmark* f {})]
    (criterium/report-point-estimate (str f) (:mean result))))
