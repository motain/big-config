(ns big-config.utils-test
  (:require
   [big-config :as bc]
   [big-config.run :as run]
   [big-config.utils :refer [default-step-fn step->workflow]]
   [clojure.test :refer [deftest is testing]]))

(defn test-step-fn [end-steps xs {:keys [f step opts]}]
  (when (end-steps step)
    (swap! xs conj opts))
  (default-step-fn {:f f
                    :step step
                    :opts opts}))

(def default-opts #:big-config.lock {:aws-account-id "111111111111"
                                     :region "eu-west-1"
                                     :ns "test.module"
                                     :fn "invoke"
                                     :owner "CI"
                                     :lock-keys [:big-config.lock/aws-account-id
                                                 :big-config.lock/region
                                                 :big-config.lock/ns]
                                     ::run/run-cmd "true"
                                     ::bc/test-mode true
                                     ::bc/env :repl})

(deftest step->workflow-test
  (testing "step->workflow"
    (let [expected {:big-config.utils-test/foo :bar, :big-config/steps [:big-config.utils-test/foo], :big-config/exit 1, :big-config/err "Error"}
          f (fn [opts]
              (merge opts
                     {::bc/exit 1
                      ::bc/err "Err"}))]
      (as-> ((step->workflow f ::foo "Error") {::foo :bar}) $
        (is (= expected $))))))
