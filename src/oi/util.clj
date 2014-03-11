(ns oi.util
  (:require [clojure.tools.logging :as log])
  (:import [java.util UUID]))

(defn random-uuid []
  (log/trace "random-uuid")
  (str (UUID/randomUUID)))
