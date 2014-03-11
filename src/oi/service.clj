(ns oi.service
  (:require [clojure.tools.logging :as log]
            [figgus.core :as cfg]
            [oi.util :refer :all]))

(defrecord Service [svc-name instance-id metadata])

(def required-keys [:registration-time :last-check-in :status :timeouts])

(def services (ref {}))

(defn- in? [vec item]
  (log/trace "in?: " item "-" vec)
  (pos? (count (filter (fn [i] (= i item)) vec))))

(defn- set-no-check [instance-id metadata-key metadata-value]
  "Sets the value of the metadata-key, returns nil."
  (log/trace "set-no-check:" instance-id "-" metadata-key "-" metadata-value)
  (dosync
   (when (contains? @services instance-id)
     (let [svc (@services instance-id)]
       (alter services merge
              {instance-id (assoc svc :metadata
                                  (merge (:metadata svc)
                                         {metadata-key metadata-value}))})
       svc))))

(defn- validate-metadata
  ([metadata-key] (validate-metadata metadata-key nil))
  ([metadata-key metadata-value]
     (log/trace "validate-metadata:" metadata-key "-" metadata-value)
     (when (in? required-keys metadata-key)
       (throw (IllegalArgumentException. (str "Cannot modify: " metadata-key " as it is protected metadata"))))))

(defn- metadata-matches? [svc metadata]
  "Tests the service and returns true when the service contains all of the metadata and the values are equal."
  (log/trace "metadata-matches?:" svc "-" metadata)
  (reduce (fn [i j] (and i (= (get (:metadata svc) (key j)) (val j)))) true metadata))

(defn register-svc [svc-name metadata]
  "Registers a service instance and returns a new service instance."
  (log/debug "register-svc:" svc-name "-" metadata)
  (dosync
   (let [instance-id (random-uuid)
         now (System/currentTimeMillis)
         reg-data {:registration-time now
                   :last-check-in now
                   :status :starting}
         service (->Service svc-name instance-id (merge metadata reg-data))]
     (alter services merge {instance-id service})
     service)))

(defn del-instance [instance-id]
  (dosync
   (alter services dissoc instance-id)))

(defn get-all-instances []
  "Returns all instance IDs"
  (keys @services))

(defn set-metadata [instance-id metadata-key metadata-value]
  "Sets the value of the metadata-key, returns nil."
  (log/debug "set-metadata:" instance-id "-" metadata-key "-" metadata-value)
  (validate-metadata metadata-key metadata-value)
  (set-no-check instance-id metadata-key metadata-value))

(defn valid-state? [state]
  (log/debug "valid-state?:" state)
  (in? [:up :down :starting :out-of-service] state))

(defn get-service
  "Returns details of the service and all instances of it."
  ([svc-name]
     (get-service svc-name {}))
  ([svc-name params]
     (log/debug "get-service:" svc-name "-" params)
     (filter (fn [i] (and (metadata-matches? i params)
                          (= (:svc-name i) svc-name)))
             (vals @services))))

(defn get-instance [instance-id]
  "Gets the details of the instance-id including service name and state and last update time."
  (log/debug "get-instance:" instance-id)
  (@services instance-id))

(defn get-metadata [instance-id metadata-key]
  "Returns the value of the metadata-key, or nil if it does not exist."
  (log/debug "get-metadata:" instance-id "-" metadata-key)
  (if-let [instance (get-instance instance-id)]
    ((:metadata instance) metadata-key)))

(defn del-metadata [instance-id metadata-key]
  "Removes the metadata key from the instance, returns the value."
  (log/debug "del-metadata:" instance-id "-" metadata-key)
  (validate-metadata metadata-key)
  (dosync
   (when (contains? @services instance-id)
     (let [svc (@services instance-id)]
       (alter services merge
              {instance-id (assoc svc :metadata
                                  (dissoc (:metadata svc) metadata-key))})))))

(defn heartbeat
  [instance-id state]
  "Records a service instance heartbeat and state"
  (log/debug "heartbeat:" instance-id "-" state)
  (when-not (valid-state? state)
    (throw (IllegalArgumentException. (str "Invalid state: " state))))
  (dosync
   (if (= :down state)
     (do
       (log/info instance-id "marked as DOWN - ending our relationship with this instance, goodbye.")
       (alter services dissoc instance-id))
     (do
       (if (and (= :up state) (in? [:dead :zombie] (get-metadata instance-id :status)))
         (set-no-check instance-id :status :zombie)
         (set-no-check instance-id :status state))
       (set-no-check instance-id :last-check-in (System/currentTimeMillis)))))
  (@services instance-id))

(defn instance-timeout [instance-id]
  "Marks the instance as timed out, after service.threshold (default 2) timeouts the instance will be marked as dead."
  (let [timeouts (inc (or (get-metadata instance-id :timeouts) 0))]
    (set-no-check instance-id :timeouts timeouts)
    (when (>= timeouts (Integer. (cfg/get "service.threshold" 2)))
      (set-no-check instance-id :status :dead))))
