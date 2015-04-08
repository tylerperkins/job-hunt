

; ; ; ; ; ; ; ; ;   Replacing the "now" function, used below  ; ; ; ; ; ; ; ; ;


(ns job-hunt.model)

(defn now-its
  "For testing, replaces the 0-arg function job-hunt.model/now with a 0-arg
  function that always reports the given time-now-ms (a time value, in
  milliseconds)."
  [time-now-ms]
  (def now (constantly time-now-ms)))


; ; ; ; ; ; ; ; ; ; ; ; ;   Now to actual testing   ; ; ; ; ; ; ; ; ; ; ; ; ; ;


(ns job-hunt.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [clojure.edn :as edn]
            [job-hunt.handler :refer :all]
            [job-hunt.model :as model]
            ))


(defn send-and-read
  "Helper function to send a request and parse the response. An assertion is
  made verifying that the response has status 200 (OK). If the method is :post
  or :put, the given params map is URL-encoded and used as the body of the
  request, otherwise its contents are added to the query string. The response
  is assumed to contain a Clojure literal, which is parsed and returned. (Note
  that an empty string parses as nil.)
  "
  [method uri params]
  (let [maybe-body (if (#{:post :put} method)
                     #(mock/body % params)
                     identity)
        is-ok      (fn [response]
                     (is (= 200 (:status response))
                         (str
                           "--- Response in send-and-read was not 200 OK.\n"
                           "    " [method uri params]))
                     response)
        ]
    (-> (mock/request method uri)
        maybe-body
        app
        is-ok
        :body
        edn/read-string)))


(deftest test-app

  (testing "registering and reading jobs"
    (model/reset-state!)

    (is (= [] (send-and-read :get "/jobs" {})))

    (let [i1 (send-and-read :post "/job" {:total 10})
          i2 (send-and-read :post "/job" {:total 100})
          {t :total p :progress} (send-and-read :get "/job/1" {})
          ]
      (is (= [1 2] [i1 i2])
          "Ids should be distinct.")
      (is (= 2 (count (send-and-read :get "/jobs" {})))
          "Should have jobs for all successful posts.")
      (is (= [t p] [10 0])
          "Should retrieve existing job by id.")))

  (testing "deleting jobs"
      (is (= 1 (send-and-read :delete "/job/1" {}))
          "Should delete existing job and return it.")
      (is (= "Already deleted." (send-and-read :delete "/job/1" {}))
          "Should be OK to attempt to delete job again, but get message.")
      (is (= 404 (-> (mock/request :get "/job/1") app :status))
          "Deleted job should be gone.")
      (is (= 1 (count (send-and-read :get "/jobs" {})))
          "There should be just one job remaining.")
      (let [{t :total, p :progress} (send-and-read :get "/job/2" {})
            ]
        (is (= [t p] [100 0])
            "Other jobs should remain."))))


(deftest test-expiring-jobs
  (testing "expiring jobs"
    (model/reset-state!)

    (let [i1 (do (model/now-its 1000)
                 (send-and-read :post "/job" {:total 10}))
          i2 (do (model/now-its 1001)
                 (send-and-read :post "/job" {:total 100}))
          ]
      (is (= 1001 (:last-updated-ms (send-and-read :get "/job/2" {}))))

      (model/now-its 61000)      ; One minute after i1 created.
      (is (->> (str "/job/" i1) (mock/request :get) app :status (= 404))
          "Expired job should not be returned.")
      (send-and-read :get (str "/job/" i2) {})  ; Tests for 200 OK.

      (model/now-its 61001)      ; One minute after i2 created.
      (is (= [] (send-and-read :get "/jobs" {}))
          "All jobs should have expired and been removed."))))


(deftest test-updating-jobs
  (testing "updating jobs"
    (model/now-its 180000)             ; Time now 3 min.
    (is (= [] (send-and-read :get "/jobs" {}))
        "All jobs should have expired and been removed.")
    (let [i1 (send-and-read :post "/job" {:total  10})
          i2 (send-and-read :post "/job" {:total 100})
          uri1 (str "/job/" i1)
          uri2 (str "/job/" i2)
          _    (model/now-its 210000)  ; Time now 3.5 min.
          abs5 (send-and-read :put uri1 {:progress "_5"})
          up2  (send-and-read :put uri1 {:progress    2})
          {t1 :total p1 :progress} (send-and-read :get uri1 {})
          {t2 :total p2 :progress} (send-and-read :get uri2 {})
          ]
      (is (= [abs5 up2] [5 7])
          "Updating progress should return new progress value.")
      (is (= [t1 p1] [ 10 7])
          "Job's progress should reflect update.")
      (is (= [t2 p2] [100 0])
          "Other jobs should remain unmodified.")
      (model/now-its 240000)           ; Time now 4 min.
      (is (->> uri1 (mock/request :get) app :status (= 200))
          "Job 1 updated, so still alive.")
      (is (->> uri2 (mock/request :get) app :status (= 404))
          "Job 2 not updated, so should have expired.")
      (model/now-its 270000)            ; Time now 4.5 min.
      (is (= [] (send-and-read :get "/jobs" {}))
          "All jobs should have expired and been removed."))))


(deftest test-bad-input

  (testing "Catching invalid POST/PUT requests"
    (let [old-count (count (send-and-read :get "/jobs" {}))
          ]
      (are [total-str]
           (= 400
              (:status (app (mock/request :post "/job" {:total total-str}))))
           "-10" "0" "\"1\"" "[]")
      (is (= old-count (count (send-and-read :get "/jobs" {})))
          "Invalid POST/PUT should not affect state.")))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404))
      (is (= (:body response) "\"Not Found.\"")))))
