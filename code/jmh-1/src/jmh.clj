(ns jmh
  (:import (org.openjdk.jmh.runner Runner)
           (org.openjdk.jmh.runner.options OptionsBuilder)))

(defn run-benchmark [{:strs [bench profiler] :as opts}]
  (assert bench)
  (let [opts (OptionsBuilder.)]
    (.include opts (str bench))
    (when profiler
      (.addProfiler opts (str profiler)))
    (.run (Runner. (.build opts)))))

(defn -main [& args]
  (run-benchmark (apply hash-map args)))
