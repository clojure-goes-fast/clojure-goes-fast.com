(ns runner
  (:require [clojure.java.io :as io])
  (:import (java.nio.file Files FileVisitOption Path)
           (java.util.function Function Predicate Consumer)
           java.util.stream.Collectors
           java.util.Comparator
           (javax.tools DiagnosticListener ToolProvider)
           org.openjdk.jmh.runner.Runner
           org.openjdk.jmh.runner.options.OptionsBuilder))

;; Javac

(defn- find-java-files [path]
  (-> (Files/walk (.toPath (io/file path)) (into-array FileVisitOption []))
      (.map (reify Function
              (apply [_ path]
                (.toFile ^Path path))))
      (.filter (reify Predicate
                 (test [_ f]
                   (and (.isFile f)
                        (.endsWith (.getName f) ".java")))))
      (.collect (Collectors/toList))))

(defn- delete-dir [dir]
  (let [file (io/file dir)]
    (when (.exists file)
      (-> (Files/walk (.toPath file) (into-array FileVisitOption []))
          (.sorted (Comparator/reverseOrder))
          (.forEach (reify Consumer
                      (accept [_ path]
                        (Files/delete path))))))))

(defn javac [& {:keys [javac-opts src-dirs]}]
  (when (seq src-dirs)
    (let [class-dir (doto (io/file "target/classes")
                      (delete-dir)
                      (.mkdirs))
          compiler (ToolProvider/getSystemJavaCompiler)
          listener (reify DiagnosticListener
                     (report [_ diag]
                       (println diag)))
          file-mgr (.getStandardFileManager compiler listener nil nil)
          classpath (System/getProperty "java.class.path")
          options (concat ["-classpath" classpath "-d" (.getPath class-dir)] javac-opts)
          java-files (mapcat find-java-files src-dirs)
          file-objs (.getJavaFileObjectsFromFiles file-mgr java-files)
          task (.getTask compiler nil file-mgr listener options nil file-objs)]
      (when-not (.call task)
        (throw (ex-info "Java compilation failed" {}))))))

(defn jmh [{:keys [bench profiler] :as opts}]
  (assert (:bench opts))
  (javac {:src-dirs ["src"]
          :javac-opts []})
  (when-not (io/resource "META-INF/BenchmarkList")
    (println "Could not find META-INF/BenchmarkList resource on the classpath.
Rerun this command for the problem to go away.")
    (System/exit 1))

  (let [opts (OptionsBuilder.)]
    (.include opts (str bench))
    (when profiler
      (.addProfiler opts (str profiler)))
    (.run (Runner. (.build opts)))))
