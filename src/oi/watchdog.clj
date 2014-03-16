(ns oi.watchdog
  (:require [clojure.tools.logging :as log]
            [oi.service :as service]
            [figgus.core :as cfg]))

(def ^:private run-watchdog (atom false))

(defn- check-instance [i]
  (log/trace "Checking:" i)
  (let [now (System/currentTimeMillis)]
    (case (service/get-metadata i :status)
      :up (when (> (- now (service/get-metadata i :last-check-in))
                   (cfg/get "service.timeoutms.up" 5000))
            (log/info "Timing out:" i)
            (service/instance-timeout i))
      :starting (when (> (- now (service/get-metadata i :last-check-in))
                         (cfg/get "service.timeoutms.starting" 180000))
                  (log/info "Timing out:" i)
                  (service/instance-timeout i))
      :dead (when (> (- now (service/get-metadata i :last-check-in))
                     (cfg/get "service.timeoutms.dead" 3600000))
              (log/info "Removing dead instance:" i)
              (service/del-instance i))
      (log/trace (service/get-metadata i :svc-name) "-" i "- looks OK..."))))

(defn start []
  "Starts a watchdog thread that will sleep for the given period. 
   All threads will terminate when watchdog/stop is called.
   Additional calls to this function will start additional watchdog threads."
  (log/info "Starting a watchdog")
  (swap! run-watchdog (fn [_] true))
  (future
    (loop []
      (Thread/sleep (cfg/get "watchdog.sleepms" 5000))
      (pmap check-instance (service/get-all-instances))
      (when @run-watchdog
        (recur)))))

(defn stop []
  (log/info "Stopping ALL watchdogs")
  (swap! run-watchdog (fn [_] false)))
