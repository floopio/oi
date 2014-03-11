(ns oi.core-test
  (:require [oi.core :refer :all]
            [cheshire.core :refer :all]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]))

(deftest test-index-page
  (is (= (:status (handler (request :get "/"))) 200)))

(deftest test-get-no-service
  (is (= (:status (handler (request :get "/api/service/foo-service")) 404))))

(deftest test-put-and-get-service-instance
  (let [response (handler (request :put "/api/service/foo-service"))
        body (parse-string (:body response))]
    (is (= 201 (:status response))
        "Verify a 201 Created is returned for each newly registered service instance")
    (is (= "foo-service" (get body "svc-name")))
    (is (contains? body "instance-id"))
    (is (= "starting" (get-in body ["metadata" "status"]))
        "Verify the starting state is 'starting'")
    (let [instance-id (get body "instance-id")
          loc (str "/api/service/foo-service/" instance-id)]
      (is (= (get-in response [:headers "Location"]) loc)
          "Ensure that the Location header is returned with the correct location")
      (let [instance-resp (handler (request :get loc))]
        (is (= 200 (:status instance-resp))
            "Ensure that the location exists")))))

(deftest test-put-service-with-metadata
  (let [response (handler (body (request :put "/api/service/baz-service") (generate-string {:metadata {:foo1 "bar1" :foo2 "bar2"}})))
        svc (parse-string (:body response))]
    (is (= 201 (:status response))
        "Ensure the service instance is created.")
    (let [i-resp (handler (request :get (str "/api/service/baz-service/" (get svc "instance-id"))))
          instance (parse-string (:body response))]
      (is (= 200 (:status i-resp))
          "Ensure that the service instance exists.")
      (are [k v] (= v (get-in instance ["metadata" k]))
           "foo1" "bar1"
           "foo2" "bar2"))))

(deftest test-put-get-delete-metadata
  (let [response (handler (request :put "/api/service/bar-service"))
        svc (parse-string (:body response))]
    (is (= 201 (:status response)))
    (let [k "my-key"
          v "my-value"
          v2 (str v "2")
          instance-id (get svc "instance-id")]
      (is (= 404 (:status (handler (request :get (str "/api/service/bar-service/" instance-id "/" k)))))
          "Ensure custom metadata does not exist after creation.")
      (is (= 201 (:status (handler (body (request :put (str "/api/service/bar-service/" instance-id "/" k)) v))))
          "Ensure metadata is created when PUT to resource.")
      (let [md-resp (handler (request :get (str "/api/service/bar-service/" instance-id "/" k)))
            md (parse-string (:body md-resp))]
        (is (contains? md k)
            "Response contains the correct metadata key.")
        (is (= (get md k) v)
            "Response contains the correct metadata value."))
      (is (= 204 (:status (handler (body (request :put (str "/api/service/bar-service/" instance-id "/" k)) v2))))
          "Ensure we get a 204 when PUT to existing metadata resource.")
      (is (= (get (parse-string (:body (handler (request :get (str "/api/service/bar-service/" instance-id "/" k))))) k) v2)
          "Ensure the value of the metadata resource is updated.")
      (is (= 204 (:status (handler (request :delete (str "/api/service/bar-service/" instance-id "/" k)))))
          "A delete to a metadata resource should return a 204.")
      (is (= 404 (:status (handler (request :get (str "/api/service/bar-service/" instance-id "/" k)))))
          "A metadata resource should return a 404 after it is deleted."))))
