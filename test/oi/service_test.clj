(ns oi.service-test
  (:require [oi.service :refer :all]
            [clojure.test :refer :all]))

(use-fixtures :each (fn [t]
                      (dosync
                       (alter services (fn [_] {})))
                      (doseq [s ["foo" "foo" "foo" "bar" "bar" "baz"]]
                        (register-svc s {}))
                      (t)))

(deftest test-valid-state?
  (are [state] (valid-state? state)
       :starting
       :up
       :down
       :out-of-service)
  (are [state] (not (valid-state? state))
       :zombie
       :dead
       :foo-bar))

(deftest test-register-and-get-service-and-instance
  (is (= 3 (count (get-service "foo"))))
  (let [s (register-svc "foo" {:my-data 12345})]
    (is (= (:svc-name s) "foo"))
    (is (contains? s :instance-id))
    (is (= (:my-data (:metadata s)) 12345))
    (is (= "foo" (:svc-name (get-instance (:instance-id s))))))
  (is (= 4 (count (get-service "foo"))))
  (is (= 1 (count (get-service "foo" {:my-data 12345}))))
  (is (= 0 (count (get-service "foo" {:my-data :XXXX}))))  
  (is (= 2 (count (get-service "bar"))))
  (is (= 1 (count (get-service "baz")))))

(deftest test-get-set-delete-metadata
  (let [svc (register-svc "my-service" {:key1 "val1" :key2 "val2"})
        instance-id (:instance-id svc)]
    (is (= (get-metadata instance-id :key1) "val1"))
    (is (= (get-metadata instance-id :key2) "val2"))
    (is (= (get-metadata instance-id :false-key) nil))
    (set-metadata instance-id :key1 "new-val1")
    (is (= (get-metadata instance-id :key1) "new-val1"))
    (del-metadata instance-id :key1)
    (is (= (get-metadata instance-id :key1) nil))))

(deftest test-cant-delete-protected-metadata
  (let [svc (register-svc "my-new-service" {})]
    (are [md] (thrown? IllegalArgumentException (del-metadata (:instance-id svc) md))
         :registration-time
         :last-check-in
         :status
         :timeouts)
    (are [md] (thrown? IllegalArgumentException (set-metadata (:instance-id svc) md "new-value"))
         :registration-time
         :last-check-in
         :status
         :timeouts)))

(deftest test-heartbeat-updates
  (let [svc (register-svc "another-service" {})
        instance-id (:instance-id svc)]
    (is (= (get-metadata instance-id :registration-time)
           (get-metadata instance-id :last-check-in)))
    (is (= (get-metadata instance-id :status) :starting))
    ;; Ensure rego and heartbeat timestamp differ
    (Thread/sleep 100)
    (let [next-svc (heartbeat instance-id :up)
          next-iid (:instance-id next-svc)]
      (is (= instance-id next-iid))
      (is (= (get-metadata instance-id :registration-time)
             (get-metadata next-iid :registration-time)))
      (is (> (get-metadata next-iid :last-check-in)
             (get-metadata next-iid :registration-time)))
      (is (= (get-metadata next-iid :status) :up)))))

(deftest test-instance-timeouts
  (testing "Testing that the instance will be marked as :dead after service.threshold timeouts"
    (let [svc (register-svc "timeout-service" {})]
      (System/setProperty "service.threshold" "2")
      (heartbeat (:instance-id svc) :up)
      
      (instance-timeout (:instance-id svc))
      (is (= 1 (get-metadata (:instance-id svc) :timeouts))
          "Ensure that the :timeouts metadata is set on an instance")
      (is (= :up (get-metadata (:instance-id svc) :status))
          "Ensure the service that we have create has not had its state changed")

      (instance-timeout (:instance-id svc))
      (is (= 2 (get-metadata (:instance-id svc) :timeouts))
          "Ensure that the :timeouts metadata is set on an instance")
      (is (= :dead (get-metadata (:instance-id svc) :status))
          "Ensure that the instance state gets set to :dead once the instance times out"))))

(deftest test-that-there-be-zombies
  (testing "Testing that an instance will be marked as a zombie if it comes alive after it has been marked :dead"
    (let [svc (register-svc "zombie-service" {})]
      (System/setProperty "service.threshold" "2")
      (heartbeat (:instance-id svc) :up)
      (instance-timeout (:instance-id svc))
      (is (= 1 (get-metadata (:instance-id svc) :timeouts))
          "Ensure that the :timeouts metadata is set on an instance")
      (is (= :up (get-metadata (:instance-id svc) :status))
          "Ensure the service that we have create has not had its state changed")
      (heartbeat (:instance-id svc) :up)
      (is (= :up (get-metadata (:instance-id svc) :status))
          "Ensure the service that we have create has not had its state changed after 1 timeout and a heartbeat")
      (instance-timeout (:instance-id svc))
      (is (= 2 (get-metadata (:instance-id svc) :timeouts))
          "Ensure that the :timeouts metadata is set on an instance")
      (is (= :dead (get-metadata (:instance-id svc) :status))
          "Ensure that the instance state gets set to :dead once the instance times out")
      (heartbeat (:instance-id svc) :up)
      (is (= :zombie (get-metadata (:instance-id svc) :status))
          "Ensure that the instance is a ZOMBIE (state :zombie)"))))

(deftest test-that-down-removes-service
  (testing "Testing that an instance, once it is set to down, no longer exists"
    (let [svc (register-svc "my-down-service" {})]
      (is (get-instance (:instance-id svc))
          "Ensure that the service is created")
      (heartbeat (:instance-id svc) :down)
      (is (not  (get-instance (:instance-id svc)))
          "Ensure that the service is removed once the down state is set"))))
