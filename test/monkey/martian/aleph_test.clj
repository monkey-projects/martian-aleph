(ns monkey.martian.aleph-test
  (:require [clojure.test :refer [deftest testing is]]
            [aleph
             [http :as http]
             [netty :as an]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [manifold.deferred :as md]
            [martian.core :as mc]
            [monkey.martian.aleph :as sut]))

(deftest aleph-request
  (testing "performs http request"
    (with-redefs [http/request (constantly (md/success-deferred
                                            {:status 200}))]
      (is (= 200
             (-> {:request
                  {:method :get
                   :url "http://test"}}
                 (sut/aleph-request)
                 :response
                 deref
                 :status))))))

(defn test-handler [req]
  (log/debug "Handling request:" req)
  (if (and (= :get (:request-method req))
           (= "/test" (:uri req)))
    {:status 200
     :body (json/generate-string {:key "value"})
     :headers {:content-type "application/json"}}
    {:status 406}))

(deftest integration-test
  (testing "full client/server test"
    (with-open [server (http/start-server test-handler {:port 0})]
      (let [port (an/port server)
            routes [{:route-name :test-route
                     :path-parts ["/test"]
                     :method :get
                     :produces ["application/json"]}]
            ctx (mc/bootstrap (str "http://localhost:" port) routes sut/default-opts)
            resp (mc/response-for ctx :test-route)]
        (is (= 200 (:status @resp)))
        (is (= {:key "value"} (:body @resp)))))))
