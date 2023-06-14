(ns clj-code)

(defn id ^long [^long val] val)

(defn test-if ^long [^long val]
  (if (< val 10)
    1
    2))

(defn test-if-not ^long [^long val]
  (if-not (< val 10)
    1
    2))

(defn opaque-check [val] (< val 10))

(defn test-opaque-if ^long [^long val]
  (if (opaque-check val)
    1
    2))

(defn test-opaque-if-not ^long [^long val]
  (if-not (opaque-check val)
    1
    2))

(alter-var-root #'*compiler-options* assoc :direct-linking true)

(defn test-direct-if ^long [^long val]
  (if (< val 10)
    1
    2))

(defn test-direct-if-not ^long [^long val]
  (if-not (< val 10)
    1
    2))

(defn opaque-direct-check [val] (< val 10))

(defn test-direct-opaque-if ^long [^long val]
  (if (opaque-direct-check val)
    1
    2))

(defn test-direct-opaque-if-not ^long [^long val]
  (if-not (opaque-direct-check val)
    1
    2))

(alter-var-root #'*compiler-options* dissoc :direct-linking)
